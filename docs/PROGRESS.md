# Progress

Definition of green: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`.

- [x] Session 1: Repo bootstrap and convention plugins
- [x] Session 2: Foundation core modules
- [ ] Session 3: Data sources (network, database, datastore)
- [ ] Session 4: Article parser
- [ ] Session 5: Repositories and shared UI
- [ ] Session 6: App shell
- [ ] Session 7: `:feature:explore` and `:feature:search`
- [ ] Session 8: `:feature:article`
- [ ] Session 9: `:feature:library` and `:feature:settings`
- [ ] Session 10: Instrumented tests and baseline profile
- [ ] Session 11: CI and docs

## Session 1 notes
Verified: `:app:assembleDebug :app:assembleRelease spotlessCheck detekt` pass; debug and release APKs install and launch on an API 37 arm64 emulator (Pixel_10_Pro AVD) with no crash. Release APK is debug-signed (fallback path, no keystore configured).

### Deviations
- `profileinstaller`: newest release is stable 1.4.1 (no newer alpha), so stable is used.
- Installed `build-tools;37.0.0` via sdkmanager. `platforms;android-37.1` was already present.
- Convention plugin `wikidroid.android.room` applies KSP + `androidx.room3` and adds dependencies, but does not yet configure `room3 { schemaDirectory(...) }`. The extension type is unconfirmed; do this in Session 3.
- `wikidroid.android.feature` and the Compose plugin reference modules that don't exist yet (`:core:*`); they are not applied until Session 2+.
- Launcher icon is a placeholder vector "W"; the placeholder theme is `android:Theme.Material.Light.NoActionBar`, so status-bar icons are low contrast. Session 6 replaces the theme.

### Open TODOs
- **`:app:checkMainMetroHiddenDependencies` does not exist** in Metro gradle plugin 1.4.5 (no such task listed by `:app:tasks --all`). Session 6 "Done when" and the Session 11 `checks` job reference it. Decide in Session 6: find the replacement Metro diagnostic, or drop the check and rely on the app graph compiling.
- Gradle prints deprecation warnings ("incompatible with Gradle 10"); not yet investigated.
- `local.properties` (gitignored) holds `sdk.dir` on this machine.

## Session 2 notes
Verified: `./gradlew spotlessCheck detekt testDebugUnitTest :core:model:test :core:common:test assembleDebug` and `:app:assembleRelease` pass. 47 unit tests across the five new modules, all green. Not run on an emulator (nothing in this session changes launch behaviour beyond the theme).

Modules added: `:core:model`, `:core:common`, `:core:designsystem`, `:core:navigation`, `:core:testing`. `:app` now depends on `common`, `designsystem`, `model` and `navigation`; `MainActivity` uses `WikiDroidTheme`, and `AppGraph` exposes `@IoDispatcher` so the build proves `:core:common`'s Metro contributions reach the graph.

### Deviations
- **`ArticleSection` is not generic.** The plan wrote `ArticleSection<ContentBlock>`; it is a plain `ArticleSection(heading, blocks)`. The lead section has a `null` heading; deeper headings stay in `blocks`. `Article.tableOfContents` derives the TOC from them.
- **Extra model types** beyond the plan's list: `LibraryEntry`, `LatestVersions`, `CategoryMember`, `Paged<T>`, `ListItem`, `TableCell`, `InfoboxRow`, `CraftingSlot`. `Category` holds the name without the `Category:` prefix. `ContentBlock.Heading.text` is a plain `String`, not `RichText`. Session 4 may adjust these minimally.
- **Recent searches are not in `UserPreferences`.** The model has exactly the five fields from the plan; Session 3 decides how the datastore file wraps them.
- **Icons are Material Icons glyphs, not Material Symbols exports.** The 24 vector drawables in `:core:designsystem` use the classic filled Material Icons outlines (written by hand, no network access to export from fonts.google.com). Expose them via `WikiIcons` and `WikiIcon`. Swap in real Symbols exports later if the style matters; the resource names (`ic_*`) can stay.
- **Colour scheme values are hand-picked** (grass/stone/diamond), not generated from the Material Theme Builder.
- **`Navigator` exposes only the selected tab's stack.** `backStack` is that stack; `goBack()` pops, or from a non-start tab's root returns to Explore, or returns `false` (the shell should then let the system handle back). Session 6 should feed `NavDisplay` from `navigator.backStack` and call `goBack()` from `onBack`. The plan's "start tab + current tab flattened" idea was not used. Pushing the key already on top is ignored. State is saved as a JSON string via `Navigator.saver()` (`rememberNavigator()`), because the back stack is a sealed `WikiKey` hierarchy (no polymorphic registration needed).
- **`EntryProviderInstaller` is a `fun interface`** with `EntryProviderScope<NavKey>.install(navigator: Navigator)`, so installers can push keys without depending on other features.
- **`:core:model` pulls in `runtime-annotation` (BOM-managed) for `@Immutable`**; no Compose runtime reaches the JVM module.
- **`:core:testing` applies only the Compose compiler plugin** (not `wikidroid.android.compose`), so its own composable tests compile without dragging in Material 3.

### Workarounds
- **Robolectric 4.17 runs SDK 37, but on JDK 21 it needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`** (its `FileDescriptorInterceptor` hits `SharedSecrets`). Set for every test task in `wikidroid.android.library`. The `@Config(sdk = [36])` fallback was not needed. The SDK level lives in one place, `core:testing/.../RobolectricTest.kt`.
- Running `spotlessApply` in the same Gradle invocation as compile tasks can fail with "Could not read path ... build/kotlin/..." (the `**/*.kts` glob races the compiler). Run Spotless as its own invocation.

- **Android Studio sync failed** with `Could not create task ':<module>:generateReleaseComposePreviewRunfiles' ... Unit tests are disabled for this variant`. AGP 9.5 alpha only enables unit tests for the debug variant, yet Studio's sync creates a Compose Preview task for release too. Fixed with `android.onlyEnableUnitTestForTheTestedBuildType=false` in `gradle.properties`, which also adds `testReleaseUnitTest` tasks (`testDebugUnitTest` stays the "green" command). Command-line builds never hit this, so `./gradlew tasks --all` is the quick reproduction. Revisit when AGP leaves alpha.

### Open TODOs
- Session 3 still has to configure `room3 { schemaDirectory(...) }` (carried over from Session 1).
- `:app:checkMainMetroHiddenDependencies` decision is still open for Session 6 (carried over).
