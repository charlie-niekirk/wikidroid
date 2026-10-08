plugins {
    id("wikidroid.android.library")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.data"
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.collections.immutable)

    // Implementation details: repositories expose only model and common types.
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.articleParser)
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.room3.runtime)
    testImplementation(libs.okhttp.mockwebserver3)
}
