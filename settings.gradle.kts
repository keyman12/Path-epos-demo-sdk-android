pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Verifone PSDK for the path-psdk-adapter module — the composite
        // build resolves the substituted SDK projects' dependencies HERE
        // (in the consuming build), so the SDK repo's committed Maven repo
        // and JitPack (usb-serial transitive) must be registered too.
        maven { url = uri("../path-terminal-sdk-android/third-party/verifone/m2") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PathEPOSDemo"
include(":app")

// Include Path SDK modules via composite build
includeBuild("../path-terminal-sdk-android") {
    dependencySubstitution {
        substitute(module("tech.path2ai.sdk:path-core-models")).using(project(":path-core-models"))
        substitute(module("tech.path2ai.sdk:path-terminal-sdk")).using(project(":path-terminal-sdk"))
        substitute(module("tech.path2ai.sdk:path-emulator-adapter")).using(project(":path-emulator-adapter"))
        substitute(module("tech.path2ai.sdk:path-mock-adapter")).using(project(":path-mock-adapter"))
        substitute(module("tech.path2ai.sdk:path-diagnostics")).using(project(":path-diagnostics"))
        substitute(module("tech.path2ai.sdk:path-psdk-adapter")).using(project(":path-psdk-adapter"))
    }
}
