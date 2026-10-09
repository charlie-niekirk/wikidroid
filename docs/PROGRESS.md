# Progress

Definition of green: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`.

- [x] Session 1: Repo bootstrap and convention plugins
- [x] Session 2: Foundation core modules
- [x] Session 3: Data sources (network, database, datastore)
- [x] Session 4: Article parser
- [x] Session 5: Repositories and shared UI
- [x] Session 6: App shell
- [x] Session 7: `:feature:explore` and `:feature:search`
- [x] Session 8: `:feature:article`
- [x] Session 9: `:feature:library` and `:feature:settings`
- [x] Session 10: Instrumented tests and baseline profile
- [x] Session 11: CI and docs
- [x] Session 12: Seed map, native groundwork (`seed-map-plan.md`)
- [ ] Session 13: Seed map, engine API, tiles and ViewModel
- [ ] Session 14: Seed map, map UI and the tab
- [ ] Session 15: Seed map, polish

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
- **Inline images are dropped** (`RichText` has no image span). *Resolved in Session 8: `RichSpan.image`.* Inline sprites (`.sprite-file`) disappear but their `.sprite-text` label stays. Images inside `.iconbar` contribute their alt text, so a health bar reads `20❤️ × 10`.
- **Infobox:** the small inventory sprite (`.infobox-invimages`) is left out of `images`. When the images sit in tabs (Creeper: Normal, Charged) the tab title is the image caption.
- **Tabbers render every tab, one after another, each with a bold title paragraph.** On Creeper the drop tables appear four times (Decimal, Fraction, Distribution, Expectation). Showing only the first tab would be a one-line change in `TextBlocks.tab`.
- Footnote markers stay as superscript text (`[1]`) without a link; reference lists parse as ordinary ordered lists without the `↑` back-links.
- A `Note` built from a `msgbox` has its bold title and its text on separate lines.

### Workarounds
- **jsoup 1.23 needs `org.jspecify:jspecify` on the compile classpath.** Without it Kotlin fails with `MISSING_DEPENDENCY_IN_INFERRED_TYPE_ANNOTATION_ERROR` on any inferred jsoup type. It is `compileOnly(libs.jspecify)` in `:core:article-parser`; modules that use jsoup directly later will need the same.
- **Do not call `MutableList.removeLast()`.** On JDK 21 it resolves to `java.util.List.removeLast`, which does not exist on Android 10 (minSdk 29). The parser uses `removeAt(lastIndex)`.

### Open TODOs
- ~~**Recipe tables lose their grids (Session 8).**~~ Resolved in Session 8 (`TableCell.crafting`). Original note: to show real grids, either add a block-valued cell content to the model or let `TableParser` split a recipe table around its widget rows. The text summary is the fallback until then.
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

## Session 7 notes
Verified: `spotlessCheck` (own invocation), then `detekt testDebugUnitTest assembleDebug :app:checkMainMetroHiddenDependencies :app:assembleRelease` pass. 107 new tests (375 in total): 44 in `:feature:explore`, 53 in `:feature:search`, 8 in `:core:ui`, net +2 in `:app`. On the Pixel_10_Pro emulator (API 37) against the live wiki: Explore loads the real latest versions, a category (Hostile mobs) lists pages with thumbnails and scrolls, tapping a page or a version row opens the article stub, Random article opens a real page, search shows live suggestions then paged results, and recent searches appear and clear. On the Pixel_Tablet AVD the category list and the article stub show side by side. Screenshots are in `docs/pr-assets/session-7`.

Modules added: `:feature:explore`, `:feature:search` (both `wikidroid.android.feature`, no extra dependencies). `:app` depends on both directly.

What each exposes (for Sessions 8-9):
- Each feature contributes one `EntryProviderInstaller` (`ExploreEntryInstaller` for `ExploreKey` and `CategoryKey`, `SearchEntryInstaller` for `SearchKey`). Tapping a page pushes `ArticleKey(title)`, a subcategory pushes `CategoryKey(name)`. Version rows push `ArticleKey("Java Edition 26.3")` and so on.
- Screens are `XxxScreen(state, onAction)` (stateless) wrapped by `XxxRoute` (ViewModel via `metroViewModel()` or `assistedMetroViewModel`). Navigation actions are intercepted in the route; the ViewModel never sees them.
- `:core:ui` gained `PaginationEffect` and `pagingFooter` (paged lists), `DataError.userMessage()` (the text `ErrorState` shows, public now), `WikiPanes.list()/detail()` (list-detail pane metadata) and `ui_navigate_back`.
- `MainActivity` now provides `LocalMetroViewModelFactory`, which `metroViewModel()` reads.
- **The `ArticleKey` stub in `:app` carries `WikiPanes.detail()`.** Session 8's real entry must use `metadata = WikiPanes.detail()` too, or wide windows lose the side-by-side layout. Library (Session 9) should register its list entries with `WikiPanes.list()`.

### Deviations
- **Pane metadata lives in `:core:ui` (`WikiPanes`), not in the features.** Each feature would otherwise depend on `adaptive-navigation3` and repeat the placeholder. A small addition to an earlier module.
- **The Search text lives in a `TextFieldState` in the screen, not in the ViewModel.** Round-tripping every keystroke through an async Orbit state can drop characters. The ViewModel's `query` is an echo used for logic; changes from outside the field (recent search, clear) edit the field first. Every decision that depends on the state is made inside `reduce`, so concurrent intents can't undo each other.
- **Suggestions are fetched for the trimmed query after a 250 ms debounce; a stale response is dropped** if the query changed while it loaded. Tapping a suggestion saves its title as a recent search. Submitting saves the query.
- **Paging is guarded by a `Mutex.tryLock()` per ViewModel**, and the continuation token is read under the lock, so repeated "load more" calls fetch one batch and a queued call can't repeat a batch.
- **Category subcategories are a horizontal chip row above the pages**, not a section within the list. All pages and subcategories accumulate across batches (repeats dropped).
- **No icons on category tiles** (text only); the curated list is in `ExploreCategories`. Names were checked against the live wiki (for example `End biomes`, not `The End biomes`).
- `ExploreAction.OpenArticle/OpenCategory` and `CategoryDetailAction.Back` exist so the screens can be tested statelessly, but the ViewModels ignore them.
- **Orbit state-conflation:** the first `reduce { copy(phase = Loading) }` on a state that is already `Loading` emits nothing, so the first load shows one state change, not two.

### Workarounds
- **orbit-test (`viewModel.test(this) { ... }`):** `expectInitialState()` checks the initial state but is not an item in the stream, and `expectState { copy(...) }` is relative to the last *consumed* state, so after `skipItems` assert explicit states instead. Call `runOnCreate()` to start `orbitContainer { ... }` work.
- **`advanceUntilIdle()` ignores Orbit's background tasks.** Use `runCurrent()` and `advanceTimeBy(...)`; otherwise "does nothing" tests pass vacuously. Intents run lazily on the test scheduler, so call `runCurrent()` before releasing a gate to check a call was dropped.
- **Assisted ViewModels:** a nested `@AssistedFactory @ManualViewModelAssistedFactoryKey(Factory::class) @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>()) fun interface Factory : ManualViewModelAssistedFactory`. In tests build them with `graph.metroViewModelFactory.createManuallyAssistedFactory(Factory::class)()` (the provider maps are protected).
- `KeyboardActionHandler` is in `androidx.compose.foundation.text.input`, and `TextField(state = ...)` is the state-based Material 3 overload.
- An `adb` Enter key press does not dismiss the keyboard like the IME's search key does; test submit with a tap on the keyboard's search key.

### Open TODOs
- **ViewModel state is lost when leaving a tab.** Switching tabs removes the other tab's entries from the displayed back stack, so their ViewModels are cleared (inferred from how `rememberViewModelStoreNavEntryDecorator` works, and the category list was back at the top after a tab round trip; not otherwise verified). A typed search query would be lost the same way. If that matters, keep the saveable state (for example `SavedStateHandle`) or keep non-selected tabs' entries alive in `AppShell`.
- Autocomplete suggestions often have no description or thumbnail (the REST title search omits them for many pages); the rows show a placeholder icon.
- `Random article` skips version pages by title pattern only.
- Unchanged: article stub until Session 8, recipe tables lose their grids, tabbers render every tab, autocomplete cached 3 days.

## Session 8 notes
Verified: `spotlessCheck` (own invocation), then `detekt testDebugUnitTest assembleDebug :app:checkMainMetroHiddenDependencies :app:assembleRelease` pass. 106 new tests (481 in total): 91 in `:feature:article`, 12 in `:core:article-parser`, 2 in `:core:model`, 1 in `:app`. On the Pixel_10_Pro emulator (API 37) against the live wiki: Diamond, Creeper, Crafting Table, Mob and a version page render; internal links navigate (a redirect such as `hostile mob` opens its canonical page, `Mob`); anchors scroll from the contents sheet; sections fold; the bookmark toggle writes the bookmark, history and cache rows (read back from the app's Room file). On the Pixel_Tablet AVD an article opens beside the Explore list. Screenshots are in `docs/pr-assets/session-8`.

Module added: `:feature:article` (`wikidroid.android.feature`, plus `androidx.browser` for Custom Tabs). `:app` depends on it directly.

What it exposes (for Sessions 9-10):
- `ArticleEntryInstaller` registers `ArticleKey` with `WikiPanes.detail()`. Opening an article from a link pushes another `ArticleKey(title, anchor)`. The placeholder in `:app` is now only the fallback for a key with no entry.
- `ArticleViewModel` is assisted (`create(title, anchor)`). `ArticleScreen(state, onAction)` is stateless; `ArticleRoute` handles links, sharing, Custom Tabs and navigation.
- Rendering lives in `...feature.article.render`: one composable per `ContentBlock` (`ArticleBlock` dispatches), `RichTextView` (links become `LinkAnnotation.Clickable`), and a table grid layout. `ArticleLayout` (pure) flattens an article into list rows and finds headings.
- History is recorded on the first successful emission only (canonical title, so a redirect and its target are one entry). The bookmark star follows `LibraryRepository.isBookmarked(canonicalTitle)`, so Library (Session 9) sees the same rows. Text size follows `SettingsRepository.preferences`.

### Deviations
- **`TableCell` gained `crafting: ContentBlock.CraftingGrid?`** (`:core:model`) and `TableParser` fills it when a cell holds one crafting widget and nothing else. `content` keeps the one-line text summary, so nothing that reads text changed. A cell with a recipe plus other text, and other widgets (Smithing Table, Furnace), still show the summary or the unsupported chip. This closes the Session 4 open TODO.
- **Tables are drawn by a custom grid `Layout`, not rows of cells.** Rows of cells cannot span rows: the first version drew a `rowspan` cell in its first row only and left a blank column on Diamond's loot table. Columns are equal width, at least 130dp (176dp when a cell holds a recipe, so a compact grid fits), inside a horizontal scroll. Cells are measured with intrinsic heights and then stretched to the squares they cover.
- **Sections start folded.** Every section with a heading is folded when an article first loads, so a page opens as an outline; the lead never folds. Fold state and the contents sheet are in `ArticleState`. A refreshed copy keeps the reader's choices for the sections they already had, and folds only sections that are new. Going to an anchor (contents sheet, link, or the `ArticleKey` anchor) unfolds just that section.
- **A tapped anchor is a state field (`pendingAnchor`), not an effect.** The ViewModel unfolds the section and sets it; the screen scrolls and sends `AnchorHandled`. This keeps scrolling testable without a side-effect channel. An anchor from the `ArticleKey` is applied once, on the first emission, so a refreshed copy does not drag the reader back.
- **Anchors match leniently** (`Spawn_rates`, `Spawn rates`, percent-encoded, case-insensitive) after an exact match fails. A link to the page you are already on, with an anchor, scrolls instead of pushing a second copy.
- **Text size scales only the article body**, by multiplying `LocalDensity.fontScale` around the content. The top bar and sheets keep the system size.
- **Only http and https links are opened.** `javascript:`, `intent:`, `file:` and other schemes in article links are dropped. `Share` and `Open on wiki` appear only when the article has a page URL (the repository always sets one).
- **Reading width is capped at 720dp** and centred, so a full-width tablet window keeps readable lines.
- **Tabbers still render every tab in turn** (Creeper's drop table appears four times). Nothing is lost, but a tab picker needs a new block type; left for later.
- **Inline images are in `RichText`** (`:core:model`, `:core:article-parser`). `RichSpan` gained `image: InlineImage?` (url, size, pixelated); an image span's `text` is its alt text, so `plainText` is unchanged. `RichText.isBlank` now counts an image as content and `hasText` says whether there is readable text. `sprite-file` is no longer treated as hidden, so item and structure icons appear next to their names in tables, lists, infobox rows and message boxes, as on the wiki. Left out on purpose: the wiki's own hatnote icon (the app draws one), and the health/hunger bars, which keep their emoji text. A paragraph that holds only a block-sized picture still becomes an `Image` block.
- **Inline images are drawn with Compose inline content, sized in `sp`** (the image's own pixels as `sp`, at most 96, plus a 4sp gap), so an icon grows with the text size setting and sits in the line like a letter. A link around an icon covers it.

### Workarounds
- **Metro `@Assisted` takes no identifier argument.** Parameters are matched by name, so `@Assisted("title")` fails with "Too many arguments". Use plain `@Assisted` and distinct parameter names.
- **`Modifier.size(w)` followed by `aspectRatio` overflows the box** when the ratio asks for more than `w`: the image drew taller than its layout slot and covered the rows below (Creeper's two infobox pictures). Fix a width and let the ratio decide the height.
- **`rememberModalBottomSheetState` is deprecated** in material3 1.5.0-beta01. `ModalBottomSheet`'s default state is the replacement, so the contents sheet passes none.
- **compose-rules `lambda-param-in-effect`:** a lambda used inside `LaunchedEffect` goes through `rememberUpdatedState` first.
- **Compose tests that tap a link** must tap the left edge of the text (`click(Offset(8f, centerY))`): the node is as wide as its container, so the default centre tap misses a short link.
- **A Compose `Text` node inside a merged parent has the text's bounds, not the cell's.** Test cell geometry through positions of cells that share a row or column, not heights.
- Emulator driving: `adb shell input text` needs `%s` for spaces, and the keyboard's stylus tutorial sheet can swallow the first taps after a fresh install.

### Open TODOs
- **First load of a large page took roughly 10 s on the emulator** before the spinner cleared (Diamond, Creeper, Crafting Table). Not profiled: it could be the download (debug builds log bodies with the OkHttp interceptor), Room, or parsing. Worth measuring before the Session 10 baseline profile.
- The article shows no signal that a saved copy is stale because the device is offline (Session 5 TODO, still open).
- Images cannot be opened full screen, and galleries have no pager; both are out of this session's scope.
- Unchanged: ViewModel state is lost when leaving a tab, autocomplete is cached for 3 days, tabbers render every tab, `Random article` skips version pages by title only.

## Session 9 notes
Verified: `spotlessCheck` (own invocation), then `detekt testDebugUnitTest assembleDebug :app:checkMainMetroHiddenDependencies :app:assembleRelease` pass. 84 new tests (565 in total): 44 in `:feature:library`, 39 in `:feature:settings`, 1 in `:core:navigation`; the `ExternalLinksTest` moved to `:core:ui` as `WebLinksTest`. On the Pixel_10_Pro emulator (API 37) against the live wiki: bookmarking an article shows it in Library with the offline badge; with airplane mode on and the app force-stopped and relaunched, the bookmark still opens and renders; swipe-to-remove shows the Undo snackbar and Undo restores the row (checked on History); Dark theme chosen in Settings survives a force-stop and relaunch (system theme was light); About shows the version, disclaimer, attribution and licences. Screenshots are in `docs/pr-assets/session-9`. Not checked by hand: clear history and clear article cache (covered by ViewModel and screen tests, and by the Session 5 repository tests), the tablet layout of Library and Settings.

Modules added: `:feature:library`, `:feature:settings` (both `wikidroid.android.feature`, no extra dependencies). `:app` depends on both directly.

What each exposes (for Session 10):
- `LibraryEntryInstaller` registers `LibraryKey` with `WikiPanes.list()`; tapping a row pushes `ArticleKey(title)`. `LibraryViewModel` is plain (`@ViewModelKey`); `LibraryScreen(state, onAction)` is stateless.
- `SettingsEntryInstaller` registers `SettingsKey` and the new `AboutKey` (both without pane metadata, so they are full-screen). `SettingsRoute` sends `OpenAbout` to navigation; every other action goes to `SettingsViewModel`. `AboutScreen` has no ViewModel; `AboutRoute` reads the version name from `PackageManager` and opens links in a Custom Tab.
- Test tags for instrumented tests: `LIBRARY_LIST_TAG`, `SETTINGS_LIST_TAG`, `TEXT_SIZE_SLIDER_TAG`, `ABOUT_LIST_TAG` (all `internal` to their module).

### Deviations
- **`AboutKey` was added to `:core:navigation`.** The plan's key list has no About destination. It is a `data object : WikiKey` pushed onto the Settings tab's stack (so back works, and the Navigator saver covers it, with a test). `:app`'s `PlaceholderScreen` gained a branch for it because its `when` is exhaustive.
- **`openWebUrl`/`isWebUrl` moved from `:feature:article` to `:core:ui`** (`WebLinks.kt`, now public, with `androidx.browser` as an `implementation` dependency there), because the About screen needs Custom Tabs too. `shareArticle` stays in the article module. `:feature:article` no longer depends on `androidx.browser`.
- **Undo is state, not an effect.** `LibraryState.removed` holds the entry the snackbar is offering; the screen shows the snackbar for it and sends `Undo` or `UndoExpired`. A new removal replaces the previous one (only the latest can be undone), and clearing the history drops a pending history undo so it can't bring one entry back. The same pattern as `pendingAnchor` in Session 8.
- **Removal is also a TalkBack custom action** ("Remove <title>"), since swiping isn't available to everyone.
- **Rows read "Saved/Viewed 5 min. ago"** (`DateUtils.getRelativeTimeSpanString`, taken when the tab is shown) in the card's description line; the offline badge is `ArticleCard`'s trailing icon (`ui_offline_available` finally has a user).
- **Empty History explains itself:** when "Save reading history" is off it says so instead of "No history yet".
- **Clear history (Library top bar and Settings) and clear article cache ask for confirmation.** Clear history deletes only history; clear cache keeps bookmarked articles (`ArticleRepository.clearCache`). A failure is reported in a snackbar, not thrown.
- **Text size is saved when the slider is released**, not on every step; the percentage label and a preview sentence follow the drag. Steps of 5% between 85% and 150%.
- **Dynamic colour is hidden before Android 12** (the screen takes `dynamicColorSupported`, defaulting to `SDK_INT >= S`).
- **Licences are a hand-written list** (`OpenSourceLibrary.kt`: AndroidX/Compose/Material 3, Kotlin and kotlinx, Metro, Orbit, Retrofit, OkHttp, Coil, jsoup, Material Icons) on the About screen, not generated, so no new dependency or Gradle plugin. Keep it in step with `gradle/libs.versions.toml`.
- **No separate "Licences" screen:** About is one scrolling screen (version, disclaimer, content licence with links, source code, libraries).
- **The Settings preference "preferred edition" is stored but nothing reads it yet.** The plan lists the setting without saying what it drives. See open TODOs.

### Workarounds
- **Material 3 1.5.0-beta01 deprecates the old `ListItem(headlineContent = ...)` and `Slider(value, onValueChange, ...)`.** Use `ListItem(onClick/checked, ...) { headline }` and `Slider(state = SliderState, onValueChange = { state.value = it }, onValueChangeFinished = ...)`. The `Slider(state, modifier, onValueChange = null ...)` overload is hidden, so `onValueChange` is mandatory. `SliderState` must be created inside `remember`.
- **A snackbar effect keyed on a state field must acknowledge it after `showSnackbar` returns**, not before: clearing the field changes the key and cancels the snackbar immediately (`SettingsScreen`'s `MessageSnackbar`).
- **compose-rules:** a composable that emits two things at its top level (`Header`, `TextSection`) is wrapped in a `Column`; lambda parameters are present tense (`onCommit`, not `onChangeFinished`); `List<T>` parameters are `ImmutableList<T>`. Detekt's `TooManyFunctions` (11 per file) pushed the reusable settings rows into `SettingsRows.kt`.
- **orbit-test:** the two lists arriving are state items ahead of a side effect in the stream, so a test that expects an effect without consuming states first needs `skipItems(2)`.
- **Compose tests that wait on a snackbar timing out** set `mainClock.autoAdvance = false` and advance the clock by hand.
- Emulator driving: the short snackbar lasts 4 s, so tap Undo in the same command as the swipe (a screenshot read in between is too slow).

### Open TODOs
- **`preferredEdition` has no effect.** Decide what it drives (for example, which edition's version is shown first on Explore, or which infobox variant opens) or drop it from Settings.
- **Library and Settings view-model state is lost when leaving the tab** (the Session 7 TODO applies: the selected Library tab resets to Bookmarks, and an undo snackbar is dropped).
- Library and Settings were not checked on the tablet AVD; both are single-pane layouts (`WikiPanes.list()` on Library shows "Select a page" beside it on wide windows).
- Unchanged: first article load is slow on the emulator, no stale-offline signal in the article, tabbers render every tab, `Random article` skips version pages by title only.

## Session 10 notes
Verified: `spotlessCheck` (own invocation), then `detekt testDebugUnitTest assembleDebug :app:checkMainMetroHiddenDependencies :app:assembleRelease` pass. `:app:connectedDebugAndroidTest` passes (7 tests) on a local API 35 arm64 emulator (`google_apis_playstore`, ~1 min). `:app:generateBaselineProfile` ran on the same emulator against the live wiki and wrote `app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt` (33,048 rules each, covering all five feature modules and the article parser/jsoup); `app-release.apk` contains `assets/dexopt/baseline.prof` and `.profm`.

What was added:
- `app/src/androidTest`: `WikiDroidTestRunner` (swaps the application class for `TestWikiApp`), `TestWikiApp` (extends the now-`open` `WikiDroidApp`, starts a `MockWebServer` and overrides `baseUrl()`), `FixtureDispatcher` (routes by API action/generator to the Session 3 network fixtures), `ResetAppStateRule`, and three test classes: `TabNavigationTest` (3), `SearchToLibraryTest` (2: search → article → bookmark → Library, and history), `SettingsPersistenceTest` (2: theme survives closing and reopening the activity, history toggle shows "History is turned off").
- `:baselineprofile` (`wikidroid.baselineprofile` convention plugin: `com.android.test` + `androidx.baselineprofile`, targets `:app`) with one generator journey: cold start, Explore scroll, search "Creeper", open the article and scroll, Library, Settings. `:app` applies `androidx.baselineprofile`, depends on `profileinstaller` and `baselineProfile(projects.baselineprofile)`.

### Deviations
- **`WikiDroidApp` is now `open` with a `protected open fun baseUrl()`**, the only change to production code. `TestWikiApp` overrides it.
- **Fixtures are not copied.** `app/build.gradle.kts` adds `core/testing/src/main/resources/fixtures` as an `androidTest` assets directory, so the dispatcher and the unit tests read the same files. `:core:testing` is not an `androidTest` dependency (it would bring Robolectric into the test APK).
- **A debug-only `network_security_config`** (`app/src/debug`) allows cleartext HTTP to `localhost`/`127.0.0.1`; without it OkHttp refuses the MockWebServer. Release builds are unaffected.
- **Tests find views by text, tab role and literal test tags** (`search-field`, `article-list`, `library-list`), because the tag constants are `internal` to their feature modules. If a tag or English string changes, these tests need the same edit.
- **Settings persistence is checked by closing and relaunching the activity in the same process**, not by killing the process. DataStore is a process singleton, so the process-restart case stays a manual check (done by hand in Session 9).
- **Each test starts from reset state** (`ResetAppStateRule` empties bookmarks, history, cached articles and recent searches, and restores default settings through the repositories). It sits outside the Compose rule in a `RuleChain` so it runs before the activity starts.
- **Thumbnails are not served by the mock.** Their URLs in the fixtures point at minecraft.wiki, so Coil tries the real network and falls back to a placeholder; no test depends on them.
- **The baseline profile is generated against the live minecraft.wiki**, not the mock (the generator runs the real release-like build). With no network the profile is smaller, because every wait in the journey gives up quietly. Regenerate when screens change a lot.
- **API 35 AVD:** the machine only had `Pixel_10_Pro` (API 37.1) and `Pixel_Tablet`, and `avdmanager` could not create one ("Package path is not valid"), so `~/.android/avd/Pixel_API35` was written by hand from the Pixel 10 Pro config with the API 35 `google_apis_playstore` arm64 image.

### Workarounds
- **`androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` hung the app on its splash screen** in every run (first test never got past the splash, until the 10 minute instrumentation timeout), while the deprecated v1 rule passes. The tests use v1 with `@Suppress("DEPRECATION")`. Cause not investigated (v2 uses `StandardTestDispatcher`); revisit when moving the Robolectric tests to v2 too.
- A suggestion row and the search field both contain the typed text, so the tests match `hasText(x) and hasClickAction() and !hasSetTextAction()`.
- Nav items are matched with `Role.Tab` (`ComposeTestRule.tab(label)`), because the tab label is also a screen title.

### Open TODOs
- Session 11 must give CI an emulator that can run these (API 35 x86_64 `google_apis`) and keep `:baselineprofile` out of CI (the plan runs it locally).
- No Macrobenchmark or startup measurement was taken, so the profile's effect on startup and first article load (the ~10 s on-emulator load noted in Session 8) is unmeasured.
- The tests run on a phone-size AVD only; the tablet list-detail layout is still manual.
- Unchanged: first article load is slow on the emulator, no stale-offline signal in the article, tabbers render every tab, `preferredEdition` has no effect, ViewModel state is lost when leaving a tab.

## Session 11 notes
Verified locally: `spotlessCheck` (own invocation), then `detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies assembleDebug` (the `checks` job's commands) and `:app:assembleRelease` pass; `aapt2 dump badging` on the release APK gives the version fields the PR comment uses. The three jobs themselves can only be proved on GitHub: see the PR body for the run results.

What was added:
- `.github/workflows/pr.yml` (`pull_request` and `push: main`, cancel-in-progress): `checks`, `instrumented` and `release-apk` (needs `checks`) as in the plan.
- `.github/actions/setup-android` (local composite action): JDK 21 (Temurin), `setup-gradle@v6`, and `sdkmanager` for `platforms;android-37.1` and `build-tools;37.0.0`. All three jobs use it.
- `.github/dependabot.yml`: `github-actions` and `gradle`, weekly.
- `README.md`: setup, Studio requirement, checks, CI, signing secrets (`keytool`, `base64`, `gh secret set`), attribution and the unofficial-app disclaimer.
- `CLAUDE.md`: a short CI section.

### Deviations
- **The composite action** is not in the plan; it avoids repeating the JDK, Gradle and SDK steps three times.
- **Explicit `sdkmanager` step.** AGP can download missing SDK packages, but only if licences are accepted, so the action installs the two packages up front.
- **`setup-gradle` Terms of Use:** there is no input to accept them. Using the default `enhanced` cache provider is the acceptance (see Gradle's `DISTRIBUTION.md`); `cache-provider: basic` opts out. Noted in the composite action and the README.
- **AVD cache** uses `actions/cache@v5` and a fixed key (`avd-api35-x86_64-google_apis`); the first run creates the snapshot, later runs reuse it. Bump the key if the emulator settings change.
- **Reports are uploaded `if: failure()`** in both test jobs.
- **Comment skipped for forks and Dependabot** (read-only token). The APK is still built and uploaded for them. For a push to `main` there is no PR, so the comment is skipped and the artifact is named `wikidroid-release-run<N>`.
- **Signing:** `release-apk` always exports `WIKIDROID_KEYSTORE_FILE` (a path in the runner temp dir) and the secrets; the file is only written when `KEYSTORE_BASE64` is set, so with no secrets the convention plugin falls back to the debug key. The comment says which one was used.
- **`actionlint` is not installed on this machine**, so it was not run. The YAML was parsed with Ruby and the workflow's shell snippets were run by hand; GitHub is the real validation.
- **`ls` is aliased on the dev machine** (prints icons), so shell snippets avoid parsing `ls`; the workflow uses `printf` with a glob to find `aapt2`.

### Open TODOs
- **User action:** add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in the repo settings (README has the commands). Until then CI APKs are debug-signed.
- Consider branch protection on `main` requiring `checks`, `instrumented` and `release-apk`.
- Anyone with write access can read the signing secrets through a changed workflow on a branch; consider an environment with required reviewers if more people get access.
- Unchanged from earlier sessions: `preferredEdition` has no effect, ViewModel state is lost when leaving a tab, first article load is slow on the emulator, no startup measurement for the baseline profile, tablet layout only checked by hand.

## Session 12 notes
Verified locally: `spotlessCheck` (own invocation), then `detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies assembleDebug :app:assembleRelease` pass. `:core:seedmap:connectedDebugAndroidTest` passes (10 tests: 5 `McVersionNativeTest`, 5 `SeedMapEngineGoldenTest`) on the local API 35 arm64 emulator (`Pixel_API35`). The CI jobs themselves can only be proved on GitHub: see the PR body.

What was added:
- `:core:seedmap` (`wikidroid.android.library`, `wikidroid.android.ndk`, `wikidroid.metro`), wired into `settings.gradle.kts` and `:app`. Nothing in the UI uses it yet.
- Convention plugin `wikidroid.android.ndk`: reads `ndk` and `cmake` from `libs.versions.toml`, builds `arm64-v8a` and `x86_64`, and adds the androidTest runner and dependencies.
- Submodule `core/seedmap/src/main/cpp/cubiomes` (xpple/cubiomes), pinned at **`4f04235f3e6e25491a02a830e4f500f68045be17`** ("Add INFER_POSTFIX option"). `MC_NEWEST` is `MC_26_3` (35).
- `seedmap_jni.c` (`nativeCreate`, `nativeDestroy`, `nativeApplySeed`, `nativeGetBiomeAt`, `nativeGetSpawn`, `nativeVersionName`, `nativeNewestVersion`), `NativeSeedMap`, `SeedMapEngine` (`spawn`, `biomeAt`), `JniSeedMapEngine`, and the models `McVersion` (all 35 cubiomes versions), `Dimension`, `MapWorld`, `BlockPos`.
- Tests: JVM `McVersionTest`; instrumented `McVersionNativeTest` (enum vs `mc2str`, newest version, dimension rules) and `SeedMapEngineGoldenTest` (3 seeds x 1.12 / 1.18 / 26.3: spawn and 5 biomes each).
- CI: NDK and CMake installed from the TOML values, `submodules: true` on all three checkouts, `:core:seedmap:connectedDebugAndroidTest` in the `instrumented` job, a 16 KB alignment check plus `.so` sizes in the `release-apk` job and its PR comment, and a `gitsubmodule` Dependabot entry. README and `CLAUDE.md` updated.

Measured (release APK, NDK r30, `-O2`, stripped): `libseedmap.so` is 1,252,648 bytes on arm64-v8a and 1,313,136 on x86_64; every `LOAD` segment is aligned to `0x4000`. `-Oz` was not tried (that is Session 15).

### Deviations
- **NDK r30 (`30.0.16248370`) and CMake 4.1.2**, not r28.2 / 3.22.1 as the plan assumed: they are the newest stable releases `sdkmanager` lists, which is what the version policy asks for.
- **More cubiomes sources than the plan listed.** The static library also needs `biomenoise.c`, `terrainnoise.c`, `xradv.c` and `features/{abandoned_camp,end_city,fortress,stronghold}.c` (`generator.c` and `finders.c` reference them). `loot/`, `carver.c` and `features/ore.c` are still left out. cubiomes is compiled with `-w` so its own warnings do not flood the build.
- **`-Wl,-z,max-page-size=16384`** is set explicitly as well as relying on the NDK default.
- **`McVersion.nativeId` is `ordinal + 1`** rather than a literal per entry (detekt `MagicNumber`, and one fewer thing to keep in sync). `McVersionNativeTest` proves the order still matches cubiomes.
- **`MapWorld(seed, version, dimension)` and `BlockPos`** are extra public models; `SeedMapEngine` takes a `MapWorld` instead of loose arguments, and `MapWorld` rejects a dimension its version lacks (Nether before 1.16.1, End before 1.9).
- **`JniSeedMapEngine` keeps one generator behind a `Mutex`** and never frees it (it is a process-lifetime singleton). Session 13 replaces it with the pool.
- **`nativeApplySeed` calls `setupGenerator` again every time**, because the layer stack, Nether and End noise share a union inside cubiomes' `Generator`.
- **`docs/PLAN.md` was not extended** with Sessions 12-15; the merged docs PR only added `seed-map-plan.md`, which remains the source for these sessions. Only the checklist lines were added here.
- The golden values come from a throwaway host build of the pinned commit (not committed). They were **not spot-checked against Chunkbase**, so they prove the JNI layer and build flags, not cubiomes itself.

### Open TODOs
- R8 has not been exercised on the native code: nothing in `:app` references `SeedMapEngine` yet, so the release build tree-shakes `JniSeedMapEngine`. Session 14 must run the real release build and a tile render.
- Only the arm64 emulator was run locally; `x86_64` (the CI emulator) is proved by CI only.
- Single-point biome queries use scale 1; the native scale-4 path is checked only for rejection of other scales.
- Downloading the NDK through `sdkmanager` stalled on a slow connection; if a fresh machine hits that, install it from Android Studio's SDK Manager.
