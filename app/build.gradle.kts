plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.luxmusic.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luxmusic.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

    // GPL-licensed downloader dependency. Keep it for prototyping, swap it out if you need a proprietary release.
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeUi")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
