package com.buildsrc.easelint.lint.helper

import com.android.build.gradle.tasks.LintBaseTask
import com.buildsrc.easelint.lint.utils.log
import org.gradle.api.Project

object LintGradleHelper {
    private var version = "0.0.1"
    private var group = ""
    private var artifactId = "27.1.0-lint-gradle"

    fun init(snapshot: Boolean, version: String) {
        this.version = version
        group = "com.easelint"
        if (snapshot) {
            group += ".snapshot"
        }
    }

    private val PATH
        get() = "$group:$artifactId:$version"

    /**
     * 提前引入 lint gradle，替代系统默认的包
     * 内部将置换 Lint gradle 扫描文件对功能，支持自定义设置文件清单
     */
    fun injectLintPatch(project: Project) {
        PATH.log("LintGradleHelper:implementation ")
        project.dependencies.add(LintBaseTask.LINT_CLASS_PATH, PATH)
    }


}