package com.buildsrc.easelint.lint.task

import com.android.build.gradle.tasks.LintBaseTask
import org.gradle.api.Project

class DependencyHelper {
    companion object {
        private var VERSION_LINT_GRADLE = "0.0.1"
        private var VERSION_LINT_WRAPPER = "0.0.1"
        private val DEPENDENCY_LINT_PATH_SNAPSHOT
            get() = "com.easelint:lint-wrapper:$VERSION_LINT_WRAPPER"
        private val DEPENDENCY_LINT_GRADLE_PATH
            get() = "com.easelint:27.1.1-lint-gradle:$VERSION_LINT_GRADLE"

        /**
         * 提前引入 lint gradle，替代系统默认的包
         */
        fun injectLintPatch(project: Project) {
            // 动态获取 lint gradle版本 ,wrapper 版本
            // com.easelint:27.1.1-lint-gradle:0.0.1
            project.dependencies.add(LintBaseTask.LINT_CLASS_PATH, DEPENDENCY_LINT_GRADLE_PATH)
        }
    }
}