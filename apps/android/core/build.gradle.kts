plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Prints a sample kitchen ticket from this computer, to check the real printer before the
// tablet app exists:  gradlew :core:printTest --args="192.168.1.50"
tasks.register<JavaExec>("printTest") {
    group = "verification"
    description = "Sends sample kitchen tickets to a network printer: --args=\"<ip> [port] [--chinese|--image|--star]\" or --preview"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.restaurantorders.tools.PrintTestKt")
}
