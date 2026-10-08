plugins {
    id("wikidroid.jvm.library")
    id("wikidroid.metro")
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)
}
