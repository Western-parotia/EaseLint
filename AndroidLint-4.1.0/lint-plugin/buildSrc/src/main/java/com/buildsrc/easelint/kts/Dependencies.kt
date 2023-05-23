package com.buildsrc.easelint.kts

object Dependencies {

    object Kotlin {
        const val version = "1.4.31"
        const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    }

    object Gradle {
        const val version = "4.1.0"
        const val agp = "com.android.tools.build:gradle:$version"
        const val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"

    }
}