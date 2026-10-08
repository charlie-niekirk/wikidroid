package dev.cniekirk.wikidroid

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.cniekirk.wikidroid.core.model.Edition
import dev.cniekirk.wikidroid.core.model.ThemeMode
import dev.cniekirk.wikidroid.core.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Generous: the first screen of a cold process waits on Room, DataStore and the mock server. */
const val UI_TIMEOUT_MILLIS = 15_000L

val testApp: TestWikiApp get() = ApplicationProvider.getApplicationContext()

/**
 * Room and DataStore files outlive a test, so every test starts from an empty library, default settings and no
 * cached articles. Put this *outside* the Compose rule (`RuleChain`) so it runs before the activity starts.
 */
class ResetAppStateRule : ExternalResource() {
    override fun before() {
        runBlocking {
            val graph = testApp.graph
            graph.libraryRepository.bookmarks
                .first()
                .forEach { graph.libraryRepository.removeBookmark(it.title) }
            graph.libraryRepository.clearHistory()
            graph.articleRepository.clearCache()
            graph.searchRepository.clearRecentSearches()
            graph.settingsRepository.apply {
                setThemeMode(ThemeMode.System)
                setDynamicColor(true)
                setTextScale(UserPreferences.DEFAULT_TEXT_SCALE)
                setPreferredEdition(Edition.Java)
                setSaveHistory(true)
            }
        }
    }
}

private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/** The bottom-bar (or rail) item; the label alone also matches screen titles. */
fun ComposeTestRule.tab(label: String): SemanticsNodeInteraction = onNode(hasText(label) and isTab)

fun ComposeTestRule.openTab(label: String) {
    tab(label).performClick()
}

fun ComposeTestRule.waitForText(
    text: String,
    timeoutMillis: Long = UI_TIMEOUT_MILLIS,
) = waitUntilAtLeastOneExists(hasText(text), timeoutMillis)

fun ComposeTestRule.clickText(text: String) {
    waitForText(text)
    onNodeWithText(text).performClick()
}
