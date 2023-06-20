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
//    FAIL_ON_PROJECT_REPOS ：限制在其他地方配置仓库,默认为 PREFER_PROJECT
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
//    repositories {
//        maven("https://maven.aliyun.com/repository/gradle-plugin")
//        maven("https://maven.aliyun.com/nexus/content/groups/public/")
//        maven("https://maven.aliyun.com/repository/google/")
//        maven("https://maven.aliyun.com/repository/central/")
//        maven("https://maven.aliyun.com/repository/public/")
//        maven("https://maven.google.com/")
//
//        mavenCentral()
//        maven {
//            setUrl("https://packages.aliyun.com/maven/repository/2196753-release-jjUEtd/")
//            credentials {
//                username = "642b9f209f62bf75b33fc1ae"
//                password = "EkNR7ao]bCHh"
//            }
//        }
//    }
}


rootProject.name = "lint-gradle-api"
include(":app")
//include(":lintGradle")
include(":cli")
