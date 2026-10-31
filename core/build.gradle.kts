import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.itemujinwork.hanga"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // Integration tests that drive a real yt-dlp + ffmpeg run only when asked:
    //   ./gradlew -p core test -Dhanga.engineTests=true
    systemProperty("hanga.engineTests", System.getProperty("hanga.engineTests") ?: "false")
    System.getProperty("hanga.ytdlp")?.let { systemProperty("hanga.ytdlp", it) }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
