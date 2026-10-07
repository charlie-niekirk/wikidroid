plugins {
    `kotlin-dsl`
}

group = "dev.cniekirk.wikidroid.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeGradlePlugin)
    compileOnly(libs.kotlin.serializationGradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.metro.gradlePlugin)
    compileOnly(libs.room3.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "wikidroid.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "wikidroid.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "wikidroid.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "wikidroid.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "wikidroid.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("metro") {
            id = "wikidroid.metro"
            implementationClass = "MetroConventionPlugin"
        }
        register("jvmLibrary") {
            id = "wikidroid.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("detekt") {
            id = "wikidroid.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("spotless") {
            id = "wikidroid.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
    }
}
