import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("wikidroid.android.library")
            pluginManager.apply("wikidroid.android.compose")
            pluginManager.apply("wikidroid.metro")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))

                add("implementation", libs.library("orbit-viewmodel"))
                add("implementation", libs.library("orbit-compose"))
                add("implementation", libs.library("metro-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation3-runtime"))
                add("implementation", libs.library("kotlinx-collections-immutable"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.library("orbit-test"))
            }
        }
    }
}
