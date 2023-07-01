package com.buildsrc.lint.task

import com.buildsrc.lint.utils.log
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction


/**
 * easeLint 任务执行结束的的钩子，用于处理 easeLint执行产物
 * 比如：
 * 1.上传 lint 报告 或发送邮件
 * 2.解析 lint 报告，通知企业 IM
 * <Task 类必须都是 open>
 */
open class TreatEaseLintResultTask : DefaultTask() {
    companion object {
        const val TASK_NAME = "treatEaseLintResult"
    }

    @TaskAction
    fun action() {
        "TreatEaseLintResultTask:action".log("lifeTrack____1")
    }

}