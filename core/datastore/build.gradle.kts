plugins {
    id("wikidroid.android.library")
    id("wikidroid.metro")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.datastore"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(libs.androidx.datastore)
    api(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
