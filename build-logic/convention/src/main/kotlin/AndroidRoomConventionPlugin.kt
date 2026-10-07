import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room3")

            // The `room3 { schemaDirectory(...) }` block is configured in Session 3, once the
            // extension type is confirmed against the resolved plugin (see docs/PROGRESS.md).
            dependencies {
                add("implementation", libs.library("androidx-room3-runtime"))
                add("implementation", libs.library("androidx-sqlite-bundled"))
                add("ksp", libs.library("androidx-room3-compiler"))
                add("testImplementation", libs.library("androidx-room3-testing"))
            }
        }
    }
}
