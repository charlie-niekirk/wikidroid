import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal const val COMPILE_SDK = 37
internal const val COMPILE_SDK_MINOR = 1
internal const val TARGET_SDK = 37
internal const val MIN_SDK = 29
internal const val BUILD_TOOLS = "37.0.0"
internal const val JDK_VERSION = 21

/** Shared Android settings for both application and library modules. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version =
                release(COMPILE_SDK) {
                    minorApiLevel = COMPILE_SDK_MINOR
                }
        }
        buildToolsVersion = BUILD_TOOLS
        defaultConfig.apply {
            minSdk = MIN_SDK
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    configureKotlin()
}

/**
 * Shared Kotlin settings. AGP 9 provides built-in Kotlin, so this only configures the
 * extension and compile tasks; `org.jetbrains.kotlin.android` must not be applied.
 */
internal fun Project.configureKotlin() {
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(JDK_VERSION)
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(
                providers.gradleProperty("wikidroid.warningsAsErrors").map(String::toBoolean).orElse(false),
            )
        }
    }
}
