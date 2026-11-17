// Mixpanel atrás do AnalyticsSink do core, gêmeo do CoreKitMixpanel do iOS. Módulo próprio: só compila em quem usa.
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
    namespace = "io.github.romulomga.corekit.mixpanel"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    api(project(":core"))
    implementation(libs.mixpanel)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "mixpanel"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
