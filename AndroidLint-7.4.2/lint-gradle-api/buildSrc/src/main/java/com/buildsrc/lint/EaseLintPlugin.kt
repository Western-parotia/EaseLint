package com.buildsrc.lint

import com.android.tools.lint.EaseLintMain
import org.gradle.api.Plugin
import org.gradle.api.Project

class EaseLintPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val args = ArrayList<String>()
        args.add("--analyze-only")
        args.add("--jdk-home")
        args.add("/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home")
        args.add("--sdk-home")
        args.add("/Volumes/D/android/sdk")
        // 移除 lint-model 后才可以添加自定义的 文件
//        args.add("--lint-model")
//        args.add("/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/build/intermediates/incremental/lintAnalyzeDebug;")
        args.add("--lint-rule-jars")
        args.add("/Users/jzxs/.gradle/caches/transforms-3/737e881512efda2c04d20269a1c44f8c/transformed/27.1.0-lint-checks-0.0.1-2023-06-20-05-03-30/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/749f701bd74d06f8e03fa54fb88521f3/transformed/appcompat-1.4.1/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/5bafd71c06de8867cf782e01da2f3540/transformed/fragment-1.3.6/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/bc619fddcf1f55c3d85aa591697a0e30/transformed/activity-1.2.4/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/9ce4705938b0eefe5c6a603c4f1bcc86/transformed/startup-runtime-1.0.0/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/703a34c31fe8ddd2db7aeef18545697d/transformed/annotation-experimental-1.1.0/jars/lint.jar;")
        args.add("--cache-dir")
        args.add("/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/build/intermediates/lint-cache")
        args.add("--client-id")
        args.add("AGP")
        args.add("--client-version")
        args.add("7.4.2")
        args.add("/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/src/main/java/com/easelint/gradle/KotlinParse.kt")
        args.add("/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/src/main/java/com/easelint/gradle/JavaParse.java")
        project.afterEvaluate {
            tasks.register("easeLint").get().doLast {
                val result = EaseLintMain().run(args.toTypedArray())
                println("easeLint result:$result")
            }
        }
    }
}
// args:
/*
0 = "--analyze-only"
1 = "--jdk-home"
2 = "/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home"
3 = "--sdk-home"
4 = "/Volumes/D/android/sdk"
5 = "--lint-model"
6 = "/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/build/intermediates/incremental/lintAnalyzeDebug;"
7 = "--lint-rule-jars"
8 = "/Users/jzxs/.gradle/caches/transforms-3/737e881512efda2c04d20269a1c44f8c/transformed/27.1.0-lint-checks-0.0.1-2023-06-20-05-03-30/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/749f701bd74d06f8e03fa54fb88521f3/transformed/appcompat-1.4.1/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/5bafd71c06de8867cf782e01da2f3540/transformed/fragment-1.3.6/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/bc619fddcf1f55c3d85aa591697a0e30/transformed/activity-1.2.4/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/9ce4705938b0eefe5c6a603c4f1bcc86/transformed/startup-runtime-1.0.0/jars/lint.jar;/Users/jzxs/.gradle/caches/transforms-3/703a34c31fe8ddd2db7aeef18545697d/transformed/annotation-experimental-1.1.0/jars/lint.jar;"
9 = "--cache-dir"
10 = "/Volumes/D/PersonalAndroidProject/EaseLint_7.0/AndroidLint-7.4.2/lint-gradle-api/app/build/intermediates/lint-cache"
11 = "--client-id"
12 = "gradle"
13 = "--client-name"
14 = "AGP"
15 = "--client-version"
16 = "7.4.2"

 */