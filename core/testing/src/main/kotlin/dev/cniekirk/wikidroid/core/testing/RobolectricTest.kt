package dev.cniekirk.wikidroid.core.testing

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base class for JVM tests that need the Android framework. `@RunWith` and `@Config` are inherited,
 * so the SDK level is chosen in one place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
abstract class RobolectricTest

/** [RobolectricTest] with a Compose rule, for testing stateless screens and components. */
abstract class ComposeTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()
}

internal const val ROBOLECTRIC_SDK = 37
