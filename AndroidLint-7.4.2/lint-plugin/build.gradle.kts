com.buildsrc.kts.GlobalConfig.init(project)
buildscript {
    repositories {
        com.buildsrc.kts.Repositories.defRepositories(this)
//        maven("https://maven.aliyun.com/repository/gradle-plugin")
//        maven("https://maven.aliyun.com/nexus/content/groups/public/")
//        maven("https://maven.aliyun.com/repository/google/")
//        maven("https://maven.aliyun.com/repository/central/")
//        maven("https://maven.aliyun.com/repository/public/")
//        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath(kotlin("gradle-plugin", version = "1.7.0"))
    }
}
//为新版本API，在buildSrc 导入AGP时会报错插件版本不唯一
//plugins {
////    com.buildsrc.kts.Dependencies.Plugins.add(this)
//    id("com.android.library") version ("7.4.2") apply (false)
//    id("com.android.application") version ("7.4.2") apply (false)
//    id("org.jetbrains.kotlin.android") version ("1.7.20") apply (false)
//    id("org.jetbrains.kotlin.jvm") version ("1.7.20") apply false
//}

// 在 settings.gradle 中也无法直接使用 buildSrc的类，所以还是改到这里进行配置
// 在BuildSrc 编译与 module 存在冲突时 这里无法成功引用 buildSrc 的类
// 所以此项目中优先使用 显式配置方式
allprojects {
    repositories {
        com.buildsrc.kts.Repositories.defRepositories(this)
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
    }
}
