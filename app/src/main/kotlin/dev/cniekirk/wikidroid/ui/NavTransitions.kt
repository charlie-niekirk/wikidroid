package dev.cniekirk.wikidroid.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * Push and pop slide horizontally: the new screen comes in from the end edge while the covered one drifts a quarter of
 * the way towards the start, and a pop (or the back gesture) reverses it. [SlideDirection.Start] and
 * [SlideDirection.End] follow the layout direction, so right-to-left layouts mirror it.
 *
 * Tab switches never reach these: `AppNavDisplay` keys its `NavDisplay` by tab, so a new tab starts on its scene.
 * `NavDisplay` sets the z-order itself (a popped screen stays on top while it leaves).
 */
internal object NavTransitions {
    const val DURATION_MILLIS = 400

    /** The covered screen moves this fraction of the width. */
    private const val PARALLAX_DIVISOR = 4

    // Material 3's emphasized-decelerate curve.
    private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    private val slide = tween<IntOffset>(DURATION_MILLIS, easing = emphasizedDecelerate)

    // The back gesture seeks the animation by its own progress, which the system has already eased.
    private val gesture = tween<IntOffset>(DURATION_MILLIS, easing = LinearEasing)

    val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideIntoContainer(SlideDirection.Start, slide) togetherWith
            slideOutOfContainer(SlideDirection.Start, slide) { it / PARALLAX_DIVISOR }
    }

    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = { popTransform(slide) }

    val predictivePop: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
        popTransform(
            gesture,
        )
    }

    private fun AnimatedContentTransitionScope<Scene<NavKey>>.popTransform(
        spec: FiniteAnimationSpec<IntOffset>,
    ): ContentTransform =
        slideIntoContainer(SlideDirection.End, spec) { it / PARALLAX_DIVISOR } togetherWith
            slideOutOfContainer(SlideDirection.End, spec)
}
