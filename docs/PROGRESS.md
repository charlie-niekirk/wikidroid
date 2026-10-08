# Progress

Definition of green: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`.

- [x] Session 1: Repo bootstrap and convention plugins
- [x] Session 2: Foundation core modules
- [x] Session 3: Data sources (network, database, datastore)
- [x] Session 4: Article parser
- [x] Session 5: Repositories and shared UI
- [x] Session 6: App shell
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
- ~~**`:app:checkMainMetroHiddenDependencies` does not exist** in Metro gradle plugin 1.4.5~~ Resolved in Session 6 (custom task, see Session 6 notes). Original note: it does not exist in Metro gradle plugin 1.4.5 (no such task listed by `:app:tasks --all`). Session 6 "Done when" and the Session 11 `checks` job reference it. Decide in Session 6: find the replacement Metro diagnostic, or drop the check and rely on the app graph compiling.
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

- **AGP downgraded from 9.5.0-alpha08 to 9.4.1 (latest stable).** The alpha broke Android Studio sync: it created Compose Preview tasks for the release variant while unit tests were disabled there ("Unit tests are disabled for this variant"), and Studio's "Set up Kotlin" prompt then added `org.jetbrains.kotlin.android`, which AGP 9 rejects. Nothing else needed downgrading: the full green suite, `:app:assembleRelease` and `tasks --all` pass unchanged on 9.4.1, including `compileSdk` 37.1, Robolectric SDK 37 and Metro 1.4.5. The `android.onlyEnableUnitTestForTheTestedBuildType` workaround was tried and then removed, as it is not needed. This is a deliberate exception to the pre-release policy for Google libraries; other AndroidX pre-releases (Compose beta, Nav3 alpha, etc.) are unchanged.

### Open TODOs
- ~~Session 3 still has to configure `room3 { schemaDirectory(...) }`~~ Done in Session 3.
- `:app:checkMainMetroHiddenDependencies` decision is still open for Session 6 (carried over).

## Session 3 notes
Verified: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug` and `:core:model:test :core:common:test` pass. 80 new unit tests: 37 in `:core:network`, 22 in `:core:database`, 21 in `:core:datastore`. Not run on a device or emulator; nothing here changes launch behaviour because `:app` does not depend on the new modules yet (Session 6 wires them).

Modules added: `:core:network`, `:core:database`, `:core:datastore`. Fixtures: `core/testing/src/main/resources/fixtures/network/*.json` (trimmed real responses captured from minecraft.wiki on 2026-10-08) and `Fixtures.read(path)` in `:core:testing`.

What each module exposes (for Sessions 4-5):
- `:core:network`: `WikiRemoteDataSource` (bound to `RetrofitWikiRemoteDataSource`) returns `Result<_, DataError>` and never throws. DTOs are public, in `...core.network.dto`. `@WikiBaseUrl` qualifier for the graph factory's `HttpUrl`. `NetworkProviders` needs `Application` and `@WikiBaseUrl HttpUrl` from the graph. `OkHttpClient` is a singleton binding in the graph so Session 5's Coil `ImageLoader` can share it.
- `:core:database`: `WikiDatabase` v1 (schema exported to `core/database/schemas`), `BookmarkDao`, `HistoryDao`, `CachedArticleDao` (including `pruneUnbookmarked(keep)`). Timestamps are epoch millis; the repository converts to `kotlin.time.Instant`. `DatabaseProviders` needs `Application`.
- `:core:datastore`: `PreferencesDataSource` (user preferences plus recent searches) backed by one JSON file, `files/datastore/user_data.json`. `DataStoreProviders` needs `Application`.

