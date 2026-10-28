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
