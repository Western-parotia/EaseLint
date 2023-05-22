package com.android.tools.lint.gradle

import java.io.File

object ScanTargetContainer {

    val checkFileList: List<File> = mutableListOf()

    fun hasTarget(): Boolean {
        return checkFileList.isEmpty()
    }

}