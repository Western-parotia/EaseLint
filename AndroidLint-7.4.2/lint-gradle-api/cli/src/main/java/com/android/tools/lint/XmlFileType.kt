/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint

/** Enum representing the different types of XML files lint uses. */
enum class XmlFileType(private val typeName: String) {
    /**
     * This report is intended to be used for normal reporting,
     * processing by other tools such as a CI server creating a
     * visualization or other analysis of the issues (for example, the
     * Jenkins lint plugin creates a Jenkins-native report from this
     * data).
     */
    REPORT("report"),

    /**
     * This is a variation of [REPORT] which also includes quickfix
     * document edit operations; e.g. for an XML set attribute
     * operation, this would emit an edit operation which says exactly
     * which file and offset to delete how many characters and to insert
     * which string. This is used by for example code review tools which
     * run lint and offer to just apply a suggested fix on the fly
     * instead of requiring the user to apply fix in the IDE and upload
     * a new changelist.
     */
    REPORT_WITH_FIXES("report_with_fixes"),

    /**
     * This report is intended to be used as a baseline. This will write
     * out a lot less metadata (issue category and explanations etc)
     * which are irrelevant for the baseline use case and just creates
     * noise for code reviewers looking at baseline updates in change
     * lists.
     */
    BASELINE("baseline"),

    /**
     * A file recording incidents that were reported unconditionally.
     */
    INCIDENTS("definite"),

    /**
     * A file recording incidents that are conditional, such as
     * depending on the minSdkVersion.
     */
    CONDITIONAL_INCIDENTS("provisional"),

    /**
     * A file recording miscellaneous data used either during analysis
     * of downstream modules depending on this one, or during report
     * aggregation.
     */
    PARTIAL_RESULTS("partial"),

    /** A file recording the set of configured issues. */
    CONFIGURED_ISSUES("issues"),

    /** The resource repository cache for this module */
    RESOURCE_REPOSITORY("resources");

    /**
     * Should paths in the report always be relative, even if the lint
     * flag turning on absolute paths has been set to true?
     */
    fun relativePaths(): Boolean = this != REPORT && this != REPORT_WITH_FIXES

    /** Whether we should use path variables for this file type */
    fun variables(): Boolean = isPersistenceFile() || this == BASELINE

    /**
     * Should all paths be converted to Unix paths (file separator /
     * instead of \ on Windows) ?
     */
    fun unixPaths(): Boolean = this != REPORT

    /**
     * Include issue metadata such as explanation or category in the
     * report? (Does not include the default severity, since that's
     * just a default and only the actual severity (determined by
     * configurations and other overrides) can differ from the default.
     */
    fun includeIssueMetadata(): Boolean = this == REPORT || this == REPORT_WITH_FIXES

    /**
     * Should the report include fragments of source around the problem
     * location?
     */
    fun includeSourceLines(): Boolean = !isPersistenceFile()

    /**
     * Should the XML report include not just file and line numbers but
     * character offsets as well?
     */
    fun includeOffsets(): Boolean = isPersistenceFile()

    /**
     * Returns true if this file type is used for state persistence in
     * lint, not for some user visible or externally interpreted or
     * potentially version controlled file like baselines, XML reports,
     * etc.
     */
    fun isPersistenceFile(): Boolean {
        return this == INCIDENTS ||
                this == CONDITIONAL_INCIDENTS ||
                this == CONFIGURED_ISSUES ||
                this == PARTIAL_RESULTS
    }

    /**
     * Default filename to use for this file. Should be unique among the
     * various types of [XmlFileType].
     */
    fun getDefaultFileName(variantName: String?): String {
        return "lint-$typeName${if (variantName != null) "-$variantName" else ""}.xml"
    }
}
