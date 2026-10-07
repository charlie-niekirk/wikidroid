import org.gradle.api.Plugin
import org.gradle.api.Project

/** Metro is a compiler plugin; no KSP is involved. */
class MetroConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("dev.zacsweers.metro")
    }
}
