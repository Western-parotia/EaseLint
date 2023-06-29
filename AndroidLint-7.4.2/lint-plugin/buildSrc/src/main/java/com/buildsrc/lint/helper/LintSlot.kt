package com.buildsrc.lint.helper

import com.buildsrc.easelint.lint.utils.log
import com.buildsrc.lint.extensions.GitDiffConfig
import com.buildsrc.lint.extensions.LintConfigExtension
import com.buildsrc.lint.task.LintException
import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

/**
 * 与扫描相关的所有参数都应该通过这个 LintSlot 进行修改。
 *
 */
object LintSlot {
    private enum class TaskParams(val paramName: String) {
        FILE_WHITE_LIST("fileWhiteList"),
        SUFFIX_WHITE_LIST("suffixWhiteList"),
        COMPARE_BRANCH("compareBranch"),
        COMPARE_COMMIT_ID("compareCommitId"),
    }

    private const val PARAMS_TYPE_EXTENSION = "set extension params"
    private const val PARAMS_TYPE_TASK = "overwrite task params"

    //扫描文件白名单
    private val fileWhiteList_: LinkedList<String> = LinkedList()
    val fileWhiteList = fileWhiteList_

    //扫描文件后缀白名单
    private val suffixWhiteList_: LinkedList<String> = LinkedList()
    val suffixWhiteList = suffixWhiteList_

    //git筛选文件配置
    private var gitDiffConfig = GitDiffConfig()

    /**
     * 设置在build.gradle中配置的extension参数
     *
     * @param lcg [LintConfigExtension]
     */
    fun setExtensionParams(lcg: LintConfigExtension) {
        LintSlot.addFileWhiteList(lcg.fileWhiteList, PARAMS_TYPE_EXTENSION)
        LintSlot.addSuffixWhiteList(lcg.suffixWhiteList, PARAMS_TYPE_EXTENSION)
        LintSlot.setGitDiffConfig(lcg.gitDiffConfig, PARAMS_TYPE_EXTENSION)
    }

    /**
     * 设置使用命令执行task时配置的参数
     *
     * @param project
     */
    private fun setTaskParams(project: Project) {
        val fileWhiteList = getParamList(project, TaskParams.FILE_WHITE_LIST.paramName)
        if (fileWhiteList.isNotEmpty()) {
            clearWhiteList()
            addFileWhiteList(fileWhiteList, PARAMS_TYPE_TASK)
        }

        val suffixWhiteList = getParamList(project, TaskParams.SUFFIX_WHITE_LIST.paramName)
        if (suffixWhiteList.isNotEmpty()) {
            clearSuffixWhiteList()
            addSuffixWhiteList(suffixWhiteList, PARAMS_TYPE_TASK)
        }

        val compareBranch = getParamsString(project, TaskParams.COMPARE_BRANCH.paramName)
        val compareCommitId = getParamsString(project, TaskParams.COMPARE_COMMIT_ID.paramName)
        if (compareBranch.isNotEmpty() || compareCommitId.isNotEmpty()) {
            setGitDiffConfig(GitDiffConfig(compareBranch, compareCommitId), PARAMS_TYPE_TASK)
        }
    }

    /**
     * 白名单支持文件夹级别，所以为了避免多module 扫描的白名单存在冲突，限制白名单文件路径最少提供4级目录
     * 比如/com/xx/utils 很容易导致范围过大
     * 建议  moduleName/src/main/java/com/xx/xx
     * @param filePaths
     */
    private fun addFileWhiteList(filePaths: List<String>, type: String) {
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
        "fileWhiteList:$fileWhiteList_".log(type)
    }

    fun clearWhiteList() {
        fileWhiteList_.clear()
    }

    private fun addSuffixWhiteList(whiteList: List<String>, type: String) {
        suffixWhiteList_.addAll(whiteList)
        "suffixWhiteList:$suffixWhiteList_".log(type)
    }

    fun clearSuffixWhiteList() {
        suffixWhiteList_.clear()
    }

    private fun setGitDiffConfig(config: GitDiffConfig, type: String) {
        gitDiffConfig = config
        "gitDiffConfig:$gitDiffConfig".log(type)
    }

    fun clearAll() {
        clearWhiteList()
        clearSuffixWhiteList()
    }

