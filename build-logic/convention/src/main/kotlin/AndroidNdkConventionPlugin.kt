import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Builds C code in an Android library with CMake. Apply after `wikidroid.android.library`.
 * The module keeps its CMake project at `src/main/cpp/CMakeLists.txt` and runs its native tests as
 * instrumented tests (the shared library cannot load on the host JVM).
 */
class AndroidNdkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(pluginManager.hasPlugin("com.android.library")) {
                "wikidroid.android.ndk must be applied after wikidroid.android.library"
            }

            extensions.configure<LibraryExtension> {
                ndkVersion = libs.version("ndk")
                externalNativeBuild {
                    cmake {
                        path = file("src/main/cpp/CMakeLists.txt")
                        version = libs.version("cmake")
                    }
                }
                defaultConfig {
                    // 32-bit ABIs are not shipped: the app targets 64-bit devices and x86_64 emulators.
                    ndk.abiFilters += listOf("arm64-v8a", "x86_64")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            dependencies {
                add("androidTestImplementation", libs.library("androidx-test-runner"))
                add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
                add("androidTestImplementation", libs.library("truth"))
            }
        }
    }
}
