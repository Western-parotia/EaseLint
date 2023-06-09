package com.buildsrc.kts

object Dependencies {

    object Kotlin {
        // 这里的 kotlin 版本是与 7.4.2 的gradle 对应的
        const val version = "1.6.21"
        const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
    }

    object Lint {
        // agp 7.0 对应的 lint-gradle 是 30+
        const val version = "30.4.2"
        const val lint_gradle = "com.android.tools.lint:lint-gradle:${Lint.version}"
        const val lint_api = "com.android.tools.lint:lint-api:${Lint.version}"
        const val lint_checks = "com.android.tools.lint:lint-checks:${Lint.version}"
        const val lint = "com.android.tools.lint:lint-checks:${Lint.version}"

    }

    object Gradle {
        //由于7.4.2 版本无法查到，所以这里改为7.5
        const val version = "7.5"
        const val agp = "com.android.tools.build:gradle:$version"
        const val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"

    }
}