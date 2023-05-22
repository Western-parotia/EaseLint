package com.buildsrc.easelint

object Dependencies {
    object Kotlin {
        const val version = "1.4.31"
        const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    }

    object Lint {
        //gradle version+23
        const val lint_version = "27.1.1"
    }

    object Gradle {
        const val version = "4.1.0"
        const val agp = "com.android.tools.build:gradle:$version"
        val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"

    }
}