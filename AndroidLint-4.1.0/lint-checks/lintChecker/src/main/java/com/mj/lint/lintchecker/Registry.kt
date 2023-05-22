package com.mj.lint.lintchecker

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * 注册表配置
 * create by zhusw on 11/1/21 17:13
 */
class Registry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            LogDetector.ISSUE_LOG,
            SerializationDetector.ISSUE_SERIALIZATION_MEMBERS,
            MjManifestDetector.ISSUE_MANIFEST_ORIENTATION,
            MjXmlDetector.ISSUE_VIEW_ID,
            MjXmlDetector.ISSUE_RELATIVE_LAYOUT,
            DataClassDetector.ISSUE_DATA_CLASS,
            DataClassDetector.ISSUE_RETROFIT_GENERICS,
            ParseDetector.ISSUE_PARSE_COLOR,
            ParseDetector.ISSUE_PARSE_STRING,
            SuppressWarningsDetector.ISSUE_SUPPRESS_ANNOTATION
        )
    override val api: Int
        get() = CURRENT_API
}