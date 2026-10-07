import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applied once at the root; targets use globs so they cover every module and build-logic. */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            val ktlintVersion = libs.version("ktlint")
            val composeRules =
                libs.library("compose-rules-ktlint").get().let {
                    "${it.module}:${it.versionConstraint.requiredVersion}"
                }

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**", "**/.gradle/**")
                    ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
                }
                kotlinGradle {
                    target("**/*.kts")
                    targetExclude("**/build/**", "**/.gradle/**")
                    ktlint(ktlintVersion)
                }
            }
        }
    }
}
