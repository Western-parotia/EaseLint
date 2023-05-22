package com.android.tools.lint.gradle

import java.io.File
import java.util.*

object ScanTargetContainer {

    @JvmStatic
    var checkFileList: LinkedList<File> = LinkedList()
        private set

    /**
     * 直接反射操作 checkFileList 因为AS的bug 一直卡在类型识别上，这里给个方法用来设置，绕开这个bug
     */
    @JvmStatic
    fun putCheckListFiles(files: List<File>) {
        checkFileList.clear()
        checkFileList.addAll(files)
    }

    @JvmStatic
    fun hasTarget(): Boolean {
        "checkFileList.size=${checkFileList.size}".log("ScanTargetContainer")
        checkFileList.forEach {
            "file:${it.absolutePath}".log("ScanTargetContainer")
        }
        return checkFileList.isNotEmpty()
    }

}