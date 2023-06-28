package com.buildsrc.lint

import com.android.utils.JvmWideVariable
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

    private fun filePath(name: String): String {
        return project.projectDir.absolutePath + "/src/main/java/com/easelint/gradle/" + name
    }

    @TaskAction
    fun action() {

        val file1 = filePath("JavaLog.java")
        val file2 = filePath("JavaParse.java")
        val file3 = filePath("KotlinParse.kt")
        val file4 = filePath("KotlinLog.kt")

        targetFiles.executeCallableSynchronously {
            targetFiles.set(ArrayList<String>().apply {
                add(file1)
                add(file2)
                add(file3)
                add(file4)
            })
        }


    }

}