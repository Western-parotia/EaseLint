package com.buildsrc.easelint.lint.helper

import com.buildsrc.lint.utils.log
import org.gradle.api.Project

object LintWrapperHelper {

    fun apply27Lib(project: Project, version: String, snapshot: Boolean) {
        val pathBuilder = StringBuilder()
        pathBuilder.append("com.easelint")
        if (snapshot) {
            pathBuilder.append(".snapshot")
        }
        pathBuilder.append(":27.1.0-lint-checks")
        pathBuilder.append(":$version")
        apply(project, pathBuilder.toString())
    }

    fun apply(project: Project, mavenLib: String) {
        "${project.name} apply lib:$mavenLib".log("LintWrapperHelper")
        project.dependencies.add("implementation", mavenLib)
    }

}