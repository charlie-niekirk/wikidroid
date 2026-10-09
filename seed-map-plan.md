# Seed map: plan (Sessions 12–15)

## Context
WikiDroid is a finished wiki client (Sessions 1–11). The research proposes a Chunkbase-style **seed map**: for a given seed and Minecraft version, show biomes and structures on a map you can pan and zoom. Tapping a biome or structure opens its minecraft.wiki article, which is what sets it apart from Chunkbase. The engine is the **xpple/cubiomes** fork (MIT, current to MC 26.3), called from Kotlin through a thin **JNI** wrapper. Its own Java bindings use the FFM API, which Android doesn't have.

Decisions already made:
- **A fifth bottom-bar tab** (`SeedMapKey : TopLevelKey`), not an Explore card.
- **The version picker offers every Java version cubiomes supports** (Beta 1.7 through 26.3). Java Edition only; Bedrock is out.
- **Saved seeds live in the existing JSON DataStore** (no Room schema bump).
- **Order of work:** after approval, open a `docs/seed-map-plan` PR first. It adds Sessions 12–15 to `docs/PLAN.md`, checklist lines to `docs/PROGRESS.md` and new modules to the `CLAUDE.md` module map. Session 12 starts only when the user asks.

Housekeeping: the working tree has unrelated uncommitted edits (`ExploreScreen.kt`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties`). Leave them alone and branch from `main` without them. Before the docs PR, check that the Session 11 PR is merged; the session protocol says to stop otherwise.

## Architecture (applies to all four sessions)

**New modules** (each gets a line in `settings.gradle.kts` and a direct `implementation` in `app/build.gradle.kts`, for the custom `checkMainMetroHiddenDependencies` task):
- `:core:seedmap` (Android library: `wikidroid.android.library`, `wikidroid.android.ndk`, `wikidroid.metro`)
  - `src/main/cpp/cubiomes`: git submodule of `xpple/cubiomes`, pinned to a commit.
  - `src/main/cpp/CMakeLists.txt`: builds a static lib from only the cubiomes sources we need (generator, layers, biomes, noise, finders, util, quadbase). It leaves out `loot/`, carvers and ores. Flags: `-fwrapv -O2 -ffunction-sections -fdata-sections`, `-Wl,--gc-sections`, stripped. Links into `libseedmap.so`.
  - `src/main/cpp/seedmap_jni.c`: the only C we own. Results go back to Kotlin as **primitive arrays only** (`IntArray`/`LongArray` packed `[type, x, z, …]`). JNI never builds Kotlin objects, so there are no class lookups by name and the default `proguard-android-optimize.txt` rule for `native` methods covers R8. A `consumer-rules.pro` that keeps the `external fun` holder is added anyway as belt and braces.
  - Kotlin: public `SeedMapEngine` interface, models (`McVersion` enum mirroring cubiomes `MCVersion` ints, `Dimension`, `StructureType`, `BiomeTile`, `StructurePos`, `TileKey`), `BiomeWikiTitles`/`StructureWikiTitles`, and `TileRenderer`/`TileCache`. `internal class JniSeedMapEngine @Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class)`, matching the repositories. `System.loadLibrary` runs lazily inside the impl, so Robolectric tests never touch the `.so` (same problem as `BundledSQLiteDriver`).
- `:feature:seedmap` (`wikidroid.android.feature`, plus `implementation(projects.core.seedmap)`).

**Threading and native safety:**
- One native `Generator*` per worker, kept in a fixed-size pool (`n = availableProcessors - 1`, minimum 1). `isViableStructurePos` mutates the generator, so workers can't share one.
- Work runs on `Dispatchers.Default.limitedParallelism(n)`. Native calls can't be cancelled, so cancellation is checked between tiles and tiles are kept to 256×256 cells.
- Kotlin validates every input before it reaches C: version in range, dimension supported for that version, region size capped. A native crash would kill the app.
- Handles are `Long` pointers, freed by `close()` on the pool.

**Rendering:** the engine returns biome ids (`IntArray`). Kotlin colours them using a palette fetched once from `initBiomeColors` through JNI, then writes the result with `Bitmap.setPixels`. That keeps the engine testable without graphics. `TileCache` is an `LruCache<TileKey, Bitmap>` sized in bytes (about 1/8 of `memoryClass`). `TileKey = (seed, version, dimension, scale, tx, tz)`. Scales are 1:4, 1:16, 1:64 and 1:256, chosen from the zoom; while a finer tile loads, the coarser one is drawn scaled up.

**Navigation:** add `SeedMapKey` to the sealed `TopLevelKey` in `core/navigation/.../NavKeys.kt`, and insert it in `TopLevelKeys` as Explore, Search, **Seed map**, Library, Settings. Extend the exhaustive `when`s in `app/src/main/kotlin/dev/cniekirk/wikidroid/ui/TopLevelDestination.kt` (a new `WikiIcons.Map` Material Symbols drawable in `:core:designsystem`, and `R.string.tab_seed_map`). The feature's `EntryProviderInstaller` registers `SeedMapKey` with `WikiPanes.list()`, so an article opened from the map sits beside it on wide screens. Taps push `ArticleKey(title)`.

**Saved seeds:**
- Add `savedSeeds: List<SavedSeed> = emptyList()` to `StoredUserData` in `core/datastore/src/main/kotlin/dev/cniekirk/wikidroid/core/datastore/StoredUserData.kt`. The field has a default, so old files still read.
- `SavedSeed(seed: Long, label: String?, version: String)` lives in `:core:model`. The version is stored by name, so an enum change can't corrupt old data.
- `PreferencesDataSource` gets a `savedSeeds` flow and `saveSeed`/`removeSeed`, following the `recentSearches` pattern in the same file.
- A new `SeedRepository` interface plus an `internal` implementation in `:core:data`, modelled on `SearchRepository`'s recent searches, with a `FakeSeedRepository` in `:core:testing`.

**Testing split:**
- JVM/Robolectric: `FakeSeedMapEngine` in `:core:testing`, a plain class with `var` handlers and no Metro annotations. `core/testing/build.gradle.kts` gains `api(projects.core.seedmap)` next to `api(projects.core.data)`.
- Instrumented (API 35 x86_64 emulator): golden tests against the real `.so` in `core/seedmap/src/androidTest`.
- Plain JVM unit tests for the pure Kotlin parts: seed parsing, wiki-title maps, tile maths.

## Session 12: Native groundwork (`session-12-seedmap-native`)
- **Versions** (`gradle/libs.versions.toml` only):
  - `ndk` and `cmake` entries, chosen under the version policy (Google tooling uses the newest pre-release if newer than stable).
  - This machine has NDK `28.2.13676358` (r28, 16 KB-aligned by default) and CMake `3.22.1`. Check sdkmanager for newer ones at session time.
- **Convention plugin** `build-logic/convention/src/main/kotlin/AndroidNdkConventionPlugin.kt`, id `wikidroid.android.ndk`, registered in `build-logic/convention/build.gradle.kts`. On `LibraryExtension` it sets:
  - `ndkVersion` and `externalNativeBuild.cmake { path = "src/main/cpp/CMakeLists.txt"; version }`, using `libs.version(...)`
  - `defaultConfig.ndk.abiFilters += arm64-v8a, x86_64`
  - `testInstrumentationRunner = AndroidJUnitRunner`
  - androidTest dependencies: test runner, ext-junit, truth
- **Submodule:** `git submodule add https://github.com/xpple/cubiomes core/seedmap/src/main/cpp/cubiomes`, then check out a specific commit. Record the commit and MC_NEWEST in PROGRESS.md.
- **JNI, first slice:** `nativeCreate(version, flags)`, `nativeDestroy`, `nativeApplySeed(dim, seed)`, `nativeGetBiomeAt(scale, x, y, z)`, `nativeGetSpawn` (returns `IntArray(2)`), `nativeVersionName(int)` (`mc2str`) and `nativeNewestVersion()`. The Kotlin `SeedMapEngine` starts with `spawn()` and `biomeAt()` only.
- **`McVersion` enum:** covers every cubiomes version. An instrumented test asserts every Kotlin entry's int maps to the same `mc2str` name, so the enum and the submodule can't drift apart unnoticed.
- **Golden instrumented tests** (`core/seedmap/src/androidTest/.../SeedMapEngineGoldenTest.kt`): for 2–3 seeds across at least three versions (for example 1.12, 1.18 and 26.3), check spawn and five biomes at fixed points. Expected values come from a one-off host build of the pinned cubiomes on the Mac (a throwaway C program in the scratchpad, not committed), spot-checked against Chunkbase. The source of each value goes in the test's KDoc.
- **CI:**
  - `.github/actions/setup-android/action.yml`: add `"ndk;<ver>" "cmake;<ver>"` to the sdkmanager line, reading both versions from `libs.versions.toml` with `sed` so they live in one place.
  - `.github/workflows/pr.yml`: `actions/checkout@v7` gets `submodules: true` in all three jobs. The `instrumented` command becomes `./gradlew :app:connectedDebugAndroidTest :core:seedmap:connectedDebugAndroidTest`.
  - `release-apk`: a step that unzips `lib/*/libseedmap.so` and runs `llvm-readelf -l` from the NDK to assert every `LOAD` segment has `Align 0x4000` (16 KB pages). It also logs the `.so` sizes in the PR comment.
  - `.github/dependabot.yml`: add a `gitsubmodule` entry (weekly).
