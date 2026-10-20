# WikiDroid — Minecraft Wiki Android companion (greenfield plan)

## Context
Local checkout: `/Users/cniekirk/Projects/wikidroid`. GitHub repo:
[`charlie-niekirk/wikidroid`](https://github.com/charlie-niekirk/wikidroid) (public, default branch `main`).
The project started greenfield. Goal: a standalone native Android client
for **minecraft.wiki** (MediaWiki 1.45, hosted by Weird Gloop) with Material 3 UI, built as a multi-module
project with Gradle convention plugins, plus GitHub Actions CI that runs unit + UI tests on every PR and
posts a signed release APK to the PR in a comment.

Confirmed decisions:
- MVP: Search + **native Compose** article viewer, Browse by category, Bookmarks + History (offline), Settings.
- Identity: `dev.cniekirk.wikidroid`, app name "WikiDroid", minSdk 29.
- Tests: JUnit 4 everywhere. Robolectric Compose UI tests on the JVM, plus a small instrumented suite on a CI emulator.
- Quality: Spotless + ktlint, Detekt **2.0.0-alpha.6**, compose-rules, `:baselineprofile` module.
- CI: `gradle/actions/setup-gradle@v6` with its Terms of Use accepted. Release signing uses a keystore from GitHub secrets and falls back to the debug key.
- Version policy: Google/AndroidX libraries use the newest pre-release if it is newer than stable, otherwise stable. Everything else uses latest stable. Approved exceptions: Detekt, and AGP (latest stable).
- Workflow: after Session 1 (committed straight to `main` while bootstrapping), every session is built on its own branch and lands through its own pull request. Nothing is pushed to `main` directly. See "Session protocol".

## Versions (verified 2026-10-07; `gradle/libs.versions.toml`)

### Google / AndroidX

| Component | Version | Notes |
|---|---|---|
| AGP | 9.4.1 | Latest stable (was 9.5.0-alpha08, which broke Android Studio sync). Built-in Kotlin, new DSL. |
| Gradle wrapper | 9.8.1 | Latest stable. |
| compileSdk | 37, minor 1 | Required by Compose 1.13. |
| targetSdk | 37 | |
| build-tools | 37.0.0 | |
| `compose-bom-alpha` | 2026.10.00 | Gives Compose 1.13.0-beta01 and material3 1.5.0-beta01. Both alpha tracks have already moved to beta. |
| `material3.adaptive` | 1.4.0-alpha03 | Includes `adaptive-navigation3`. |
| `navigation3` | 1.3.0-alpha02 | |
| `lifecycle` | 2.12.0-alpha04 | `runtime-compose`, `viewmodel-compose`, `viewmodel-navigation3`. |
| `room3` | 3.1.0-alpha01 | Plugin `androidx.room3`, KSP. |
| `sqlite-bundled` | 2.8.0-alpha01 | |
| `datastore` | 1.3.0-alpha11 | |
| `activity-compose` | 1.14.0-alpha03 | |
| `benchmark` / `androidx.baselineprofile` | 1.6.0-alpha01 | |
| `profileinstaller` | latest alpha | Check when implementing. |
| `webkit` | not used | |

These have no pre-release newer than stable, so they use stable:

| Component | Version |
|---|---|
| core-ktx | 1.19.1 |
| core-splashscreen | 1.2.0 |
| browser (Custom Tabs) | 1.10.0 |
| androidx.test runner/core | 1.7.0 |
| ext-junit | 1.3.0 |
| espresso | 3.7.0 |
| uiautomator | 2.4.0 |
| KSP | 2.3.12 |

### Other libraries (stable)

| Component | Version |
|---|---|
| Kotlin, `plugin.compose`, `plugin.serialization` | 2.4.20 |
| kotlinx-serialization-json | 1.11.0 |
| coroutines | 1.11.0 |
| collections-immutable | 0.5.2 |
| datetime | 0.8.0 |
| Metro (`dev.zacsweers.metro`, `metrox-viewmodel`, `metrox-viewmodel-compose`) | 1.4.5 |
| Orbit (`core`, `viewmodel`, `compose`, `test`) | 12.0.1 |
| Retrofit + `converter-kotlinx-serialization` | 3.0.0 |
| OkHttp BOM (`okhttp`, `logging-interceptor`, `mockwebserver3`) | 5.5.0 |
| Coil 3 (`coil-compose`, `coil-network-okhttp`) | 3.6.3 |
| jsoup | 1.23.2 |
| junit | 4.13.2 |
| Turbine | 1.2.1 |
| Truth | 1.4.5 |
| Robolectric | 4.17 |
| Spotless | 8.10.3 |
| ktlint | 1.8.0 |
| compose-rules (ktlint + detekt) | 0.6.7 |
| Detekt (`dev.detekt`) | 2.0.0-alpha.6 |
| foojay resolver | 1.0.0 |
| JDK toolchain | 21 |

### GitHub Actions

| Action | Version |
|---|---|
| `actions/checkout` | v7 |
| `actions/setup-java` | v6 |
| `gradle/actions/setup-gradle` | v6 |
| `actions/upload-artifact` | v7 |
| `reactivecircus/android-emulator-runner` | v2 |
| `marocchino/sticky-pull-request-comment` | v3.0.5 (pinned exactly; there is no floating v3 tag) |

### Gotchas to respect
- **AGP 9:** don't apply `org.jetbrains.kotlin.android`.
  - Putting the KGP 2.4.20 plugins on the classpath at the root, with `apply false`, overrides AGP's Kotlin 2.2.10 floor.
  - `CommonExtension` is no longer generic, and `defaultConfig.apply {}` is used on `CommonExtension`.
  - Use only the `androidComponents` variant API.
  - R8 full mode is strict, so use `proguard-android-optimize.txt`.
- **Metro:** it is a compiler plugin with no KSP.
  - Apply it in every module that has `@Inject` or `@Contributes*`.
  - `:app` must depend **directly** on every module that contributes bindings. Verify with `:app:checkMainMetroHiddenDependencies`.
- **Room 3:** KSP only, and a `SQLiteDriver` is required (`BundledSQLiteDriver`).
  - DAO functions are `suspend` or return `Flow`.
  - `@ColumnTypeConverter` replaces `@TypeConverter`, and `room3 { schemaDirectory(...) }` is required.
- **Orbit 12:** `OrbitContainerHost<S, S, E>` and `orbitContainer()`. Tests use `testWithExternalState { expectState{} }`.
- **Retrofit 3** pulls in OkHttp 4. Force OkHttp 5.5.0 through the BOM.
- **Material icons are frozen at 1.7.8.** Ship Material Symbols as vector drawables in `:core:designsystem` instead.

## Module layout
```
build-logic/convention      included build with the convention plugins
app                         MainActivity, AppGraph, Nav3 host, NavigationSuiteScaffold, splash
core:model                  JVM: domain models (ArticleSummary, Article, ContentBlock, RichText, Category, UserPreferences…)
core:common                 JVM: dispatcher qualifiers, Result/DataError, coroutine utils
core:article-parser         JVM + jsoup: MediaWiki HTML → List<ArticleSection<ContentBlock>> (pure, heavily unit-tested)
core:network                Retrofit MediaWikiApi (api.php) + WikiRestApi (rest.php/v1), DTOs, OkHttp (UA, cache), Metro providers
core:database               Room 3: bookmarks, history, cached_articles (HTML + revid), DAOs, BundledSQLiteDriver
core:datastore              JSON DataStore<UserPreferences> with a kotlinx-serialization Serializer, plus recent searches
core:data                   Repositories: Search, Article (offline-first), Category, Library, Settings, WikiInfo
core:domain                 Use cases: the business logic view models call (open an article, run a search, clear data, seed map queries)
core:designsystem           M3 theme (dynamic color + Minecraft-inspired fallback scheme), typography, Symbols icons, base components
core:ui                     Shared composables: ArticleCard, PageThumbnail (pixel-art aware), Loading/Error/Empty states, AttributionFooter
core:navigation             @Serializable NavKeys, Navigator (top-level back stacks), EntryProviderInstaller contract
core:testing                Fakes for repositories, MainDispatcherRule, HTML/JSON fixtures, Robolectric test base
feature:explore             Home (latest versions card, random article, category grid), category detail (paged members + subcats)
feature:search              Debounced autocomplete, full-text results (paged), recent searches
feature:article             Article screen: native block renderer, TOC sheet, bookmark/share/open-in-browser, attribution
feature:library             Bookmarks and History tabs (Room Flows), swipe-to-delete, offline badges; opened from Explore's top bar since Session 24
feature:settings            Theme/dynamic colour/text size/edition, clear cache/history, About + licences + disclaimer
feature:tools               Tools tab (Session 24): the resource calculator over bundled Java Edition crafting recipes
baselineprofile             com.android.test + androidx.baselineprofile generator (run locally, not in CI)
```
Feature modules never depend on each other. They navigate only through NavKeys in `:core:navigation`.

## Convention plugins (`build-logic/convention`, ids `wikidroid.*`)
- **`wikidroid.android.application`**
  - SDK levels: compileSdk 37.1, targetSdk 37, minSdk 29.
  - Build setup: JDK 21 toolchain, R8 on release, `versionCode` from `GITHUB_RUN_NUMBER`.
  - Release signing reads `WIKIDROID_KEYSTORE_*` env vars or Gradle properties and falls back to the debug signing config.
- **`wikidroid.android.library`:** library defaults, `unitTests.isIncludeAndroidResources = true` for Robolectric, and the shared test dependencies (junit, truth, turbine, coroutines-test).
- **`wikidroid.android.compose`:** the compose compiler plugin, `compose-bom-alpha`, ui-tooling for debug, and Robolectric and compose ui-test-junit4 for tests. Used by both app and library.
- **`wikidroid.android.feature`:** applies the library, compose, metro and serialization plugins.
  - Adds `core:designsystem`, `ui`, `navigation`, `model`, `data` and `domain`.
  - Adds orbit-viewmodel/compose, `metrox-viewmodel-compose`, lifecycle and Nav3 runtime.
  - Adds `core:testing` and orbit-test for tests.
- **`wikidroid.android.room`:** room3 plugin, KSP, schema dir `$projectDir/schemas`, room3 and sqlite-bundled deps.
- **`wikidroid.metro`:** applies `dev.zacsweers.metro`.
- **`wikidroid.jvm.library`:** `org.jetbrains.kotlin.jvm` plus a JDK 21 toolchain and test deps.
- **`wikidroid.detekt`:** applied by every base plugin. Uses `config/detekt/detekt.yml` and the compose-rules detekt plugin.
- **`wikidroid.spotless`:** applied at the root to all projects. ktlint 1.8.0 plus compose-rules ktlint, with `.editorconfig`.
- **Shared helpers:** a `Project.libs` extension, `configureKotlin()` (explicit API off, `-Xcontext-parameters` if needed), and `configureAndroidCommon()`.

## Key architecture

### DI (Metro)
- `:app` defines `@DependencyGraph(AppScope::class) interface AppGraph : ViewModelGraph`.
  - Its factory takes `@Provides Application` and `@Provides @WikiBaseUrl HttpUrl`. The test runner injects a MockWebServer URL through that second parameter.
  - The graph exposes `metroViewModelFactory` and `entryInstallers: Set<EntryProviderInstaller>`.
- Core modules contribute through `@ContributesTo(AppScope::class)` provider interfaces (OkHttp, Retrofit, Json, Room DB/DAOs, DataStore, Coil `ImageLoader`).
- Repositories use `@Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class)`.
- `AppViewModelFactory : MetroViewModelFactory` lives in `:app`.
- Plain ViewModels use `@ViewModelKey @ContributesIntoMap`. ViewModels that take a nav argument (Article, CategoryDetail) use `@AssistedInject` with a `@ManualViewModelAssistedFactoryKey` factory, and call `assistedMetroViewModel<VM, Factory> { create(key.title) }`.

### Navigation (Nav3)
- NavKeys:
  - Top level: `ExploreKey`, `SearchKey`, `SeedMapKey`, `ToolsKey`, `SettingsKey` (Session 14 added the seed map; Session 24 replaced the Library tab with Tools and made `LibraryKey` a pushed key).
  - Detail: `CategoryKey(title)`, `ArticleKey(title, anchor?)`.
- `Navigator` keeps one back stack per top-level tab and is saved with `rememberSerializable`.
- `NavDisplay` uses:
  - `entryDecorators = [saveableStateHolder, viewModelStore]`
  - `sceneStrategies = [rememberListDetailSceneStrategy()]`, so category/search lists and the article sit side by side on large screens.
- Each feature contributes `@ContributesIntoSet(AppScope::class) EntryProviderInstaller`. The app installs all of them into `entryProvider {}`.
- `NavigationSuiteScaffold` provides the bottom bar or rail depending on window size.
- Internal wiki links (`/w/Title#anchor`) push an `ArticleKey`. External links open Custom Tabs.

### MVI (Orbit)
- Every ViewModel is `OrbitContainerHost<UiState, UiState, SideEffect>` with an `@Immutable` state using immutable collections.
- Screens are split into a stateful wrapper that calls `collectAsState()` / `collectSideEffect`, and a stateless `XxxScreen(state, onAction)` used by previews and tests.
- Pagination is manual: the MediaWiki `continue` token is stored in state, with `intent { loadMore() }` guarded against concurrent loads. Paging 3 isn't used because manual paging is simpler with Orbit.
- ViewModel shape (from Session 16):
  - Each action is one `intent { }` block. There are no `Syntax<S, E>` helper extensions; detekt's `ForbiddenImport` rejects `org.orbitmvi.orbit.syntax.Syntax`.
  - Suspend work is a use case (`:core:domain`) or repository call that returns a value; the intent then reduces with a member function of the state class (`state.withResults(query, result)`). Staleness checks live in those members.
  - A load that is also a retry (or runs on open) is one intent function that `onCreate` and the retry action both call; its guard (`Mutex.tryLock`, a phase check) is inside it.
  - Work driven by a `collectLatest` pipeline in `onCreate` stays in the collector, so a newer value still cancels it.
- Use cases (`:core:domain`) exist only where there is logic: `@Inject class XxxUseCase(...) { suspend operator fun invoke(...) }`, concrete and unscoped. Pass-through calls (a setter, a repository flow) go straight to the repository. Tests build use cases over the `:core:testing` fakes.

### Network (`:core:network`)
- `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
- Interceptors:
  - User-Agent: `WikiDroid/<versionName> (Android; https://github.com/charlie-niekirk/wikidroid)`. This is required, since an empty UA gets a 403.
  - Default params on api.php: `format=json&formatversion=2`.
  - Logging in debug builds.
- 50 MB OkHttp disk cache. Article parse calls add `maxage=600&smaxage=600`.
- MediaWiki returns `{"error":…}` with HTTP 200, so a response wrapper maps that to `DataError.Api`.
- Endpoints used:

| Feature | Endpoint |
|---|---|
| Autocomplete | `rest.php/v1/search/title?q=&limit=10` (includes thumbnails) |
| Full-text search | `api.php?action=query&generator=search&gsrsearch=&gsrnamespace=0&gsrlimit=20&prop=pageimages\|extracts\|info&piprop=thumbnail&pithumbsize=160&exintro&explaintext&exsentences=2&exlimit=max&inprop=url` + `gsroffset` continue (sorted by `index`) |
| Category members | `generator=categorymembers&gcmtitle=Category:X&gcmnamespace=0\|14&gcmlimit=50` + the same props + `gcmcontinue` |
| Article | `action=parse&page=&prop=text\|sections\|displaytitle\|categories\|revid&mobileformat=1&disableeditsection=1&disabletoc=1&redirects=1` |
| Freshness | `prop=info` → `lastrevid` |
| Latest versions | `action=expandtemplates&text={{Version\|java}}\|{{Version\|java-snap}}\|{{Version\|bedrock}}\|{{Version\|bedrock-preview}}&prop=wikitext` |
| Random | `generator=random&grnnamespace=0&grnfilterredir=nonredirects` + pageimages/extracts. Skip titles that look like version or snapshot pages and retry. |

- Explore tiles are a curated list in code:
  - Natural blocks / Blocks, Items, Hostile/Passive/Neutral mobs
  - Overworld/Nether/End biomes, Generated structures
  - Enchantments, Effects, Potions, Food, Tools, Weapons, Armor
  - Redstone, Plants, Commands, Tutorials

### Article parsing (`:core:article-parser`, pure JVM)
- **Strip:**
  - Everything from `h2#Navigation` onward, `table.navbox`, `.navigation-not-searchable`
  - `pre.history-json`, `.chest-json`, `.noexcerpt`
  - `.navbar-mini`, `#toc`, `.mw-editsection`, `<style>`
- **Map to `ContentBlock`:**
  - Text and lists: `Heading(level, text, anchor)`, `Paragraph(RichText)`, `ListBlock(ordered, items)`, `Note(RichText)` for hatnotes.
  - Media: `Image(url, w, h, caption, pixelated)`, `Gallery`.
  - Tables: `Table(rows: List<List<Cell(RichText, header, colspan)>>)`.
  - Structured: `Infobox(title, images, rows)`, `CraftingGrid(slots[3][3], output)` from `span.mcui-Crafting_Table`.
  - `Unsupported(htmlSnippet)`, which renders as an "Open on wiki" chip.
- `RichText` is a list of spans with style flags and an optional `Link.Internal(title, anchor)` or `Link.External(url)`. The UI converts it to an `AnnotatedString` with `LinkAnnotation.Clickable`.
- Sections come from the `mf-section-N` wrappers and are collapsible in the UI.
- Image URLs are resolved against `https://minecraft.wiki`. Elements with `.pixel-image` / `.sprite-file` set `pixelated = true`, which the UI renders with `FilterQuality.None`.

### Offline (`:core:database` + ArticleRepository)
- Tables:
  - `bookmarks(title PK, displayTitle, thumbnailUrl, savedAt)`
  - `history(title PK, displayTitle, thumbnailUrl, viewedAt)`
  - `cached_articles(title PK, revid, html, fetchedAt)`
- `getArticle(title): Flow<Result<Article>>`:
  1. Emit the cached copy, parsed, immediately.
  2. Fetch from the network.
  3. If `revid` changed, update the cache and emit again.
- Unbookmarked cache entries are pruned beyond 100 rows. Storing raw HTML rather than parsed blocks means parser upgrades apply to cached pages too.

### Settings (`:core:datastore`)
- `UserPreferences(themeMode, dynamicColor, textScale, preferredEdition, saveHistory)` is persisted with `DataStore.Builder` and a custom `JsonSerializer<T>` (kotlinx.serialization).
- Recent searches are stored in the same file.

### Legal and UX
- Every article ends with a footer: "Content from the Minecraft Wiki, CC BY-NC-SA 3.0", plus a link to the page and its history.
- The About screen includes "Unofficial app, not affiliated with Mojang or Microsoft".
- The NC clause means the app has no ads or monetisation.

## Testing
- **Unit tests (JVM):**
  - Parser tests against trimmed real HTML fixtures (Diamond, Creeper, Crafting Table) in `core:article-parser/src/test/resources`.
  - Network tests with MockWebServer and JSON fixtures.
  - Repository tests with fake DAOs and services.
  - DataStore serializer round-trip tests.
  - ViewModel tests with orbit-test (`testWithExternalState`), with real use cases over the repository fakes.
  - Use case tests in `:core:domain`, and plain tests of the state classes' reducer members.
  - Room DAO tests under Robolectric with an in-memory DB and `BundledSQLiteDriver`.
- **Robolectric Compose UI tests:** in each feature's `src/test`, using `createComposeRule()` against the stateless screens (loading, error, content, interactions).
- **Instrumented (emulator):** in `app/src/androidTest`.
  - A custom `WikiDroidTestRunner` swaps in a `TestWikiApp` that builds `AppGraph` pointed at an in-process `MockWebServer`, which serves fixtures through a `Dispatcher`.
  - Scenarios: tab navigation, search → article → bookmark → appears in Library, settings theme toggle persists.

## CI (`.github/workflows/`)
- **`pr.yml`**, on `pull_request` and `push: main`, with `concurrency` set to cancel in progress.
  1. **`checks`** (ubuntu): checkout, JDK 21, setup-gradle v6 with ToU accepted.
     - Runs `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies`.
     - Uploads test and lint reports with `if: failure()`.
  2. **`instrumented`** (ubuntu): enable KVM, cache the AVD, then `android-emulator-runner@v2` (API 35, x86_64, google_apis, no animations) runs `./gradlew :app:connectedDebugAndroidTest`. Reports are uploaded.
  3. **`release-apk`** (needs `checks`):
     - Decodes `KEYSTORE_BASE64` into the runner temp dir. If the secrets are absent, it builds debug-signed.
     - Runs `./gradlew :app:assembleRelease`, then `upload-artifact@v7` (`wikidroid-release-pr<N>`).
     - `sticky-pull-request-comment@v3.0.5` posts or updates a comment with the artifact `artifact-url`, version, short SHA, APK size, and whether it was release- or debug-signed.
     - Permissions: `pull-requests: write`. The comment step is skipped for fork PRs, where the token is read-only.
- **Limitation:** GitHub can't attach binaries to PR comments. The comment links the workflow artifact, which needs a GitHub login to download.
- **`dependabot.yml`:** github-actions and gradle, weekly.
- **Required repo secrets:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The README documents generating them.

## Root files
- Build config: `settings.gradle.kts` (pluginManagement includeBuild `build-logic`, foojay, typesafe project accessors), root `build.gradle.kts` (plugins `apply false`, spotless), `gradle.properties` (configuration cache, build cache, parallel, `android.useAndroidX`).
- Gradle wrapper 9.8.1.
- Lint config: `.editorconfig`, `config/detekt/detekt.yml`.
- `.gitignore`, `README.md` (setup, Android Studio version requirement, secrets, attribution).
- Git: the repo is `charlie-niekirk/wikidroid` and `main` is the default branch. All changes after Session 1 reach `main` through a pull request (see "Session protocol").

## Implementation sessions (run in order, one per session)

### Session protocol (applies to every session after Session 1)
Each session is one branch and one pull request into `main`. The user's request to run a session is standing permission to create the branch, commit, push it and open the PR. It is **not** permission to merge: the user reviews and merges.

- **Start:**
  1. Make sure the previous session's PR is merged. If it isn't, stop and tell the user rather than stacking branches, unless they ask for a stacked PR (branch from the previous session's branch and set `--base` to it).
  2. Sync and branch:
     ```
     git switch main && git pull --ff-only
     git switch -c session-<N>-<short-slug>      # e.g. session-2-foundation-core
     ```
  3. Read `CLAUDE.md`, `docs/PLAN.md` (a copy of this plan) and `docs/PROGRESS.md`.
  4. Confirm the previous session's "Done when" still passes: `./gradlew build -x connectedCheck` or the listed commands.
- **During:**
  - Stay inside the session's scope. If an earlier module needs changing, keep the change minimal and record it in PROGRESS.md.
  - Commit on the session branch in small logical commits using conventional prefixes (`feat:`, `fix:`, `test:`, `build:`, `docs:`, `chore:`). Never commit to `main`, never force-push `main`, and keep secrets, keystores and `local.properties` out of git.
- **End:**
  1. Run the session's "Done when" commands and keep the results.
  2. Tick the session in `docs/PROGRESS.md`, adding deviations, version workarounds and open TODOs. This goes in the same PR.
  3. Push and open the PR:
     ```
     git push -u origin session-<N>-<short-slug>
     gh pr create --base main --title "feat: session <N> – <name>" --body-file <body.md>
     ```
     The PR body has these sections: **Summary** (what was built), **Done when** (each command or manual check and its result), **Deviations and workarounds**, **Open TODOs**, **Manual checks for the reviewer**. End the body with the attribution line required by the session's tooling.
  4. Report the PR link to the user and stop. Do not merge, enable auto-merge, or start the next session.
- **After the PR is open:**
  - Review feedback and CI fixes go on the same branch as new commits. Don't amend or force-push once the PR has been reviewed unless the user asks.
  - Until Session 11 there is no CI, so the PR body's "Done when" results are the evidence. From Session 11 on, all workflow jobs must be green before the PR is reported as ready.
  - Changes to this plan or to `CLAUDE.md` also go through a PR (a `docs/<slug>` branch).
- **Definition of green** for every session, unless the session says otherwise: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug` passes.

### Session 1: Repo bootstrap and convention plugins
- **Status:** done. Committed directly to `main` (`ff5d899`) because the repo did not exist yet; this is the only session that skips the PR flow.
- **Depends on:** nothing.
- **Build:**
  - Setup: `git init`, `.gitignore`, `.editorconfig`, `gradle.properties`, Gradle wrapper 9.8.1.
  - Gradle config: `gradle/libs.versions.toml` with every version above, `settings.gradle.kts` (includeBuild `build-logic`, foojay, all module includes commented out until they exist), root `build.gradle.kts`.
  - `build-logic/convention` with all `wikidroid.*` plugins listed above, and `config/detekt/detekt.yml`.
  - Docs: `CLAUDE.md` (module map, conventions, commands, version policy), `docs/PLAN.md` (this plan), `docs/PROGRESS.md` (session checklist).
  - A minimal `:app` (Compose "Hello WikiDroid" Activity) using `wikidroid.android.application`, `wikidroid.android.compose` and `wikidroid.metro`, with a trivial `@DependencyGraph`. This proves Metro works with AGP 9 built-in Kotlin.
  - Install SDK `platforms;android-37.1` and `build-tools;37.0.0` if missing. Locally there are only the 37.0/37.1 platforms and build-tools 36.1.0-rc1.
- **Done when:** `./gradlew :app:assembleDebug :app:assembleRelease spotlessCheck detekt` passes and the APK installs and launches on an emulator. Record any AGP alpha, Metro or Detekt workarounds in CLAUDE.md.

### Session 2: Foundation core modules
- **Depends on:** Session 1.
- **Build:**
  - `:core:model`: all domain models, including `ContentBlock`, `RichText`, `UserPreferences`, `ArticleSummary` and `Category`.
  - `:core:common`: dispatcher qualifiers and Metro providers, `DataError`/`Result`.
  - `:core:designsystem`: color schemes, typography, `WikiDroidTheme(themeMode, dynamicColor)`, Material Symbols drawables, base components.
  - `:core:navigation`: NavKeys, `Navigator` with per-tab back stacks, `EntryProviderInstaller`.
  - `:core:testing`: `MainDispatcherRule`, Robolectric base.
- **Done when:** green. Includes Navigator unit tests (push, pop, tab switch, state restore) and Robolectric smoke tests for the theme and components.

### Session 3: Data sources (network, database, datastore)
- **Depends on:** Session 2.
- **Build:**
  - `:core:network`:
    - Retrofit services, DTOs for every endpoint in the table above, interceptors (UA, default params, logging), disk cache, MediaWiki error mapping.
    - Metro `@ContributesTo` providers, including the `@WikiBaseUrl HttpUrl` binding.
    - Capture **real trimmed JSON fixtures** from the live API into `core:testing` resources.
  - `:core:database`: Room 3 entities and DAOs, `BundledSQLiteDriver`, exported schema v1, providers.
  - `:core:datastore`: `JsonSerializer<UserPreferences>`, `DataStore.Builder`, a preferences data source with recent searches.
- **Done when:** green. Includes MockWebServer tests per endpoint, Robolectric Room DAO tests, and a DataStore round-trip test.

### Session 4: Article parser
- **Depends on:** Session 2 (models).
- **Build:** `:core:article-parser`, which turns jsoup HTML into sections and blocks. It handles stripping rules, infobox, crafting grid, tables, lists, images (pixelated detection), hatnotes, rich-text links and anchors, and `Unsupported` fallback. Store trimmed HTML fixtures from `action=parse` for Diamond, Creeper, Crafting Table and one tutorial page.
- **Done when:** green. Parser tests cover each block type and the stripping rules, and parse the full Diamond fixture in under 300 ms on the JVM.

### Session 5: Repositories and shared UI
- **Depends on:** Sessions 3 and 4.
- **Build:**
  - `:core:data` repositories: Search, Article (offline-first with revid check, cache pruning), Category, Library, Settings, WikiInfo (latest versions, random), all bound with `@ContributesBinding`.
  - Fake implementations of every repository in `:core:testing`.
  - `:core:ui`: ArticleCard, PageThumbnail (Coil and pixel-art aware), Loading/Error/Empty states, AttributionFooter, and a Coil `ImageLoader` provider that shares the OkHttp client.
- **Done when:** green. Includes repository tests (cache emit, then network emit, revid unchanged meaning no re-emit, pruning) and Robolectric tests for the shared composables.

### Session 6: App shell
- **Depends on:** Session 5.
- **Build:** in `:app`:
  - DI: `AppGraph : ViewModelGraph` with factory params (Application, base URL), `AppViewModelFactory`, `WikiDroidApp`.
  - UI: `MainActivity` with the splash screen and edge-to-edge, `NavigationSuiteScaffold` with four tabs, `NavDisplay` (decorators and list-detail scene strategy) installing `Set<EntryProviderInstaller>`, a theme driven by `SettingsRepository`, and placeholder screens for each tab.
  - Wire every core module dependency directly into `:app`.
- **Done when:** green, plus `:app:checkMainMetroHiddenDependencies` passes. The app launches on the emulator, tabs switch with independent back stacks, and the dark/light theme follows the system.

### Session 7: `:feature:explore` and `:feature:search`
- **Depends on:** Session 6.
- **Build:**
  - Explore: latest-versions card, random article, curated category grid, and the CategoryDetail screen (assisted VM, manual pagination, subcategory chips). Tapping an item pushes `ArticleKey` or `CategoryKey`.
  - Search: 250 ms debounced autocomplete, full-text paged results, recent searches.
  - Article navigation targets a stub until Session 8.
- **Done when:** green. Includes orbit-test ViewModel tests and Robolectric screen tests. Manually check on the emulator that live data loads.

### Session 8: `:feature:article`
- **Depends on:** Session 7.
- **Build:**
  - `ArticleViewModel` (assisted, title arg) with offline-first loading and history recording.
  - Block renderers: heading, paragraph with clickable links, list, image, gallery, horizontally scrollable table, infobox card, crafting grid, note, unsupported chip.
  - Collapsible sections, TOC bottom sheet with anchor scroll, and anchor handling on open.
  - Top bar actions: bookmark toggle, share, Custom Tabs "Open on wiki". Text scale comes from preferences, followed by the attribution footer.
  - Replace the Session 7 stub.
- **Done when:** green. Includes VM tests and Robolectric renderer tests per block type. On the emulator, Diamond, Creeper and Crafting Table render correctly, internal links navigate, and the tablet layout shows list and detail.

### Session 9: `:feature:library` and `:feature:settings`
- **Depends on:** Session 8.
- **Build:**
  - Library: Bookmarks and History tabs, swipe-to-delete with undo snackbar, offline badge, clear history.
  - Settings: theme mode, dynamic color, text size, preferred edition, save-history toggle, clear article cache and history, and an About screen with licences, attribution and the unofficial disclaimer.
- **Done when:** green. Includes VM and Robolectric tests. Manually: bookmark an article, go into airplane mode, and it still opens from Library. Theme changes persist across restarts.

### Session 10: Instrumented tests and baseline profile
- **Depends on:** Session 9.
- **Build:**
  - `WikiDroidTestRunner` and `TestWikiApp`, which build the graph with a MockWebServer URL. Add a fixture `Dispatcher`.
  - `app/src/androidTest` scenarios: tab navigation; search → article → bookmark → Library; settings persistence.
  - `:baselineprofile` module with a generator journey (startup, Explore scroll, open article). Add `androidx.baselineprofile` and `profileinstaller` to `:app`.
- **Done when:** `./gradlew :app:connectedDebugAndroidTest` passes on a local API 35 emulator, `:app:generateBaselineProfile` produces a profile (committed), and the release build includes it.

### Session 11: CI and docs
- **Depends on:** Session 10. The GitHub remote (`charlie-niekirk/wikidroid`) already exists.
- **PR note:** this session's own PR is the first one with CI, so it doubles as the "test PR" below. Repo secrets (`KEYSTORE_BASE64` etc.) are added by the user in GitHub settings; the session never handles real keystore values.
- **Build:**
  - `.github/workflows/pr.yml` with the `checks`, `instrumented` and `release-apk` jobs as specified above.
  - `.github/dependabot.yml`.
  - README covering setup, the Android Studio version requirement, secret generation (`keytool` + `base64`), attribution and the disclaimer.
  - Run `actionlint` locally if available.
- **Done when:** a test PR shows all three jobs green, the sticky comment carries the APK artifact link, and a fork-style run with no secrets still produces a debug-signed APK.

### Session 16: ViewModel use cases
- **Status:** done (refactor; no user-visible change).
- **Build:** add `:core:domain` with the use cases; rewrite every `Syntax<S, E>` helper as an intent or a use case call; move the pure state helpers into the state classes; forbid the `Syntax` import in detekt.
- **Done when:** green, `lintDebug` and `:app:checkMainMetroHiddenDependencies` pass, the existing ViewModel tests pass with only their constructors changed, and `:app:connectedDebugAndroidTest` passes.

### Session 17: Immersive seed map
- **Status:** done.
- **Build:** the seed map in the style of a maps app.
  - The map runs edge to edge, behind the status bar. A surface-coloured scrim keeps the status bar icons readable.
  - Floating over it:
    - The seed **search pill**. Expanded, it lists saved seeds, a random seed, "use seed" for typed text and "go to" for typed coordinates. It also holds the save and legend buttons.
    - A **World chip** that opens the **layers sheet**. The sheet has dimension cards, a version menu, and grid and crosshair switches.
    - A **Structures chip** that opens the filter sheet.
    - The coordinate readout.
    - A floating **toolbar** for spawn and zoom. It is vertical on a tall map and horizontal on a short one.
  - The top bar, the version and dimension sheets, the saved-seeds menu and the go-to-coordinates dialog are gone.
- **Done when:** the build is green, `lintDebug` and `:app:checkMainMetroHiddenDependencies` pass, `:app:connectedDebugAndroidTest` passes, and an emulator check covers portrait, landscape (list pane), search, the layers sheet and the grid.

### Session 18: Seed search that finds structures
- **Status:** done.
- **Build:**
  - Typing a structure name in the seed search offers "Nearest <structure>" rows, with the marker icon. Tapping one centres the map on the nearest structure of that type, turns its toggle on, and opens its selection sheet. When none is within reach, a snackbar says so.
  - `FindNearestStructureUseCase` in `:core:domain`. Region-based types search square rings of at most `MAX_STRUCTURE_SPAN` outward from the view's centre. The search stops when the closest hit is nearer than the next ring, or after about 4 rings. Strongholds take the nearest of `engine.strongholds`.
  - `SeedSuggestion` gains a `Nearest(type)` row between the coordinates and "use seed".
- **Done when:** the build is green, unit tests cover the use case (fake engine), the suggestions and the ViewModel intent (found and not found), and `:app:connectedDebugAndroidTest` passes.

### Session 19: Slime chunks and points of interest
- **Status:** done.
- **Build:**
  - A Slime chunks switch in the layers sheet shades the Overworld's slime chunks. `SlimeChunks` is cubiomes' `isSlimeChunk` in Kotlin, checked against host-built goldens. A tapped biome says whether it is a slime chunk.
  - Points of interest, per seed and dimension, are stored in Room (database version 2, auto-migrated):
    - A long press adds one: name, kind (base, Nether portal, farm, mine, landmark, other), colour, optional Y and a note.
    - Points draw on the map with their names. Their sheet has edit, delete (undo) and, for a portal, the linked position in the other dimension with a button to add it.
    - A Points chip lists them, and the seed search finds them by name.
- **Done when:** the build is green, unit tests cover the slime goldens, the repository, the ViewModel, the hit test and the sheets, the migration test passes on a device, and `:app:connectedDebugAndroidTest` passes.

### Session 20: Measure distance and My worlds
- **Status:** done (My worlds opens from the seed search's saved seeds, not the pill; see `docs/PROGRESS.md`).
- **Depends on:** Session 19.
- **Build:**
  - A ruler button in the map toolbar starts measuring. Map taps then set the start and move the end, and a dashed line joins them. A chip shows the distance in blocks and the Nether equivalent (Overworld distance / 8, or Nether distance x 8), with a close button. A point's or structure's sheet offers "Measure from here".
  - A My worlds sheet, opened from the saved seeds in the seed search, lists saved seeds with their version and number of points:
    - Tapping a seed switches to it.
    - Rename reuses the save dialog's field.
    - Delete keeps the points, which belong to the seed; the confirmation says so.
    - Counts come from a new `PointOfInterestDao.countBySeed()` through `PointOfInterestRepository.countsBySeed()`.
- **Done when:** the build is green, unit tests cover the measurement maths, the ViewModel actions and the sheet, and `:app:connectedDebugAndroidTest` passes.

## Architecture follow-up (Sessions 22–34)
An architecture review after Session 21 found bugs that come from structure. Each tab loses its screen state when the user leaves it, redirect titles miss the article cache, and the seed map's tile cache is smaller than what the canvas asks for. It also found structural debt: an inconsistent error model, unenforced layering, a 23-field `SeedMapState`, domain logic in `:feature:seedmap`, and the UI model doubling as the stored JSON schema. These sessions fix that in order: bugs (22–25), the error model (26), the seed map restructure (27–28), domain-only layering (29–30), a feature MVI tidy-up (31), then hygiene (32–34). Each follows the Session protocol above.

Decided with the user: **features call use cases only.** From Session 31 on, features depend on `:core:domain` and never on `:core:data`.

### Session 22: Per-tab screen state
- **Status:** done.
- **Depends on:** Session 21.
- **Problem:** `AppShell` gives `NavDisplay` only the current tab's stack, with one pair of decorators. On a tab switch, every entry of the old tab leaves that stack, so its ViewModels and saveable state are cleared. This is the "state lost when leaving a tab" TODO open since Session 7.
- **Build:**
  - `Navigator`: add `stack(tab)` and `canGoBack`, which is the rule now duplicated between `goBack()` and AppShell's `BackHandler`.
  - `AppNavDisplay`, following the Nav3 `MultipleBackStackSample` pattern:
    - On **every** composition, loop over `TopLevelKeys`, never behind `if (tab == currentTab)`.
    - Inside `key(tab)`, give each tab its own `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`, then call `rememberDecoratedNavEntries(navigator.stack(tab), decorators, entryProvider)`. ViewModel store providers key by composite key hash, so `key(tab)` keeps each tab's stores apart.
    - Show the current tab with the `NavDisplay(entries = ...)` overload, wrapped in `key(navigator.currentTab)`. Entry identity is the key alone, so without the outer key the same `ArticleKey` on two tabs (or two `WikiPanes.list()` scenes) would share or leak saved state.
    - Keep `BackHandler(enabled = backStack.size == 1 && navigator.canGoBack)`. `NavDisplay` only handles back when the scene has previous entries.
    - `placeholderEntry` uses `key as? WikiKey`.
  - PROGRESS.md: close the per-tab state TODOs. Note that ViewModels survive tab switches but not process death (no `SavedStateHandle`), and that every tab's `ArticleViewModel` now stays in memory.
- **Done when:**
  - Green. `lintDebug`, `:app:checkMainMetroHiddenDependencies` and `:app:connectedDebugAndroidTest` pass.
  - `NavigatorTest` covers `stack`/`canGoBack`.
  - `AppShellTest` uses a test ViewModel that counts instances: it survives a tab round trip, is cleared on pop, and the same key on two tabs keeps separate state.
  - New instrumented test: the search query and results, and the seed map's seed and camera, survive a tab round trip.
  - Emulator check: tablet list-detail, and predictive back from a tab root.

### Session 23: Article cache correctness
- **Status:** done.
- **Depends on:** Session 22.
- **Build:**
  - **Redirect aliases (database v3):**
    - New entity `ArticleAliasEntity(requested PK, canonical)`, with a foreign key to `cached_articles.title` `ON DELETE CASCADE` and `Index("canonical")`. Add `@AutoMigration(2, 3)` and commit `schemas/3.json`.
    - Keep `@Upsert` on `cached_articles`: `REPLACE` would delete the row and cascade to its aliases.
    - A `@Transaction` DAO method stores the article and its alias, and deletes any row cached under the requested title, so a stale "old name" row can't keep winning the lookup.
    - `getArticle` reads `cache.get(requested) ?: aliasFor(requested)?.let(cache::get)`.
    - Today a redirect name always misses the cache, and `ArticleRepositoryTest` asserts that. The test flips: the name now resolves from cache, offline too.
  - **Refresh by revision:**
    - Add `MediaWikiApi.parseRevision(oldid)`: the same props, without `page`, `redirects` or `maxage`. Add a matching `WikiRemoteDataSource.parseRevision` and fake handler.
    - `refreshIfStale` downloads the probed revision, so OkHttp's cached `page=` response (`maxage=600`) can't return the old revision.
    - Add a fixture and a MockWebServer test.
  - **Errors:** `cache.get` and `aliasFor` are wrapped in `dataResultOf`. `toArticle` maps only parser failures to `DataError.Parse`.
  - **Dead field:** remove `Article.categories`, which nothing fills in, and the `categories` parse prop. Update `ArticleEndpointsTest`.
- **Done when:** green. The v2→v3 migration test (`core/database/src/androidTest`) and `:app:connectedDebugAndroidTest` pass. Emulator check: open a redirect title offline after viewing it once.

### Session 24: Tools tab, resource calculator and slide transitions (done)
- An unplanned session, asked for by the user; what it built is in `docs/PROGRESS.md` ("Session 24 notes"). The planned sessions below moved up one.
- The Library tab became a Tools tab (`ToolsKey`); `LibraryKey` is now pushed from Explore's top bar. `:feature:tools` holds the Tools list and the resource calculator, which plans from a crafting-recipe asset generated by `scripts/recipes/generate_recipes.py`. Push and pop slide; tab switches stay instant.

### Session 25: Seed map memory and rendering
- **Depends on:** Session 22.
- **Build:**
  - **Tile budget:**
    - `TileCache.capacityTiles` replaces `MAX_WANTED_TILES = 96`. That constant is 24 MB of tiles, while the cache on a memoryClass-128 device is 16 MB (64 tiles).
    - Test that the smallest budget covers a 1080×2400 view at every scale boundary. If it doesn't, allow up to 4 px per cell at coarse scales.
  - **Memory pressure:**
    - `WikiDroidApp` registers `ComponentCallbacks2`. `TileCache.trim(level)` halves the cache on `TRIM_MEMORY_UI_HIDDEN` and clears it on `RUNNING_LOW`/`onLowMemory`.
    - `SlimeLayer`'s region cache becomes an app-scoped, byte-bounded `SlimeRegionCache` in `:core:seedmap`, trimmed the same way.
  - **Main thread:**
    - Slime region bitmaps are built on a worker, like `TileLoader`, and only finished regions are drawn.
    - `StructureIcons` sprites are decoded once.
  - **Allocations:** `wantedTiles` sorts by integer tile-centre maths, without a `BlockArea` per comparison. `TileDrawer` reuses a scratch list for its fallback keys.
  - **`TileLoader`:** clear `failed` on a world change, and retry a failed tile after a backoff.
  - **Dispatcher:** `SeedMapCanvas` takes `@DefaultDispatcher` through `SeedMapEntryInstaller` instead of a hard-coded `Dispatchers.Default`.
- **Done when:**
  - Green. `:app:connectedDebugAndroidTest` and `:core:seedmap:connectedDebugAndroidTest` pass.
  - `TilePerformanceTest` (`perf=true`) is no slower.
  - The baseline profile is regenerated (open since Session 15).
  - Emulator check: pan, zoom and toggles on a memoryClass-128 AVD.

### Session 26: Seed map engine hardening
- **Depends on:** Session 25.
- **Build:**
  - **Native:**
    - Add a regression test that `nativeGetBiomeAt` on a fresh handle returns -1. cubiomes' `setupGenerator` already sets `DIM_UNDEF`, so no C change is needed.
    - Bounds-check x/y/z in C.
    - Add a contract test that resolves every `external fun`, or move to `RegisterNatives` in `JNI_OnLoad`, so a renamed class fails at load.
  - **`GeneratorPool`:**
    - `close()` waits for lent generators, as `shutDown()` does.
    - Keep one concurrency limit, not both `limitedParallelism` and a semaphore.
    - Add a `NativeGenerators` interface between the pool and the `NativeSeedMap` statics, with JVM tests for lending and re-seeding.
  - **Cancellation:** split `structuresIn` over large areas into native calls of at most 64 regions, so a superseded search frees its worker. Cap `FindNearestStructureUseCase`'s per-ring concurrency to the pool size.
  - **Drift checks:**
    - Kotlin and C limits disagree: structure span is 8192 against 16384, and coordinates are 30M against 60M. Expose the C limits through one native call, and have `NativeContractTest` assert Kotlin ≤ C.
    - Check `StructureType`/`McVersion` support against `nativeStructureSupported`.
  - **Version labels:** a pinned-labels test of every `McVersion.label`, so renaming one fails. Labels stay the stored key, as `SavedSeed` documents.
- **Done when:** green. `:core:seedmap:connectedDebugAndroidTest` and `:app:connectedDebugAndroidTest` pass.

### Session 27: One error model
- **Depends on:** Session 23.
- **Build:**
  - **One mapping per layer:** each layer names only its own exception types, since `:core:common` is plain JVM.
    - `:core:common`: `toDataError()` (cancellation rethrown, IOException→Network, else Unknown).
    - `:core:network`: `toNetworkDataError()` adds HTTP 404→NotFound and `SerializationException`→Parse, and delegates the rest to common.
    - `:core:data`: `SQLiteException`→ a new `DataError.Storage`.
  - **Logging:** a `Logger` interface in `:core:common`, bound in `:app` (Logcat in debug, no-op in release) and faked in `:core:testing`. Unknown errors and dropped writes are logged.
  - **Repository returns:** `LibraryRepository`, `PointOfInterestRepository`, `SeedRepository` and `SettingsRepository` mutations return `Result<_, DataError>` like the other repositories. The fakes gain scriptable failures.
  - **Seed map refusals:** `Attempt.kt` is replaced by a `SeedMapRefusal` that `JniSeedMapEngine` throws for world-border and unsupported-combination cases. Only that is caught; `require`/`check` failures propagate.
  - **History writes:** `ObserveArticleUseCase` logs a failed history write instead of dropping it silently.
  - **Rename:** `Result` becomes `DataResult`, so it no longer shadows `kotlin.Result`. Do this in its own mechanical commit.
- **Done when:** green, and the existing ViewModel tests pass with only error-type changes.

### Session 28: Seed map domain extraction
- **Depends on:** Session 27.
- **Build:** move pure logic out of `:feature:seedmap` and bring its tests along.
  - **To `:core:seedmap`:**
    - `DimensionScale.kt`.
    - `parseCoordinates`, next to `SeedParser`.
    - A `SeedMapQueries` planner for the spawn, stronghold and pin queries and type filtering, taking a `BlockArea` rather than a feature type.
    - One visible-area function, shared by `MapCamera` (floor/ceil of the centre) and `MapViewport` (rounded centre) so tiles and pins agree at the edges.
    - The "World spawn" title, which joins `StructureWikiTitles`.
  - **To `:core:domain/seedmap`:** the `Pois.kt` helpers (`linkedPortal`, `edited`), taking plain fields instead of `SeedMapAction.SavePoi`.
- **Done when:** green. The moved tests pass in their new modules, and the feature holds only UI, state and ViewModel code.

### Session 29: Seed map state split
- **Depends on:** Session 28.
- **Build:**
  - **Sub-states:** split `SeedMapState` (23 fields) into nested immutable sub-states, each with its own reducers. Every field gets a home; list the field→sub-state table in the PR.
    - `WorldState`: seed, version, dimension, saved seeds, viewport.
    - `PinsState`: toggles, pins, spawn, strongholds, nearest search and miss.
    - `PoiState`: points, editor, counts, removed point, linked-portal result.
    - `MeasureState`.
    - `LayersState`: grid, crosshair, slime, biome legend.
    - `SelectionState`.
  - **Actions:**
    - Group `SeedMapAction` (37 leaves) into one sealed sub-interface per sub-state, as `SeedMapAction.Measure` already is.
    - The dispatcher becomes one exhaustive `when` with no `else -> error()`, and a handler per group.
  - **Collectors and screen:** each collector reduces only into its own sub-state, and the screen passes sub-states down.
- **Done when:** green. The ViewModel tests pass with their assertions moved to the sub-states, and `:app:connectedDebugAndroidTest` passes.

### Session 30: Domain-only use cases (wiki features)
- **Depends on:** Session 27.
- **Build:**
  - **Coverage:** a use case for every repository call in Article, Explore, Search, Library and Settings, in each area's package. Each is `@Inject class XxxUseCase { suspend operator fun invoke() }`, or returns a `Flow` for observers.
  - **Logic that moves in:**
    - The random-article retry and version-page filter → `GetRandomArticleUseCase`.
    - `LibraryRepository`'s direct `saveHistory` read → `RecordHistoryUseCase`.
  - **Search:** `SearchViewModel` stops mixing `RunSearchUseCase` with direct repository calls.
  - **`:app`:** `MainViewModel` uses `ObservePreferencesUseCase`.
- **Done when:** green. The use cases are tested, and `:app:connectedDebugAndroidTest` passes. Searching those five features' `src/main` for `core.data` finds nothing.

### Session 31: Domain-only use cases (seed map) and enforcement
- **Depends on:** Sessions 29 and 30.
- **Build:**
  - **Seed map use cases:** saved seeds and points of interest. Group them into one `@Inject` holder per Session 29 sub-state, for example `PoiUseCases(observe, save, delete, restore, countsBySeed)`, so the ViewModel takes about six holders instead of about 17 parameters.
  - **Typed dimension:** `PointOfInterestRepository` takes and returns `Dimension`, not `String`, and drops unknown stored values. Update `SeedMapViewModel` (no more `Dimension.valueOf`) and `ResetAppStateRule`.
  - **Enforcement:**
    - `wikidroid.android.feature` drops `:core:data`.
    - `:core:domain` uses `implementation(projects.core.data)`.
    - Feature tests still see repository types through `:core:testing`.
  - **Docs:** CLAUDE.md and "MVI (Orbit)" above change to "features call use cases only; per-area holders are allowed for large ViewModels".
- **Done when:** green. `:app:checkMainMetroHiddenDependencies` and `:app:connectedDebugAndroidTest` pass, and no `feature/*/src/main` file references `core.data`.

### Session 32: Feature MVI tidy-up
- **Depends on:** Session 31.
- **Build:**
  - **Navigation:** navigation leaves the `Action` types that ViewModels ignore (Explore, CategoryDetail, Article, Settings). Routes take callbacks, and the ViewModels' no-op branches go.
  - **Article links:**
    - The anchor-or-navigate decision moves from `ArticleRoute` into `ArticleViewModel`, which emits a navigate effect or reduces a scroll-to-anchor.
    - One title normaliser (`normalizeTitle` → `:core:model`) is used by data, the ViewModel and the route.
  - **Single-flight:**
    - A `SingleFlight` helper (wrapping `Mutex.tryLock`) in `:core:common`, used by CategoryDetail, Search and Article.
    - Search runs inside one intent, with no nested `intent {}`, and loses its duplicated suggestion fetch.
  - **Compose:**
    - `ArticleActions`/`ArticleContent` take only the fields they read.
    - Library row mapping moves into the ViewModel state.
    - Explore's version titles come from the state.
  - **Design system:**
    - `WikiTopAppBar`'s back description defaults to a design-system string.
    - One `ThemeMode.isDark()` helper, shared by `MainActivity` and `Theme.kt`.
- **Done when:** green. The screen tests are updated for the callbacks, and `:app:connectedDebugAndroidTest` passes.

### Session 33: Stored types and model dependencies
- **Depends on:** Session 32.
- **Build:**
  - **Stored types:**
    - `StoredUserData` switches to `StoredPreferences`/`StoredSavedSeed` types in `:core:datastore`, with the same JSON field names.
    - The stored enums are their own types with the same constant names, so `coerceInputValues` keeps reading old files leniently.
    - Map to the `:core:model` types at the data source.
  - **`:core:model`:**
    - Drop the `api` dependencies on Compose and kotlinx-serialization; kotlinx-collections-immutable stays.
    - `@Immutable` gives way to a Compose compiler `stabilityConfigurationFile` (`dev.cniekirk.wikidroid.core.model.**`) set in `wikidroid.android.compose`.
    - Compare compose compiler reports for `:feature:article` before and after.
- **Done when:** green. A test decodes today's JSON file, and installing over the previous release APK keeps preferences and saved seeds.

### Session 34: Network/data boundary and shared test code
- **Depends on:** Session 33.
- **Build:**
  - **Network/data boundary:**
    - `WikiRemoteDataSource` returns DTOs only; `latestVersions` parsing and search sorting move to `:core:data` mappers.
    - `stripMarkup` and the thumbnail heuristic move into `:core:article-parser`, which removes jsoup from `:core:data`.
    - `ArticleRepository` takes a `String` base URL, and `:core:network` exposes OkHttp as `implementation`.
  - **`:core:testing`:** test libraries become `implementation`, except those in fake and rule signatures. Data-layer fakes (`FakeRemote`, `FakePreferencesDataSource`, `EchoParser`) are promoted if they are reused.
  - **`:core:ui`:** the Coil `ImageLoader` provider moves to `:app`.
- **Done when:** green.

### Session 35: Build and CI hygiene
- **Depends on:** Session 34.
- **Build:**
  - **Signing:** `assembleRelease` fails when `CI=true` and the signing secrets are missing, unless `-Pwikidroid.allowDebugSigning` is passed, which the fork-PR path in `pr.yml` sets.
  - **Robolectric flags:** the duplicated `--add-exports` block moves into `configureAndroidCommon`.
  - **Metro check:** `checkMainMetroHiddenDependencies` computes its module list at configuration time.
  - **Accessors:** remove `TYPESAFE_PROJECT_ACCESSORS` if it is unused.
- **Done when:** CI is green, and a fork-style run with no secrets still produces a debug-signed APK.

### Not planned
- Persisting parsed blocks instead of HTML. Profile the slow first article load first (open since Session 8); if parsing dominates, add a `parserVersion` column.
- `SavedStateHandle` for process death.

## Final end-to-end verification (after Session 11)
- `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleRelease` passes, and so does `:app:connectedDebugAndroidTest`.
- Emulator walkthrough:
  - Explore → category → article (infobox, crafting grid and tables render, links navigate).
  - Search autocomplete.
  - Offline bookmark reopen.
  - Settings persistence.
  - Tablet list-detail layout.
- PR CI posts the release APK comment.

## Risks
- AGP 9 with Metro, Detekt alpha and Robolectric on SDK 37 may need small workarounds. Fallbacks:
  - Robolectric: `@Config(sdk = [36])`.
  - Metro: pin compiler compatibility flags.
- A stable Android Studio release that supports AGP 9.4 is required to open the project.
