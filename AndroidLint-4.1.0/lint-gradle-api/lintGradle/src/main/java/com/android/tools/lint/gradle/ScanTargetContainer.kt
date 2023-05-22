package com.android.tools.lint.gradle

import java.io.File

object ScanTargetContainer {

    val checkFileList: ArrayList<File> = arrayListOf()

    fun hasTarget(): Boolean {
        "checkFileList.size=${checkFileList.size}".log("ScanTargetContainer")
        checkFileList.forEach {
            "file:${it.absolutePath}".log("ScanTargetContainer")
        }
        return checkFileList.isEmpty()
    }

}