- **Wiring:** `:app` depends on `:core:seedmap` (nothing uses it in the UI yet).
- **Docs:** the `CLAUDE.md` module map, plus new gotchas (submodule checkout, NDK/CMake versions in the TOML, `.so` not loadable in Robolectric), README (`git clone --recurse-submodules`).
- **Done when:** green, plus `:app:checkMainMetroHiddenDependencies` and `:app:assembleRelease`. `:core:seedmap:connectedDebugAndroidTest` passes on the local API 35 emulator, and all three CI jobs are green with the 16 KB check passing.

## Session 13: Engine API, tiles and ViewModel (`session-13-seedmap-engine`)
- **JNI additions:**
  - `nativeGenBiomes(handle, scale, x, z, w, h, y): IntArray` (`genBiomes` with a `Range`)
  - `nativeStructures(handle, structType, x0, z0, x1, z1): IntArray` (`getStructurePos` per region plus `isViableStructurePos`)
  - `nativeStrongholds(handle, count): IntArray` (`initFirstStronghold`/`nextStronghold`)
  - `nativeBiomeColors(): IntArray`
  - `nativeStructureSupported(type, version, dim)`
- **Kotlin:**
  - `GeneratorPool` (fixed size, re-seeds every generator on a seed, version or dimension change)
  - `JniSeedMapEngine` exposing `suspend` `biomeTile`, `structuresIn`, `strongholds`, `spawn` and `biomeAt`
  - `TileRenderer` (palette → `Bitmap`)
  - `TileCache`
  - `SeedParser`: numeric text parses as a `Long`; any other text uses Java's `String.hashCode()`, as the game does; blank picks a random seed.
