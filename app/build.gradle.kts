import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledSigningStoreFile = file("../signing/luxmusic-dev.jks")
val bundledSigningStorePassword = "luxmusic"
val bundledSigningKeyAlias = "luxmusic-dev"
val baseVersionName = providers.gradleProperty("luxmusic.baseVersion").orNull ?: "0.7.4"
val appVersionCode = System.getenv("LUXMUSIC_VERSION_CODE")?.toIntOrNull() ?: 7_004_000
val appVersionName = System.getenv("LUXMUSIC_VERSION_NAME")?.takeUnless { it.isBlank() } ?: baseVersionName
val emulatorBuild = providers.gradleProperty("luxmusic.emulator").orNull == "true"

android {
    namespace = "com.luxmusic.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luxmusic.android"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = if (providers.gradleProperty("luxmusic.upgradeTest").orNull == "true")
            "com.luxmusic.android.data.UpgradeFixtureInstrumentation"
        else "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

    }

    signingConfigs {
        create("luxmusic") {
            storeFile = bundledSigningStoreFile
            storePassword = bundledSigningStorePassword
            keyAlias = bundledSigningKeyAlias
            keyPassword = bundledSigningStorePassword
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("luxmusic")
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            signingConfig = signingConfigs.getByName("luxmusic")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            if (!emulatorBuild) {
                excludes += setOf("lib/x86/**", "lib/x86_64/**")
            }
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            if (emulatorBuild) include("x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    val composeUi = "1.10.6"
    val material3 = "1.4.0"
    val activityCompose = "1.13.0"
    val lifecycle = "2.10.0"
    val media3 = "1.10.0"
    val coroutines = "1.10.2"
    val youtubedlAndroid = "0.19.0"

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:$activityCompose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")
    implementation("androidx.compose.foundation:foundation-layout:$composeUi")
    implementation("androidx.compose.runtime:runtime-saveable:$composeUi")
    implementation("androidx.compose.animation:animation:$composeUi")
    implementation("androidx.compose.material3:material3:$material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // GPL-licensed downloader dependency. Keep it for prototyping, swap it out if you need a proprietary release.
    // Includes QuickJS on Android: required by current yt-dlp for YouTube extraction.
    implementation("io.github.deniscerri.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.deniscerri.youtubedl-android:ffmpeg:$youtubedlAndroid")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeUi")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeUi")
}

// Backward-compatible task name; Gradle discovers all tests and writes standard XML reports.
tasks.register("offlineUnitTest") {
    group = "verification"
    description = "Runs all JVM regression tests without Docker or an Android device."
    dependsOn("testDebugUnitTest")
}

val verifyBundledExtractor = tasks.register("verifyBundledExtractor") {
    group = "verification"
    val extractor = file("src/main/assets/yt-dlp")
    inputs.file(extractor)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(extractor.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6") {
            "Bundled yt-dlp checksum mismatch. See docs/bundled-extractor.md."
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyBundledExtractor) }

val verifyReleaseRuntimeKeepRules = tasks.register("verifyReleaseRuntimeKeepRules") {
    group = "verification"
    description = "Checks that R8 preserves classes instantiated reflectively by the release downloader."
    dependsOn("minifyReleaseWithR8")

    doLast {
        val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        check(mappingFile.isFile) {
            "Release R8 mapping is missing: ${mappingFile.absolutePath}"
        }

        val mapping = mappingFile.readText()
        val reflectiveClasses = listOf(
            "org.apache.commons.compress.archivers.zip.AsiExtraField",
            "org.apache.commons.compress.archivers.zip.ExtraFieldUtils",
            "org.apache.commons.compress.archivers.zip.ZipFile",
        )
        reflectiveClasses.forEach { className ->
            check(mapping.contains("$className -> $className:")) {
                "$className was renamed by R8; release link downloads would fail at runtime"
            }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseRuntimeKeepRules)
}
