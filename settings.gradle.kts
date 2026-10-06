rootProject.name = "bangun-woi"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Platform-independent logic (pure Kotlin/JVM, no Android dependencies).
// The Android `:app` module will be added here once the Android SDK/Maven repos are reachable.
include(":core")
