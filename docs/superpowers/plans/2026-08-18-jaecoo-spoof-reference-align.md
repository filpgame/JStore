# Jaecoo Spoof Reference Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the Jaecoo spoofing behavior from the reference `as.apk` 4.8.3-jaecoo build so the JStore Jaecoo flavor provisions the `Jaecoo Generic HU Tablet` profile at splash, persists it as the active spoof, and clears stale AuthData on re-provision.

**Architecture:** A new `assets/` tree (`jaecoo_hu_capabilities.properties`, `jaecoo_profiles/api27.properties`) ships the API 27 fallback and the HU/tablet capabilities. `SpoofProvider` gains API-based profile selection, an asset-driven merge, and a signature-gated provisioning entrypoint. `SplashScreen` invokes provisioning on first composition (Jaecoo flavor only) and clears `PREFERENCE_AUTH_DATA` whenever the merged profile is written.

**Tech Stack:** Kotlin (Hilt singleton provider), Android `Properties`, `SharedPreferences` via the existing `Preferences` helper, Jetpack Compose `LaunchedEffect`, JUnit4 + Truth instrumented tests.

**Spec:** `/Users/suellenleite/projetos/as-decompiled/analysis/SPOOFING_COMPARISON.md` (also embedded in the decompiled APK at `sources/com/aurora/store/view/p003ui/splash/SplashFragment.java` and `sources/p000/{e37,x27}.java`).

## Global Constraints

- Only the `jaecoo` flavor of the `device` dimension changes; `vanilla`, `huawei`, `preload` keep existing behavior.
- Profile files in the gplayapi AAR are scanned at runtime via `SpoofDeviceProvider.apkAsJar`; no extra packaging is needed for them.
- New `.properties` files in `app/src/main/assets/` are merged into the APK as bundled assets (Gradle handles this automatically).
- The provisioning signature is `revision|Build.FINGERPRINT|Build.VERSION.SDK_INT|Build.VERSION.RELEASE|Platforms|SharedLibraries|GL.Version` of the *base* profile (before merging), separated by `|`. Bump `JAECOO_PROFILE_REVISION` whenever the merge logic changes.
- `PREFERENCE_AUTH_DATA` is the persisted `AuthData` JSON in `SharedPreferences`. Removing it forces the next `AuthViewModel.updateAuthState` to re-check-in.
- Tests live in `app/src/androidTest/java/com/aurora/store/data/providers/SpoofProviderTest.kt` and use the existing `HiltAndroidRule` pattern; add new tests there, do not create a new test class.

---

### Task 1: Ship the Jaecoo assets

**Files:**
- Create: `app/src/main/assets/jaecoo_hu_capabilities.properties`
- Create: `app/src/main/assets/jaecoo_profiles/api27.properties`

**Interfaces:**
- Consumes: existing `app/src/main/assets/` (currently absent — Gradle will pick the directory up on first build).
- Produces: byte streams the new `SpoofProvider.provisionJaecooDefault()` will open via `context.assets.open(...)`.

- [ ] **Step 1: Add `jaecoo_hu_capabilities.properties`**

Copy the file from `/Users/suellenleite/projetos/as-decompiled/resources/assets/jaecoo_hu_capabilities.properties` verbatim into `app/src/main/assets/jaecoo_hu_capabilities.properties`. It must contain `Features`, `TouchScreen`, `Keyboard`, `Navigation`, `ScreenLayout`, `HasHardKeyboard`, `HasFiveWayNavigation`. No `UserReadableName` (it is intentionally not offered as a standalone profile).

- [ ] **Step 2: Add `jaecoo_profiles/api27.properties`**

Copy `/Users/suellenleite/projetos/as-decompiled/resources/assets/jaecoo_profiles/api27.properties` verbatim into `app/src/main/assets/jaecoo_profiles/api27.properties`. Keep `UserReadableName=sirius` so the file passes `SpoofDeviceProvider`'s `UserReadableName != null` filter if it ever gets surfaced (it is loaded explicitly from assets, not via `availableDeviceProperties`, but the field is required for parity with the reference).

