plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.compose")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(projects.core.common)
    api(libs.coil.compose)

    // Pane roles for the list/detail scene strategy (see WikiPanes).
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)

    // The ImageLoader provider shares the app's OkHttpClient (bound in :core:network).
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(projects.core.testing)
    testImplementation(libs.coil.test)
}
