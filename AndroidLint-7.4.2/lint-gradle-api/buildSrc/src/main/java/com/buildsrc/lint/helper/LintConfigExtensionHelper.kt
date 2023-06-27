package com.buildsrc.lint.helper

import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Project
import  com.buildsrc.lint.extensions.LintConfigExtension

object LintConfigExtensionHelper {
    private const val NAME = "easeLintExt"

    fun apply(project: Project) {
        project.extensions.create(NAME, LintConfigExtension::class.java)
    }

    fun findLintConfigExtension(project: Project): LintConfigExtension {
        val target = project.extensions.getByName(NAME)
        return target as LintConfigExtension
    }


    fun setCoverLintOptions(project: Project) {
        val libPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        val basePlugin = appPlugin ?: libPlugin!!

        val globalConfig = basePlugin.variantManager.globalTaskCreationConfig
        /*
        这里是 在 app module 中的配置，在这里进行修改
            lint {
        abortOnError = false
        xmlReport = true
        htmlReport = true
        textReport = false
        disable.add("LogDetector")
        checkOnly.add("ParseStringDetector")
        checkOnly.add("LogDetector")
    }
         */
        val lint = globalConfig.lintOptions
    }
}