- [ ] **Step 3: Confirm Gradle picks the assets**

Run: `./gradlew :app:tasks --all | grep -i "merge.*assets" | head -3` from `app/src/main/`.
Expected: an asset merge task such as `mergeJaecooDebugAssets` is listed. No further action needed.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/jaecoo_hu_capabilities.properties app/src/main/assets/jaecoo_profiles/api27.properties
git commit -m "feat(jaecoo): ship HU capability and API 27 fallback profiles"
```

---

### Task 2: Add signature preference key

**Files:**
- Modify: `app/src/main/java/com/aurora/store/util/Preferences.kt` (append after line 47 near `PREFERENCE_INTRO`)

**Interfaces:**
- Produces: `Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE` — `String` constant.

- [ ] **Step 1: Add the constant**

Append right after the existing `PREFERENCE_INTRO` line in `Preferences.kt`:

```kotlin
const val PREFERENCE_JAECOO_PROFILE_SIGNATURE = "PREFERENCE_JAECOO_PROFILE_SIGNATURE_V1"
```

Pin the version suffix to `_V1` for now; bumping the merge algorithm later means bumping to `_V2` so old signature rows no longer match.

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleJaecooDebug --quiet` (this will fail without the rest of the wiring, but it must compile).
Expected: compile error pointing at the missing callers in the next task. Continue.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aurora/store/util/Preferences.kt
git commit -m "feat(jaecoo): add provisioning signature preference key"
```

---

### Task 3: Add provisioning helpers to `SpoofProvider`

**Files:**
- Modify: `app/src/main/java/com/aurora/store/data/providers/SpoofProvider.kt`
- Modify: `app/src/androidTest/java/com/aurora/store/data/providers/SpoofProviderTest.kt`

**Interfaces:**
- Produces:
  - `fun provisionJaecooDefault(): Boolean` — returns `true` when it (re)wrote the spoof profile, `false` when the existing profile already matches the signature.
  - `fun jaecooProfileSignature(): String?` — returns the signature of the *base* profile selected by API, or `null` if no base profile is available.
  - `companion object` constants:
    - `JAECOO_PROFILE_NAME = "Jaecoo Generic HU Tablet"`
    - `JAECOO_PROFILE_REVISION = "1"`
    - `JAECOO_API_27_ASSET = "jaecoo_profiles/api27.properties"`
    - `JAECOO_CAPABILITY_ASSET = "jaecoo_hu_capabilities.properties"`
    - `JAECOO_BUILT_IN_BY_MIN_SDK = listOf(Pair(35, "Google Pixel 9a"), Pair(34, "Nothing Phone(1)"), Pair(33, "Google Pixel Tablet"), Pair(29, "Nokia 1.3"))`

- [ ] **Step 1: Write failing test for base profile selection**

Add to `SpoofProviderTest.kt`:

```kotlin
@Test
fun testJaecooBaseProfileSelectsByApi() {
    val base = spoofProvider.selectJaecooBaseProfile()
    if (Build.VERSION.SDK_INT >= 35) {
        assertThat(base?.getProperty("UserReadableName")).isEqualTo("Google Pixel 9a")
    } else if (Build.VERSION.SDK_INT >= 34) {
        assertThat(base?.getProperty("UserReadableName")).isEqualTo("Nothing Phone(1)")
    } else if (Build.VERSION.SDK_INT >= 33) {
        assertThat(base?.getProperty("UserReadableName")).isEqualTo("Google Pixel Tablet")
    } else if (Build.VERSION.SDK_INT >= 29) {
        assertThat(base?.getProperty("UserReadableName")).isEqualTo("Nokia 1.3")
    } else {
        assertThat(base?.getProperty("UserReadableName")).isEqualTo("sirius")
    }
}
```

`selectJaecooBaseProfile()` is a new internal-visibility helper. It must read `Build.VERSION.SDK_INT` and pick the first profile whose `UserReadableName` matches one of the four shipped entries, falling back to `assets.open(JAECOO_API_27_ASSET)` when below 29. It does not consult the persisted spoof.

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:connectedJaecooDebugAndroidTest --tests "com.aurora.store.data.providers.SpoofProviderTest.testJaecooBaseProfileSelectsByApi"`. If no device is attached, run the unit-test variant under `:app:testJaecooDebugUnitTest` with the same body (declare the method `@VisibleForTesting` in that case).
Expected: compilation error (`selectJaecooBaseProfile` not found).

