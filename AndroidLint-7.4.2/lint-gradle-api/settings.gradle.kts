// pluginManagement 必须在第一个，同时他不可以使用buildScr 内的文件
// 文档链接：https://docs.gradle.org/current/userguide/plugins.html#sec:applying_plugins_buildscript
pluginManagement {
    repositories {
        maven(url = "https://mvnrepository.com/artifact/com.android.tools.build/gradle")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://maven.aliyun.com/nexus/content/groups/public/")
        maven(url = "https://maven.aliyun.com/repository/google/")
        maven(url = "https://maven.aliyun.com/repository/jcenter/")
        maven(url = "https://maven.aliyun.com/repository/central/")
        maven(url = "https://maven.aliyun.com/repository/public/")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "lint-gradle-api"
//include ':app'
