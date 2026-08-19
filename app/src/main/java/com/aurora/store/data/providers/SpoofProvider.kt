/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.data.providers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.aurora.extensions.TAG
import com.aurora.store.BuildConfig
import com.aurora.store.R
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_AUTH_DATA
import com.aurora.store.util.Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE
import com.aurora.store.util.Preferences.PREFERENCE_VENDING_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Provider class to work with device and locale spoofs
 */
@Singleton
class SpoofProvider @Inject constructor(
    private val json: Json,
    @ApplicationContext val context: Context
) : SpoofDeviceProvider(context) {

    companion object {
        private const val LOCALE_SPOOF_ENABLED = "LOCALE_SPOOF_ENABLED"
        private const val LOCALE_SPOOF_LANG = "LOCALE_SPOOF_LANG"
        private const val LOCALE_SPOOF_COUNTRY = "LOCALE_SPOOF_COUNTRY"

        private const val DEVICE_SPOOF_ENABLED = "DEVICE_SPOOF_ENABLED"
        private const val DEVICE_SPOOF_PROPERTIES = "DEVICE_SPOOF_PROPERTIES"

        const val JAECOO_PROFILE_NAME = "Jaecoo Generic HU Tablet"

        /** Bump when the merge algorithm changes so persisted signatures stop matching. */
        const val JAECOO_PROFILE_REVISION = "1"

        const val JAECOO_API_27_ASSET = "jaecoo_profiles/api27.properties"
        const val JAECOO_CAPABILITY_ASSET = "jaecoo_hu_capabilities.properties"

        /**
         * Highest-API first. The first entry whose `UserReadableName` matches one of the gplayapi
         * bundled profiles wins for the current [Build.VERSION.SDK_INT].
         */
        val JAECOO_BUILT_IN_BY_MIN_SDK: List<Pair<Int, String>> = listOf(
            35 to "Google Pixel 9a",
            34 to "Nothing Phone(1)",
            33 to "Google Pixel Tablet",
            29 to "Nokia 1.3"
        )

        /** Keys overwritten with HU/tablet values when provisioning. */
        private val CAPABILITY_KEYS = listOf(
            "TouchScreen",
            "Keyboard",
            "Navigation",
            "ScreenLayout",
            "HasHardKeyboard",
            "HasFiveWayNavigation"
        )
    }

    val availableSpoofDeviceProperties get() = availableDeviceProperties
    val availableSpoofLocales = Locale.getAvailableLocales().toMutableList().apply {
        remove(Locale.getDefault())
        sortBy { it.displayName }
    }

    val deviceProperties: Properties
        get() {
            val currentProperties = if (isDeviceSpoofEnabled) {
                spoofDeviceProperties
            } else {
                defaultDeviceProperties
            }
            setVendingVersion(currentProperties)
            return currentProperties
        }

    private val defaultDeviceProperties: Properties by lazy {
        if (BuildConfig.FLAVOR == "jaecoo") {
            selectJaecooBaseProfile() ?: NativeDeviceInfoProvider.getNativeDeviceProperties(context)
        } else {
            NativeDeviceInfoProvider.getNativeDeviceProperties(context)
        }
    }

    val locale: Locale
        get() = if (isLocaleSpoofEnabled) {
            spoofLocale
        } else {
            Locale.getDefault()
        }

    val isLocaleSpoofEnabled: Boolean
        get() = Preferences.getBoolean(context, LOCALE_SPOOF_ENABLED)

    val isDeviceSpoofEnabled: Boolean
        get() = Preferences.getBoolean(context, DEVICE_SPOOF_ENABLED)

    private val spoofLocale: Locale
        get() = Locale.Builder()
            .setLanguage(Preferences.getString(context, LOCALE_SPOOF_LANG))
            .setRegion(Preferences.getString(context, LOCALE_SPOOF_COUNTRY))
            .build()

    private val spoofDeviceProperties: Properties
        get() = json.decodeFromString<Properties>(
            Preferences.getString(context, DEVICE_SPOOF_PROPERTIES)
        )

    fun setSpoofLocale(locale: Locale) {
        Preferences.putBoolean(context, LOCALE_SPOOF_ENABLED, true)
        Preferences.putString(context, LOCALE_SPOOF_LANG, locale.language)
        Preferences.putString(context, LOCALE_SPOOF_COUNTRY, locale.country)
    }

    fun setSpoofDeviceProperties(properties: Properties) {
        Preferences.putBoolean(context, DEVICE_SPOOF_ENABLED, true)
        Preferences.putString(context, DEVICE_SPOOF_PROPERTIES, json.encodeToString(properties))
    }

    fun removeSpoofLocale() {
        Preferences.remove(context, LOCALE_SPOOF_ENABLED)
        Preferences.remove(context, LOCALE_SPOOF_LANG)
        Preferences.remove(context, LOCALE_SPOOF_COUNTRY)
    }

    fun removeSpoofDeviceProperties() {
        Preferences.remove(context, DEVICE_SPOOF_ENABLED)
        Preferences.remove(context, DEVICE_SPOOF_PROPERTIES)
    }

    /**
     * Selects the Jaecoo base profile for the current API level. For API < 29, the bundled
     * `assets/jaecoo_profiles/api27.properties` is loaded from the APK assets; otherwise the first
     * matching entry from [JAECOO_BUILT_IN_BY_MIN_SDK] is returned from the gplayapi library.
     *
     * Visible for tests; not part of the public API.
     */
    @VisibleForTesting
    internal fun selectJaecooBaseProfile(): Properties? {
        val sdk = Build.VERSION.SDK_INT
        val (minSdk, name) = JAECOO_BUILT_IN_BY_MIN_SDK.firstOrNull { (min, _) -> sdk >= min }
            ?: return loadJaecooAsset()
        return availableDeviceProperties.firstOrNull { it.getProperty("UserReadableName") == name }
            ?: loadJaecooAsset()
    }

    private fun loadJaecooAsset(): Properties? = runCatching {
        val properties = Properties()
        context.assets.open(JAECOO_API_27_ASSET).use { properties.load(it) }
        properties.setProperty("CONFIG_NAME", JAECOO_API_27_ASSET)
        properties
    }.onFailure {
        Log.w(TAG, "Could not load Jaecoo fallback profile from assets", it)
    }.getOrNull()

    /**
     * Fingerprint of the Jaecoo base profile. Returns `null` when no base profile is available.
     * The signature is intentionally derived from the *base* (pre-merge) profile so that
     * re-provisioning only happens when the underlying device changes.
     */
    fun jaecooProfileSignature(): String? {
        val base = selectJaecooBaseProfile() ?: return null
        return listOf(
            JAECOO_PROFILE_REVISION,
            base.getProperty("Build.FINGERPRINT"),
            base.getProperty("Build.VERSION.SDK_INT"),
            base.getProperty("Build.VERSION.RELEASE"),
            base.getProperty("Platforms"),
            base.getProperty("SharedLibraries"),
            base.getProperty("GL.Version")
        ).joinToString("|")
    }

    /**
     * Provisions the [JAECOO_PROFILE_NAME] device spoof for the Jaecoo flavor. The base profile is
     * chosen by API, HU/tablet capabilities from [JAECOO_CAPABILITY_ASSET] overwrite the matching
     * keys, and the merged profile is persisted as the active spoof. Re-provisioning is skipped
     * when the persisted profile already carries the Jaecoo name and the signature is unchanged.
     *
     * Returns `true` when the persisted spoof was rewritten, `false` when no work was performed
     * (caller is free to keep the existing AuthData in that case).
     */
    fun provisionJaecooDefault(): Boolean {
        if (BuildConfig.FLAVOR != "jaecoo") return false
        val base = selectJaecooBaseProfile() ?: run {
            Log.w(TAG, "No Jaecoo base profile available; skipping provisioning")
            return false
        }
        val signature = jaecooProfileSignature() ?: return false
        val alreadyApplied =
            isDeviceSpoofEnabled &&
                spoofDeviceProperties.getProperty("UserReadableName") == JAECOO_PROFILE_NAME &&
                Preferences.getString(context, PREFERENCE_JAECOO_PROFILE_SIGNATURE) == signature
        if (alreadyApplied) return false

        val capabilities = Properties().apply {
            context.assets.open(JAECOO_CAPABILITY_ASSET).use { load(it) }
        }

        val merged = Properties().apply { putAll(base) }
        merged.setProperty("UserReadableName", JAECOO_PROFILE_NAME)
        for (key in CAPABILITY_KEYS) {
            capabilities.getProperty(key)?.let { merged.setProperty(key, it) }
        }
        val baseFeatures = base.getProperty("Features").orEmpty().split(',')
        val capabilityFeatures = capabilities.getProperty("Features").orEmpty().split(',')
        val mergedFeatures = (baseFeatures + capabilityFeatures)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        merged.setProperty("Features", mergedFeatures)

        setSpoofDeviceProperties(merged)
        Preferences.putString(context, PREFERENCE_JAECOO_PROFILE_SIGNATURE, signature)
        // Reprovisioning changes the device identity, so any cached AuthData would be sent with
        // a stale profile. Drop it; the next AuthViewModel cycle will rebuild from scratch.
        Preferences.remove(context, PREFERENCE_AUTH_DATA)
        return true
    }

    private fun setVendingVersion(currentProperties: Properties) {
        val vendingVersionIndex = Preferences.getInteger(context, PREFERENCE_VENDING_VERSION)
        if (vendingVersionIndex > 0) {
            val resources = context.resources
            val versionCodes = resources.getStringArray(R.array.pref_vending_version_codes)
            val versionStrings = resources.getStringArray(R.array.pref_vending_version)

            currentProperties.setProperty("Vending.version", versionCodes[vendingVersionIndex])
            currentProperties.setProperty(
                "Vending.versionString",
                versionStrings[vendingVersionIndex]
            )
        }
    }
}
