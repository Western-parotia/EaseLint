package com.buildsrc.lint

import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.lint.VariantWithTests
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import org.gradle.api.Plugin
import org.gradle.api.Project
import  com.buildsrc.lint.helper.LintConfigExtensionHelper
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val libPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        if (libPlugin == null && appPlugin == null) throw GradleException("libPlugin and appPlugin both null")
        val basePlugin = appPlugin ?: libPlugin!!
        LintConfigExtensionHelper.apply(project)

        project.gradle.taskGraph.whenReady {
            // after afterEvaluate
            val task = project.tasks.getByName("lintAnalyzeDebug") as AndroidLintAnalysisTask
            val lintTool = task


        }


    }
}