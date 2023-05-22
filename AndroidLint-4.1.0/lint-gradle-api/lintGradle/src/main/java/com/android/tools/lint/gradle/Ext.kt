package com.android.tools.lint.gradle

private const val TAG = "EaseLint_lint_gradle:>"
fun String.log(customTag: String? = null) {
    println("$TAG${customTag ?: ""}$this")
}
