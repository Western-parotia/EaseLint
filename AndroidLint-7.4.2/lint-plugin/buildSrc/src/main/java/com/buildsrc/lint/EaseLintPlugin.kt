package com.buildsrc.lint

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import  com.buildsrc.lint.helper.LintConfigExtensionHelper
import com.buildsrc.lint.helper.LintSlot
import com.buildsrc.lint.task.EaseLintTask
import com.buildsrc.lint.helper.LintHookHelper
import com.buildsrc.lint.task.TreatEaseLintResultTask
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.configure
import com.buildsrc.easelint.lint.helper.LintWrapperHelper

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val libPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        if (libPlugin == null && appPlugin == null) throw GradleException("libPlugin and appPlugin can not all be null")

        LintConfigExtensionHelper.apply(project)

        // 修改checkOnly
        project.configure<BaseAppModuleExtension> {
            lint.checkOnly.add("LogDetector")
            lint.checkOnly.add("ParseStringDetector")
            // disable 优先级最高
//            lint.disable.add("LogDetector")

        }

        project.afterEvaluate {

            LintWrapperHelper.apply27Lib(this, "0.0.1-2023-05-24-10-18-01", true)

            val lcg = LintConfigExtensionHelper.findLintConfigExtension(project)
            LintSlot.setExtensionParams(lcg)

            val buildType = "Debug"

            val lintAnalyzeDebug =
                project.tasks.getByName("lintAnalyze$buildType") as AndroidLintAnalysisTask
            LintHookHelper.loadHook(
                lintAnalyzeDebug.lintTool,
                project,
                "0.0.1-2023-06-28-06-30-10"
            )

            // 添加新任务 关联到 lintAnalyzeDebug ，来做准备工作
            val lintConfigTask = project.tasks.register(
                EaseLintTask.TASK_NAME, EaseLintTask::class.java,
            )
            lintConfigTask.get().finalizedBy("lint$buildType")
            //添加处理最终结果任务
            val treatEaseLintResultTask = project.tasks.register(
                TreatEaseLintResultTask.TASK_NAME,
                TreatEaseLintResultTask::class.java
            )
            project.tasks.getByName("lintReport$buildType").finalizedBy(treatEaseLintResultTask)
        }
    }
}
