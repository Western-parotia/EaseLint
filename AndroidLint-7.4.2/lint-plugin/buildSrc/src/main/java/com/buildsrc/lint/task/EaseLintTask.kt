package com.buildsrc.lint.task

import com.android.utils.JvmWideVariable
import com.buildsrc.lint.helper.LintSlot
import com.google.common.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class EaseLintTask : DefaultTask() {
    companion object {
        const val TASK_NAME = "a1EaseLint"
        private val targetFiles: JvmWideVariable<ArrayList<String>> =
            JvmWideVariable(
                LintHook.lintRequestClass,
                "targetFiles",
                object : TypeToken<ArrayList<String>>() {}
            ) { ArrayList() }
    }

    @TaskAction
    fun action() {
        targetFiles.executeCallableSynchronously {
            targetFiles.set(ArrayList<String>().apply {
                LintSlot.finalTargets(project).forEach {
                    add(it.absolutePath)
                }
            })
        }
    }

}