- [ ] **Step 3: Implement `selectJaecooBaseProfile` and the constants**

Replace the `JAECOO_DEFAULT_DEVICE_CONFIG` constant block in `SpoofProvider.kt` with:

```kotlin
companion object {
    private const val LOCALE_SPOOF_ENABLED = "LOCALE_SPOOF_ENABLED"
    private const val LOCALE_SPOOF_LANG = "LOCALE_SPOOF_LANG"
    private const val LOCALE_SPOOF_COUNTRY = "LOCALE_SPOOF_COUNTRY"

    private const val DEVICE_SPOOF_ENABLED = "DEVICE_SPOOF_ENABLED"
    private const val DEVICE_SPOOF_PROPERTIES = "DEVICE_SPOOF_PROPERTIES"

    const val JAECOO_PROFILE_NAME = "Jaecoo Generic HU Tablet"
    const val JAECOO_PROFILE_REVISION = "1"
    const val JAECOO_API_27_ASSET = "jaecoo_profiles/api27.properties"
    const val JAECOO_CAPABILITY_ASSET = "jaecoo_hu_capabilities.properties"

    /** Highest API first. The first matching entry is the base profile. */
    val JAECOO_BUILT_IN_BY_MIN_SDK: List<Pair<Int, String>> = listOf(
        35 to "Google Pixel 9a",
        34 to "Nothing Phone(1)",
        33 to "Google Pixel Tablet",
        29 to "Nokia 1.3",
    )

    private val CAPABILITY_KEYS = listOf(
        "TouchScreen", "Keyboard", "Navigation", "ScreenLayout",
        "HasHardKeyboard", "HasFiveWayNavigation"
    )
}
```

Add `@VisibleForTesting internal fun selectJaecooBaseProfile(): Properties? { ... }` that:
1. Iterates `JAECOO_BUILT_IN_BY_MIN_SDK` in declared order; returns the first entry from `availableDeviceProperties` whose `UserReadableName` matches when `Build.VERSION.SDK_INT >= minSdk`.
2. If `Build.VERSION.SDK_INT < 29`, opens `context.assets.open(JAECOO_API_27_ASSET)` and loads the result, setting `CONFIG_NAME` to the asset path so debug logs match the reference.
3. Returns `null` only if the asset is missing — log a warning.

- [ ] **Step 4: Run the new test**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Add `provisionJaecooDefault()` and signature helpers**

Add to `SpoofProvider`:

