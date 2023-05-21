package com.buildsrc.easelint.lint.task

import com.android.build.gradle.internal.dsl.LintOptions
import com.buildsrc.easelint.lint.extensions.LintConfigExtension
import com.buildsrc.easelint.lint.extensions.ExtensionHelper

import org.gradle.api.Project
import java.io.File


class LintOptionsInjector {

    companion object {
        const val XML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.xml"
        const val HTML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.html"
        const val BASELINE_RELATIVE_PATH = "lint-baseline.xml"
        const val CHECK_ONLY_CONFIG_URL =
            "http://xpxsrcapp.51xpx.com/lintReport/lint_check_only_config.json"
        var lintConfigRes: LintConfigRes? = null
    }

    fun inject(project: Project, lintOptions: LintOptions) {
        //只有流水线执行时需要设置check only
        if (System.getProperty("os.name").equals("Linux", true)) {
            println("========Lint options injector==========")
            val checkOnlyList = arrayListOf<String>()
            lintConfigRes?.let { res ->
                res.checkOnlyList.forEach {
                    if (it.enable == 1 && it.issueId.isNotEmpty()) checkOnlyList.add(it.issueId)
                    println("issue:${it.issueId}--enable:${it.enable == 1}")
                }
                if (checkOnlyList.isNotEmpty()) {
                    lintOptions.checkOnly(*checkOnlyList.toTypedArray())
                }
            }
        }

        lintOptions.apply {
            val issueDisableList =
                (project.extensions.getByName(ExtensionHelper.EXTENSION_LINT_CONFIG) as LintConfigExtension).issueDisableList
            disable(*issueDisableList.toTypedArray())
            xmlOutput = File(XML_OUTPUT_RELATIVE_PATH)//指定xml输出目录
            htmlOutput = File(HTML_OUTPUT_RELATIVE_PATH)//指定html输出目录
            isWarningsAsErrors = false//返回lint是否应将所有警告视为错误
            isAbortOnError = false//发生错误停止task执行 默认true
            if ((project.extensions.getByName(ExtensionHelper.EXTENSION_LINT_CONFIG) as LintConfigExtension).baseline) {
                baselineFile = project.file(BASELINE_RELATIVE_PATH)//创建警告基准
            }
        }
    }

    data class LintConfigRes(
        val checkOnlyList: ArrayList<Issue> = arrayListOf(),
        val testBranch: String = ""
    ) {
        data class Issue(
            val issueId: String = "",
            val enable: Int = 0//0关闭 1启用
        )
    }
}