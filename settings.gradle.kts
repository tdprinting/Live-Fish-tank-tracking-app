pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ReefLive"

include(":core")

// `./gradlew -PcoreOnly :core:test` configures and tests :core with no Android SDK
// and no Google Maven access. CI (no -PcoreOnly) builds everything.
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
include(":web")
