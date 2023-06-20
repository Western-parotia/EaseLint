import com.buildsrc.kts.Repositories

com.buildsrc.kts.GlobalConfig.init(project)
buildscript {
    dependencies {
        classpath(com.buildsrc.kts.Dependencies.Gradle.agp)
    }
}

plugins {
    com.buildsrc.kts.Dependencies.Plugins.add(this)
}

// 在 settings.gradle 中也无法直接使用 buildSrc的类，所以还是改到这里进行配置
allprojects {
    repositories {
        Repositories.defRepositories(this)
    }
}
