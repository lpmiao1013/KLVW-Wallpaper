pluginManagement {
    repositories {
        // Prefer Maven Central first so plugin transitive deps (e.g. gson) resolve
        // without relying on Google's Android Maven (which does not host them).
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
plugins {
    // 1.0.0 shades its deps (gson etc.), avoiding separate classpath downloads that
    // intermittently fail on CI when resolving foojay 0.10.0 → gson:2.10.1.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KLVWWallpaper"
include(":app")
include(":wear")
