package com.buildsrc.easelint.lint.helper

import com.buildsrc.easelint.lint.utils.log
import org.gradle.api.Project

object LintWrapperHelper {
    private var version = "0.0.1"
    private var group = "com.easelint.snapshot"
    private var artifactId = "lint-checks"

    fun init(snapshot: Boolean, version: String) {
        this.version = version
        group = "com.easelint"
        if (snapshot) {
            group += ".snapshot"
        }
    }

    private val PATH
        get() = "$group:$artifactId:$version"

    fun apply(project: Project) {
        // 加载自定义的 Lint rules
        PATH.log("LintWrapperHelper:implementation ")
        project.dependencies.add("implementation", PATH)
    }

}