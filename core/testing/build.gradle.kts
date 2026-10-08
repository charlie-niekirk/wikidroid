plugins {
    id("wikidroid.android.library")
    // Compiler plugin only, so composable lambdas in this module's own tests compile.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.testing"
}

// Test fixtures live in src/main so other modules can use them via testImplementation(projects.core.testing).
dependencies {
    api(platform(libs.androidx.compose.bom.alpha))
    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    api(libs.robolectric)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.test.core)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.compose.foundation)
}
