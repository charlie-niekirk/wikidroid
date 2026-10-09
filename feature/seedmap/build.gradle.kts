plugins {
    id("wikidroid.android.feature")
}

android {
    namespace = "dev.cniekirk.wikidroid.feature.seedmap"
}

dependencies {
    implementation(projects.core.seedmap)
}
