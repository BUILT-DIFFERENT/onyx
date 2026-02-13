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
        maven {
            url = uri("http://maven.ghostscript.com/")
            isAllowInsecureProtocol = true
        }
        maven("https://jitpack.io") // PdfiumAndroid (Zoltaneusz fork)
    }
}

rootProject.name = "onyx-android"
include(":app")
