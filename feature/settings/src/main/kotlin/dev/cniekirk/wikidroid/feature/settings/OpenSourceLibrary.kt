package dev.cniekirk.wikidroid.feature.settings

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class OpenSourceLibrary(
    val name: String,
    val licence: String,
    val url: String,
)

private const val APACHE_2 = "Apache License 2.0"

/** The projects WikiDroid is built with, for the About screen. Keep in step with `gradle/libs.versions.toml`. */
val OpenSourceLibraries: ImmutableList<OpenSourceLibrary> =
    persistentListOf(
        OpenSourceLibrary(
            "AndroidX, Jetpack Compose and Material 3",
            APACHE_2,
            "https://developer.android.com/jetpack/androidx",
        ),
        OpenSourceLibrary("Kotlin and kotlinx libraries", APACHE_2, "https://github.com/JetBrains/kotlin"),
        OpenSourceLibrary("Metro", APACHE_2, "https://github.com/ZacSweers/metro"),
        OpenSourceLibrary("Orbit MVI", APACHE_2, "https://github.com/orbit-mvi/orbit-mvi"),
        OpenSourceLibrary("Retrofit", APACHE_2, "https://github.com/square/retrofit"),
        OpenSourceLibrary("OkHttp", APACHE_2, "https://github.com/square/okhttp"),
        OpenSourceLibrary("Coil", APACHE_2, "https://github.com/coil-kt/coil"),
        OpenSourceLibrary("jsoup", "MIT License", "https://github.com/jhy/jsoup"),
        OpenSourceLibrary("Material Icons", APACHE_2, "https://fonts.google.com/icons"),
    )
