package com.buildsrc.easelint.lint.helper

import org.gradle.api.Project

object LintWrapperHelper {
    private var VERSION_LINT_WRAPPER = "0.0.1"

    private val DEPENDENCY_LINT_PATH
        get() = "com.easelint:lint-wrapper:$VERSION_LINT_WRAPPER"


    fun apply(project: Project) {
        // 动态获取 check

        project.dependencies.add("implementation", DEPENDENCY_LINT_PATH)
    }

}