package com.buildsrc.easelint.lint.extensions

import java.util.*
/**
 * 在module的build.gradle中配置 easeLint 参数
 */
open class LintConfigExtension {

    //扫描目标，统一为文件全路径
    var targetFiles: LinkedList<String> = LinkedList()

    //需要关闭的 issue 清单，部署到CI时用与快速降级，快速停用个别异常issue，优先级最高
    var disableIssues: LinkedList<String> = LinkedList()

    // 用于定向控制所有的 issue ,主要用于上线自己开发的 Issue
    var checkOnlyIssues: LinkedList<String> = LinkedList()

    //扫描文件白名单,有些文件始终都不需要被扫描
    var fileWhiteList: LinkedList<String> = LinkedList()

}

