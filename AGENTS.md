# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project overview

This is **Aurora Store** (v4, codebase root project name `AuroraStore4`), a FOSS
(GPL-3.0-or-later) Android client for the Google Play Store. It lets users search
and download apps directly from Google Play using either a personal Google account
or an anonymous account. The underlying Google Play protocol is provided by the
external [`gplayapi`](https://gitlab.com/AuroraOSS/gplayapi) library
(`com.auroraoss:gplayapi`), which is reverse-engineered and may break when Google
changes server behavior.

- Package / applicationId: `com.aurora.store`
- Current version: `4.8.4` (versionCode 76) — defined in `app/build.gradle.kts`
- minSdk 23, targetSdk/compileSdk 37
- Language: Kotlin (single Java file: `util/AC2DMUtil.java`, plus Huawei flavor stubs)
- UI: Jetpack Compose with Material 3 (no XML layouts; the app is fully Compose-based)
- License compliance follows the [REUSE specification](https://reuse.software/) —
  see `REUSE.toml` and the `LICENSES/` directory

## Technology stack

- **Build system**: Gradle (wrapper 9.5.0, `gradle/wrapper/gradle-wrapper.properties`)
  with Kotlin DSL; version catalog in `gradle/libs.versions.toml`
- **Toolchain**: Java 21, Android Gradle Plugin 9.3.0, Kotlin 2.4.10
- **Dependency injection**: Hilt (`@HiltAndroidApp` on `AuroraApp`, modules in
  `com.aurora.store.module`), processed with KSP
- **UI**: Jetpack Compose BOM 2026.06.01, Material 3 (incl. adaptive layouts),
  Navigation 3 (`androidx.navigation3`), Coil 3 for image loading
- **Persistence**: Room (schema export enabled, JSON schemas in `app/schemas/`),
  SharedPreferences wrapper in `util/Preferences.kt`
- **Background work**: WorkManager with Hilt workers (`data/work/`)
- **Networking**: OkHttp 5; Google Play protobuf models via `protobuf-javalite` and gplayapi
- **Privileged installs**: Shizuku (`dev.rikka.shizuku`), libsu (root), HiddenApiBypass
- **Other**: Paging 3, kotlinx.serialization, Process Phoenix, LeakCanary (debug only)

## Repository layout

- `app/` — the only Gradle module (`:app`)
  - `src/main/java/com/aurora/store/` — main source set
    - `AuroraApp.kt` — Application class (Hilt entry point, WorkManager/Coil setup)
    - `ComposeActivity.kt` — main activity; `DeepLinkConfirmActivity.kt` for deep links
    - `compose/` — all UI: `ui/` (per-screen composables, one package per screen),
      `composable/` (shared composables), `navigation/` (Navigation 3 destinations),
      `theme/`, `preview/`
    - `viewmodel/` — ViewModels, one package per screen/feature
    - `data/` — non-UI logic:
      - `helper/` — `DownloadHelper`, `UpdateHelper` (download/update orchestration)
      - `installer/` — install strategies: session, native, root, Shizuku, App Manager, microG
      - `room/` — Room database `AuroraDatabase` (version 12) with per-entity subpackages
        (download, favourite, update, review, account, exodus)
      - `work/` — WorkManager workers (Auth, Cache, Download, ExodusTracker, Export, Update)
      - `providers/` — account/auth/spoofing/device-info providers
      - `receiver/` — broadcast receivers; `network/` — OkHttp client Hilt module
      - `model/`, `paging/`, `event/`, `activity/`
    - `module/` — Hilt modules (`CommonModule`, `HelperModule`)
    - `util/` — utilities and the `IFlavouredUtil` flavor interface
  - `src/main/java/com/aurora/extensions/` — Kotlin extension/helper functions
  - `src/androidTest/` — instrumented tests (there is **no** `src/test/` unit-test source set)
  - `src/vanilla/`, `src/huawei/`, `src/preload/` — per-flavor implementations of
    `FlavouredUtil` / `InstallerStatusReceiver` (huawei/preload have own manifests)
  - `src/debug/`, `src/nightly/` — launcher-icon overrides per build type
  - `schemas/` — exported Room schemas (committed, also archived by CI)
- `gradle/libs.versions.toml` — all dependency/plugin versions (edit versions here)
- `fastlane/metadata/` — store metadata & screenshots for F-Droid
- `.gitlab-ci.yml` — CI pipeline; `.gitlab/` — issue/MR templates
- `updates.json` — self-update metadata served to older app versions
- `LICENSES/`, `REUSE.toml` — licensing (REUSE-compliant)

## Build variants

Flavor dimension `device` × build types:

- **Flavors**: `vanilla` (default; anonymous login enabled), `huawei` (adds HMS
  `ag-coreservice`, anonymous login disabled), `preload` (for system-preloaded devices)
- **Build types**: `debug` (`.debug` suffix, signed with AOSP test key `app/testkey.jks`),
  `release` (minified + resource shrinking; signed only when `app/signing.properties`
  exists — otherwise unsigned), `nightly` (release init, `.nightly` suffix,
  version name suffixed with the git short hash; huawei/preload nightly variants are disabled)

Build and version metadata come from git: the build runs `git rev-parse --short HEAD`
and `git log -1 --format=%ct`, so building requires a git checkout.

## Build and test commands

All commands run from the repository root via the Gradle wrapper:

```bash
./gradlew assembleVanillaDebug        # standard local build (used by CI)
./gradlew assembleVanillaRelease      # unsigned release APK without signing.properties
./gradlew installVanillaDebug         # install to a connected device
./gradlew ktlintCheck                 # lint Kotlin style (CI gate)
./gradlew ktlintFormat                # auto-fix style violations
./gradlew lintVanillaDebug            # Android lint (config: app/lint.xml)
./gradlew connectedVanillaDebugAndroidTest   # instrumented tests, needs device/emulator
```

Notes:

- Release signing is opt-in: create `app/signing.properties` with `STORE_FILE`,
  `KEY_ALIAS`, `KEY_PASSWORD` (see `app/build.gradle.kts`); debug builds are signed
  automatically with the committed AOSP test key.
- The huawei flavor requires the Huawei Maven repo (already configured in
  `settings.gradle.kts`); libsu comes from jitpack.

## Testing instructions

- Tests are **instrumented tests only** under `app/src/androidTest/`; there is no JVM
  unit-test source set (`app/src/test` does not exist), even though `testImplementation`
  dependencies for JUnit/Truth are declared.
- Tests use JUnit4, Google Truth, Espresso, Compose UI Test (`createComposeRule`),
  and Hilt testing. The custom runner `com.aurora.store.HiltInstrumentationTestRunner`
  (configured in `app/build.gradle.kts`) swaps in `HiltTestApplication`.
- Compose tests assert on semantics tags/texts; keep existing test tags stable when
  editing composables, or update the corresponding tests.
- Run with `./gradlew connectedVanillaDebugAndroidTest` on a device/emulator.
- Before finishing any change, at minimum run `./gradlew ktlintCheck` and assemble
  the affected variant; CI runs `assembleVanillaDebug` + `ktlintCheck`.

## Code style guidelines

- Kotlin official code style, enforced by ktlint (`org.jlleitschuh.gradle.ktlint`,
  android mode). `.editorconfig` sets `ktlint_code_style = android_studio`, allows
  composable function naming, and disables the class-signature rule.
- Every source file carries an SPDX license header (`SPDX-FileCopyrightText` /
  `SPDX-License-Identifier: GPL-3.0-or-later`); add one to new files. Files that
  cannot be annotated are covered by `REUSE.toml`.
- Architecture: single-activity Compose app; screens live in `compose/ui/<screen>/`
  with a matching `viewmodel/<screen>/` package; state flows through ViewModels and
  the shared `EventFlow` bus (`data/event/`); long-running operations are
  WorkManager workers, not ad-hoc coroutines.
- Dependency injection is constructor/field injection via Hilt; new singletons are
  provided in `module/CommonModule.kt` or `HelperModule.kt`.
- Room schema changes must bump the database version in
  `data/room/AuroraDatabase.kt`, provide a migration in `MigrationHelper.kt`, and
  commit the regenerated schema under `app/schemas/`.
- Flavor-specific behavior goes behind `IFlavouredUtil` with per-flavor
  `FlavouredUtil` implementations — do not sprinkle flavor checks through shared code.
- Match existing conventions: Material 3 composables, `kotlinx.serialization` for
  JSON, OkHttp for HTTP, Coil for images, Paging 3 for paginated lists.

## Deployment / release process

- CI (`.gitlab-ci.yml`) has three stages: build (`assembleVanillaDebug`), lint
  (`ktlintCheck`), and upload of the vanilla debug APK to the GitLab Package
  Registry on the default branch. Android lint is not part of CI.
- Stable releases are published manually to GitLab Releases, F-Droid, IzzyOnDroid
  and Huawei App Gallery; store metadata and per-version changelogs live in
  `fastlane/metadata/android/`.
- `updates.json` at the repo root feeds the in-app self-update check and must be
  kept in sync with releases.

## Security considerations

- `app/testkey.jks` is the public AOSP test key — it is for debug builds only,
  never for production signing. Real release credentials come from the untracked
  `app/signing.properties`; never commit them.
- An Exodus Privacy API key is embedded as a `BuildConfig` field in
  `app/build.gradle.kts`; treat it as a public client key, not a secret.
- The app handles Google account credentials and auth tokens (`data/providers/`,
  `AccountRepository`); never log tokens or persist them outside the Room
  `account` table / encrypted preferences already used.
- The app requests sensitive permissions (install/delete packages, storage,
  query-all-packages) and supports root/Shizuku installers — be careful with any
  change in `data/installer/`, and do not weaken the checks around install sources.
- The Google Play API is reverse-engineered; do not add code that exfiltrates user
  data or talks to endpoints other than those already used (Google Play, Exodus,
  Plexus, token dispensers, self-update).
