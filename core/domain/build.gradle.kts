plugins {
    id("wikidroid.android.library")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.domain"
}

// Use cases: the business logic the view models call. They sit on the repository and seed map engine interfaces.
dependencies {
    api(projects.core.data)
    api(projects.core.seedmap)

    testImplementation(projects.core.testing)
}
