package dev.cniekirk.wikidroid.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the paths a first-time reader takes: cold start, scrolling Explore, searching, opening an article and
 * reading it, then a pan across the seed map, a look at Library and Settings. It talks to the live wiki, so a
 * missing network only makes the profile smaller (every wait below gives up quietly).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            device.waitForText("Latest versions")
            device.scrollContent()

            device.openTab("Search")
            device.searchFor("Creeper")
            device.waitForText("Creeper", excludeEditable = true)?.click()
            // Opening an article parses and lays out the whole page; this is the slowest and most useful step.
            device.waitForScrollable()
            device.scrollContent()
            device.pressBack()

            device.openTab("Seed map")
            device.panMap()

            device.openTab("Library")
            device.waitForText("Bookmarks")
            device.openTab("Settings")
            device.waitForText("Appearance")
            device.openTab("Explore")
        }

    private companion object {
        const val PACKAGE_NAME = "dev.cniekirk.wikidroid"
        const val WAIT_MILLIS = 10_000L
        const val GESTURE_SPEED = 3_000
        const val PAN_STEPS = 40
        const val PAN_NEAR_EDGE = 0.25f
        const val PAN_FAR_EDGE = 0.75f
        const val MAP_DESCRIPTION = "Map of seed"
    }

    private fun UiDevice.waitForText(
        text: String,
        excludeEditable: Boolean = false,
    ) = wait(Until.findObjects(By.text(text)), WAIT_MILLIS)
        ?.firstOrNull { !excludeEditable || it.className != EDIT_TEXT }

    private fun UiDevice.waitForScrollable() = wait(Until.hasObject(By.scrollable(true)), WAIT_MILLIS)

    private fun UiDevice.openTab(label: String) {
        waitForText(label)?.click()
        waitForIdle()
    }

    private fun UiDevice.searchFor(query: String) {
        wait(Until.findObject(By.clazz(EDIT_TEXT)), WAIT_MILLIS)?.apply {
            click()
            text = query
        }
    }

    /** Drags across the map and back, which draws tiles at the first view and at the panned one. */
    private fun UiDevice.panMap() {
        wait(Until.hasObject(By.descStartsWith(MAP_DESCRIPTION)), WAIT_MILLIS)
        val left = (displayWidth * PAN_NEAR_EDGE).toInt()
        val right = (displayWidth * PAN_FAR_EDGE).toInt()
        val middle = displayHeight / 2
        swipe(right, middle, left, middle, PAN_STEPS)
        waitForIdle()
        swipe(left, middle, right, middle, PAN_STEPS)
        waitForIdle()
    }

    private fun UiDevice.scrollContent() {
        val list = findObject(By.scrollable(true)) ?: return
        list.setGestureMargin(displayWidth / GESTURE_MARGIN_DIVISOR)
        list.fling(Direction.DOWN, GESTURE_SPEED)
        waitForIdle()
        list.fling(Direction.UP, GESTURE_SPEED)
        waitForIdle()
    }
}

private const val EDIT_TEXT = "android.widget.EditText"
private const val GESTURE_MARGIN_DIVISOR = 5
