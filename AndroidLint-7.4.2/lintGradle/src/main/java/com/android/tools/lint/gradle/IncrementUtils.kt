package com.android.tools.lint.gradle

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintRequest
import com.google.common.annotations.Beta
import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 增量扫描工具
 *
 */
object IncrementUtils {
    @JvmStatic
    var whiteList = emptyList<String>()

    private var checkFileList: MutableList<File> = mutableListOf()

    @Beta
    fun inject(project: Project, lintRequest: LintRequest) {
        //a1MjLintIncrement和a1MjLintFull判断的名称不能改变，是ci里task的名字
        when {
            project.gradle.startParameter.taskNames.find { it.contains("a1MjLint") } != null -> {
                if (checkFileList.isEmpty()) return

                lintRequest.getProjects()?.forEach { p ->
                    checkFileList.forEach {
                        p.addFile(it)
                    }
                }
            }
            else -> {
                return
            }
        }
    }

    fun hasDiffFiles(project: Project): Boolean {
        //清空待检测文件列表
        checkFileList.clear()
        //a1MjLintIncrement和a1MjLintFull判断的名称不能改变，是ci里task的名字
        when {
            project.gradle.startParameter.taskNames.find { it.contains("a1MjLintIncrement") } != null -> {
                //增量扫描，通过git diff命令获取到新增和改动的文件
                checkIncrement(project)
            }
            project.gradle.startParameter.taskNames.find { it.contains("a1MjLintFull") } != null -> {
                //全量扫描，获取当前提交记录与master分支最后的提交记录之间的差异文件
                checkFull(project)
            }
        }
        //非空就继续执行lint检查，否则直接停止
        return checkFileList.isNotEmpty()
    }

    private fun checkIncrement(project: Project) {
        printSplitLine("Lint Increment Info START")
        //找出增量文件，配置给Lint
        val bos = ByteArrayOutputStream()
        project.exec {
            it.standardOutput = bos
            it.setCommandLine("git", "diff", "--cached", "--name-only", "--diff-filter=ACMRTUXB")
        }
        val diffFileStr = bos.toString()
        val diffFileList = diffFileStr.split("\n")
        bos.close()
        diffFileList.forEach {
            if (it.isEmpty()) return@forEach
            val file = File(project.rootProject.rootDir, it)
            if (isIgnoreFile(file)) {
                println("ignore-file:${file.absolutePath}")
            } else {
                checkFileList.add(file)
                println("target-file:${file.absolutePath}")
            }
        }
        printSplitLine("Lint Increment Info END")
    }

    private fun checkFull(project: Project) {
        printSplitLine("Lint Full Info START")
        //查询master分支上最新的commitId
        val commitIdOutput = ByteArrayOutputStream()
        project.rootProject.exec {
            it.standardOutput = commitIdOutput
            it.setCommandLine("git", "log", "origin/master", "--pretty=%h", "--max-count=1")
        }
        val commitList = commitIdOutput.toString().split("\n")
        val commitId = if (commitList.isNotEmpty()) commitList[0] else ""
        println("commitId:$commitId")
        commitIdOutput.close()

        //找出项目中所有文件，指定的commit和最新的commit之间的差异文件
        val bos = ByteArrayOutputStream()
        project.rootProject.exec {
            it.standardOutput = bos
            it.setCommandLine(
                "git",
                "diff",
                commitId,
                "HEAD",
                "--name-only",
                "--diff-filter=ACMRTUXB"
            )
        }
        val fileListStr = bos.toString()
        val fileList = fileListStr.split("\n")
        bos.close()

        fileList.forEach {
            if (it.isEmpty()) return@forEach
            val file = File(project.rootProject.rootDir, it)
            if (isIgnoreFile(file)) {
                println("ignore-file:${file.absolutePath}")
            } else {
                checkFileList.add(file)
                println("target-file:${file.absolutePath}")
            }
        }
        printSplitLine("Lint Full Info End")
    }

    private fun isIgnoreFile(file: File): Boolean {
        //只检查java、kt和xml文件
        if (file.absolutePath.endsWith(SdkConstants.DOT_JAVA)
            || file.absolutePath.endsWith(SdkConstants.DOT_KT)
            || file.absolutePath.endsWith(SdkConstants.DOT_XML)
        ) {
            //在白名单中，忽略该文件
            if (whiteList.isNotEmpty()) {
                whiteList.forEach {
                    if (file.absolutePath.contains(it)) return true
                }
            }
            return false
        }
        return true
    }

    /**
     * 添加筛选文件的白名单
     *
     * @param list
     */
    @JvmStatic
    fun addWhiteList(list: List<String>) {
        whiteList = list
    }

    /**
     * 打印设置的白名单和开始时间，用于测试反射调用addWhiteList()和addStartTime()后的结果
     *
     */
    @JvmStatic
    fun printIgnoreInfo() {
        printSplitLine("printIgnoreInfo")
        println("whiteList:$whiteList")
        printSplitLine("printIgnoreInfo END")
    }

    fun printSplitLine(tag: String) {
        println("==================$tag======================")
    }
}




