plugins {
    id("wikidroid.android.feature")
}

android {
    namespace = "dev.cniekirk.wikidroid.feature.article"
}

dependencies {
    testImplementation(libs.coil.test)
}
