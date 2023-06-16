buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
    }
}
// plugins 内 无法访问 buildSrc,plugins
plugins {
    id("com.android.library") version ("7.4.2") apply (false)
    id("com.android.application") version ("7.4.2") apply (false)
    id("org.jetbrains.kotlin.android") version ("1.7.20") apply (false)
    id("org.jetbrains.kotlin.jvm") version "1.8.20" apply false
}
