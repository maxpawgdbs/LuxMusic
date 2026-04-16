plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.luxmusic.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luxmusic.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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
    val youtubedlAndroid = "0.18.1"

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
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeUi")

    testImplementation(files("libs/junit4.jar"))
    testRuntimeOnly(files("libs/junit4.jar"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

tasks.register<JavaExec>("offlineUnitTest") {
    group = "verification"
    description = "Runs JVM unit tests with the bundled offline JUnit runtime."
    dependsOn(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac",
        "compileDebugUnitTestKotlin",
        "compileDebugUnitTestJavaWithJavac",
        "processDebugJavaRes",
        "processDebugUnitTestJavaRes",
    )

    mainClass.set("org.junit.runner.JUnitCore")
    val buildOutputDir = layout.buildDirectory.get().asFile
    classpath = files(
        file("libs/junit4.jar"),
        buildOutputDir.resolve("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
        buildOutputDir.resolve("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"),
        buildOutputDir.resolve("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        buildOutputDir.resolve("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
        configurations.getByName("debugUnitTestRuntimeClasspath"),
    )
    args(
        "com.luxmusic.android.download.DownloadParsingTest",
        "com.luxmusic.android.download.DownloadPlannerTest",
        "com.luxmusic.android.download.DownloadMetadataResolverTest",
        "com.luxmusic.android.download.LinkDownloadExecutorTest",
    )
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    enabled = false
}

tasks.matching { it.name == "test" || it.name == "check" }.configureEach {
    dependsOn("offlineUnitTest")
}
