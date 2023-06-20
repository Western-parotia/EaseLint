package com.buildsrc.kts

import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependenciesSpec

object Dependencies {

    object Plugins {
        // 奇怪： 命名 拓展是 PluginDependenciesSpecScope.() 但是编译完要用 PluginDependenciesSpec 接收
        fun add(ps: PluginDependenciesSpec) {
            ps.apply {
                id("com.android.library") version (Gradle.version) apply (false)
                id("com.android.application") version (Gradle.version) apply (false)
                id("org.jetbrains.kotlin.android") version (Kotlin.version) apply (false)
                id("org.jetbrains.kotlin.jvm") version (Kotlin.version) apply false
            }
        }

    }

    object Kotlin {
        // 为什么这里选择 1.7.20 的kotlin
//          +--- com.android.tools.lint:lint:30.4.2
//        |    |    +--- org.jetbrains.kotlin:kotlin-reflect:1.7.10
//        |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10
//        |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10
//        |    |    |         \--- org.jetbrains:annotations:13.0
//        |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.10
//        |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
//        |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.10
//        |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
        const val version = "1.7.20"
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
        // 7.4.2 为7.0 最后一个版本
        const val version = "7.4.2"
        const val agp = "com.android.tools.build:gradle:$version"
        const val kgp = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"
    }
}