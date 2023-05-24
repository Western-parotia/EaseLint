package com.buildsrc.easelint.lint.helper

import com.buildsrc.easelint.lint.extensions.LintConfigExtension
import org.gradle.api.Project

object LintConfigExtensionHelper {
    const val EXTENSION_EASELINT = "easeLintExt"

    fun apply(project: Project) {
        project.extensions.create(EXTENSION_EASELINT, LintConfigExtension::class.java)
    }

    fun findLintConfigExtension(project: Project): LintConfigExtension {
        val target = project.extensions.getByName(EXTENSION_EASELINT)
        return target as LintConfigExtension
    }
}