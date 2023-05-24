package com.buildsrc.easelint.lint.helper

import com.android.build.gradle.internal.dsl.LintOptions
import org.gradle.api.Project
import java.io.File


class LintOptionsInjector {

    companion object {
        private val TAG = LintOptionsInjector::class.java.simpleName
        const val XML_OUTPUT_RELATIVE_PATH = "build/easeLintReports/lint-results.xml"
        const val HTML_OUTPUT_RELATIVE_PATH = "build/easeLintReports/lint-results.html"
        const val BASELINE_RELATIVE_PATH = "lint-baseline.xml"
    }

    /**
     * 每次运行 Lint 时都再次更新lint 扫描规则配置
     * 如果运行在CI上，这里应该动态从网络获取较好
     * project: Project 作为运行时核心上下文对象，这里作为保留参数传入
     */
    fun inject(project: Project, lintOptions: LintOptions) {
        lintOptions.apply {
            xmlOutput = File(XML_OUTPUT_RELATIVE_PATH)//指定xml输出目录
            htmlOutput = File(HTML_OUTPUT_RELATIVE_PATH)//指定html输出目录
            isWarningsAsErrors = false//返回lint是否应将所有警告视为错误
            isAbortOnError = false//发生错误停止task执行 默认true
            //不需要baseline
//            if (lcg.baseline) {
//                baselineFile = project.file(BASELINE_RELATIVE_PATH)//创建警告基准
//            }
            disable(*LintSlot.disableIssues.toTypedArray())
            checkOnly(*LintSlot.checkOnlyIssues.toTypedArray())
        }
    }

}