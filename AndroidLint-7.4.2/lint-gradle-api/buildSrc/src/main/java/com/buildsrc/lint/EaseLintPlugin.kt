package com.buildsrc.lint

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import  com.buildsrc.lint.helper.LintConfigExtensionHelper
import org.gradle.api.GradleException

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        LintConfigExtensionHelper.apply(project)

        val ext =
            project.extensions.getByName("androidComponents") as ApplicationAndroidComponentsExtension
        ext.finalizeDsl { appExt ->
            appExt.buildTypes.create("staging").let { buildType ->
                buildType.initWith(appExt.buildTypes.getByName("debug"))
            }
        }

        project.afterEvaluate {
            val appPlugin1 = project.plugins.findPlugin("com.android.application")

            val appPlugin2 = project.plugins.getPlugin(AppPlugin::class.java)

//             EaseLintTask 继承自 AndroidLintAnalysisTask
            val easeLintTask = project.tasks.create(
                EaseLintTask.TASK_NAME,
                EaseLintTask::class.java
            )
        }
    }
}