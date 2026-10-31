pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Dalma"

// The pure Kotlin core lives in its own build so it can be tested without the Android SDK.
includeBuild("core") {
    dependencySubstitution {
        substitute(module("io.github.itemujinwork.dalma:dalma-core")).using(project(":"))
    }
}

include(":app")
