pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RestaurantOrders"

// Plain Kotlin: menu rules, pricing, modifiers, tickets and the printer connection.
// Builds and tests with only a JDK, no Android SDK needed.
include(":core")

// The tablet app. Included only where an Android SDK is installed (Android Studio writes
// local.properties), so :core still builds and tests anywhere with just a JDK.
val hasAndroidSdk = file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") } ||
    System.getenv("ANDROID_HOME") != null
if (hasAndroidSdk) include(":app")
