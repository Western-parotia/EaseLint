package com.buildsrc.lint

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import  com.buildsrc.lint.helper.LintConfigExtensionHelper
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.configure

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
            val lintAnalyzeDebug =
                project.tasks.getByName("lintAnalyzeDebug") as AndroidLintAnalysisTask
            LintHook.loadHook(lintAnalyzeDebug.lintTool, project, "0.0.1-2023-06-28-06-30-10")

            // 添加新任务 关联到 lintAnalyzeDebug ，来做准备工作
            val lintConfigTask = project.tasks.register(
                EaseLintTask.TASK_NAME, EaseLintTask::class.java,
            )
            lintConfigTask.get().finalizedBy("lintDebug")
        }
    }
}
