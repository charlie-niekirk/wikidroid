plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.compose")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.designsystem"
}

dependencies {
    api(projects.core.model)

    testImplementation(projects.core.testing)
}
