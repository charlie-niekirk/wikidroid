plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.room")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.database"
    defaultConfig {
        // Migration tests run on a device: the room3 plugin puts the exported schemas in androidTest assets only.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(projects.core.common)

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.room3.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
}
