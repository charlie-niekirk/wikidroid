import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room3")

            // Exported schemas are committed, so every schema version has a reviewable JSON file.
            extensions.configure<RoomExtension>("room3") {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.library("androidx-room3-runtime"))
                add("implementation", libs.library("androidx-sqlite-bundled"))
                add("ksp", libs.library("androidx-room3-compiler"))
                add("testImplementation", libs.library("androidx-room3-testing"))
            }
        }
    }
}
