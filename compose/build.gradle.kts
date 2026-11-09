// O que depende de Android e Compose: navegação, rascunho de formulário no SavedStateHandle e efeitos de tela.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

apply {
    from("$rootDir/ktlint.gradle")
}

group = project.property("GROUP").toString()
version = project.property("VERSION_NAME").toString()

android {
    namespace = "io.github.romulomga.corekit.compose"
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

    buildFeatures {
        compose = true
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
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    api(libs.kotlinx.serialization.json)
    implementation(libs.compose.runtime.saveable)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    testImplementation(project(":testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "compose"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
