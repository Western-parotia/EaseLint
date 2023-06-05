package com.buildsrc.kts

object Dependencies {

    object Kotlin {
        const val version = "1.4.31"
        const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    }

    object Lint {
        // agp 7.0 对应的 lint-gradle 是 30+
        const val version = "30.0.1"
        const val lint_gradle = "com.android.tools.lint:lint-gradle:${Lint.version}"

    }

    object Gradle {
        const val version = "7.4.2"
        const val agp = "com.android.tools.build:gradle:$version"
        const val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"

    }
}