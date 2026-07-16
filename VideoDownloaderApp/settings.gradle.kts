pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // JitPack hosts some community artifacts (e.g. GitHub packages built via JitPack)
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Added JitPack so the kotest-playwright extension (if published there) can be resolved.
        maven("https://jitpack.io")
    }
}

rootProject.name = "VideoDownloader"
include(":app")
