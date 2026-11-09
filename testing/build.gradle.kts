// Dublês e regras de teste que os apps repetiam: entram como `testImplementation`.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

apply {
    from("$rootDir/ktlint.gradle")
}

group = project.property("GROUP").toString()
version = project.property("VERSION_NAME").toString()

kotlin {
    jvmToolchain(21)
    explicitApi()
}

java {
    withSourcesJar()
}

dependencies {
    api(project(":core"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "testing"

            from(components["java"])
        }
    }
}