```kotlin
fun jaecooProfileSignature(): String? {
    val base = selectJaecooBaseProfile() ?: return null
    return listOf(
        JAECOO_PROFILE_REVISION,
        base.getProperty("Build.FINGERPRINT"),
        base.getProperty("Build.VERSION.SDK_INT"),
        base.getProperty("Build.VERSION.RELEASE"),
        base.getProperty("Platforms"),
        base.getProperty("SharedLibraries"),
        base.getProperty("GL.Version"),
    ).joinToString("|")
}

/** Returns true when the persisted spoof was rewritten. */
fun provisionJaecooDefault(): Boolean {
    if (BuildConfig.FLAVOR != "jaecoo") return false
    val base = selectJaecooBaseProfile() ?: run {
        Log.w(TAG, "No Jaecoo base profile available")
        return false
    }
    val target = Preferences.getString(context, Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE)
    val signature = jaecooProfileSignature() ?: return false
    val alreadyApplied =
        isDeviceSpoofEnabled &&
            spoofDeviceProperties.getProperty("UserReadableName") == JAECOO_PROFILE_NAME &&
            target == signature
    if (alreadyApplied) return false

    val capabilities = Properties().apply {
        context.assets.open(JAECOO_CAPABILITY_ASSET).use { load(it) }
    }
    val merged = Properties().apply { putAll(base) }
    merged.setProperty("UserReadableName", JAECOO_PROFILE_NAME)
    for (key in CAPABILITY_KEYS) {
        capabilities.getProperty(key)?.let { merged.setProperty(key, it) }
    }
    val mergedFeatures = (base.getProperty("Features").orEmpty().split(',') +
        capabilities.getProperty("Features").orEmpty().split(','))
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
    merged.setProperty("Features", mergedFeatures)

    setSpoofDeviceProperties(merged)
    Preferences.putString(context, Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE, signature)
    Preferences.remove(context, Preferences.PREFERENCE_AUTH_DATA)
    return true
}
```

Imports needed: `android.util.Log`, `com.aurora.extensions.TAG`, `com.aurora.store.util.Preferences.PREFERENCE_AUTH_DATA`, `kotlin.use`.

- [ ] **Step 6: Add a test for the idempotency contract**

```kotlin
@Test
fun testProvisionJaecooDefaultIsIdempotent() {
    val first = spoofProvider.provisionJaecooDefault()
    val second = spoofProvider.provisionJaecooDefault()
    assertThat(spoofProvider.isDeviceSpoofEnabled).isTrue()
    assertThat(spoofProvider.deviceProperties.getProperty("UserReadableName"))
        .isEqualTo(SpoofProvider.JAECOO_PROFILE_NAME)
    assertThat(first == second).isFalse()
}
```

`first` may be either `true` or `false` depending on whether a previous test run left state; the contract is that the second call never re-writes. If the device was previously provisioned by another test, `first` will be `false`; what we assert is `first != second` (exactly one of them rewrote).

- [ ] **Step 7: Run the new tests**

Run: `./gradlew :app:connectedJaecooDebugAndroidTest --tests "com.aurora.store.data.providers.SpoofProviderTest"`.
Expected: all green.

- [ ] **Step 8: Drop the old `JAECOO_DEFAULT_DEVICE_CONFIG` path**

Replace `defaultDeviceProperties` with the API-based selection when the spoof is not already enabled, so a vanilla user with a previously persisted non-HU profile is not silently downgraded on first read after upgrading:

```kotlin
private val defaultDeviceProperties: Properties by lazy {
    if (BuildConfig.FLAVOR == "jaecoo") {
        selectJaecooBaseProfile() ?: NativeDeviceInfoProvider.getNativeDeviceProperties(context)
    } else {
        NativeDeviceInfoProvider.getNativeDeviceProperties(context)
    }
}
```

Keep the public `JAECOO_DEFAULT_DEVICE_CONFIG` constant out of the file. If any external test references it, fail this task and surface the dependency; do not silently preserve it.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/aurora/store/data/providers/SpoofProvider.kt \
        app/src/androidTest/java/com/aurora/store/data/providers/SpoofProviderTest.kt
git commit -m "feat(jaecoo): provision Jaecoo Generic HU Tablet via API-aware selector"
```

---

### Task 4: Wire provisioning into the splash

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/ui/splash/SplashScreen.kt`
- Modify: `app/src/main/java/com/aurora/store/viewmodel/auth/AuthViewModel.kt`

**Interfaces:**
- Consumes: `SpoofProvider.provisionJaecooDefault()` (returns `Boolean`).
- Produces: `AuthViewModel.invalidateAuthForProfileChange()` — `Unit`; clears the persisted AuthData and resets the StateFlow to `AuthState.Unavailable`.

