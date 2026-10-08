package dev.cniekirk.wikidroid.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.cniekirk.wikidroid.core.ui.openWebUrl

/** Connects [AboutScreen] to the app's version and to the browser. */
@Composable
fun AboutRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { context.versionName() }
    AboutScreen(
        versionName = versionName,
        onOpenUrl = context::openWebUrl,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

private fun Context.versionName(): String? =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
