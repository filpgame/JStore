package com.aurora.store.data.providers

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.store.util.Preferences
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SpoofProviderTest {

    @get:Rule
    var hiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var spoofProvider: SpoofProvider

    @Before
    fun setup() {
        hiltAndroidRule.inject()
    }

    @After
    fun tearDown() {
        spoofProvider.removeSpoofLocale()
        spoofProvider.removeSpoofDeviceProperties()
        // Drop the provisioning signature so each test starts from a clean Jaecoo slate.
        Preferences.remove(spoofProvider.context, Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE)
    }

    @Test
    fun testSpoofingDeviceLocale() {
        assertThat(spoofProvider.isLocaleSpoofEnabled).isFalse()

        spoofProvider.setSpoofLocale(Locale.JAPAN)
        assertThat(spoofProvider.isLocaleSpoofEnabled).isTrue()
        assertThat(spoofProvider.locale == Locale.JAPAN).isTrue()
    }

    @Test
    fun testSpoofingDeviceProperties() {
        assertThat(spoofProvider.isDeviceSpoofEnabled).isFalse()

        val properties = Properties().apply {
            setProperty("UserReadableName", "Test")
        }
        spoofProvider.setSpoofDeviceProperties(properties)
        assertThat(spoofProvider.isDeviceSpoofEnabled).isTrue()
        assertThat(spoofProvider.deviceProperties == properties).isTrue()
    }

    @Test
    fun testJaecooBaseProfileSelectsByApi() {
        val base = spoofProvider.selectJaecooBaseProfile()
        assertThat(base).isNotNull()
        val expected = when {
            Build.VERSION.SDK_INT >= 35 -> "Google Pixel 9a"
            Build.VERSION.SDK_INT >= 34 -> "Nothing Phone(1)"
            Build.VERSION.SDK_INT >= 33 -> "Google Pixel Tablet"
            Build.VERSION.SDK_INT >= 29 -> "Nokia 1.3"
            else -> "sirius"
        }
        assertThat(base?.getProperty("UserReadableName")).isEqualTo(expected)
    }

    @Test
    fun testJaecooProvisioningProducesMergedProfile() {
        spoofProvider.removeSpoofDeviceProperties()
        Preferences.remove(spoofProvider.context, Preferences.PREFERENCE_JAECOO_PROFILE_SIGNATURE)
        val first = spoofProvider.provisionJaecooDefault()
        val second = spoofProvider.provisionJaecooDefault()

        assertThat(spoofProvider.isDeviceSpoofEnabled).isTrue()
        assertThat(spoofProvider.deviceProperties.getProperty("UserReadableName"))
            .isEqualTo(SpoofProvider.JAECOO_PROFILE_NAME)
        // Exactly one of the two calls performed work: provisioning is idempotent on signature match.
        assertThat(first xor second).isTrue()
    }

    @Test
    fun testJaecooProfileSignatureIsStable() {
        val a = spoofProvider.jaecooProfileSignature()
        val b = spoofProvider.jaecooProfileSignature()
        assertThat(a).isEqualTo(b)
    }
}
