package com.buildsrc.easelint.lint.extensions

import org.gradle.api.Project

class ExtensionHelper {

    companion object {
        const val EXTENSION_LINT_CONFIG = "EaseLintExtensions"
    }

    fun apply(project: Project) {
        project.extensions.create(EXTENSION_LINT_CONFIG, LintConfigExtension::class.java)
    }
}