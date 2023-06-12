package com.buildsrc.easelint.lint.task

import com.android.build.gradle.internal.cxx.json.jsonStringOf
import com.buildsrc.easelint.lint.helper.LintOptionsInjector
import com.buildsrc.easelint.lint.utils.log
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction
import org.jdom2.input.SAXBuilder
import java.net.URL
import com.buildsrc.easelint.lint.bean.IssueBean

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
        xmlToJson()
    }

    /**
     * 将xml格式的lint报告转换为json格式文件
     *
     */
    private fun xmlToJson() {
        val xmlFile = project.file(LintOptionsInjector.XML_OUTPUT_RELATIVE_PATH)
        if (!xmlFile.exists()) return
        val issueList = arrayListOf<IssueBean>()
        //分析xml
        val rootElement = SAXBuilder().build(xmlFile).rootElement
        rootElement.children.forEach { element ->
            val id = element.getAttributeValue("id")
            val severity = element.getAttributeValue("severity")
            val message = element.getAttributeValue("message")
            val category = element.getAttributeValue("category")
            val priority = element.getAttributeValue("priority")
            val summary = element.getAttributeValue("summary")
            val explanation = element.getAttributeValue("explanation")
            val errorLine1 = element.getAttributeValue("errorLine1")
            val errorLine2 = element.getAttributeValue("errorLine2")
            val location = IssueBean.LocationBean();
            element.children.forEach {
                location.file = it.getAttributeValue("file")
                location.line = it.getAttributeValue("line")
                location.column = it.getAttributeValue("column")
            }
            issueList.add(
                IssueBean(
                    id,
                    severity,
                    message,
                    category,
                    priority,
                    summary,
                    explanation,
                    errorLine1,
                    errorLine2,
                    location
                )
            )
        }
        //保存json文件
        val jsonFile = project.file(LintOptionsInjector.JSON_OUTPUT_RELATIVE_PATH)
        jsonFile.writeText(jsonStringOf(issueList))
        "Json Report: ${jsonFile.absolutePath}".log("lifeTrack____1")
    }
}