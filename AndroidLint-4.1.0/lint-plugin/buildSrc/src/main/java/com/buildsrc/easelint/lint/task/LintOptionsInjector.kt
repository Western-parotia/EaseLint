package com.buildsrc.easelint.lint.task

import com.android.build.gradle.internal.dsl.LintOptions
import com.buildsrc.easelint.lint.extensions.LintConfigExtensionHelper
import org.gradle.api.Project
import java.io.File


class LintOptionsInjector {

    companion object {
        const val XML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.xml"
        const val HTML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.html"
        const val BASELINE_RELATIVE_PATH = "lint-baseline.xml"
        private var lintConfigRes: LintConfigRes? = null
    }

    /**
     * 判断运行环境，确认是否需要 设置 check only
     * 需要则拉取check only 配置文件 进行设置
     */
    fun inject(project: Project, lintOptions: LintOptions) {
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
            val lcg = LintConfigExtensionHelper.findLintConfigExtension(project)
            val issueDisableList = lcg.issueDisableList
            disable(*issueDisableList.toTypedArray())
            xmlOutput = File(XML_OUTPUT_RELATIVE_PATH)//指定xml输出目录
            htmlOutput = File(HTML_OUTPUT_RELATIVE_PATH)//指定html输出目录
            isWarningsAsErrors = false//返回lint是否应将所有警告视为错误
            isAbortOnError = false//发生错误停止task执行 默认true
            //不需要baseline
//            if (lcg.baseline) {
//                baselineFile = project.file(BASELINE_RELATIVE_PATH)//创建警告基准
//            }
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