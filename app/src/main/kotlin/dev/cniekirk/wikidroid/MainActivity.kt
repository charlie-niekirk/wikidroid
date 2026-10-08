package dev.cniekirk.wikidroid

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import dev.cniekirk.wikidroid.core.navigation.rememberNavigator
import dev.cniekirk.wikidroid.ui.AppShell
import kotlinx.collections.immutable.toImmutableSet

class MainActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as WikiDroidApp).graph

    private val viewModel: MainViewModel by viewModels { graph.metroViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash up until the saved theme is known, so a dark-mode user never sees a light flash.
        installSplashScreen().setKeepOnScreenCondition { viewModel.container.stateFlow.value.preferences == null }
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        val installers = graph.entryInstallers.toImmutableSet()
        setContent {
            val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
            val preferences = state.preferences ?: UserPreferences()
            val darkTheme =
                when (preferences.themeMode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            SystemBarIcons(window = window, darkTheme = darkTheme)
            WikiDroidTheme(themeMode = preferences.themeMode, dynamicColor = preferences.dynamicColor) {
                AppShell(navigator = rememberNavigator(), installers = installers)
            }
        }
    }
}

/** Edge-to-edge bars are transparent, so their icons must contrast with the app's theme, not the system's. */
@Composable
private fun SystemBarIcons(
    window: Window,
    darkTheme: Boolean,
) {
    val view = LocalView.current
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
