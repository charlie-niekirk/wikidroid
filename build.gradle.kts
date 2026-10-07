// Plugins are put on the root classpath with `apply false` so every module resolves the same versions.
// This also overrides AGP 9's built-in Kotlin 2.2.10 floor with the Kotlin version from the catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    id("wikidroid.spotless")
}
