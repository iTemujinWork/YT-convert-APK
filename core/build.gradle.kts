import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.itemujinwork.dalma"
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
    //   ./gradlew -p core test -Ddalma.engineTests=true
    systemProperty("dalma.engineTests", System.getProperty("dalma.engineTests") ?: "false")
    System.getProperty("dalma.ytdlp")?.let { systemProperty("dalma.ytdlp", it) }
    // A real yt-dlp release file (zipapp) for ZipAppTest: ./gradlew -p core test -Ddalma.zipapp=<file>
    System.getProperty("dalma.zipapp")?.let { systemProperty("dalma.zipapp", it) }
    // A Python with mutagen for TagScriptTest (ffmpeg and ffprobe on the PATH too): ./gradlew -p core test -Ddalma.python=<python>
    System.getProperty("dalma.python")?.let { systemProperty("dalma.python", it) }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