- **Wiki tie-in:**
  - `BiomeWikiTitles` maps ids to page titles: `biome2str` id title-cased by default, plus overrides for old names (for example `extreme_hills` → "Windswept Hills" or the old page, whichever exists).
  - `StructureWikiTitles` covers ids such as Swamp_Hut → "Witch Hut" and Treasure → "Buried Treasure".
  - A JVM test asserts every `StructureType` and every biome id that cubiomes can return for any `McVersion` has a title (the id list is a generated constant, re-checked by an instrumented test).
- **Saved seeds:** a DataStore field and serializer round-trip test, a repository method in `:core:data`, and a fake.
- **`:feature:seedmap` skeleton:**
  - `SeedMapViewModel` (`@ViewModelKey @ContributesIntoMap`, `OrbitContainerHost`). Its state holds seed, version, dimension, structure toggles (`ImmutableSet`), saved seeds, viewport and selection.
  - Intents: change seed/version, toggle structures, save/delete seed, select a point, go to spawn or coordinates.
  - Structure queries are debounced per viewport change. Tiles are not in VM state: the screen pulls them from `TileCache` (next session).
- **Tests:** `FakeSeedMapEngine` in `:core:testing`; orbit-test VM tests (seed change resets selection, toggles filter pins, save/delete round-trip); JVM tests for `SeedParser`, tile maths and the wiki maps; instrumented golden tests extended to structures (first stronghold, villages in a fixed region) and a `genBiomes` tile checksum.
- **Done when:** green, plus the instrumented golden tests pass locally and in CI.

## Session 14: Map UI and the tab (`session-14-seedmap-ui`)
- **Navigation:**
  - `SeedMapKey` plus `TopLevelKeys`, the `TopLevelDestination` icon and label, and the `WikiIcons.Map` drawable.
  - The `EntryProviderInstaller` in `:feature:seedmap`.
  - Update `NavigatorTest`/`NavigatorSaverTest` if they enumerate tabs, and `app/src/androidTest/.../TabNavigationTest.kt` (a fifth tab).
