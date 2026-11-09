// Kotlin puro: nada de Android aqui, para um módulo `:core` de app (regras de negócio) poder usar e os testes rodarem
// na JVM em segundos.
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
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "core"

            from(components["java"])
        }
    }
}
