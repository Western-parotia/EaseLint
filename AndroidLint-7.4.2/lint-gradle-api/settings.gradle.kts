// pluginManagement 与 dependencyResolutionManagement 为新版本API，在buildSrc 导入AGP时会报错插件版本不唯一
//pluginManagement {
//    repositories {
//    }
//
//}
//dependencyResolutionManagement {
// {

println("gv---:${gradle.gradleVersion}")

rootProject.name = "lint-gradle-api"
include(":app")

include(":lint-api")
