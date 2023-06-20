import com.buildsrc.kts.Repositories

com.buildsrc.kts.GlobalConfig.init(project)
buildscript {
    dependencies {
        classpath(com.buildsrc.kts.Dependencies.Gradle.agp)
    }
}

plugins {
    com.buildsrc.kts.Dependencies.Plugins.add(this)
//    id("com.android.library") version ("7.4.2") apply (true)
//    id("com.android.application") version ("7.4.2") apply (false)
//    id("org.jetbrains.kotlin.android") version ("1.7.20") apply (false)
//    id("org.jetbrains.kotlin.jvm") version ("1.7.20") apply false
}
//
//// 在 settings.gradle 中也无法直接使用 buildSrc的类，所以还是改到这里进行配置
allprojects {
    repositories {
        Repositories.defRepositories(this)
    }
}
