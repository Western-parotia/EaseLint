package com.buildsrc.easelint.lint.extensions

import com.buildsrc.easelint.lint.task.LintException
import org.gradle.api.Project
import java.io.File

object LintConfigExtensionHelper {
    const val EXTENSION_LINT_CONFIG = "EaseLintExtensions"

    fun apply(project: Project) {
        project.extensions.create(EXTENSION_LINT_CONFIG, LintConfigExtension::class.java)
    }

    fun findLintConfigExtension(project: Project): LintConfigExtension {
        val target = project.extensions.getByName(EXTENSION_LINT_CONFIG)
        return target as LintConfigExtension
    }
}
open class LintConfigExtension {
    //需要关闭的 issue 清单，部署到CI时用与快速降级，快速停用个别异常issue
    var issueDisableList: MutableList<String> = mutableListOf()

    //扫描文件白名单
    val fileWhiteList: MutableList<String> = mutableListOf()

    //扫描目标，统一为文件全路径
    var targetFiles: MutableList<String> = mutableListOf()

    /**
     * 为了避免多module 扫描的白名单存在冲突，限制白名单文件路径最少提供4级目录
     *
     * @param filePaths
     */
    fun setFileWhiteList(vararg filePaths: String) {
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
                fileWhiteList.add(it.replace("/", File.separator))
            }
        }
    }

}

