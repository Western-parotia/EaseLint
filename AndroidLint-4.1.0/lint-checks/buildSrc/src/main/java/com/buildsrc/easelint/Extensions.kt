package com.buildsrc.easelint

import java.io.File
import java.util.*

private const val TAG = "buildSrc"
fun String.log(secondTag: String = "") {
    println("$TAG $secondTag $this")
}


fun getProperties(file: File, key: String): String {
    val properties = Properties()
    properties.load(file.inputStream())
    return properties.getProperty(key)
}