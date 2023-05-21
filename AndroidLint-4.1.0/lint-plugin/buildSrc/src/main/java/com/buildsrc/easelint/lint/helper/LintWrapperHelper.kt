package com.buildsrc.easelint.lint.helper

import com.buildsrc.easelint.lint.utils.log
import org.gradle.api.Project

object LintWrapperHelper {
    // 动态更新可采取反射修改 版本号
    private var VERSION_LINT_WRAPPER = "0.0.1"

    private val DEPENDENCY_LINT_PATH
        get() = "com.easelint:lint-wrapper:$VERSION_LINT_WRAPPER"


    fun apply(project: Project) {
        // 加载自定义的 Lint rules
        DEPENDENCY_LINT_PATH.log("LintWrapperHelper add LINT_WRAPPER")
        project.dependencies.add("implementation", DEPENDENCY_LINT_PATH)
    }

}