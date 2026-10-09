plugins {
    id("wikidroid.android.library")
    id("wikidroid.android.ndk")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid.core.seedmap"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    // Only the stability annotations (@Immutable) are used, as in :core:model.
    api(platform(libs.androidx.compose.bom.alpha))
    api(libs.androidx.compose.runtime.annotation)
    api(libs.kotlinx.coroutines.core)

    implementation(projects.core.common)

    androidTestImplementation(libs.kotlinx.coroutines.test)
}
