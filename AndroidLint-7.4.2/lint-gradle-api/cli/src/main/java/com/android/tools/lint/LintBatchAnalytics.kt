/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.analytics.Anonymizer
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.UsageTracker
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.utils.NullLogger
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.LINT_SESSION
import com.google.wireless.android.sdk.stats.LintIssueId
import com.google.wireless.android.sdk.stats.LintPerformance
import com.google.wireless.android.sdk.stats.LintSession
import java.io.File
import java.io.IOException

/** Helper for submitting analytics for batch usage of lint (for users who have opted in) */
class LintBatchAnalytics {
    fun logSession(
        registry: IssueRegistry,
        flags: LintCliFlags,
        driver: LintDriver,
        projects: Collection<Project>,
        warnings: List<Warning>
    ) {
        assert(!projects.isEmpty())

        val session = LintSession.newBuilder().apply {
            analysisType = computeAnalysisType(flags)
            projectId = computeProjectId(projects)
            lintPerformance = computePerformance(driver)
            baselineEnabled = driver.baseline != null
            includingGeneratedSources = driver.checkGeneratedSources
            includingTestSources = driver.checkTestSources
            includingDependencies = driver.checkDependencies
            abortOnError = flags.isSetExitCode
            ignoreWarnings = flags.isIgnoreWarnings
            warningsAsErrors = flags.isWarningsAsErrors
            for (issueBuilder in computeIssueData(warnings, flags, registry).values) {
                addIssueIds(issueBuilder)
            }
        }.build()

        val event = AndroidStudioEvent.newBuilder().apply {
            kind = LINT_SESSION
            lintSession = session
            javaProcessStats = CommonMetricsData.javaProcessStats
            jvmDetails = CommonMetricsData.jvmDetails

            // We may not have raw project id's, for example when analyzing
            // non-Android projects
            computeApplicationId(projects)?.let { setRawProjectId(it) }
        }

        UsageTracker.log(event)
    }

    private fun computeApplicationId(projects: Collection<Project>): String? {
        //  TODO: There can be more than one. Update this once AndroidStudioEvent
        // supports a collection of project id's.
        // There might also be none: if you run lint on a Java or Kotlin-only library,
        // there's no application id.

        // Prefer application projects, in list order
        for (project in projects) {
            if (project.isAndroidProject && !project.isLibrary) {
                return project.applicationId ?: continue
            }
        }

        // Prefer android libraries, in list order
        for (project in projects) {
            if (project.isAndroidProject) {
                return project.applicationId ?: continue
            }
        }

        return null
    }

    private fun computeAnalysisType(flags: LintCliFlags) =
        if (flags.isFatalOnly) LintSession.AnalysisType.VITAL else LintSession.AnalysisType.BUILD

    private fun computeProjectId(projects: Collection<Project>): String? {
        return computeProjectId(projects.firstOrNull()?.dir)
    }

    private fun computeProjectId(projectPath: File?): String? {
        projectPath ?: return null

        return try {
            Anonymizer.anonymizeUtf8(NullLogger(), projectPath.absolutePath)
        } catch (e: IOException) {
            "*ANONYMIZATION_ERROR*"
        }
    }

    private fun computePerformance(driver: LintDriver): LintPerformance =
        LintPerformance.newBuilder().apply {
            analysisTimeMs = System.currentTimeMillis() - driver.analysisStartTime
            fileCount = driver.fileCount.toLong()
            moduleCount = driver.moduleCount.toLong()
            javaSourceCount = driver.javaFileCount.toLong()
            kotlinSourceCount = driver.kotlinFileCount.toLong()
            resourceFileCount = driver.resourceFileCount.toLong()
            testSourceCount = driver.testSourceCount.toLong()
            initializeTimeMs = driver.initializeTimeMs
            registerCustomDetectorsTimeMs = driver.registerCustomDetectorsTimeMs
            computeDetectorsTimeMs = driver.computeDetectorsTimeMs
            checkProjectTimeMs = driver.checkProjectTimeMs
            extraPhasesTimeMs = driver.extraPhasesTimeMs
            reportBaselineIssuesTimeMs = driver.reportBaselineIssuesTimeMs
            disposeProjectsTimeMs = driver.disposeProjectsTimeMs
            reportGenerationTimeMs = driver.reportGenerationTimeMs
        }.build()

    private fun recordSeverityOverride(
        map: HashMap<String, LintIssueId.Builder>,
        id: String,
        lintSeverity: Severity
    ) {
        val builder = map[id]
        if (builder != null) {
            // already got severity from ProblemData entry
            return
        }
        LintIssueId.newBuilder().apply {
            map[id] = this
            issueId = id
            count = 0
            severity = lintSeverity.toAnalyticsSeverity()
        }
    }

    // Mapping from Lint's severity enum to analytics severity
    private fun Severity.toAnalyticsSeverity(): LintIssueId.LintSeverity =
        when (this) {
            Severity.FATAL -> LintIssueId.LintSeverity.FATAL_SEVERITY
            Severity.ERROR -> LintIssueId.LintSeverity.ERROR_SEVERITY
            Severity.WARNING -> LintIssueId.LintSeverity.WARNING_SEVERITY
            Severity.INFORMATIONAL -> LintIssueId.LintSeverity.INFORMATIONAL_SEVERITY
            Severity.IGNORE -> LintIssueId.LintSeverity.IGNORE_SEVERITY
            else -> LintIssueId.LintSeverity.UNKNOWN_SEVERITY
        }

    private fun computeIssueData(
        warnings: List<Warning>,
        flags: LintCliFlags,
        registry: IssueRegistry
    ): Map<String, LintIssueId.Builder> {
        val map = LinkedHashMap<String, LintIssueId.Builder>(BuiltinIssueRegistry.INITIAL_CAPACITY)
        for (warning in warnings) {
            val issue = warning.issue
            val id = issue.id
            val issueBuilder = map[id] ?: run {
                LintIssueId.newBuilder().apply {
                    map[id] = this
                    issueId = issue.id
                    severity =
                        if (warning.severity == issue.defaultSeverity) {
                            LintIssueId.LintSeverity.DEFAULT_SEVERITY
                        } else {
                            warning.severity.toAnalyticsSeverity()
                        }
                }
            }
            issueBuilder.count = issueBuilder.count + 1
        }

        // Also record manual severity overrides specified in lintOptions
        for ((id, severity) in flags.severityOverrides.entries) {
            recordSeverityOverride(map, id, severity)
        }

        // Also record manually disabled issues since we wouldn't have any
        // occurrences of these, but we want to track with analytics which issues
        // are explicitly disabled
        for (id in flags.suppressedIds) {
            recordSeverityOverride(map, id, Severity.IGNORE)
        }
        for (id in flags.enabledIds) {
            recordSeverityOverride(
                map,
                id,
                registry.getIssue(id)?.defaultSeverity ?: Severity.WARNING
            )
        }

        return map
    }
}
