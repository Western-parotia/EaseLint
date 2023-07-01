package com.buildsrc.lint.task

import com.android.utils.JvmWideVariable
import com.buildsrc.lint.helper.LintHookHelper
import com.buildsrc.lint.helper.LintSlot
import com.google.common.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class EaseLintTask : DefaultTask() {
    companion object {
        const val TASK_NAME = "a1EaseLint"
        private val targetFiles: JvmWideVariable<ArrayList<String>> =
            JvmWideVariable(LintHookHelper.lintRequestClass,
                "targetFiles",
                object : TypeToken<ArrayList<String>>() {}) { ArrayList() }
    }

    /**
     * 如果没有获取到文件，那么使用一个僵尸文件进行替代，避免project.subset 没有文件而进行全量扫描
     */
    private val zombieFile by lazy {
        var file = File(project.buildDir, "easeLint.zombie")
        if (!file.exists()) {
            val parent = File(project.buildDir.absolutePath)
            parent.mkdir()
            file.createNewFile()
        }
        file.absolutePath
    }

    @TaskAction
    fun action() {
        targetFiles.executeCallableSynchronously {
            targetFiles.set(ArrayList<String>().apply {
                val files = LintSlot.finalTargets(project)
                if (files.isNullOrEmpty()) {
                    add(zombieFile)
                } else {
                    LintSlot.finalTargets(project).forEach {
                        add(it.absolutePath)
                    }
                }

            })
        }
    }

}