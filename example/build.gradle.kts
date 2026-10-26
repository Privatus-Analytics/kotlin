plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.6.0"

dependencies {
    implementation("com.privatusanalytics:privatus-analytics-ktor:1.0.1")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.20")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ExampleKt")
}
