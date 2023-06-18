/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeCounts
import com.android.utils.SdkUtils
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import java.io.File
import java.io.IOException
import java.io.Writer

/** A reporter which emits lint warnings as plain text strings */
class TextReporter(
    client: LintCliClient,
    private val flags: LintCliFlags,
    file: File?,
    private val writer: Writer,
    private val close: Boolean
) : Reporter(client, file) {
    var format = TextFormat.TEXT

    /**
     * Whether the reporter should convert paths to forward slashes.
     */
    var isForwardSlashPaths = false

    /** Whether the report should include secondary line content. */
    var includeSecondaryLineContent = false

    private var writeStats = true

    /**
     * Constructs a new [TextReporter]
     *
     * @param client the client
     * @param flags the flags
     * @param writer the writer to write into
     * @param close whether the writer should be closed when done
     */
    constructor(
        client: LintCliClient,
        flags: LintCliFlags,
        writer: Writer,
        close: Boolean
    ) : this(client, flags, null, writer, close)

    @Throws(IOException::class)
    override fun write(
        stats: LintStats,
        issues: List<Incident>,
        registry: IssueRegistry
    ) {
        val abbreviate = !flags.isShowEverything
        val output = StringBuilder(issues.size * 200)
        if (issues.isEmpty()) {
            if (isDisplayEmpty && writeStats) {
                writer.write("No issues found")
                if (stats.baselineErrorCount > 0 || stats.baselineWarningCount > 0) {
                    val baselineFile = flags.baselineFile!!
                    val counts = describeCounts(
                        stats.baselineErrorCount,
                        stats.baselineWarningCount,
                        comma = true,
                        capitalize = true
                    )
                    writer.write(" ($counts filtered by baseline ${baselineFile.name})")
                }
                writer.write('.'.toInt())
                writer.write('\n'.toInt())
                writer.flush()
            }
        } else {
            var lastIssue: Issue? = null
            for (incident in issues) {
                val issue = incident.issue
                if (issue !== lastIssue) {
                    explainIssue(output, lastIssue)
                    lastIssue = issue
                }
                val startLength = output.length
                val displayPath = incident.getPath(client)
                appendPath(output, displayPath)
                output.append(':')
                if (incident.line >= 0) {
                    output.append((incident.line + 1).toString())
                    output.append(':')
                }
                if (startLength < output.length) {
                    output.append(' ')
                }
                var severity = incident.severity
                if (severity === Severity.FATAL) {
                    // Treat the fatal error as an error such that we don't display
                    // both "Fatal:" and "Error:" etc in the error output.
                    severity = Severity.ERROR
                }
                output.append(severity.description)
                output.append(':')
                output.append(' ')
                output.append(
                    TextFormat.RAW.convertTo(
                        incident.message,
                        format
                    )
                )
                output.append(' ').append('[')
                output.append(issue.id)

                val from = issue.vendor ?: issue.registry?.vendor
                from?.identifier?.let { identifier ->
                    if (from != IssueRegistry.AOSP_VENDOR && identifier.isNotBlank()) {
                        output.append(" from ")
                        output.append(TextFormat.RAW.convertTo(identifier, format))
                    }
                }
                output.append(']')

                output.append('\n')
                if (incident.wasAutoFixed) {
                    output.append("This issue has been automatically fixed.\n")
                }
                if (flags.isShowSourceLines) {
                    incident.getErrorLines(textProvider = { client.getSourceText(it) })?.let {
                        if (it.isNotEmpty()) output.append(it)
                    }
                }
                if (incident.location.secondary != null) {
                    var location = incident.location.secondary
                    var omitted = false
                    while (location != null) {
                        val locationMessage = location.message
                        if (locationMessage != null && locationMessage.isNotEmpty()) {
                            output.append("    ")
                            val path = incident.getPath(client, location.file)
                            appendPath(output, path)
                            val start = location.start
                            if (start != null) {
                                val line = start.line
                                if (line >= 0) {
                                    output.append(':')
                                    output.append((line + 1).toString())
                                }
                            }
                            output.append(':')
                            output.append(' ')
                            output.append(
                                TextFormat.RAW.convertTo(
                                    locationMessage,
                                    format
                                )
                            )
                            output.append('\n')
                        } else {
                            omitted = true
                        }
                        if (flags.isShowSourceLines && includeSecondaryLineContent) {
                            location.getErrorLines(textProvider = { client.getSourceText(it) })
                                ?.let {
                                    if (it.isNotEmpty()) output.append(it)
                                }
                        }
                        location = location.secondary
                    }
                    if (!abbreviate && omitted) {
                        location = incident.location.secondary
                        val sb = StringBuilder(100)
                        sb.append("Also affects: ")
                        val begin = sb.length
                        while (location != null) {
                            val locationMessage = location.message
                            if (locationMessage == null || locationMessage.isEmpty()) {
                                if (sb.length > begin) {
                                    sb.append(", ")
                                }
                                val path = incident.getPath(client, location.file)
                                appendPath(sb, path)
                                val start = location.start
                                if (start != null) {
                                    val line = start.line
                                    if (line >= 0) {
                                        sb.append(':')
                                        sb.append((line + 1).toString())
                                    }
                                }
                            }
                            location = location.secondary
                        }
                        val wrapped = Main.wrap(
                            sb.toString(),
                            Main.MAX_LINE_WIDTH,
                            "     "
                        )
                        output.append(wrapped)
                    }
                }
                val applicableVariants = incident.applicableVariants
                if (applicableVariants != null && applicableVariants.variantSpecific) {
                    val names = if (applicableVariants.includesMoreThanExcludes()) {
                        output.append("Applies to variants: ")
                        applicableVariants.includedVariantNames
                    } else {
                        output.append("Does not apply to variants: ")
                        applicableVariants.excludedVariantNames
                    }
                    output.append(Joiner.on(", ").join(names))
                    output.append('\n')
                }
            }
            explainIssue(output, lastIssue)
            writer.write(output.toString())
            if (writeStats) {
                // TODO: Update to using describeCounts
                writer.write("${stats.errorCount} errors, ${stats.warningCount} warnings")
                if (stats.baselineErrorCount > 0 || stats.baselineWarningCount > 0) {
                    val baselineFile = flags.baselineFile!!
                    val counts = describeCounts(
                        stats.baselineErrorCount,
                        stats.baselineWarningCount,
                        comma = true,
                        capitalize = true
                    )
                    writer.write(" ($counts filtered by baseline ${baselineFile.name})")
                }
            }
            writer.write('\n'.toInt())
            writer.flush()
        }
        if (close) {
            writer.close()
            if (!client.flags.isQuiet && this.output != null) {
                val path = convertPath(this.output.absolutePath)
                println("Wrote text report to $path")
            }
        }
    }

    private fun appendPath(sb: StringBuilder, path: String) {
        sb.append(convertPath(path))
    }

    private fun convertPath(path: String): String {
        return if (isForwardSlashPaths) {
            if (File.separatorChar == '/') {
                path
            } else path.replace(File.separatorChar, '/')
        } else path
    }

    private fun explainIssue(
        output: StringBuilder,
        issue: Issue?
    ) {
        if (issue == null ||
            !flags.isExplainIssues ||
            issue === IssueRegistry.LINT_ERROR ||
            issue === IssueRegistry.LINT_WARNING ||
            issue === IssueRegistry.BASELINE
        ) {
            return
        }
        val explanation = issue.getExplanation(format)
        if (explanation.isBlank()) {
            return
        }
        val indent = "   "
        val formatted = SdkUtils.wrap(explanation, Main.MAX_LINE_WIDTH - indent.length, null)
        output.append('\n')
        output.append(indent)
        output.append("Explanation for issues of type \"").append(issue.id).append("\":\n")
        for (line in Splitter.on('\n').split(formatted)) {
            if (line.isNotEmpty()) {
                output.append(indent)
                output.append(line)
            }
            output.append('\n')
        }
        val moreInfo = issue.moreInfo
        if (moreInfo.isNotEmpty()) {
            for (url in moreInfo) {
                if (formatted.contains(url)) {
                    continue
                }
                output.append(indent)
                output.append(url)
                output.append('\n')
            }
            output.append('\n')
        }

        val options = issue.getOptions()
        if (options.isNotEmpty()) {
            for (line in Option.describe(options).lines()) {
                if (line.isBlank()) {
                    output.append("\n")
                } else {
                    output.append(indent).append(line).append('\n')
                }
            }
        }

        val issueVendor = issue.vendor ?: issue.registry?.vendor
        issueVendor?.let { vendor ->
            if (vendor != IssueRegistry.AOSP_VENDOR) {
                vendor.describeInto(output, format, indent)
                output.append('\n')
            }
        }
    }

    /** Whether the report should include stats. Default is true. */
    fun setWriteStats(writeStats: Boolean) {
        this.writeStats = writeStats
    }
}
