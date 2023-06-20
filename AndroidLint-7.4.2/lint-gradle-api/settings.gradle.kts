// pluginManagement 必须在第一个，同时他不可以使用buildScr 内的文件
// 文档链接：https://docs.gradle.org/current/userguide/plugins.html#sec:applying_plugins_buildscript
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/nexus/content/groups/public/")
        maven("https://maven.aliyun.com/repository/google/")
        maven("https://maven.aliyun.com/repository/central/")
        maven("https://maven.aliyun.com/repository/public/")

    }

}
println("gv---:${gradle.gradleVersion}")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/nexus/content/groups/public/")
        maven("https://maven.aliyun.com/repository/google/")
        maven("https://maven.aliyun.com/repository/central/")
        maven("https://maven.aliyun.com/repository/public/")
    }
}
rootProject.name = "lint-gradle-api"
include(":app")
//include(":lintGradle")
include(":cli")
