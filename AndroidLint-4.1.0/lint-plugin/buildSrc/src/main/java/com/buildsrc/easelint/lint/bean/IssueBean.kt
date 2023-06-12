package com.buildsrc.easelint.lint.bean

/**
 * @describe:
 * @author: ljj
 * @creattime: 2023/6/12 12:13
 *
 */
data class IssueBean(
    val id: String,
    val severity: String,
    val message: String,
    val category: String,
    val priority: String,
    val summary: String,
    val explanation: String,
    val errorLine1: String,
    val errorLine2: String,
    val location: LocationBean
) {
    data class LocationBean(
        var file: String = "",
        var line: String = "",
        var column: String = ""
    )
}