    fun finalTargets(project: Project): List<File> {
        //在获取最终检查的文件前，先配置gradlew命令中的参数
        setTaskParams(project)
        val files = LinkedList<File>()
        //在手动配置的文件列表中插入git diff查询出的差异文件
        val targetFiles = addGitDiffTarget(project)
        //先将所有文件路径去重
        targetFiles.distinct().forEach { t ->
            //判断文件白名单
            if (!fileWhiteList.firstOrNull { t.contains(it) }.isNullOrEmpty()) return@forEach
            //判断文件后缀白名单
            if (!suffixWhiteList.firstOrNull { t.endsWith(it) }.isNullOrEmpty()) return@forEach
            val file = File(t)
            if (file.exists()) {
                files.add(file)
            } else {
                "this file[$t] is not exists".log("EaseLintReflectiveLintRunner")
            }
        }
        return files
    }

    /**
     * 获取gradlew命令中配置的list类型参数
     *
     * @param project
     * @param paramName 参数名称
     * @return
     */
    private fun getParamList(project: Project, paramName: String): List<String> {
        return if (project.hasProperty(paramName)) {
            return project.findProperty(paramName)!!.toString().split(",")
        } else {
            emptyList<String>()
        }
    }

    /**
     * 获取gradlew命令中配置的string类型参数
     *
     * @param project
     * @param paramName 参数名称
     * @return
     */
    private fun getParamsString(project: Project, paramName: String): String {
        return if (project.hasProperty(paramName)) {
            return project.findProperty(paramName)!!.toString()
        } else {
            ""
        }
    }

    private fun addGitDiffTarget(project: Project): List<String> {
        if (gitDiffConfig.compareBranch.isEmpty() && gitDiffConfig.compareCommitId.isEmpty())
            return emptyList()
        val targetList = LinkedList<String>()
        val pathList = LinkedList<String>()

        //=============git diff指令开始==============
        //step1.使用配置的commitId，未配置则先查询指定分支最新的commitId
        val commitId = gitDiffConfig.compareCommitId.ifEmpty {
            //待对比分支，未指定则默认使用远程master分支
            val branch = gitDiffConfig.compareBranch.ifEmpty { "origin" }
            //查询指定分支上最新的commitId
            val bos = ByteArrayOutputStream()
            project.rootProject.exec {
                standardOutput = bos
                setCommandLine("git", "log", branch, "--pretty=%h", "--max-count=1")
            }
            val commitList = bos.toString().split("\n")
            bos.close()
            if (commitList.isNotEmpty()) commitList[0] else ""
        }

        //step2.找出和指定commitId之间的差异文件
        val bos = ByteArrayOutputStream()
        project.rootProject.exec {
            standardOutput = bos
            setCommandLine("git", "diff", commitId, "HEAD", "--name-only", "--diff-filter=ACMRTUXB")
        }
        val fileListStr = bos.toString()
        pathList.addAll(fileListStr.split("\n"))
        bos.reset()

        /*
        step3.
        上面的git diff命令只能比较两次提交记录间的差异，适合在CI上执行。
        但是也需要本地执行，因此需要加上对比工作区和上一次提交之间的差异，
        寻找未提交的改动新增文件
         */
        project.exec {
            standardOutput = bos
            setCommandLine("git", "diff", "HEAD", "--name-only", "--diff-filter=ACMRTUXB")
        }
        val cachedFileListStr = bos.toString()
        pathList.addAll(cachedFileListStr.split("\n"))
        bos.close()
        //=============git diff指令结束==============
        //截取项目绝对路径的最后一个文件夹名作为项目名称
        val projectName = project.rootDir.absolutePath.substringAfterLast(File.separator)
        pathList.forEach {
            if (it.isEmpty()) return@forEach
            /*
            从git指令中获取到的文件路径是相对路径，
            路径开头可能和从project.rootDir获取到
            的根目录绝对路径有重复。因此通过项目名称来
            来截取相对路径，去掉多余路径，避免Lint加载
            文件失败。且git diff获取到的文件路径分隔符
            为/，统一替换成当前系统分隔符
             */
            targetList.add(
                "${project.rootDir}${
                    it.substringAfter(projectName).replace("/", File.separator)
                }"
            )
        }
        return targetList
    }
}