package com.buildsrc.easelint.lint.utils

private const val TAG = "EaseLint_lint_plugin: "
internal fun String.log(customTag: String? = null) {
    println("$TAG${customTag ?: ""}: $this")
}
