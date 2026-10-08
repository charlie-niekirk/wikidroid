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
./gradlew :app:connectedDebugAndroidTest     # instrumented tests (needs a device or emulator, API 35 is the reference)
./gradlew :app:generateBaselineProfile       # regenerates app/src/release/generated/baselineProfiles/*-prof.txt (device, live wiki)
```
Needs `local.properties` with `sdk.dir=...` (gitignored) or `ANDROID_HOME`, and the SDK packages `platforms;android-37.1`, `build-tools;37.0.0`.
Open it with a stable Android Studio release that supports AGP 9.4 (see the AGP/Studio compatibility table); no canary needed.

## CI
`.github/workflows/pr.yml` (PRs and pushes to `main`): `checks` (spotless, detekt, lintDebug, unit tests, `checkMainMetroHiddenDependencies`), `instrumented` (API 35 x86_64 `google_apis` emulator, `connectedDebugAndroidTest`), `release-apk` (needs `checks`; uploads the APK and posts a sticky PR comment). Shared setup is the composite action `.github/actions/setup-android`. `:baselineprofile` is never run in CI. Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are optional; without them the APK is debug-signed. Setup is in `README.md`. From Session 11 on, all three jobs must be green before a PR is reported as ready.

## Version policy
Google/AndroidX libraries use the newest pre-release if newer than stable, otherwise stable. Everything else uses latest stable.
Approved exceptions: Detekt (2.0.0-alpha.6), and AGP, which stays on the latest *stable* (9.4.1) because the 9.5 alpha broke Android Studio sync.

## Workarounds and gotchas found so far
- Root `build.gradle.kts` lists every plugin with `apply false` (including spotless/detekt). Convention plugins use them `compileOnly`, so they must be on the root classpath. This also lifts AGP's Kotlin 2.2.10 floor.
- AGP 9 DSL: `compileSdk { version = release(37) { minorApiLevel = 1 } }`; `CommonExtension` is non-generic.
- Detekt 2.0 config schema differs from 1.x: no top-level `build:` block, no `LongParameterList` thresholds, `UnusedPrivateMember` is now `UnusedPrivateFunction`/`UnusedPrivateProperty`. Unknown keys fail the run, so check `config/detekt/detekt.yml` against the error's allowed list.
- activity-compose 1.14 alpha deprecates `enableEdgeToEdge()`; use `WindowCompat.enableEdgeToEdge(window)`.
- Metro 1.4.5 has no `checkMainMetroHiddenDependencies` task, so `:app` defines its own (every `:core:*`/`:feature:*` module must be a direct `implementation` dependency of `:app`). A new module therefore needs a line in `app/build.gradle.kts`.
- `wikidroid.metro` sets `generateContributionProviders = true`; without it `:app` cannot see `internal` `@ContributesBinding` classes from other modules.
- Compose params of type `Set`/`List` trip compose-rules `UnstableCollections`; use `ImmutableSet`/`ImmutableList`.
- Screens for a key are registered by a feature's `EntryProviderInstaller`; any key without one shows `PlaceholderScreen` (the `entryProvider` fallback in `:app`).
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
- jsoup 1.23 needs `compileOnly(libs.jspecify)` in any module that calls it, or Kotlin fails on inferred jsoup types.
- Never use `MutableList.removeLast()`/`removeFirst()` in code that runs on Android: on JDK 21 they bind to Java 21 methods missing on API 29-34. Use `removeAt(lastIndex)`.
- Article-parser HTML fixtures are in `core/article-parser/src/test/resources/fixtures` (the JVM module can't use `:core:testing`).
- `:core:testing` depends on `:core:data` (it holds the repository fakes). Fakes are plain classes with scriptable `var` handlers; they carry no Metro annotations, so they never join a graph.
- Repository implementations are `internal` (`@Inject @ContributesBinding @SingleIn`); feature modules see only the interfaces in `:core:data`.
- Coil tests: `FakeImageLoaderEngine` + `setSingletonImageLoaderFactory` (there is no `FakeImageLoader` in Coil 3). `PageThumbnail` uses the singleton loader, which the app must register from the graph's `ImageLoader`.
- Feature screens read ViewModels through `LocalMetroViewModelFactory`, which `MainActivity` provides; a screen test that calls a `Route` needs the same.
- List/detail panes: register list-like entries with `WikiPanes.list()` and article entries with `WikiPanes.detail()` (`:core:ui`); `ArticleEntryInstaller` registers `ArticleKey` that way (the `:app` placeholder is only a fallback).
- orbit-test: `expectInitialState()` is not a stream item; `expectState { copy() }` is relative to the last consumed state; `advanceUntilIdle()` ignores Orbit's tasks, so use `runCurrent()`/`advanceTimeBy()`. See "Session 7" in `docs/PROGRESS.md`.
- Search text lives in a `TextFieldState` in the screen, not in the ViewModel (async state round-trips drop keystrokes).
- Metro `@Assisted` has no identifier: assisted constructor parameters are matched by name, so use plain `@Assisted` with distinct names.
- Article tables are drawn by a custom grid `Layout` (`TableBlock`) because `rowspan` needs it. `TableCell.crafting` carries a recipe grid when a cell holds only a crafting widget.
- Don't force a box size and then apply `aspectRatio` inside it (the image overflows the box); fix the width and let the ratio set the height.
- Compose tests that tap a link tap its left edge, not the node's centre (`click(Offset(8f, centerY))`).
- Inline images are `RichSpan.image`. `RichText.isBlank` counts them as content; use `hasText` to ask whether there is readable text (a paragraph with only a block-sized picture is an `Image` block).
- Material 3 1.5 deprecates `ListItem(headlineContent = ...)` and `Slider(value, ...)`: use `ListItem(onClick|checked, ...) { headline }` and `Slider(state = SliderState, onValueChange = { state.value = it })`.
- External links go through `openWebUrl` in `:core:ui` (Custom Tabs, http/https only).
- A snackbar shown from a state field (`removed`, `message`) is acknowledged after `showSnackbar` returns; acknowledging first changes the effect key and cancels it.
- Instrumented tests run in `TestWikiApp` (swapped in by `WikiDroidTestRunner`), which points the graph at a `MockWebServer` serving `core/testing` fixtures; debug builds allow cleartext to localhost for it. Use `createAndroidComposeRule` v1: the `v2` one hangs the app on its splash screen.
- `WikiDroidApp.baseUrl()` is the seam for pointing the app at another server.
