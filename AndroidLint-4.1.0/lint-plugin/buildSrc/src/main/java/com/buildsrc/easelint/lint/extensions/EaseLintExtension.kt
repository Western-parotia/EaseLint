package com.buildsrc.easelint.lint.extensions

import org.gradle.api.Project


object LintConfigExtensionHelper {
    const val EXTENSION_EASELINT = "easeLintExt"

    fun apply(project: Project) {
        project.extensions.create(EXTENSION_EASELINT, LintConfigExtension::class.java)
    }

    fun findLintConfigExtension(project: Project): LintConfigExtension {
        val target = project.extensions.getByName(EXTENSION_EASELINT)
        return target as LintConfigExtension
    }
}

open class LintConfigExtension {

    //扫描目标，统一为文件全路径
    var targetFiles: HashSet<String> = hashSetOf()

    //需要关闭的 issue 清单，部署到CI时用与快速降级，快速停用个别异常issue，优先级最高
    var issueDisableList: HashSet<String> = hashSetOf()

    // 用于定向控制所有的 issue ,主要用于上线自己开发的 Issue
    var checkOnlyConfig: HashSet<String> = hashSetOf()

    //扫描文件白名单
    var fileWhiteList: HashSet<String> = hashSetOf()

}

