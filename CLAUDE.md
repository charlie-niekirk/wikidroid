# WikiDroid

Native Android client for minecraft.wiki (MediaWiki 1.45). Material 3, Compose, multi-module, Gradle convention plugins.
Full plan: `docs/PLAN.md`. Session checklist and deviations: `docs/PROGRESS.md`. Read both before starting a session.

## Git workflow
Repo: https://github.com/charlie-niekirk/wikidroid (default branch `main`). Never commit or push to `main`.
Each session = one branch `session-<N>-<slug>` + one PR into `main`; open the PR but don't merge it. Full steps (branching, PR body, review fixes) are in "Session protocol" in `docs/PLAN.md`.

## Module map
Modules are enabled in `settings.gradle.kts` (commented out until their session creates them).

```
build-logic/convention      included build, plugin ids `wikidroid.*`
app                         MainActivity, AppGraph (Metro), Nav3 host
core:model | common | article-parser | network | database | datastore | data
core:designsystem | ui | navigation | testing
feature:explore | search | article | library | settings     (never depend on each other; navigate via :core:navigation keys)
baselineprofile
```

## Conventions
- Package root `dev.cniekirk.wikidroid`; app id `dev.cniekirk.wikidroid`; minSdk 29; compileSdk 37.1; targetSdk 37; JDK 21.
- DI: Metro (compiler plugin, no KSP). Apply `wikidroid.metro` in every module with `@Inject`/`@Contributes*`.
- MVI: Orbit 12. Stateful wrapper + stateless `XxxScreen(state, onAction)`.
- Tests: JUnit 4 only. Robolectric for Compose UI tests on the JVM.
- Convention plugins: `wikidroid.android.application|library|compose|feature|room`, `wikidroid.metro`, `wikidroid.jvm.library`, `wikidroid.detekt`, `wikidroid.spotless` (root only).
- Do NOT apply `org.jetbrains.kotlin.android`: AGP 9 has built-in Kotlin.
- Versions live only in `gradle/libs.versions.toml`.
- Release signing reads `WIKIDROID_KEYSTORE_FILE/_PASSWORD`, `WIKIDROID_KEY_ALIAS/_PASSWORD` (env or Gradle properties); falls back to the debug key.
- No ads or monetisation (content is CC BY-NC-SA 3.0). Keep the "unofficial app" disclaimer in About.

## Commands
```
./gradlew spotlessApply                      # format
./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug     # "green"
./gradlew :app:assembleRelease
```
Needs `local.properties` with `sdk.dir=...` (gitignored) or `ANDROID_HOME`, and the SDK packages `platforms;android-37.1`, `build-tools;37.0.0`.
Open it with a stable Android Studio release that supports AGP 9.4 (see the AGP/Studio compatibility table); no canary needed.

## Version policy
Google/AndroidX libraries use the newest pre-release if newer than stable, otherwise stable. Everything else uses latest stable.
Approved exceptions: Detekt (2.0.0-alpha.6), and AGP, which stays on the latest *stable* (9.4.1) because the 9.5 alpha broke Android Studio sync.

## Workarounds and gotchas found so far
- Root `build.gradle.kts` lists every plugin with `apply false` (including spotless/detekt). Convention plugins use them `compileOnly`, so they must be on the root classpath. This also lifts AGP's Kotlin 2.2.10 floor.
- AGP 9 DSL: `compileSdk { version = release(37) { minorApiLevel = 1 } }`; `CommonExtension` is non-generic.
- Detekt 2.0 config schema differs from 1.x: no top-level `build:` block, no `LongParameterList` thresholds, `UnusedPrivateMember` is now `UnusedPrivateFunction`/`UnusedPrivateProperty`. Unknown keys fail the run, so check `config/detekt/detekt.yml` against the error's allowed list.
- activity-compose 1.14 alpha deprecates `enableEdgeToEdge()`; use `WindowCompat.enableEdgeToEdge(window)`.
- Metro 1.4.5 has no `checkMainMetroHiddenDependencies` task (see docs/PROGRESS.md open items).
- Material icons are frozen at 1.7.8; ship Material Symbols as vector drawables in `:core:designsystem`.
- Robolectric (SDK 37) on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`; `wikidroid.android.library` sets it on test tasks. Compose tests extend `ComposeTest` from `:core:testing`.
- Run `spotlessApply` as its own Gradle invocation, not alongside compile tasks (glob race on `build/`).
- Modules without `wikidroid.android.compose` that compile `@Composable` code (even in tests) must apply `org.jetbrains.kotlin.plugin.compose`.
- Metro provider containers are `@BindingContainer @ContributesTo(AppScope::class) object` (an interface with `@Provides` triggers a warning). Modules whose providers need `Application` or `@WikiBaseUrl HttpUrl` get them from the graph factory; test with a test-only `@DependencyGraph` (see `NetworkTestGraph`).
- `BundledSQLiteDriver` can't load on the host JVM (Android-ABI natives only). It is bound through `SqliteDriverProviders`; Robolectric tests replace it with `AndroidSQLiteDriver` via `@ContributesTo(replaces = ...)`.
- Room schemas are exported to `<module>/schemas` (set by `wikidroid.android.room`) and are committed. Bump `version` and add a migration when changing an entity.
- DataStore allows one active instance per file; tests that reopen a file must cancel the first store's scope.
- Network fixtures are trimmed real responses in `core/testing/src/main/resources/fixtures/network`; read them with `Fixtures.read("network/<name>.json")`.
- Android Studio may offer "Set up Kotlin"/"Configure Kotlin". Dismiss it: it adds `org.jetbrains.kotlin.android` and `kotlinOptions`, and AGP 9 then fails the sync ("no longer required for Kotlin support since AGP 9.0").
