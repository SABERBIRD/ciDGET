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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ciDGET"

include(":core", ":github")
// The Android module is skipped when CIDGET_SKIP_APP is set (used to check the pure-JVM modules
// on machines without an Android SDK). CI always builds it.
if (System.getenv("CIDGET_SKIP_APP") == null) {
    include(":app")
}
