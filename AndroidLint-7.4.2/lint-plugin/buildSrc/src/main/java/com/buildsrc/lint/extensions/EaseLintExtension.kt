package com.buildsrc.lint.extensions

import java.util.*

/**
 * 在module的build.gradle中配置 easeLint 参数
 */
open class LintConfigExtension {

    //扫描文件白名单,有些文件始终都不需要被扫描
    var fileWhiteList: LinkedList<String> = LinkedList()

    //扫描文件后缀白名单,指定后缀文件始终都不需要被扫描
    var suffixWhiteList: LinkedList<String> = LinkedList()

    //git diff 配置参数
    var gitDiffConfig = GitDiffConfig()

    /**
     * 设置查找目标文件（git diff）的参数
     *
     * @param compareBranch 对比指定分支，默认对比远程master分支
     * @param compareCommitId 对比指定的提交记录
     */
    fun setGitDiffConfig(
        compareBranch: String,
        compareCommitId: String
    ) {
        gitDiffConfig = GitDiffConfig(compareBranch, compareCommitId)
    }

}

data class GitDiffConfig(
    //通过git获取目标文件，对比指定分支，默认对比远程master分支
    val compareBranch: String = "",

    //通过git获取目标文件，对比指定的提交记录
    val compareCommitId: String = ""
)