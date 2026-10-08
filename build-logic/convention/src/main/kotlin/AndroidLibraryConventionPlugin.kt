import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("wikidroid.detekt")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
            }

            // Robolectric's SDK 37 sandbox reaches into JDK internals that JDK 21 doesn't export.
            tasks.withType<Test>().configureEach {
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }

            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("truth"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
