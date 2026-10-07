# Progress

Definition of green: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`.

- [x] Session 1: Repo bootstrap and convention plugins
- [ ] Session 2: Foundation core modules
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
