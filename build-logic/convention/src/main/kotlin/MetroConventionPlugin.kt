import dev.zacsweers.metro.gradle.MetroPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Metro is a compiler plugin; no KSP is involved. */
class MetroConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("dev.zacsweers.metro")
        target.extensions.configure<MetroPluginExtension> {
            // Lets `internal` classes carry @ContributesBinding: the module generates a public provider for
            // the binding, so :app's graph can use it without seeing the implementation class.
            generateContributionProviders.set(true)
        }
    }
}
