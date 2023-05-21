package com.buildsrc.easelint.lint.utils

private const val TAG = "EaseLint:>"
fun String.log(customTag: String? = null) {
    println("$TAG${customTag ?: ""}$this")
}
