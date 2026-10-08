plugins {
    id("wikidroid.android.library")
    id("wikidroid.metro")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.network"
}

dependencies {
    api(platform(libs.okhttp.bom))
    api(projects.core.common)
    // HttpUrl is part of the public API (the @WikiBaseUrl binding).
    api(libs.okhttp)
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver3)
}
