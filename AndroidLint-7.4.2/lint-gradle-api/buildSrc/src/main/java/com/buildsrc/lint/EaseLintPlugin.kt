package com.buildsrc.lint

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
        if (libPlugin == null && appPlugin == null) throw GradleException("libPlugin and appPlugin both null")

        LintConfigExtensionHelper.apply(project)

        project.afterEvaluate {
            project.tasks.create(
                EaseLintTask.TASK_NAME,
                EaseLintTask::class.java
            )
        }
    }
}