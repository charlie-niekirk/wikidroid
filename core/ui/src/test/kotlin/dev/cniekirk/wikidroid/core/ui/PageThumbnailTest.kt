package dev.cniekirk.wikidroid.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.test.FakeImageLoaderEngine
import dev.cniekirk.wikidroid.core.designsystem.theme.WikiDroidTheme
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

/** Answers every image request with a solid colour, so no test touches the network. */
private fun fakeImageLoader(context: PlatformContext): ImageLoader {
    val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.Red.toArgb())).build()
    return ImageLoader.Builder(context).components { add(engine) }.build()
}

class PageThumbnailTest : ComposeTest() {
    @Test
    fun withAnUrlItExposesTheImageDescription() {
        composeRule.setContent {
            setSingletonImageLoaderFactory(::fakeImageLoader)
            WikiDroidTheme(dynamicColor = false) {
                PageThumbnail(url = "https://minecraft.wiki/images/Diamond.png", contentDescription = "Diamond")
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Diamond").assertIsDisplayed()
    }

    @Test
    fun withoutAnUrlItShowsOnlyThePlaceholder() {
        composeRule.setContent {
            WikiDroidTheme(dynamicColor = false) {
                PageThumbnail(url = null, contentDescription = "Diamond")
            }
        }

        composeRule.onNodeWithContentDescription("Diamond").assertDoesNotExist()
    }

    @Test
    fun nonPixelatedImagesCompose() {
        composeRule.setContent {
            setSingletonImageLoaderFactory(::fakeImageLoader)
            WikiDroidTheme(dynamicColor = false) {
                PageThumbnail(
                    url = "https://minecraft.wiki/images/Creeper.png",
                    contentDescription = "Creeper",
                    pixelated = false,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Creeper").assertIsDisplayed()
    }
}
