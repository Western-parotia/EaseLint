package com.android.tools.lint.gradle

import java.io.File
import java.util.*

object ScanTargetContainer {

    var checkFileList: LinkedList<File> = LinkedList()
        private set

    fun hasTarget(): Boolean {
        "checkFileList.size=${checkFileList.size}".log("ScanTargetContainer")
        checkFileList.forEach {
            "file:${it.absolutePath}".log("ScanTargetContainer")
        }
        return checkFileList.isEmpty()
    }

}