package com.buildsrc.kts

object Dependencies {

    object Kotlin {
        const val version = "1.4.31"
        const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    }

    object Lint {
        // agp 4.1.0 对应的 lint-gradle 是 27.1.1
        const val version = "27.1.1"
        const val lint_gradle = "com.android.tools.lint:lint-gradle:${Lint.version}"

    }

    object Gradle {
        const val version = "4.1.0"
        const val agp = "com.android.tools.build:gradle:$version"
        const val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"

    }
}