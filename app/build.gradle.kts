import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// CI passes these; local builds get a development version.
val dalmaVersionName: String = (findProperty("dalmaVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.0.0-dev"
val dalmaVersionCode: Int = (findProperty("dalmaVersionCode") as String?)?.toIntOrNull() ?: 1
val dalmaAbis: List<String> = ((findProperty("dalmaAbis") as String?) ?: "arm64-v8a").split(',').map { it.trim() }.filter { it.isNotEmpty() }

// Release signing: keystore.properties next to this file, or environment variables in CI.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStore = signingValue("storeFile", "DALMA_KEYSTORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.isFile }

// The read-only token for the update check is compiled into the APK, where anyone can read it:
// only a fine-grained token limited to this repository (Contents: Read-only) may go there.
val updateToken = System.getenv("DALMA_UPDATE_READ_TOKEN").orEmpty()
require(updateToken.isEmpty() || updateToken.startsWith("github_pat_")) {
    "DALMA_UPDATE_READ_TOKEN ships inside the APK: use a fine-grained token (github_pat_...) limited to this repository with Contents: Read-only, never a classic ghp_ token."
}

android {
    namespace = "io.github.itemujinwork.dalma"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.itemujinwork.dalma"
        minSdk = 29
        targetSdk = 35
        versionCode = dalmaVersionCode
        versionName = dalmaVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += dalmaAbis }
        buildConfigField("String", "UPDATE_REPO", "\"iTemujinWork/YT-convert-APK\"")
        buildConfigField("String", "UPDATE_TOKEN", "\"$updateToken\"")
    }

    signingConfigs {
        // A public throwaway key for previews and local builds, so one preview can update the
        // previous one. Anyone can build an APK that installs over a preview; the owner's real
        // key (keystore.properties / CI secrets) is used whenever it exists.
        create("preview") {
            storeFile = file("preview.keystore")
            storePassword = "preview"
            keyAlias = "preview"
            keyPassword = "preview"
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = signingValue("storePassword", "DALMA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DALMA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DALMA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("preview")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // The on-device tests run against the shrunk release APK, the one people install: R8
    // once removed reflective constructors the engine's unzip needs, and debug never noticed.
    testBuildType = "release"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The engine executes its .so files as programs, so they must be real files on disk.
        jniLibs { useLegacyPackaging = true }
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.md", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Robolectric only sees the variant's merged assets, never src/test/assets, so the
    // picture fixtures of ScreenshotTest ride along in debug builds (release stays clean).
    sourceSets.getByName("debug").assets.srcDir("src/test/assets")

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("ObsoleteLintCustomCheck", "GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi", "ChromeOsAbiSupport")
    }
}

kotlin {
    // Like core/: any JDK >= 17 that runs Gradle can build this; no toolchain download needed.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

roborazzi {
    outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
}

dependencies {
    implementation(libs.dalma.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // The download engine: Python + yt-dlp + QuickJS, and FFmpeg, as native libraries. Only the
    // binaries are used; the library's own classes and their dependencies are left out.
    implementation(libs.youtubedl.library) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "commons-io")
        exclude(group = "org.apache.commons")
        exclude(group = "androidx.appcompat")
    }
    implementation(libs.youtubedl.ffmpeg) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "commons-io")
        exclude(group = "org.apache.commons")
        exclude(group = "androidx.appcompat")
    }
    // Reads the Unix symlinks inside the engine zips (java.util.zip cannot).
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
