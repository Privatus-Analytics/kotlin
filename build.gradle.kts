import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.privatusanalytics"
version = "1.0.0"

// CI tests older Ktor 3 releases with -PktorVersion=3.0.3.
val ktorVersion = providers.gradleProperty("ktorVersion").getOrElse("3.6.0")

repositories {
    mavenCentral()
}

dependencies {
    api("io.ktor:ktor-server-core:$ktorVersion")
    // The kotlinx.html helpers are optional. Apps that use them already have
    // ktor-server-html-builder (or kotlinx-html) on the classpath.
    compileOnly("io.ktor:ktor-server-html-builder:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    testImplementation("io.ktor:ktor-server-compression:$ktorVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.test {
    useJUnitPlatform()
    // CI runs Gradle on Java 21 and the tests on each supported Java with
    // -PtestJava=17 (or 21, 25), as Gradle 8 cannot itself run on Java 25.
    providers.gradleProperty("testJava").orNull?.let { testJava ->
        javaLauncher.set(
            javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(testJava)) },
        )
        doFirst { println("Testing on Java ${javaLauncher.get().metadata.javaRuntimeVersion}") }
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(
                testDescriptor: TestDescriptor,
                result: TestResult,
            ) {}

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) {
                if (suite.parent == null) {
                    println(
                        "Tests: ${result.testCount} run, ${result.successfulTestCount} passed, " +
                            "${result.failedTestCount} failed, ${result.skippedTestCount} skipped",
                    )
                }
            }
        },
    )
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral(automaticRelease = false)
    // The release workflow passes the GPG key as ORG_GRADLE_PROJECT_signingInMemoryKey.
    // Local builds have no key, so they skip signing.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates("com.privatusanalytics", "privatus-analytics-ktor", version.toString())

    pom {
        name.set("Privatus Analytics for Ktor")
        description.set(
            "A Ktor server plugin that prints the cookieless Privatus Analytics tracking script, " +
                "event and click map attributes, and page props. It never sends events from the server.",
        )
        url.set("https://privatusanalytics.com")
        inceptionYear.set("2026")
        organization {
            name.set("Two Phase LLC")
            url.set("https://privatusanalytics.com")
        }
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("privatus-analytics")
                name.set("Privatus Analytics (Two Phase LLC)")
                email.set("james@privatusanalytics.com")
                organization.set("Two Phase LLC")
                organizationUrl.set("https://privatusanalytics.com")
            }
        }
        scm {
            url.set("https://github.com/Privatus-Analytics/kotlin")
            connection.set("scm:git:https://github.com/Privatus-Analytics/kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/Privatus-Analytics/kotlin.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/Privatus-Analytics/kotlin/issues")
        }
    }
}
