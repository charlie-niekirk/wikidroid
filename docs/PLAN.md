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
- Version policy: Google/AndroidX libraries use the newest pre-release if it is newer than stable, otherwise stable. Everything else uses latest stable. The one approved exception is Detekt.
- Workflow: after Session 1 (committed straight to `main` while bootstrapping), every session is built on its own branch and lands through its own pull request. Nothing is pushed to `main` directly. See "Session protocol".

## Versions (verified 2026-10-07; `gradle/libs.versions.toml`)

### Google / AndroidX

| Component | Version | Notes |
|---|---|---|
| AGP | 9.5.0-alpha08 | Built-in Kotlin, new DSL. Needs the Android Studio canary. |
| Gradle wrapper | 9.8.1 | Latest stable. AGP 9.5 needs at least 9.6. |
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
core:designsystem           M3 theme (dynamic color + Minecraft-inspired fallback scheme), typography, Symbols icons, base components
core:ui                     Shared composables: ArticleCard, PageThumbnail (pixel-art aware), Loading/Error/Empty states, AttributionFooter
core:navigation             @Serializable NavKeys, Navigator (top-level back stacks), EntryProviderInstaller contract
core:testing                Fakes for repositories, MainDispatcherRule, HTML/JSON fixtures, Robolectric test base
feature:explore             Home (latest versions card, random article, category grid), category detail (paged members + subcats)
feature:search              Debounced autocomplete, full-text results (paged), recent searches
feature:article             Article screen: native block renderer, TOC sheet, bookmark/share/open-in-browser, attribution
feature:library             Bookmarks and History tabs (Room Flows), swipe-to-delete, offline badges
feature:settings            Theme/dynamic colour/text size/edition, clear cache/history, About + licences + disclaimer
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
  - Adds `core:designsystem`, `ui`, `navigation`, `model` and `data`.
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
  - Top level: `ExploreKey`, `SearchKey`, `LibraryKey`, `SettingsKey`.
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
  - ViewModel tests with orbit-test (`testWithExternalState`).
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
- `.gitignore`, `README.md` (setup, Android Studio canary requirement, secrets, attribution).
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
  - README covering setup, the Android Studio canary requirement, secret generation (`keytool` + `base64`), attribution and the disclaimer.
  - Run `actionlint` locally if available.
- **Done when:** a test PR shows all three jobs green, the sticky comment carries the APK artifact link, and a fork-style run with no secrets still produces a debug-signed APK.

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
- AGP 9.5 alpha with Metro, Detekt alpha and Robolectric on SDK 37 may need small workarounds. Fallbacks:
  - Robolectric: `@Config(sdk = [36])`.
  - Metro: pin compiler compatibility flags.
- Android Studio canary (Rabbit 2) is required to open the project.
