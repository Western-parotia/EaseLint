package com.easelint.registry

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.easelint.detector.*

@Suppress("UnstableApiUsage")
class Registry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
            LogDetector.ISSUE_LOG,
            SerializationDetector.ISSUE_SERIALIZATION_MEMBERS,
            ManifestScreenOrientationDetector.ISSUE_MANIFEST_ORIENTATION,
            LayoutXmlDetector.ISSUE_VIEW_ID,
            LayoutXmlDetector.ISSUE_RELATIVE_LAYOUT,
            ParseDetector.ISSUE_PARSE_COLOR,
            ParseDetector.ISSUE_PARSE_STRING,
            SuppressWarningsDetector.ISSUE_SUPPRESS_ANNOTATION
        )
    override val api: Int
        get() = CURRENT_API

    override val minApi: Int
        get() = 8 // works with Studio 4.1 or later; see com.android.tools.lint.detector.api.Api / ApiKt
}