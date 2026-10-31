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
        buildConfigField("String", "UPDATE_TOKEN", "\"${System.getenv("DALMA_UPDATE_READ_TOKEN").orEmpty()}\"")
    }

    signingConfigs {
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

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

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("ObsoleteLintCustomCheck", "GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    jvmToolchain(17)
}

roborazzi {
    outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
}

dependencies {
    implementation("io.github.itemujinwork.dalma:dalma-core")

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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // The download engine: Python + yt-dlp + QuickJS, and FFmpeg, as native libraries.
    implementation(libs.youtubedl.library) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "commons-io")
        exclude(group = "androidx.appcompat")
    }
    implementation(libs.youtubedl.ffmpeg) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "commons-io")
        exclude(group = "androidx.appcompat")
    }

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
