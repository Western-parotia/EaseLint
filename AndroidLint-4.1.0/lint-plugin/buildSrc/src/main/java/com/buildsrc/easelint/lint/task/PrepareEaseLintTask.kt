package com.buildsrc.easelint.lint.task

import com.buildsrc.easelint.lint.helper.LintGradleHelper
import com.buildsrc.easelint.lint.helper.LintWrapperHelper
import com.buildsrc.easelint.lint.utils.log
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction

/**
 * easeLint 任务执行前的钩子，用于获取动态配置，并设置参数
 * 比如：
 * 1.获取lint-gradle,lint-checks 库的目标版本号（不建议用last等默认下载最新版本的配置，
 * 这对排查问题会造成极大的阻力）
 * 2.获取 issue 清单配置
 * 3.读取git 记录，挑选本次需要扫描的文件清单
 *
 * 在 easelint 运行前 都可以通过修改 [com.buildsrc.easelint.lint.helper.LintSlot]
 * 来动态管理本次扫描的配置
 *
 * <Task 类必须都是 open>
 */
open class PrepareEaseLintTask : DefaultTask() {
    companion object {
        const val TASK_NAME = "prepareEaseLint"
    }

    @TaskAction
    fun action() {
        "PrepareEaseLintTask:action".log("lifeTrack____1")
//        LintSlot.xxx
    }

}