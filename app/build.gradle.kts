plugins {
    id("wikidroid.android.application")
    id("wikidroid.android.compose")
    id("wikidroid.metro")
}

android {
    namespace = "dev.cniekirk.wikidroid"

    defaultConfig {
        applicationId = "dev.cniekirk.wikidroid"
    }
}

// Metro only sees contributions from modules on the compile classpath, so every module that can
// contribute bindings must be a direct dependency of :app (a transitive `implementation` is invisible).
dependencies {
    implementation(projects.core.articleParser)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.feature.article)
    implementation(projects.feature.explore)
    implementation(projects.feature.library)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.metro.viewmodel)
    implementation(libs.metro.viewmodel.compose)
    implementation(libs.orbit.compose)
    implementation(libs.orbit.viewmodel)

    testImplementation(projects.core.testing)
    testImplementation(libs.orbit.test)
}

// `checkMainMetroHiddenDependencies` does not exist in Metro 1.4.5, so this is our own version of it:
// every :core:* and :feature:* module in the build must be a direct dependency of :app.
val directProjectDependencies: Provider<Set<String>> =
    configurations
        .named("implementation")
        .map { configuration ->
            configuration.dependencies
                .withType<ProjectDependency>()
                .map { it.path }
                .toSet()
        }
val contributingModules: Provider<Set<String>> =
    provider {
        rootProject.subprojects
            .map { it.path }
            .filter { it.startsWith(":core:") || it.startsWith(":feature:") }
            // :core:testing only provides fakes for tests; it never contributes to the graph.
            .filterNot { it == ":core:testing" }
            .toSet()
    }

tasks.register("checkMainMetroHiddenDependencies") {
    group = "verification"
    description = "Fails if a module that may contribute Metro bindings is not a direct dependency of :app."
    val expected = contributingModules
    val actual = directProjectDependencies
    doLast {
        val missing = expected.get() - actual.get()
        check(missing.isEmpty()) {
            "Metro cannot see contributions from modules that :app does not depend on directly: " +
                missing.sorted().joinToString() + ". Add them as `implementation(projects...)` in app/build.gradle.kts."
        }
    }
}

tasks.named("check") { dependsOn("checkMainMetroHiddenDependencies") }
