package com.buildsrc.lint

import com.android.build.gradle.internal.plugins.AppPlugin
import org.gradle.api.Project

class EaseLintTaskHelper {

    fun apply(project: Project) {
        // 这将会在BasePlugin 的task 执行完之后加载
        onProjectAfterEvaluate(project)
    }

    private fun onProjectAfterEvaluate(project: Project) {


    }
}