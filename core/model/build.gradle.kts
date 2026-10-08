plugins {
    id("wikidroid.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    // Only the stability annotations (@Immutable) are used; no Compose runtime ends up on this JVM module.
    api(platform(libs.androidx.compose.bom.alpha))
    api(libs.androidx.compose.runtime.annotation)
    api(libs.kotlinx.collections.immutable)
    api(libs.kotlinx.serialization.json)
}
