// O que todo banco SQLite dos apps precisa: o arquivo corrompido fica onde está em vez de ser apagado pelo Android.
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
    namespace = "io.github.romulomga.corekit.database"
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
    api(libs.androidx.sqlite.framework)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "database"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
