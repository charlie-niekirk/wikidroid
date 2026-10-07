import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Works for both application and library modules; apply it after the Android plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom-alpha"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("testImplementation", bom)

                add("implementation", libs.library("androidx-compose-ui"))
                add("implementation", libs.library("androidx-compose-ui-graphics"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.library("androidx-compose-foundation"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))

                add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("testImplementation", libs.library("robolectric"))
                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))

                add("detektPlugins", libs.library("compose-rules-detekt"))
            }
        }
    }
}
