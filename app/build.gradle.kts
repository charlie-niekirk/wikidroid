plugins {
    id("wikidroid.android.application")
    id("wikidroid.android.compose")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid"

    defaultConfig {
        applicationId = "dev.cniekirk.wikidroid"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.navigation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
