pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CoreKit-Android"

// Como submódulo de um app (`includeBuild`), o CoreKit não tem `local.properties` (não vai pelo git) e o AGP não
// acharia o Android SDK. Sem `ANDROID_HOME`, copia o `sdk.dir` do `local.properties` do app, subindo as pastas.
if (!file("local.properties").exists() && System.getenv("ANDROID_HOME") == null && System.getenv("ANDROID_SDK_ROOT") == null) {
    val sdkDir = generateSequence(settingsDir.parentFile) { it.parentFile }
        .map { it.resolve("local.properties") }
        .filter { it.isFile }
        .mapNotNull { file -> file.readLines().firstOrNull { it.startsWith("sdk.dir=") } }
        .firstOrNull()

    if (sdkDir != null) file("local.properties").writeText("$sdkDir\n")
}

include(
    ":core",
    ":compose",
    ":testing",
    ":billing",
    ":firebase",
    ":review",
)
