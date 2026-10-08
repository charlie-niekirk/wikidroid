plugins {
    id("wikidroid.android.feature")
}

android {
    namespace = "dev.cniekirk.wikidroid.feature.article"
}

dependencies {
    // Custom Tabs for "Open on wiki" and external links.
    implementation(libs.androidx.browser)

    testImplementation(libs.coil.test)
}
