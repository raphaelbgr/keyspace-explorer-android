pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.android") version "1.9.22"
        id("com.android.application") version "8.11.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KeyspaceExplorer"
include(":app")