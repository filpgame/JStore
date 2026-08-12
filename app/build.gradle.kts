/*
 * SPDX-FileCopyrightText: 2021-2025 Rahul Kumar Patel <whyorean@gmail.com>
 * SPDX-FileCopyrightText: 2022-2025 The Calyx Institute
 * SPDX-FileCopyrightText: 2023 grrfe <grrfe@420blaze.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import java.util.Base64
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrepareJaecooSigning : DefaultTask() {
    @get:Internal
    abstract val encodedKeystore: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        val keystore = outputFile.get().asFile
        runCatching {
            keystore.parentFile.mkdirs()
            keystore.outputStream().use { output ->
                output.write(Base64.getDecoder().decode(encodedKeystore.get()))
            }
            keystore.setReadable(false, false)
            keystore.setReadable(true, true)
        }.getOrElse { error("RELEASE_KEYSTORE_BASE64 is not valid base64") }
    }
}

abstract class CleanJaecooSigning : DefaultTask() {
    @get:Internal
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun clean() {
        val keystore = outputFile.get().asFile
        if (keystore.exists() && !keystore.delete()) {
            error("Failed to remove temporary Jaecoo keystore")
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.rikka.tools.refine.plugin)
    alias(libs.plugins.hilt.android.plugin)
}

fun getSigningValue(key: String): String? {
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        val properties = Properties().apply {
            localProperties.inputStream().use(::load)
        }
        properties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

val releaseKeystoreBase64 = getSigningValue("RELEASE_KEYSTORE_BASE64")
val decodedKeystore = layout.buildDirectory.file("tmp/jaecoo-signing/release.jks").get().asFile
// The encoded keystore is the portable source of truth. RELEASE_KEYSTORE_PATH is
// accepted only as a local fallback and is never propagated to CI.
val releaseKeystorePath = if (releaseKeystoreBase64 != null) {
    decodedKeystore.absolutePath
} else {
    getSigningValue("RELEASE_KEYSTORE_PATH")
}
val releaseKeystorePassword = getSigningValue("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = getSigningValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = getSigningValue("RELEASE_KEY_PASSWORD")
val requestedJaecooBuild = gradle.startParameter.taskNames.any {
    it.contains("Jaecoo", ignoreCase = true) &&
        (it.contains("assemble", ignoreCase = true) || it.contains("bundle", ignoreCase = true))
}
val environmentSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

if (requestedJaecooBuild && !environmentSigningConfigured) {
    error(
        "Jaecoo builds require RELEASE_KEYSTORE_BASE64 (or local RELEASE_KEYSTORE_PATH), " +
            "RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD"
    )
}

val prepareJaecooSigning = tasks.register<PrepareJaecooSigning>("prepareJaecooSigning") {
    releaseKeystoreBase64?.let(encodedKeystore::set)
    outputFile.set(decodedKeystore)
}
val cleanJaecooSigning = tasks.register<CleanJaecooSigning>("cleanJaecooSigning") {
    outputFile.set(decodedKeystore)
}

if (releaseKeystoreBase64 != null) {
    tasks.configureEach {
        val signsJaecoo = name.contains("Jaecoo", ignoreCase = true) &&
            (
                name.startsWith("validateSigning") ||
                    name.startsWith("assemble") ||
                    name.startsWith("bundle")
                )
        if (signsJaecoo || name == "signingReport") {
            dependsOn(prepareJaecooSigning)
        }
        if (signsJaecoo || name == "signingReport") {
            finalizedBy(cleanJaecooSigning)
        }
    }
}

val lastCommitHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

val lastCommitTimestamp = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct")
}.standardOutput.asText.map { it.trim() }

val appVersionName = providers.gradleProperty("versionName").orNull ?: "4.8.4"
val appVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 76

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property"
        )
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "coil3.annotation.ExperimentalCoilApi",
            "kotlin.uuid.ExperimentalUuidApi"
        )
    }
}

configure<ApplicationExtension> {
    namespace = "com.aurora.store"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.aurora.store"
        minSdk {
            version = release(23)
        }
        targetSdk {
            version = release(37)
        }

        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "com.aurora.store.HiltInstrumentationTestRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        buildConfigField("String", "EXODUS_API_KEY", "\"bbe6ebae4ad45a9cbacb17d69739799b8df2c7ae\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${lastCommitTimestamp.get()}L")

        missingDimensionStrategy("device", "vanilla")
    }

    signingConfigs {
        create("jaecoo") {
            if (environmentSigningConfigured) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                // Never let a Jaecoo variant inherit the public debug key.
                storeFile =
                    layout.buildDirectory.file("missing-jaecoo-release-keystore").get().asFile
                storePassword = "missing"
                keyAlias = "missing"
                keyPassword = "missing"
            }
        }
        if (environmentSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        } else if (File("signing.properties").exists()) {
            create("release") {
                val properties = Properties().apply {
                    File("signing.properties").inputStream().use { load(it) }
                }

                keyAlias = properties["KEY_ALIAS"] as String
                keyPassword = properties["KEY_PASSWORD"] as String
                storeFile = file(properties["STORE_FILE"] as String)
                storePassword = properties["KEY_PASSWORD"] as String
            }
        }
        create("aosp") {
            // Generated from the AOSP test key:
            // https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r29/target/product/security/testkey.pk8
            keyAlias = "testkey"
            keyPassword = "testkey"
            storeFile = file("testkey.jks")
            storePassword = "testkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        register("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-${lastCommitHash.get()}"
        }

        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("aosp")
        }
    }

    flavorDimensions += "device"

    productFlavors {
        create("vanilla") {
            isDefault = true
            dimension = "device"
            buildConfigField("Boolean", "SHOW_ANONYMOUS_LOGIN", "true")
        }

        create("huawei") {
            dimension = "device"
            versionNameSuffix = "-hw"
            buildConfigField("Boolean", "SHOW_ANONYMOUS_LOGIN", "false")
        }

        // This flavor is only for preloaded devices / users who push the app to system
        create("preload") {
            dimension = "device"
            versionNameSuffix = "-preload"
            buildConfigField("Boolean", "SHOW_ANONYMOUS_LOGIN", "true")
        }

        // Jaecoo devices use the privileged jconfig installation bridge.
        create("jaecoo") {
            dimension = "device"
            buildConfigField("Boolean", "SHOW_ANONYMOUS_LOGIN", "true")
            signingConfig = signingConfigs.getByName("jaecoo")
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
        compose = true
    }

    lint {
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        val flavour = variant.flavorName
        if ((flavour == "huawei" || flavour == "preload") && variant.buildType == "nightly") {
            variant.enable = false
        }
    }

    onVariants(selector().withFlavor("device" to "jaecoo")) { variant ->
        // jconfig authenticates this Jaeecoo client package, including dev builds.
        variant.applicationId.set("com.frodrigues.jstore")
        variant.signingConfig.setConfig(
            extensions.getByType<ApplicationExtension>().signingConfigs.getByName("jaecoo")
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    android = true
    verbose = true
}

dependencies {

    // Google's Goodies
    implementation(libs.google.android.material)
    implementation(libs.google.protobuf.javalite)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.navigation3)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)

    implementation(libs.androidx.adaptive.core)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Coil
    implementation(libs.coil.kt)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // HTTP Clients
    implementation(libs.squareup.okhttp)

    // Lib-SU
    implementation(libs.github.topjohnwu.libsu)

    // GPlayApi
    implementation(libs.auroraoss.gplayapi)

    // Shizuku
    compileOnly(libs.rikka.hidden.stub)
    implementation(libs.rikka.tools.refine.runtime)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(libs.lsposed.hiddenapibypass)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.hilt.viewmodel)
    implementation(libs.hilt.android.core)
    implementation(libs.hilt.androidx.work)

    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    // Room
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)

    implementation(libs.process.phoenix)

    "huaweiImplementation"(libs.huawei.hms.coreservice)

    // LeakCanary
    debugImplementation(libs.squareup.leakcanary.android)
}
