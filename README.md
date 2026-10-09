# WikiDroid

A native Android reader for [minecraft.wiki](https://minecraft.wiki), the community-run Minecraft Wiki.
Search, browse by category, read articles with a native Material 3 layout (infoboxes, crafting grids, tables),
bookmark pages and keep reading offline.

> **Unofficial app.** WikiDroid is not affiliated with Mojang or Microsoft, and it is not run by the Minecraft Wiki.

## Attribution and licence of the content

Article text and images come from the [Minecraft Wiki](https://minecraft.wiki) and are available under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). Every article ends with that
attribution and a link to the page and its history. Because of the non-commercial clause the app has no ads and no
monetisation, and it must stay that way.

"Minecraft" is a trademark of Mojang Synergies AB.

## Building

Requirements:

- JDK 21 (Gradle's toolchain resolver can download it).
- A **stable** Android Studio release that supports AGP 9.4 (check the AGP/Studio compatibility table). A canary is not needed.
- Android SDK packages `platforms;android-37.1` and `build-tools;37.0.0`, plus the NDK and CMake whose versions are the
  `ndk` and `cmake` entries in `gradle/libs.versions.toml` (`sdkmanager "ndk;<version>" "cmake;<version>"`, or let Android Studio install them).
- The cubiomes submodule (the seed map's world generator). Clone with `git clone --recurse-submodules`, or in an existing
  clone run `git submodule update --init`.
- `local.properties` in the project root (it is gitignored), or `ANDROID_HOME` set:
  ```
  sdk.dir=/path/to/Android/sdk
  ```

When Android Studio offers "Set up Kotlin" or "Configure Kotlin", dismiss it. AGP 9 has built-in Kotlin support, and the
prompt adds a plugin that makes the sync fail.

```bash
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug
./gradlew :app:assembleRelease      # release APK (debug-signed unless a keystore is configured, see below)
```

## Checks

```bash
./gradlew spotlessApply             # format (run on its own, not together with compile tasks)
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies
./gradlew :app:connectedDebugAndroidTest   # needs a device or emulator; API 35 is the reference
./gradlew :app:generateBaselineProfile     # needs a device and network; rewrites the committed profile
```

Unit and Compose UI tests run on the JVM (JUnit 4 and Robolectric). The instrumented tests in `app/src/androidTest`
run the real app against an in-process `MockWebServer` that serves the fixtures in `core/testing`, so they do not need
the network.

See [`CLAUDE.md`](CLAUDE.md) for the module map, conventions and known workarounds, and [`docs/PLAN.md`](docs/PLAN.md)
for the design.

## Continuous integration

`.github/workflows/pr.yml` runs on every pull request and on pushes to `main`:

| Job | What it does |
|---|---|
| `checks` | `spotlessCheck detekt lintDebug testDebugUnitTest :app:checkMainMetroHiddenDependencies` |
| `instrumented` | `:app:connectedDebugAndroidTest` on an API 35 x86_64 `google_apis` emulator |
| `release-apk` | builds the release APK, uploads it as a workflow artifact and posts or updates a comment on the PR with the link |

GitHub cannot attach a file to a comment, so the comment links the workflow artifact. Downloading it needs a GitHub login.
Pull requests from forks, and from Dependabot, get a read-only token: they build and upload the APK but do not get a comment.

The caching in `gradle/actions/setup-gradle` uses its default provider, which means accepting the
[Gradle Technologies Terms of Use](https://gradle.com/legal/terms-of-use/). Set `cache-provider: basic` in
`.github/actions/setup-android/action.yml` to opt out.

## Release signing

The release build reads its signing config from environment variables or Gradle properties:

| Variable | Meaning |
|---|---|
| `WIKIDROID_KEYSTORE_FILE` | path to the keystore |
| `WIKIDROID_KEYSTORE_PASSWORD` | keystore password |
| `WIKIDROID_KEY_ALIAS` | key alias |
| `WIKIDROID_KEY_PASSWORD` | key password |

If the keystore file is missing, the release build is signed with the debug key. That is what a fork gets, and
what you get locally without any setup. Such an APK installs fine but must not be distributed.

### Creating the keystore and the CI secrets

Generate a keystore (keep it out of git; `*.jks` and `*.keystore` are ignored):

```bash
keytool -genkeypair -v -keystore wikidroid-release.jks -alias wikidroid \
  -keyalg RSA -keysize 4096 -validity 10000
```

Encode it for GitHub:

```bash
base64 -i wikidroid-release.jks | tr -d '\n' > keystore.b64      # macOS
base64 -w0 wikidroid-release.jks > keystore.b64                  # Linux
```

Then add four repository secrets (Settings → Secrets and variables → Actions), or use the GitHub CLI:

```bash
gh secret set KEYSTORE_BASE64 < keystore.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

Back up the keystore somewhere safe. Delete `keystore.b64` once the secret is stored. Anyone who can change the workflow on a
branch in this repository can read these secrets, so keep write access limited.

Without the secrets, CI still produces a debug-signed APK.

## Layout

```
app                 MainActivity, Metro graph, Navigation 3 host
core:*              model, common, article-parser, network, database, datastore, data, designsystem, ui, navigation, seedmap, testing
feature:*           explore, search, article, library, settings
baselineprofile     baseline profile generator (run locally, not in CI)
build-logic         Gradle convention plugins (wikidroid.*)
```
