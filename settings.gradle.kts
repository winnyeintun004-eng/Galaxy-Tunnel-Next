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
        flatDir {
            dirs("v2ray/libs")
        }
    }
}

rootProject.name = "Galaxy-Tunnel-Next"
include(":app")
include(":v2ray")  // ← ADD THIS LINE
