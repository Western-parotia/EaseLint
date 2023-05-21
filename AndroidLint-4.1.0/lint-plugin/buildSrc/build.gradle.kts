plugins {
    `kotlin-dsl`
    /*
    org.jetbrains.kotlin.jvm 避免升级，否则将会有API不兼容
    Unsupported Kotlin plugin version.
    The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `1.3.72`
     that might work differently than in the requested version `1.4.30`.
     */
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
}

repositories {
    mavenCentral()
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // 无法引用 buildSrc 内的类
    implementation("com.android.tools.build:gradle:4.1.0")
}