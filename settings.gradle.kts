pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MobiXournal"

// The window is a plain JVM application and must be packageable on a machine with no Android SDK
// (`docs/tools.md`, *Desktop installer*). `-PdesktopOnly` leaves the tablet module out entirely, so
// Gradle never applies the Android plugin and never looks for an SDK; every other build configures all
// three modules, as before.
if (providers.gradleProperty("desktopOnly").orNull != "true") include(":app")
include(":mobix-core")
include(":desktop")
