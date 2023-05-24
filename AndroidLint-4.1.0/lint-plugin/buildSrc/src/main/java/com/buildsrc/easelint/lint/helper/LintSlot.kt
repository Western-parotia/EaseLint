package com.buildsrc.easelint.lint.helper

import com.buildsrc.easelint.lint.task.LintException
import com.buildsrc.easelint.lint.utils.log
import java.io.File
import java.util.*

/**
 * 与扫描相关的所有参数都应该通过这个 LintSlot 进行修改。
 *
 */
object LintSlot {

    //扫描目标，统一为文件全路径
    private val targetFiles_: LinkedList<String> = LinkedList()
    val targetFiles = targetFiles_

    //需要关闭的 issue 清单，部署到CI时用于快速降级，快速停用个别异常issue，优先级最高
    private val disableIssues_: LinkedList<String> = LinkedList()
    val disableIssues = disableIssues_

    // 用于定向控制所有的 issue ,主要用于上线自己开发的 Issue
    private val checkOnlyIssues_: LinkedList<String> = LinkedList()
    val checkOnlyIssues = checkOnlyIssues_

    //扫描文件白名单
    private val fileWhiteList_: LinkedList<String> = LinkedList()
    val fileWhiteList = fileWhiteList_

    /**
     * 白名单支持文件夹级别，所以为了避免多module 扫描的白名单存在冲突，限制白名单文件路径最少提供4级目录
     * 比如/com/xx/utils 很容易导致范围过大
     * 建议  moduleName/src/main/java/com/xx/xx
     * @param filePaths
     */
    fun addFileWhiteList(filePaths: LinkedList<String>) {
        filePaths.forEach {
            val itemPath = it.split("/")
            if (itemPath.size < 4) {
                //路径小于4级，不规范
                throw LintException(
                    "Invalid whitelist file path," +
                            " a minimum of 4 levels of directory is required: $it"
                )
            } else {
                //路径规范，添加至白名单
                fileWhiteList_.add(it.replace("/", File.separator))
            }
        }
    }

    fun clearTargetFiles() {
        targetFiles_.clear()
    }

    fun addTargetFile(file: LinkedList<String>) {
        targetFiles_.addAll(file)
    }

    fun clearWhiteList() {
        fileWhiteList_.clear()
    }

    fun addDisableIssues(issueIds: LinkedList<String>) {
        disableIssues_.addAll(issueIds)
    }

    fun clearDisableIssues() {
        disableIssues_.clear()
    }

    fun addCheckOnlyIssues(issueIds: LinkedList<String>) {
        checkOnlyIssues_.addAll(issueIds)
    }

    fun clearCheckOnlyIssues() {
        checkOnlyIssues_.clear()
    }

    fun clearAll() {
        clearWhiteList()
        clearDisableIssues()
        clearCheckOnlyIssues()
        clearTargetFiles()
    }

    fun finalTargets(): List<File> {
        val files = LinkedList<File>()
        targetFiles.forEach { t ->
            if (!fileWhiteList.contains(t)) {
                val file = File(t)
                if (file.exists()) {
                    files.add(file)
                } else {
                    "this file[$t] is not exists".log("EaseLintReflectiveLintRunner")
                }
            }
        }
        return files
    }
}