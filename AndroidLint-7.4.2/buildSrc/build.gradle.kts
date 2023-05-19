plugins {
    `kotlin-dsl`
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
}

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
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies {
// 如果在这里引入了4.1.0
// 那么项目根配置 也默认从 这里的版本进行加载，将找不到高于4.1.0的 android 插件
//    implementation("com.android.tools.build:gradle:7.4.2")
//    gradleApi()
}