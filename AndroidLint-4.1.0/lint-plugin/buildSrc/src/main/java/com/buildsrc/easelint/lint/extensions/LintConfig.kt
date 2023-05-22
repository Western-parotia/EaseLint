package com.buildsrc.easelint.lint.extensions

import com.buildsrc.easelint.lint.task.LintException
import java.io.File

object LintConfig {
    //需要关闭的 issue 清单，部署到CI时用与快速降级，快速停用个别异常issue，优先级最高
    private val issueDisableList_: HashSet<String> = hashSetOf()
    val issueDisableList: Set<String> = issueDisableList_

    // 用于定向控制所有的 issue ,主要用于上线自己开发的 Issue
    private val checkOnlyConfig_: HashSet<String> = hashSetOf()
    val checkOnlyConfig: Set<String> = checkOnlyConfig_

    //扫描文件白名单
    private val fileWhiteList_: MutableList<String> = mutableListOf()
    val fileWhiteList: List<String> = fileWhiteList_

    /**
     * 白名单支持文件夹级别，所以为了避免多module 扫描的白名单存在冲突，限制白名单文件路径最少提供4级目录
     * 比如/com/xx/utils 很容易导致范围过大
     * 建议  moduleName/src/main/java/com/xx/xx
     * @param filePaths
     */
    fun addFileWhiteList(filePaths: HashSet<String>) {
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

    fun clearWhiteList() {
        fileWhiteList_.clear()
    }

    fun addDisableIssue(issueIds: HashSet<String>) {
        issueDisableList_.addAll(issueIds.toHashSet())
    }

    fun clearDisable() {
        issueDisableList_.clear()
    }

    fun addCheckOnly(issueIds: HashSet<String>) {
        checkOnlyConfig_.addAll(issueIds)
    }

    fun clearCheckOnly() {
        checkOnlyConfig_.clear()
    }

    fun clearAll() {
        clearWhiteList()
        clearDisable()
        clearCheckOnly()
    }
}