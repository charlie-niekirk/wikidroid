package dev.cniekirk.wikidroid.core.common

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.time.Clock

/** Injecting the clock, rather than calling `Clock.System`, lets tests control timestamps. */
@BindingContainer
@ContributesTo(AppScope::class)
object ClockProviders {
    @Provides
    fun provideClock(): Clock = Clock.System
}