### Deviations
- **Category listings request 20 pages, not 50.** `exlimit` is capped at 20 by the server; with `gcmlimit=50` MediaWiki splits the response and continues the extracts separately (`excontinue`), so pages 21-50 arrive without their descriptions. Search uses 20 as well. The continuation token is nevertheless the whole `continue` object, so it keeps working if the server ever adds more continuation keys.
- **`exintro`/`explaintext` are sent as `=1`** rather than as bare flags; equivalent, and avoids relying on valueless query parameters.
- **`MediaWikiApi.parseArticle` and friends return DTOs; `WikiRemoteDataSource` is the layer that maps errors.** The plan did not name this layer. It is what Session 5 repositories should depend on, not the Retrofit interfaces. Only `latestVersions()` returns a domain type (`LatestVersions`), because parsing the `{{Version}}` wikitext is a wire-format concern.
- **Recent searches share the preferences file** via a wrapper, `StoredUserData(preferences, recentSearches)`; `UserPreferences` itself is unchanged (resolves the Session 2 open point).
- **`DataStoreFactory.create(...)` instead of `DataStore.Builder`.** In 1.3.0-alpha11 the builder takes a `Storage` and an `InterProcessCoordinator` that would have to be wired by hand, for no benefit here.
- **`JsonSerializer` is lenient on read** (`ignoreUnknownKeys`, `coerceInputValues`) so a file from a newer or older app version loads; unreadable content raises `CorruptionException` and the file is replaced with defaults. `textScale` is clamped to `UserPreferences.MIN/MAX_TEXT_SCALE` on read and write.
- **DAO tests use `AndroidSQLiteDriver`, not `BundledSQLiteDriver`.** The bundled library in the AAR only has Android-ABI natives, so it throws `UnsatisfiedLinkError` on the host JVM. The driver is an injectable binding (`SqliteDriverProviders`), and the tests replace it with `@ContributesTo(replaces = ...)`. The bundled driver is exercised by the instrumented tests in Session 10.
- **Provider containers are `@BindingContainer object`s** (Metro 1.4.5 warns that interfaces with `@Provides` should be binding containers). `DispatcherProviders` in `:core:common` still uses the old interface style and prints that warning; left alone to keep Session 2 untouched.
- **Each module has a test-only Metro graph** (`NetworkTestGraph`, `DatabaseTestGraph`, `DataStoreTestGraph`) built through `createGraphFactory`. This checks the providers and the `@WikiBaseUrl` binding without waiting for `:app` to depend on the modules in Session 6.
- Android Studio left an uncommitted `org.gradle.tooling.parallel=true` in `gradle.properties` and a generated `gradle/gradle-daemon-jvm.properties` (JDK 25) in the working tree. Neither is part of this PR.

### Workarounds
- `AndroidRoomConventionPlugin` now sets `room3 { schemaDirectory("$projectDir/schemas") }` via the typed `androidx.room3.gradle.RoomExtension` (extension name `room3`).
- DataStore allows one active instance per file. Tests that "restart" must cancel the first store's scope before opening the file again (see `PreferencesDataSourceTest`).

### Open TODOs
- **Category member order.** With a generator, MediaWiki returns each batch ordered by page id, not by sort key, and gives no `index`. Subcategories and pages are mixed within a batch. Session 5/7 should sort each batch (for example subcategories first, then by title) before showing it.
- **Random article filtering** ("skip version/snapshot pages") is left to the Session 5 repository; `randomPages(count)` returns 5 by default so it can filter without a retry.
- `latestRevision()` resolves redirects, so its `revisionId` is the target page's. The Session 5 article cache should key on the resolved title from `parseArticle` (`ParsedPageDto.title`) and compare against that.
- Autocomplete responses are cached by OkHttp for 3 days (the server sends `max-age=259200`). Revisit if suggestions feel stale.

## Session 4 notes
Verified: `spotlessCheck`, then `detekt testDebugUnitTest :core:article-parser:test assembleDebug` pass (Spotless in its own invocation, see the Session 2 workaround). 60 new unit tests, all in `:core:article-parser`. The full Diamond page parses in about 22 ms on the JVM (limit 300 ms). Not run on a device: `:app` does not depend on the module yet (Session 6 wires it).

Module added: `:core:article-parser` (pure JVM, jsoup). Public surface: `ArticleParser` (`fun interface`, `parse(html): ImmutableList<ArticleSection>`) and `JsoupArticleParser`, bound with `@ContributesBinding(AppScope::class)`. Everything else is `internal`. No changes to `:core:model`.

