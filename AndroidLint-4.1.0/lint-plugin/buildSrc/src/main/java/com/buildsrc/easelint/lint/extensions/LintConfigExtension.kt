package com.buildsrc.easelint.lint.extensions

import com.buildsrc.easelint.lint.task.LintException
import org.gradle.api.Project
import java.io.File

open class LintConfigExtension {
    var baseline = false

    //文件白名单
    val fileWhiteList: MutableList<String> = mutableListOf()

    //issue黑名单
    var issueDisableList = emptyList<String>()

    var targetFiles = emptyList<String>()

    /**
     * 输入可变参为文件路径，例如 buildSrc/src/main/java/com/ci/plugin/lint/LintIncrementPlugin
     *
     * @param filePaths
     */
    fun setFileWhiteList(vararg filePaths: String) {
        filePaths.forEach {
            val itemPath = it.split("/")
            if (itemPath.size < 4) {
                //路径小于4级，不规范
                throw LintException("Lint Config: file path illegal: $it")
            } else {
                //路径规范，添加至白名单
                fileWhiteList.add(it.replace("/", File.separator))
            }
        }
    }

}

object LintConfigExtensionHelper {
    fun findLintConfigExtension(project: Project): LintConfigExtension {
        val target = project.extensions.getByName(ExtensionHelper.EXTENSION_LINT_CONFIG)
        return target as LintConfigExtension
    }
}