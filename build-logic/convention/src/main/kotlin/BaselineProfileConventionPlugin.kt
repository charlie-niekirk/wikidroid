import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `:baselineprofile` module: an instrumentation-only `com.android.test` project that drives `:app` on a
 * device and records which classes and methods it used.
 */
class BaselineProfileConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")
            pluginManager.apply("androidx.baselineprofile")
            pluginManager.apply("wikidroid.detekt")

            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                defaultConfig.apply {
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                targetProjectPath = ":app"
            }
        }
    }
}