What the parser does, for Sessions 5 and 8:
- Input is the `parse.text` HTML (mobile format or plain). Each `<h2>` starts an `ArticleSection`; `h3` and deeper stay in the blocks as `ContentBlock.Heading` with the element `id` as `anchor`. An empty lead is omitted.
- Stripped: everything from `h2#Navigation` on, `table.navbox`, `.navigation-not-searchable`, `pre.history-json`, `.chest-json`, `.noexcerpt`, `.navbar-mini`, `#toc`, `.mw-editsection`, `.mw-cite-backlink`, `style`/`script`/`link`/`meta`, `.hidden-alt-text`, `.msgbox-icon`.
- Block mapping: `div.infobox` → `Infobox`, hatnotes/`msgbox` → `Note`, `figure[typeof^=mw:File]` and bare file spans → `Image`, `ul.gallery` → `Gallery`, `table` → `Table`, `ul`/`ol` → `ListBlock` (nested), `dl` → bold term paragraph plus definitions, `pre` → code `Paragraph`, standalone `span.mcui-Crafting_Table` → `CraftingGrid`. Unknown containers are looked through; loose inline content between blocks becomes a `Paragraph`.
- `Unsupported` is used for `figure.embedvideo`, `div.issue-list`, `mcw-calc*`, `calculator-container`, `load-page`, `treeview`, non-crafting `mcui` widgets (Furnace, Smithing Table) and `iframe`/`video`/`form`-style tags. The snippet is capped at 1000 characters.
- Links: `/w/Title#Anchor` and `https://minecraft.wiki/w/...` → `Link.Internal(title, anchor)` (underscores become spaces in the title, the anchor keeps the `id` spelling). `File:`, `Special:` and `Media:` pages, query-string URLs and other hosts → `Link.External`. Red links and same-page `#fragment` links (citation markers) are plain text.
- Images resolve against `https://minecraft.wiki`; an image is `pixelated` when it sits inside `.pixel-image`, `.sprite-file` or `.pixelated`.

### Deviations
- **HTML fixtures live in `core/article-parser/src/test/resources/fixtures`, not `:core:testing`.** The parser is a JVM module and cannot depend on the Android library `:core:testing`. They are `parse.text` from `action=parse&mobileformat=1` for Diamond, Creeper, Crafting Table and `Tutorial:Mining`, captured 2026-10-08 (1.2 MB total). They are untouched except that the Navigation footer (200 to 340 KB of navboxes each) is cut down to the first two navbox rows, which keeps the stripping rule under test.
- **Crafting grids inside table cells become text, not `CraftingGrid` blocks.** All 36 crafting widgets on the four real pages sit in recipe table cells, and `TableCell` holds only `RichText`. A cell shows `Oak Planks → Crafting Table` (distinct ingredients, then the output with `×count` when above 1). `CraftingGrid` is produced only for widgets outside tables. See open TODOs.
- **Inline images are dropped** (`RichText` has no image span). Inline sprites (`.sprite-file`) disappear but their `.sprite-text` label stays. Images inside `.iconbar` contribute their alt text, so a health bar reads `20❤️ × 10`.
- **Infobox:** the small inventory sprite (`.infobox-invimages`) is left out of `images`. When the images sit in tabs (Creeper: Normal, Charged) the tab title is the image caption.
- **Tabbers render every tab, one after another, each with a bold title paragraph.** On Creeper the drop tables appear four times (Decimal, Fraction, Distribution, Expectation). Showing only the first tab would be a one-line change in `TextBlocks.tab`.
- Footnote markers stay as superscript text (`[1]`) without a link; reference lists parse as ordinary ordered lists without the `↑` back-links.
- A `Note` built from a `msgbox` has its bold title and its text on separate lines.

### Workarounds
- **jsoup 1.23 needs `org.jspecify:jspecify` on the compile classpath.** Without it Kotlin fails with `MISSING_DEPENDENCY_IN_INFERRED_TYPE_ANNOTATION_ERROR` on any inferred jsoup type. It is `compileOnly(libs.jspecify)` in `:core:article-parser`; modules that use jsoup directly later will need the same.
- **Do not call `MutableList.removeLast()`.** On JDK 21 it resolves to `java.util.List.removeLast`, which does not exist on Android 10 (minSdk 29). The parser uses `removeAt(lastIndex)`.

