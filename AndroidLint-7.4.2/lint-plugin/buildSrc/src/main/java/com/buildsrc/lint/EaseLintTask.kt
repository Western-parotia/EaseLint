package com.buildsrc.lint

import com.android.tools.lint.client.api.LintRequest
import com.android.utils.JvmWideVariable
import com.google.common.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class EaseLintTask : DefaultTask() {
    companion object {
        const val TASK_NAME = "a1EaseLint"
        val targetFiles: JvmWideVariable<ArrayList<String>> =
            JvmWideVariable(
                LintHook.lintRequestClass,
                "targetFiles",
                object : TypeToken<ArrayList<String>>() {}
            ) { ArrayList() }
    }

    @TaskAction
    fun action() {

        val file1 =
            "/Volumes/D/CodeProject/AndroidProject/EaseLint-7.0/AndroidLint-7.4.2/lint-gradle-api/app/src/main/java/com/easelint/gradle/SubModuleKotlinPrint.kt"

        targetFiles.executeCallableSynchronously {
            targetFiles.set(ArrayList<String>().apply {
//                add(file1)
            })
        }


    }

}