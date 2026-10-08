plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