### Open TODOs
- **Recipe tables lose their grids (Session 8).** To show real grids, either add a block-valued cell content to the model or let `TableParser` split a recipe table around its widget rows. The text summary is the fallback until then.
- Parsing is synchronous and CPU-bound (about 22 ms for the largest page here, longer on a phone). The Session 5 repository should call it on the default dispatcher.
- Same-page `#fragment` links are plain text because the parser does not know the page title. If in-page links matter, give `parse` an optional title and emit `Link.Internal(title, anchor)`.

## Session 5 notes
Verified: `spotlessCheck`, then `detekt testDebugUnitTest assembleDebug` pass (Spotless in its own invocation). 70 new tests: 40 in `:core:data`, 17 in `:core:ui`, 12 for the fakes in `:core:testing`, 1 in `:core:model`. Not run on a device or emulator: `:app` does not depend on the new modules yet (Session 6 wires them).

Modules added: `:core:data`, `:core:ui`. `:core:testing` now depends on `:core:data` (for the fakes); this does not create a cycle with the data-source modules' own tests.

What each module exposes (for Sessions 6-9):
- `:core:data` (interfaces are public, implementations are `internal` and bound with `@ContributesBinding`; the module's public API is model and common types only):
  - `SearchRepository`: `autocomplete(query)`, `search(query, continuation)` returning `Paged<ArticleSummary>`, and recent searches (`saveRecentSearch` and friends).
  - `ArticleRepository`: `getArticle(title): Flow<Result<Article, DataError>>` (offline-first, see below) and `clearCache()`.
  - `CategoryRepository`: `getMembers(category, continuation)` returning `Paged<CategoryMember>`.
  - `LibraryRepository`: `bookmarks`/`history` flows, `isBookmarked`, add/remove/restore for bookmarks and history, `recordVisit`, `clearHistory`.
  - `SettingsRepository`: `preferences` flow and one setter per preference.
  - `WikiInfoRepository`: `latestVersions()` and `randomArticle()`.
  - `OfflineFirstArticleRepository` needs `@WikiBaseUrl HttpUrl` (to build `Article.pageUrl`) and `Clock`; `DataGraphTest` shows the whole stack assembled through Metro.
- `:core:ui`: `ArticleCard`, `PageThumbnail`, `LoadingState`, `EmptyState`, `ErrorState(error: DataError, onRetry)`, `MessageState`, `AttributionFooter(onOpenUrl, pageUrl)`, and `ImageLoaderProviders` (a `@SingleIn(AppScope)` Coil `ImageLoader` on the shared `OkHttpClient`).
- `:core:testing`: `fake.FakeSearchRepository`, `FakeArticleRepository`, `FakeCategoryRepository`, `FakeLibraryRepository`, `FakeSettingsRepository`, `FakeWikiInfoRepository`. Results are scripted through public `var` handlers or `emit(...)`; calls are recorded.

How `ArticleRepository.getArticle` behaves:
1. A cached copy (looked up by the requested title, underscores read as spaces) is parsed and emitted first.
2. `latestRevision` is then asked for the current revision. If revision and canonical title match the cache, the flow completes with no second emission. Otherwise the page is downloaded, cached and emitted again.
3. With no cache the page is downloaded and emitted, or a `Failure` is emitted. Once a cached copy was emitted, network failures are swallowed.
4. After each download the cache is pruned to the 100 most recent unbookmarked articles plus every bookmarked one. `clearCache()` removes everything unbookmarked.
Parsing runs on `@DefaultDispatcher`.

### Deviations
- **`Article` gained `thumbnailUrl` and `toSummary()`** (`:core:model`). History and bookmark rows need a picture, and the article screen is the only place that has the page. The repository fills it from the first infobox image, else the first lead image. `LibraryRepository` takes an `ArticleSummary`, so the article ViewModel calls `recordVisit(article.toSummary())`.
- **`Article.displayTitle` is the canonical title, and `Article.categories` is empty.** The cache table stores only the HTML, and showing different titles for a cached and a fresh copy would flicker. Pages with a `DISPLAYTITLE` (for example `Commands/give`, shown by the wiki as `/give`) therefore show the plain title. Fixing it needs a schema v2 with a nullable `displayTitle` column and an auto-migration; see open TODOs.
- **Freshness uses `latestRevision` (`prop=info`) rather than re-downloading.** The plan said "fetch from the network"; this keeps the common case, an unchanged page, to one tiny request.
- **A redirect title misses the cache.** The cache is keyed by the canonical title from `parseArticle`, so opening `Dirt block` always downloads (OkHttp's 10-minute response cache helps) while `Dirt` opens offline. Library and history entries use canonical titles, so they open offline.
- **`recordVisit` honours the `saveHistory` preference; recent searches ignore it.** The toggle is described as history, and recent searches are a separate list with their own clear button.
- **`restoreBookmark`/`restoreHistoryEntry` were added** to `LibraryRepository` so Session 9's swipe-to-delete undo keeps the original timestamp.
- **The curated Explore category list is not in `CategoryRepository`.** It is UI-flavoured (names, icons) and belongs to Session 7.
- **`PageThumbnail` is pixelated by default** (`FilterQuality.None`), because wiki imagery is mostly sprites; pass `pixelated = false` for photographs.
- **`Clock` is injected.** `:core:common` gained `ClockProviders`, a `kotlin.time.Clock` binding, so repositories and tests control timestamps.
- **Detekt `TooManyFunctions` now has `ignoreOverridden: true`**, so implementing a wide interface (a repository or its fake) doesn't count against the class limit.
- Added `coil-test` to the version catalog for the thumbnail tests.

### Workarounds
- **Coil 3's test artifact has no `FakeImageLoader`.** Build an `ImageLoader` with `FakeImageLoaderEngine` as a component and register it with `setSingletonImageLoaderFactory` inside `setContent` (see `PageThumbnailTest`).
- Repository tests use real Room (in-memory, `AndroidSQLiteDriver` on Robolectric) with a hand-written `FakeRemote`, so cache and pruning behaviour is the real SQL. `DataTestGraph` replaces `SqliteDriverProviders` the same way the database module's graph test does.

### Open TODOs
- **Session 6 must register the Coil loader:** make `WikiDroidApp` implement `SingletonImageLoader.Factory` and return the graph's `ImageLoader`, otherwise `PageThumbnail` falls back to a default loader with no shared User-Agent. `:app` also needs direct dependencies on `:core:data`, `:core:ui`, `:core:network`, `:core:database`, `:core:datastore` and `:core:article-parser`.
- **Cache `displayTitle` (and categories) with a schema v2** if `DISPLAYTITLE` pages matter. A nullable column plus `@AutoMigration(1, 2)` keeps old rows readable.
- `ArticleRepository` emits no signal that a cached copy is stale because the device is offline. If Session 8 wants an "offline, showing saved copy" banner, add a flag to the emission rather than an error.
- Unchanged from Session 4: recipe tables lose their grids, and tabbers render every tab (Session 8).
- Unchanged from Session 3: autocomplete responses are cached by OkHttp for 3 days.

## Session 6 notes
Verified: `spotlessCheck` (own invocation), then `detekt testDebugUnitTest assembleDebug :app:checkMainMetroHiddenDependencies :app:assembleRelease` pass. 13 new unit tests in `:app` (3 `MainViewModelTest`, 6 `AppShellTest`, 4 `AppGraphTest`; Robolectric). On the Pixel_10_Pro emulator (API 37): the app launches with no crash, the four tabs switch, system back from a non-start tab returns to Explore, and the theme follows the system light/dark setting (checked with `cmd uimode night`).

What `:app` now has (for Sessions 7-9):
- `AppGraph : ViewModelGraph` with a factory taking `Application` and `@WikiBaseUrl HttpUrl` (`WikiDroidApp` passes `https://minecraft.wiki/`; Session 10's test app passes a MockWebServer URL). It exposes `entryInstallers` (a `@Multibinds(allowEmpty = true)` set), `imageLoader`, and every repository.
- `AppViewModelFactory : MetroViewModelFactory`, bound with `@ContributesBinding`. Feature ViewModels only need `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`. Assisted ones use `@ManualViewModelAssistedFactoryKey` as planned.
- `WikiDroidApp` is also `SingletonImageLoader.Factory` and returns the graph's loader (closes the Session 5 TODO).
- `MainActivity`: splash screen held until the saved preferences are read (`MainViewModel`, Orbit), `WindowCompat.enableEdgeToEdge`, `WikiDroidTheme(themeMode, dynamicColor)` from `SettingsRepository`, and system-bar icon contrast that follows the app theme rather than the system's.
- `AppShell(navigator, installers)`: `NavigationSuiteScaffold` with the four `TopLevelKeys` tabs (re-selecting a tab pops it to its root) around a `NavDisplay` fed from `navigator.backStack`, with the saveable-state and ViewModel-store decorators and `rememberListDetailSceneStrategy`.
- **Placeholders are the `entryProvider` fallback**, not installers. Any key with no registered entry shows `PlaceholderScreen`, so a feature session only has to contribute an `EntryProviderInstaller`; nothing in `:app` changes and nothing is registered twice. List/detail panes still need each entry to carry `ListDetailSceneStrategy.listPane()` / `detailPane()` metadata (Sessions 7 and 8).
- `:app` depends directly on every `:core:*` module except `:core:testing` (a test dependency).

### Deviations
- **`checkMainMetroHiddenDependencies` is our own task**, defined in `app/build.gradle.kts` (Metro 1.4.5 has none). It fails when a `:core:*` or `:feature:*` module in the build (other than `:core:testing`) is not a direct `implementation` dependency of `:app`, which is the condition under which Metro silently misses contributions. It is wired into `check`. Features added in later sessions are covered automatically once they are included in `settings.gradle.kts`.
- **`MainViewModel` is the only ViewModel in `:app`.** It holds just the preferences, so the activity can drive the splash screen and the theme without a flash.
- **Metro option `generateContributionProviders = true` is now set in `wikidroid.metro`.** Without it the graph in `:app` could not see the `internal` repository implementations ("internal to its module and its module is not a friend module"), which broke the Session 5 convention that implementations stay `internal`. Each contributing module now generates a public provider for its binding.
- **`wikidroid.android.application` now also sets the Robolectric `--add-exports` JVM argument** on test tasks, as `wikidroid.android.library` does.
- **The graph exposes every repository** only so that the whole data stack (network, Room, DataStore, parser) is verified by the compiler; Metro checks only bindings reachable from the graph's roots, and without these accessors it warned that `@WikiBaseUrl` was unused. Features inject the repositories directly, so these accessors can go once a feature uses each of them.
- Splash: green (`#3C8527`) background with the white launcher "W" in both light and dark. `Theme.WikiDroid` is now `android:Theme.DeviceDefault.DayNight` with transparent system bars.

### Workarounds
- Compose-rules `UnstableCollections` applies to `AppShell`'s installers, so the activity passes `entryInstallers.toImmutableSet()`.
- `NavigationSuiteScaffold` moved to the `androidx.compose.material3.adaptive.navigationsuite` package; use the overload whose `navigationItems` lambda calls the composable `NavigationSuiteItem`. `rememberListDetailSceneStrategy` needs `@OptIn(ExperimentalMaterial3AdaptiveApi::class)`.
- `NavDisplay` in Nav3 1.3.0-alpha02 takes `entryDecorators`, `sceneStrategies` and `onBack` by name; `entryProvider(fallback = ...)` is how unknown keys are handled.
- Orbit ViewModel tests: intents run off the test dispatcher, so Turbine tests on `container.stateFlow` loop on `awaitItem()` until the loaded state arrives.

### Open TODOs
- The splash icon is the placeholder launcher "W".
- Predictive back and the tablet/list-detail layout can only be checked once Sessions 7-8 register real entries (the placeholders carry no pane metadata).
- Unchanged from earlier sessions: recipe tables lose their grids, tabbers render every tab (Session 8), autocomplete is cached for 3 days.
