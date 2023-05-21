package com.buildsrc.easelint.lint.helper

import com.android.build.gradle.tasks.LintBaseTask
import org.gradle.api.Project

object LintGradleHelper {
    private var VERSION_LINT_GRADLE = "0.0.1"

    private val DEPENDENCY_LINT_GRADLE_PATH
        get() = "com.easelint:27.1.1-lint-gradle:$VERSION_LINT_GRADLE"

    /**
     * 提前引入 lint gradle，替代系统默认的包
     * 内部将置换 Lint gradle 扫描文件对功能，支持自定义设置文件清单
     */
    fun injectLintPatch(project: Project) {
        // 动态获取 lint gradle版本 ,wrapper 版本
        // com.easelint:27.1.1-lint-gradle:0.0.1
        project.dependencies.add(LintBaseTask.LINT_CLASS_PATH, DEPENDENCY_LINT_GRADLE_PATH)
    }


}