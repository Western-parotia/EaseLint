package com.buildsrc.easelint.lint.task

import com.android.build.gradle.internal.dsl.LintOptions
import com.buildsrc.easelint.lint.utils.log
import org.gradle.api.Project
import java.io.File


class LintOptionsInjector {

    companion object {
        private val TAG = LintOptionsInjector::class.java.simpleName
        const val XML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.xml"
        const val HTML_OUTPUT_RELATIVE_PATH = "build/reports/lint-results.html"
        const val BASELINE_RELATIVE_PATH = "lint-baseline.xml"
    }

    /**
     * 每次运行 Lint 时都再次更新lint 扫描规则配置
     * 如果运行在CI上，这里应该动态获取较好
     */
    fun inject(project: Project, lintOptions: LintOptions) {
        /* 判断 os 为 linux 时 处于CI服务器上，动态获取 lintOptions 配置进行覆盖
      if (System.getProperty("os.name").equals("Linux", true)){
                    io
         }
         */
        "========sync Lint options ==========".log(TAG)

        lintOptions.apply {
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

}