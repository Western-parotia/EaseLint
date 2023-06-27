package com.buildsrc.lint

import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import  com.buildsrc.lint.helper.LintConfigExtensionHelper
import org.gradle.api.GradleException

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val libPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        if (libPlugin == null && appPlugin == null) throw GradleException("libPlugin and appPlugin can not all be null")
        val basePlugin = appPlugin ?: libPlugin!!
        LintConfigExtensionHelper.apply(project)
        project.gradle.taskGraph.whenReady {
            // after afterEvaluate
            val task = project.tasks.getByName("lintAnalyzeDebug") as AndroidLintAnalysisTask
            LintHook.loadHookFile(task.lintTool, project)
            // 添加新任务 关联到 lintAnalyzeDebug ，来做准备工作

            val globalConfig = basePlugin.variantManager.globalTaskCreationConfig
            val lint = globalConfig.lintOptions
            val checkOnly = lint.checkOnly
            val disableIssue = lint.disable
        }

    }
}