- **`SeedMapCanvas`:** a custom composable using `pointerInput { detectTransformGestures }` for pan and pinch, plus double-tap zoom.
  - It holds a hoisted `MapViewport(centerX, centerZ, blocksPerPixel)` and works out the visible `TileKey`s.
  - It launches tile loads through `TileCache`/`GeneratorPool` and cancels loads for tiles that scroll out of view. It draws with `drawImage(filterQuality = FilterQuality.None)`.
  - Overlays: structure pins (icons per type), spawn and strongholds, and a coordinate readout.
- **Controls:**
  - Seed field: a `TextFieldState` held in the screen, per the search gotcha.
  - Version dropdown: every `McVersion`, newest first.
  - Structure filter sheet: `FilterChip`s, showing only types supported by the selected version.
  - Saved-seeds menu and a go-to-coordinates dialog.
- **Taps:** a tap on a biome or pin opens a bottom sheet with the name, coordinates, "Copy coordinates" and "Open wiki article" (pushes `ArticleKey`).
- **Stateless `SeedMapScreen(state, onAction)`** plus a stateful `SeedMapRoute` using `LocalMetroViewModelFactory`.
- **Tests:**
  - Robolectric screen tests: controls, filter sheet, the pin-tap sheet, and that "Open wiki article" emits the right action.
  - Canvas viewport-maths unit tests.
  - Instrumented: the tab opens, at least one tile renders (real native code on x86_64), and tapping through to an article works against the MockWebServer fixtures. If a needed article fixture is missing, add one trimmed fixture.
- **Baseline profile:** add `device.openTab("Seed map")` plus a pan gesture to `baselineprofile/src/main/kotlin/dev/cniekirk/wikidroid/baselineprofile/BaselineProfileGenerator.kt`, then regenerate locally (not in CI).
- **Done when:** green, plus instrumented tests locally and all three CI jobs green. Manual check on the emulator: pan, zoom and a tap through to an article; the tablet list/detail layout.

## Session 15: Polish (`session-15-seedmap-polish`)
- **Dimensions:** a Nether/End switch, with valid structure sets per dimension and version, and Nether coordinates shown at 1:8.
- **Legend:** a legend sheet listing biome colours and names, searchable. Pins use distinct shapes as well as colours, for colour accessibility.
- **Wiki titles, verified live:** a one-off scratchpad script queries `action=query&titles=…&redirects` against minecraft.wiki for every mapped title. Fix the misses and record the run in PROGRESS.md.
- **Performance:** a pass on a real low-end arm64 phone (pool size, tile size, cache budget). Record ms per tile in PROGRESS.md.
- **APK size:** compare `-O2` with `-Oz`, confirm the sources left out and the stripping, and record the per-ABI `.so` size.
- **Legal:** add `OpenSourceLibrary` entries for cubiomes (Cubitect, MIT) and the xpple fork (MIT) to `OpenSourceLibraries` in `feature/settings/src/main/kotlin/dev/cniekirk/wikidroid/feature/settings/OpenSourceLibrary.kt`. The disclaimer stays.
- **Done when:** green, plus all three CI jobs green, and the manual checks above are recorded.

## Risks
- **Fork maintenance:** the fork has one maintainer. The submodule is pinned, so a fork stall freezes the newest supported version but breaks nothing.
- **Very old versions:** pre-1.18 versions have different structure rules and some biome ids that need custom wiki titles. The golden tests and the title-coverage test catch gaps.
- **AGP 9.4.1:** its `externalNativeBuild` DSL may differ slightly; if so, record the workaround in `CLAUDE.md`.

## Verification (end to end, after Session 15)
- `./gradlew spotlessCheck` on its own, then `detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies assembleDebug :app:assembleRelease`.
- `./gradlew :app:connectedDebugAndroidTest :core:seedmap:connectedDebugAndroidTest` on the API 35 emulator.
- CI: `checks`, `instrumented` and `release-apk` green. The 16 KB alignment step passes, and the PR comment shows the APK and `.so` sizes.
- Manual check on the emulator and a phone:
  1. On the Seed map tab, enter seed `262` with version 26.3.
  2. Pan and zoom; tiles fill in progressively.
  3. Toggle villages, then tap one; "Open wiki article" opens "Village".
  4. Save the seed, restart the app, and confirm it's still listed.
  5. Switch to version 1.12 and to the Nether.