- [ ] **Step 1: Expose `invalidateAuthForProfileChange`**

Append to `AuthViewModel.kt`:

```kotlin
fun invalidateAuthForProfileChange() {
    viewModelScope.launch(Dispatchers.IO) {
        authProvider.removeAuthData(context)
        _authState.value = AuthState.Unavailable
    }
}
```

- [ ] **Step 2: Wire `LaunchedEffect` in `SplashScreen.kt`**

Inside `SplashScreen` composable, before the existing `LaunchedEffect(authState)` block, add:

```kotlin
LaunchedEffect(Unit) {
    if (BuildConfig.FLAVOR == "jaecoo") {
        val provisioned = runCatching { viewModel.provisionJaecooDefault() }
            .getOrDefault(false)
        if (provisioned) viewModel.invalidateAuthForProfileChange()
    }
}
```

Add `fun provisionJaecooDefault(): Boolean` to `AuthViewModel` that delegates to `spoofProvider.provisionJaecooDefault()` (inject `SpoofProvider` alongside `AuthProvider`).

- [ ] **Step 3: Inject `SpoofProvider` into `AuthViewModel`**

Add a `private val spoofProvider: SpoofProvider` constructor parameter (Hilt resolves it for free — there is no `@Provides` to write). The `@HiltViewModel` already lists all its injected types.

- [ ] **Step 4: Build to confirm wiring**

Run: `./gradlew :app:compileJaecooDebugKotlin`.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aurora/store/compose/ui/splash/SplashScreen.kt \
        app/src/main/java/com/aurora/store/viewmodel/auth/AuthViewModel.kt
git commit -m "feat(jaecoo): provision HU tablet profile at splash"
```

---

### Task 5: End-to-end verification

**Files:** none.

- [ ] **Step 1: Run the full Jaecoo Android test suite**

Run: `./gradlew :app:connectedJaecooDebugAndroidTest`.
Expected: all tests pass, including the two new ones in `SpoofProviderTest`. If no device is attached, use `./gradlew :app:testJaecooDebugUnitTest` and document the gap in the commit message.

- [ ] **Step 2: Run ktlint**

Run: `./gradlew :app:ktlintJaecooDebugCheck`.
Expected: no formatting violations.

- [ ] **Step 3: Assemble the Jaecoo debug APK**

Run: `./gradlew :app:assembleJaecooDebug`.
Expected: APK is produced under `app/build/outputs/apk/jaecoo/debug/`.

- [ ] **Step 4: Verify the assets shipped in the APK**

Run:
```bash
unzip -l app/build/outputs/apk/jaecoo/debug/app-jaecoo-debug.apk | \
    grep -E "jaecoo_(hu_capabilities|profiles)"
```
Expected: both `assets/jaecoo_hu_capabilities.properties` and `assets/jaecoo_profiles/api27.properties` are present.

- [ ] **Step 5: Commit any cleanup**

If ktlint flagged formatting fixes, commit them:

```bash
git add -A
git commit -m "style(jaecoo): ktlint cleanup"
```

---

## Self-Review Notes

- Spec coverage map:
  - "Perfil Jaecoo padrão" → Task 3, step 8 + Task 4 step 2.
  - "Base por API" → Task 3, step 3.
  - "Capacidades HU" → Task 3, step 5.
  - "Persistência / forçar DEVICE_SPOOF_ENABLED" → Task 3, step 5.
  - "Profile POCO F1" → unchanged, comes from the gplayapi AAR.
  - "Limpar PREFERENCE_AUTH_DATA" → Task 3 step 5 + Task 4 step 1.
  - "Assinatura para evitar recriar" → Task 3 step 5.
- Placeholder scan: clean — every step has concrete code or commands.
- Type consistency: `provisionJaecooDefault(): Boolean` is referenced consistently in Tasks 3, 4, and the test.
