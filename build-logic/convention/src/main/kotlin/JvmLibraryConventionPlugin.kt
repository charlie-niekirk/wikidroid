import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("wikidroid.detekt")
            configureKotlin()

            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("truth"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
