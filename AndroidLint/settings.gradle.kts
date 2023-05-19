pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { setUrl("https://jitpack.io") }
        maven {
            setUrl("https://maven.aliyun.com/repository/gradle-plugin")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/public/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/google/")
        }
        maven {
            setUrl("https://maven.aliyun.com/repository/central/")
        }
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidLint"
include(":app")