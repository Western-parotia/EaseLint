@file:Suppress("UnstableApiUsage")

package com.easelint.utils

import com.android.tools.lint.detector.api.*
import java.util.*

/**
 * @param implementationScope 检测类型，如：[Scope.MANIFEST_SCOPE]
 * @param id 注册id，尽量以Detector结尾
 */
inline fun <reified T : Detector> Issue.Companion.createWithMj(
    briefDescription: String,
    explanation: String,
    implementationScope: EnumSet<Scope>,
    id: String = T::class.java.simpleName,
    category: Category = Category.create("风险代码检测", 110),
    priority: Int = 5,
    severity: Severity = Severity.ERROR
) = create(
    id = id,
    briefDescription = briefDescription,
    explanation = explanation,
    category = category,
    priority = priority,
    severity = severity,
    implementation = Implementation(T::class.java, implementationScope)
)