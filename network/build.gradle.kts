// A rede que todo app com servidor repete (gêmeo do CoreKitNetwork do iOS): token em cada request, retry só de leitura,
// nada sai sem rede, log sem credencial nem dado pessoal da URL, e o limite entre transporte e o erro do app.
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

apply {
    from("$rootDir/ktlint.gradle")
}

group = project.property("GROUP").toString()
version = project.property("VERSION_NAME").toString()

android {
    namespace = "io.github.romulomga.corekit.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "network"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
