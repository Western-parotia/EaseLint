package com.buildsrc.lint

import com.android.Version
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintMode
import java.util.Collections

/**
easeLintTask 每次执行都是针对指定文件，且为主动执行，
不启用 @input,@output 等引入增量，这让执行结果永远不受，编译与gradle 缓存影响。
再无异常的情况下，通常也不会产生二次扫描，那么始终保持执行过程与结果是纯净的比象征性的提高
lint扫描速度要恰当
 */

abstract class EaseLintTask : AndroidLintAnalysisTask() {
    companion object {
        const val TASK_NAME = "easeLint"
    }

    override fun doTaskAction() {
        val parent =
            project.tasks.findByName("lintAnalyze") as AndroidLintAnalysisTask
        parent.lintTool.submit(
            mainClass = "com.android.tools.lint.Main",
            workerExecutor = workerExecutor,
            arguments = generateCommandLineArguments(parent),
            android = android.get(),
            fatalOnly = fatalOnly.get(),
            await = false,
            lintMode = LintMode.ANALYSIS
        )
    }

    private fun Collection<String>.asLintPaths() = joinToString(separator = ";", postfix = ";")

    private fun MutableList<String>.add(arg: String, value: String) {
        add(arg)
        add(value)
    }

    private fun generateCommandLineArguments(parent: AndroidLintAnalysisTask): List<String> {

        val arguments = mutableListOf<String>()

        arguments += "--analyze-only"
        if (parent.fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        arguments += listOf("--jdk-home", parent.systemPropertyInputs.javaHome.get())
        parent.androidSdkHome.orNull?.let { arguments.add("--sdk-home", it) }

        /*
        不设置model，在Main.java 中的逻辑: model 与 file list 不能同时存在，且在model 为null
        时才会扫描特殊指定的 file 集合
        要制定自己的扫描文件，只需要直接在集合末尾 追加 file 绝对路径即可。
        */
//        arguments += "--lint-model"
//        arguments += listOf(parent.lintModelDirectory.get().asFile.absolutePath).asLintPaths()

        for (check in parent.checkOnly.get()) {
            arguments += listOf("--check", check)
        }

        val rules = parent.lintRuleJars.files.filter { it.isFile }.map { it.absolutePath }
        if (rules.isNotEmpty()) {
            arguments += "--lint-rule-jars"
            arguments += rules.asLintPaths()
        }
        if (parent.printStackTrace.get()) {
            arguments += "--stacktrace"
        }
        arguments += parent.lintTool.initializeLintCacheDir()

        // Pass information to lint using the --client-id, --client-name, and --client-version flags
        // so that lint can apply gradle-specific and version-specific behaviors.
        arguments.add("--client-id", "gradle")
        arguments.add("--client-name", "AGP")
        arguments.add("--client-version", Version.ANDROID_GRADLE_PLUGIN_VERSION)

        // Pass --offline flag only if lint version is 30.3.0-beta01 or higher because earlier
        // versions of lint don't accept that flag.
//        if (offline.get()
//            && GradleVersion.tryParse(lintTool.version.get())
//                ?.isAtLeast(30, 3, 0, "beta", 1, false) == true) {
//            arguments += "--offline"
//        }
        return Collections.unmodifiableList(arguments)
    }
}