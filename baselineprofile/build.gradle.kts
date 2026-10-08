plugins {
    id("wikidroid.baselineprofile")
}

android {
    namespace = "dev.cniekirk.wikidroid.baselineprofile"
}

baselineProfile {
    // Generated on whatever device or emulator is connected; run `:app:generateBaselineProfile` locally.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
