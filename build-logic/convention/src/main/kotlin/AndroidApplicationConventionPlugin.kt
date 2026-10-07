import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("wikidroid.detekt")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)

                defaultConfig.apply {
                    targetSdk = TARGET_SDK
                    versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
                    versionName = APP_VERSION_NAME
                }

                // Release signing comes from WIKIDROID_KEYSTORE_* env vars or Gradle properties.
                // Without them (forks, local builds) the release build is signed with the debug key.
                val keystorePath = signingValue("WIKIDROID_KEYSTORE_FILE")
                val releaseSigning =
                    if (keystorePath != null && file(keystorePath).exists()) {
                        signingConfigs.create("release") {
                            storeFile = file(keystorePath)
                            storePassword = signingValue("WIKIDROID_KEYSTORE_PASSWORD")
                            keyAlias = signingValue("WIKIDROID_KEY_ALIAS")
                            keyPassword = signingValue("WIKIDROID_KEY_PASSWORD")
                        }
                    } else {
                        signingConfigs.getByName("debug")
                    }

                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = releaseSigning
                    }
                }
            }
        }
    }

    private fun Project.signingValue(name: String): String? =
        providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

    private companion object {
        const val APP_VERSION_NAME = "0.1.0"
    }
}
