package com.android.tools.lint.gradle
object Constant{
    // 以下的 lint task 名称 将与 lint plugin 中的 task 名称 对应

    const val LINT_TASK_NAME_PREFIX = "easeLint"
    // 全量扫描
    const val LINT_TASK_FULL = "${LINT_TASK_NAME_PREFIX}Full"
    // 靶向扫描
    const val LINT_TASK_TARGET = "${LINT_TASK_NAME_PREFIX}Target"
}
