plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.room")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.database"
}

dependencies {
    api(projects.core.common)

    testImplementation(projects.core.testing)
}
