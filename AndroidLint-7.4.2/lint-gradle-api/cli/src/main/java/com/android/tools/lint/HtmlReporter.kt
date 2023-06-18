/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.lint.MultiProjectHtmlReporter.ProjectEntry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.IssueRegistry.Companion.AOSP_VENDOR
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeCounts
import com.android.utils.HtmlBuilder
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.Maps
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.Date
import kotlin.math.max
import kotlin.math.min

/** A reporter which emits lint results into an HTML report. */
class HtmlReporter(
    client: LintCliClient,
    output: File,
    flags: LintCliFlags
) : Reporter(client, output) {

    private val writer: Writer
    private val flags: LintCliFlags
    private var builder: HtmlBuilder? = null
    private var sb: StringBuilder? = null
    private var highlightedFile: String? = null
    private var highlighter: LintSyntaxHighlighter? = null

    init {
        writer = output.bufferedWriter()
        this.flags = flags
    }

    override fun write(
        stats: LintStats,
        incidents: List<Incident>,
        registry: IssueRegistry
    ) {
        val missing = computeMissingIssues(registry, incidents)
        val related = computeIssueLists(incidents)
        val extra = computeExtraIssues(registry)
        startReport(stats)
        writeNavigationHeader(stats) {
            append(
                "      <a class=\"mdl-navigation__link\" href=\"#overview\"><i class=\"material-icons\">dashboard</i>Overview</a>\n"
            )
            for (warnings in related) {
                val first = warnings[0]
                val anchor = first.issue.id
                val desc = first.issue.getBriefDescription(TextFormat.HTML)
                append("      <a class=\"mdl-navigation__link\" href=\"#$anchor\">")
                if (first.severity.isError) {
                    append("<i class=\"material-icons error-icon\">error</i>")
                } else {
                    append("<i class=\"material-icons warning-icon\">warning</i>")
                }
                append("$desc (${warnings.size})</a>\n")
            }
        }
        if (incidents.isNotEmpty()) {
            append("\n<a name=\"overview\"></a>\n")
            writeCard(
                "Overview",
                true,
                "OverviewCard",
                appender = { writeOverview(related, missing.size, extra.size) }
            )
            var previousCategory: Category? = null
            for (warnings in related) {
                val category = warnings[0].issue.category
                if (category !== previousCategory) {
                    previousCategory = category
                    append("\n<a name=\"")
                    append(category.fullName)
                    append("\"></a>\n")
                }
                writeIssueCard(warnings)
            }
            writeExtraIssues(extra)
            writeMissingIssues(missing)
            writeSuppressIssuesCard()
        } else {
            writeCard(
                title = "No Issues Found",
                cardId = "NoIssuesCard"
            ) { append("Congratulations!") }
        }
        finishReport()
        writeReport()
        val output = output
        if (!client.flags.isQuiet &&
            output != null && (stats.errorCount > 0 || stats.warningCount > 0)
        ) {
            val url = SdkUtils.fileToUrlString(output.absoluteFile)
            println("Wrote HTML report to $url")
        }
    }

    private fun append(s: String) {
        sb?.append(s)
    }

    private fun append(s: Char) {
        sb?.append(s)
    }

    private fun writeSuppressIssuesCard() {
        append("\n<a name=\"SuppressInfo\"></a>\n")
        writeCard(
            title = "Suppressing Warnings and Errors",
            cardId = "SuppressCard"
        ) {
            append(
                TextFormat.RAW.convertTo(
                    Main.getSuppressHelp(),
                    TextFormat.HTML
                )
            )
            this.append('\n')
        }
    }

    private fun writeIssueCard(incidents: List<Incident>) {
        val firstIssue = incidents[0].issue
        append(
            """
            <a name="${firstIssue.id}"></a>

            """.trimIndent()
        )
        writeCard(
            title = XmlUtils.toXmlTextValue(firstIssue.getBriefDescription(TextFormat.TEXT)),
            dismissible = true,
            cardId = firstIssue.id + "Card",
            actions = listOf(Action("Explain", getExplanationId(firstIssue), "reveal"))
        ) {
            val first = incidents[0]
            val issue = first.issue
            append("<div class=\"issue\">\n")
            append("<div class=\"warningslist\">\n")
            val partialHide = incidents.size > SPLIT_LIMIT
            var count = 0
            for (incident in incidents) {
                // Don't show thousands of matches for common errors; this just
                // makes some reports huge and slow to render and nobody really wants to
                // inspect 50+ individual reports of errors of the same type
                if (count >= MAX_COUNT) {
                    if (count == MAX_COUNT) {
                        append(
                            "<br/><b>NOTE: " +
                                    (incidents.size - count).toString() +
                                    " results omitted.</b><br/><br/>"
                        )
                    }
                    count++
                    continue
                }
                if (partialHide && count == SHOWN_COUNT) {
                    val id = incident.issue.id + "Div"
                    append("<button")
                    append(" class=\"mdl-button mdl-js-button mdl-button--primary\"")
                    append(" id=\"")
                    append(id)
                    append("Link\" onclick=\"reveal('")
                    append(id)
                    append("');\" />")
                    append(
                        String.format(
                            "+ %1\$d More Occurrences...",
                            incidents.size - SHOWN_COUNT
                        )
                    )
                    append("</button>\n")
                    append("<div id=\"")
                    append(id)
                    append("\" style=\"display: none\">\n")
                }
                count++

                val displayPath = incident.getPath(client)
                val url = writeLocation(incident.file, displayPath, incident.line)
                append(':')
                append(' ')

                // Is the URL for a single image? If so, place it here near the top
                // of the error floating on the right. If there are multiple images,
                // they will instead be placed in a horizontal box below the error
                var addedImage = false
                if (url != null && incident.location.secondary == null) {
                    addedImage = addImage(url, incident.file, incident.location)
                }
                var rawMessage = incident.message

                // Improve formatting of exception stacktraces
                if (issue === IssueRegistry.LINT_ERROR && rawMessage.contains("\u2190")) {
                    rawMessage = rawMessage.replace("\u2190", "\n\u2190")
                }
                append("<span class=\"message\">")
                append(
                    TextFormat.RAW.convertTo(
                        rawMessage,
                        TextFormat.HTML
                    )
                )
                append("</span>")
                if (addedImage) {
                    append("<br clear=\"right\"/>")
                } else {
                    append("<br />")
                }
                if (incident.wasAutoFixed) {
                    append("This issue has been automatically fixed.<br />")
                }

                // Insert surrounding code block window
                val fileContents = if (incident.line >= 0)
                    client.getSourceText(incident.file)
                else null
                if (fileContents != null && incident.startOffset != -1 && incident.endOffset != -1) {
                    appendCodeBlock(
                        incident.file,
                        fileContents,
                        incident.startOffset,
                        incident.endOffset,
                        incident.severity
                    )
                }
                append('\n')
                if (incident.location.secondary != null) {
                    append("<ul>")
                    var l = incident.location.secondary
                    var otherLocations = 0
                    var shownSnippetsCount = 0
                    while (l != null) {
                        val message = l.message
                        if (message != null && message.isNotEmpty()) {
                            val start = l.start
                            val line = start?.line ?: -1
                            val path = incident.getPath(client, l.file)
                            writeLocation(l.file, path, line)
                            append(':')
                            append(' ')
                            append("<span class=\"message\">")
                            append(
                                TextFormat.RAW.convertTo(
                                    message,
                                    TextFormat.HTML
                                )
                            )
                            append("</span>")
                            append("<br />")

                            // Only display up to 3 inlined views to keep big reports from
                            // getting massive in rendering cost
                            if (shownSnippetsCount < 3 && !SdkUtils.isBitmapFile(l.file)) {
                                val s = client.readFile(l.file)
                                if (s.isNotEmpty()) {
                                    val offset = start?.offset ?: -1
                                    val endOffset = l.end?.offset ?: -1
                                    appendCodeBlock(l.file, s, offset, endOffset, incident.severity)
                                }
                                shownSnippetsCount++
                            }
                        } else {
                            otherLocations++
                        }
                        l = l.secondary
                    }
                    append("</ul>")
                    if (otherLocations > 0) {
                        val id = "Location" + count + "Div"
                        append("<button id=\"")
                        append(id)
                        append("Link\" onclick=\"reveal('")
                        append(id)
                        append("');\" />")
                        append("+ $otherLocations Additional Locations...")
                        append("</button>\n")
                        append("<div id=\"")
                        append(id)
                        append("\" style=\"display: none\">\n")
                        append("Additional locations: ")
                        append("<ul>\n")
                        l = incident.location.secondary
                        while (l != null) {
                            val start = l.start
                            val line = start?.line ?: -1
                            val path = incident.getPath(client, l.file)
                            append("<li> ")
                            writeLocation(l.file, path, line)
                            append("\n")
                            l = l.secondary
                        }
                        append("</ul>\n")
                        append("</div><br/><br/>\n")
                    }
                }

                // Place a block of images?
                if ((!addedImage && url != null) && incident.location.secondary != null) {
                    addImage(url, incident.file, incident.location)
                }
                val applicableVariants = incident.applicableVariants
                if (applicableVariants != null && applicableVariants.variantSpecific) {
                    append("\n")
                    append("Applies to variants: ")
                    append(Joiner.on(", ").join(applicableVariants.includedVariantNames))
                    append("<br/>\n")
                    append("Does <b>not</b> apply to variants: ")
                    append(Joiner.on(", ").join(applicableVariants.excludedVariantNames))
                    append("<br/>\n")
                }
            }
            if (partialHide) { // Close up the extra div
                append("</div>\n") // partial hide
            }
            append("</div>\n") // class=warningslist
            writeIssueMetadata(issue, null, true)
            append("</div>\n") // class=issue

            val issueVendor = issue.vendor ?: issue.registry?.vendor
            issueVendor?.let { vendor ->
                if (vendor != AOSP_VENDOR) {
                    append("<div class=\"vendor\">\n")
                    vendor.describeInto(sb!!, TextFormat.HTML)
                    append("</div>\n") // class=vendor
                }
            }

            append("<div class=\"chips\">\n")
            writeChip(issue.id)
            var category: Category? = issue.category
            while (category != null && category !== Category.LINT) {
                writeChip(category.name)
                category = category.parent
            }
            writeChip(first.severity.description)
            writeChip("Priority " + issue.priority + "/10")
            append("</div>\n") // class=chips
        } // HTML style isn't handled right by card widget
    }

    private fun startReport(stats: LintStats) {
        sb = StringBuilder(1800 * stats.count())
        builder = HtmlBuilder(sb!!)
        writeOpenHtmlTag()
        writeHeadTag()
        writeOpenBodyTag()
    }

    private fun finishReport() {
        writeCloseNavigationHeader()
        writeCloseBodyTag()
        writeCloseHtmlTag()
    }

    private fun writeNavigationHeader(stats: LintStats, appender: () -> Unit) {
        append(
            """<div class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
  <header class="mdl-layout__header">
    <div class="mdl-layout__header-row">
      <span class="mdl-layout-title">$title: """ +
                    describeCounts(
                        stats.errorCount, stats.warningCount, comma = false, capitalize = true
                    ) +
                    "</span>\n" +
                    "      <div class=\"mdl-layout-spacer\"></div>\n" +
                    "      <nav class=\"mdl-navigation mdl-layout--large-screen-only\">"
        )
        append("Check performed at ${Date()} by ${client.getClientDisplayName()}")
        append(
            """</nav>
    </div>
  </header>
  <div class="mdl-layout__drawer">
    <span class="mdl-layout-title">Issue Types</span>
    <nav class="mdl-navigation">
"""
        )
        appender()
        append(
            """    </nav>
  </div>
  <main class="mdl-layout__content">
    <div class="mdl-layout__tab-panel is-active">"""
        )
    }

    private fun writeCloseNavigationHeader() {
        append("    </div>\n  </main>\n</div>")
    }

    private fun writeOpenBodyTag() {
        append("<body class=\"mdl-color--grey-100 mdl-color-text--grey-700 mdl-base\">\n")
    }

    private fun writeCloseBodyTag() {
        append("\n</body>\n")
    }

    private fun writeOpenHtmlTag() {
        append(
            """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
"""
        )
    }

    private fun writeCloseHtmlTag() {
        append("</html>")
    }

    private fun writeHeadTag() {
        append(
            """
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>$title</title>
"""
        )

        // Material
        append(
            """<link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
 <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.blue-indigo.min.css" />
<link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Roboto:300,400,500,700" type="text/css">
<script defer src="https://code.getmdl.io/1.2.0/material.min.js"></script>
"""
        )
        append("<style>\n$cssStyles</style>\n")

        // JavaScript for collapsing/expanding long lists
        append(
            """<script language="javascript" type="text/javascript">
<!--
function reveal(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'block';
document.getElementById(id+'Link').style.display = 'none';
}
}
function hideid(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'none';
}
}
//-->
</script>
"""
        )
        append("</head>\n")
    }

    private fun writeIssueMetadata(
        issue: Issue,
        disabledBy: String?,
        hide: Boolean,
        includeSuppressInfo: Boolean = true,
        includeVendorInfo: Boolean = false,
    ) {
        append("<div class=\"metadata\">")
        if (disabledBy != null) {
            append(String.format("Disabled By: %1\$s<br/>\n", disabledBy))
        }
        append("<div class=\"explanation\"")
        if (hide) {
            append(" id=\"" + getExplanationId(issue) + "\" style=\"display: none;\"")
        }
        append(">\n")
        val explanationHtml = issue.getExplanation(TextFormat.HTML)
        append(explanationHtml)
        val moreInfo = issue.moreInfo
        append("<br/>")

        val options = issue.getOptions()
        if (options.isNotEmpty()) {
            append("<br/>\nThis check can be configured via the following options:<br/><br/>\n")
            append("<div class=\"options\">\n")
            for (option in options) {
                append(option.describe(TextFormat.HTML, includeExample = false))
                append("<br/>\n")
                append(
                    "To configure this option, use a `lint.xml` file in the project or source folder using an " +
                            "<code>&lt;option&gt;</code> block like the following:\n"
                )

                val name = option.name
                val defaultValue = option.defaultAsString()
                val builder = HtmlBuilder()
                val snippet = "<lint>\n" +
                        "    <issue id=\"${issue.id}\">\n" +
                        "        <option name=\"$name\" value=\"${defaultValue ?: "some string"}\" />\n" +
                        "    </issue>\n" +
                        "</lint>\n"

                val highlighter = LintSyntaxHighlighter("lint.xml", snippet)
                highlighter.isPadCaretLine = true
                highlighter.isDedent = true
                val start = snippet.indexOf(name) - 1
                val end = snippet.lastIndexOf(" />")
                highlighter.generateHtml(builder, start, end, false)
                val highlighted = builder.html
                append(highlighted)
            }

            append("</div>") // options
        }

        // TODO: Skip MoreInfo links already present in the HTML to avoid redundancy.
        val count = moreInfo.size
        if (count > 0) {
            append("<div class=\"moreinfo\">")
            append("More info: ")
            if (count > 1) {
                append("<ul>")
            }
            for (uri in moreInfo) {
                if (count > 1) {
                    append("<li>")
                }
                append("<a href=\"")
                append(uri)
                append("\">")
                append(uri)
                append("</a>\n")
            }
            if (count > 1) {
                append("</ul>")
            }
            append("</div>")
        }
        val vendor = issue.vendor ?: issue.registry?.vendor
        if (vendor === AOSP_VENDOR) {
            if (hasAutoFix(issue)) {
                append(
                    "Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA."
                )
                append("<br>\n")
            }
        }
        if (includeSuppressInfo) {
            append(
                String.format(
                    "To suppress this error, use the issue id \"%1\$s\" as explained in the " +
                            "%2\$sSuppressing Warnings and Errors%3\$s section.",
                    issue.id, "<a href=\"#SuppressInfo\">", "</a>"
                )
            )
        }
        if (includeVendorInfo && vendor != null && vendor != AOSP_VENDOR) {
            append("<div class=\"vendor\">\n")
            vendor.describeInto(sb!!, TextFormat.HTML)
            append("</div>\n") // class=vendor
        }
        append("<br/>\n")
        append("<br/>")
        append("</div>") // class=moreinfo
        append("\n</div>\n") // class=explanation
    }

    /**
     * Returns the list of extra issues that were included in analysis
     * (those that are not built in).
     */
    private fun computeExtraIssues(registry: IssueRegistry): List<Issue> {
        val issues = registry.issues
        return issues.filter { issue ->
            val vendor = issue.vendor ?: issue.registry?.vendor
            vendor != AOSP_VENDOR && issue.isEnabledByDefault()
        }
    }

    private fun computeMissingIssues(
        registry: IssueRegistry,
        incidents: List<Incident>
    ): Map<Issue, String> {
        val projects: MutableSet<Project> = HashSet()
        val seen: MutableSet<Issue> = HashSet()
        for (incident in incidents) {
            incident.project?.let { projects.add(it) }
            seen.add(incident.issue)
        }
        val cliConfiguration = client.configurations.fallback
        val map: MutableMap<Issue, String> = Maps.newHashMap()
        val issues = registry.issues
        for (issue in issues) {
            if (!seen.contains(issue)) {
                if (client.isSuppressed(issue)) {
                    map[issue] = "Command line flag"
                    continue
                }
                if (!issue.isEnabledByDefault() &&
                    !client.isAllEnabled &&
                    !client.isExplicitlyEnabled(issue)
                ) {
                    map[issue] = "Default"
                    continue
                }
                if (cliConfiguration != null && !cliConfiguration.isEnabled(issue)) {
                    map[issue] = "Command line supplied --config lint.xml file"
                    continue
                }

                // See if any projects disable this warning
                for (project in projects) {
                    if (!project.getConfiguration(null).isEnabled(issue)) {
                        map[issue] = "Project lint.xml file"
                        break
                    }
                }
            }
        }
        return map
    }

    private fun writeMissingIssues(missing: Map<Issue, String>) {
        if (!client.isCheckingSpecificIssues && missing.isNotEmpty()) {
            append("\n<a name=\"MissingIssues\"></a>\n")
            writeCard(
                title = "Disabled Checks",
                dismissible = true,
                cardId = "MissingIssuesCard",
                actions = listOf(Action("List Missing Issues", "SuppressedIssues", "reveal"))
            ) {
                append(
                    """
                    One or more issues were not run by lint, either
                    because the check is not enabled by default, or because
                    it was disabled with a command line flag or via one or
                    more <code>lint.xml</code> configuration files in the project directories.

                    """.trimIndent()
                )
                append("<div id=\"SuppressedIssues\" style=\"display: none;\">")
                val list = ArrayList(missing.keys)
                list.sort()
                append("<br/><br/>")
                for (issue in list) {
                    append("<div class=\"issue\">\n")

                    // Explain this issue
                    append("<div class=\"id\">")
                    append(issue.id)
                    append("<div class=\"issueSeparator\"></div>\n")
                    append("</div>\n")
                    val disabledBy = missing[issue]
                    writeIssueMetadata(
                        issue,
                        disabledBy,
                        hide = false,
                        includeSuppressInfo = false,
                        includeVendorInfo = false
                    )
                    append("</div>\n")
                }
                append("</div>\n") // SuppressedIssues
            }
        }
    }

    private fun writeExtraIssues(extra: List<Issue>) {
        if (!client.isCheckingSpecificIssues && extra.isNotEmpty()) {
            append("\n<a name=\"ExtraIssues\"></a>\n")
            writeCard(
                title = "Included Additional Checks",
                dismissible = true,
                cardId = "ExtraIssuesCard",
                actions = listOf(Action("List Issues", "IncludedIssues", "reveal"))
            ) {
                append(
                    """
                    This card lists all the extra checks run by lint, provided from libraries,
                    build configuration and extra flags. This is included to help you verify
                    whether a particular check is included in analysis when configuring builds.
                    (Note that the list does not include the hundreds of built-in checks into lint,
                    only additional ones.)

                    """.trimIndent()
                )
                append("<div id=\"IncludedIssues\" style=\"display: none;\">")
                append("<br/><br/>")
                for (issue in extra.sorted()) {
                    append("<div class=\"issue\">\n")

                    // Explain this issue
                    append("<div class=\"id\">")
                    append(issue.id)
                    append("<div class=\"issueSeparator\"></div>\n")
                    append("</div>\n")
                    writeIssueMetadata(
                        issue,
                        null,
                        hide = false,
                        includeSuppressInfo = false,
                        includeVendorInfo = true
                    )
                    append("</div>\n")
                }
                append("</div>\n") // ExtraIssues
            }
        }
    }

    private fun writeOverview(
        related: List<List<Incident>>,
        missingCount: Int,
        extraCount: Int
    ) {
        // Write issue id summary
        append("<table class=\"overview\">\n")
        var previousCategory: Category? = null
        for (warnings in related) {
            val first = warnings[0]
            val issue = first.issue
            val isError = first.severity.isError
            if (issue.category !== previousCategory) {
                append("<tr><td class=\"countColumn\"></td><td class=\"categoryColumn\">")
                previousCategory = issue.category
                val categoryName = issue.category.fullName
                append("<a href=\"#")
                append(categoryName)
                append("\">")
                append(categoryName)
                append("</a>\n")
                append("</td></tr>")
                append("\n")
            }
            append("<tr>\n")

            // Count column
            append("<td class=\"countColumn\">")
            append(warnings.size.toString())
            append("</td>")
            append("<td class=\"issueColumn\">")
            if (isError) {
                append("<i class=\"material-icons error-icon\">error</i>")
            } else {
                append("<i class=\"material-icons warning-icon\">warning</i>")
            }
            append('\n')
            append("<a href=\"#")
            append(issue.id)
            append("\">")
            append(issue.id)
            append("</a>")
            append(": ")
            append(issue.getBriefDescription(TextFormat.HTML))
            append("</td></tr>\n")
        }
        if (extraCount > 0 && !client.isCheckingSpecificIssues) {
            append("<tr><td></td>")
            append("<td class=\"categoryColumn\">")
            append("<a href=\"#ExtraIssues\">")
            append(String.format("Included Additional Checks (%1\$d)", extraCount))
            append("</a>\n")
            append("</td></tr>\n")
        }
        if (missingCount > 0 && !client.isCheckingSpecificIssues) {
            append("<tr><td></td>")
            append("<td class=\"categoryColumn\">")
            append("<a href=\"#MissingIssues\">")
            append(String.format("Disabled Checks (%1\$d)", missingCount))
            append("</a>\n")
            append("</td></tr>\n")
        }
        append("</table>\n")
        append("<br/>")
    }

    private fun writeCardHeader(title: String?, cardId: String) {
        append(
            """<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="$cardId" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
"""
        )
        if (title != null) {
            append(
                """  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">$title</h2>
  </div>
"""
            )
        }
        append("              <div class=\"mdl-card__supporting-text\">\n")
    }

    class Action(
        val title: String,
        val id: String,
        val function: String
    )

    private fun writeCardAction(actions: List<Action>) {
        append(
            """              </div>
              <div class="mdl-card__actions mdl-card--border">
"""
        )
        for (action in actions) {
            append(
                """<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="${action.id}Link" onclick="${action.function}('${action.id}');">
${action.title}</button>"""
            )
        }
    }

    private fun writeCardFooter() {
        append("            </div>\n            </div>\n          </section>")
    }

    private fun writeCard(
        title: String?,
        cardId: String?,
        appender: () -> Unit
    ) {
        writeCard(title = title, dismissible = false, cardId = cardId, appender = appender)
    }

    private fun writeChip(text: String) {
        append(
            """
            <span class="mdl-chip">
                <span class="mdl-chip__text">$text</span>
            </span>

            """.trimIndent()
        )
    }

    private var cardNumber = 0
    private val usedCardIds: MutableSet<String> = mutableSetOf()

    private fun writeCard(
        title: String?,
        dismissible: Boolean,
        cardId: String?,
        actions: List<Action> = emptyList(),
        appender: () -> Unit
    ) {
        @Suppress("NAME_SHADOWING")
        val cardId = cardId ?: run {
            val card = cardNumber++
            getCardId(card)
        }
        usedCardIds.add(cardId)
        writeCardHeader(title, cardId)
        appender()
        if (dismissible) {
            var dismissTitle = "Dismiss"
            if ("New Lint Report Format" == title) {
                dismissTitle = "Got It"
            }
            val merged = actions + Action(dismissTitle, cardId, "hideid")
            writeCardAction(merged)
        }
        writeCardFooter()
    }

    private fun writeLocation(
        file: File,
        path: String,
        line: Int
    ): String? {
        append("<span class=\"location\">")
        val url: String? = getUrl(file)
        if (url != null) {
            append("<a href=\"")
            append(url)
            append("\">")
        }
        var displayPath = stripPath(path)
        if (url != null && url.startsWith("../") && File(displayPath).isAbsolute) {
            displayPath = url
        }

        // Clean up super-long and ugly paths to cache files such as
        //    ../../../../../../.gradle/caches/transforms-1/files-1.1/timber-4.6.0.aar/
        //      8fe9cb22a46026bb3bd0c9d976e2897a/jars/lint.jar
        if (displayPath.contains("transforms-1") &&
            displayPath.endsWith("lint.jar") &&
            displayPath.contains(".aar")
        ) {
            val aarIndex = displayPath.indexOf(".aar")
            val startWin = displayPath.lastIndexOf('\\', aarIndex) + 1
            val startUnix = displayPath.lastIndexOf('/', aarIndex) + 1
            val start = max(startWin, startUnix)
            displayPath = (
                    displayPath.substring(start, aarIndex + 4) +
                            File.separator +
                            "..." +
                            File.separator +
                            "lint.jar"
                    )
        }
        append(displayPath)
        if (url != null) {
            append("</a>")
        }
        if (line >= 0) {
            // 0-based line numbers, but display 1-based
            append(':')
            append((line + 1).toString())
        }
        append("</span>")
        return url
    }

    private fun addImage(
        url: String?,
        urlFile: File?,
        location: Location
    ): Boolean {
        if (url != null && urlFile != null && SdkUtils.isBitmapFile(urlFile)) {
            if (location.secondary != null) {
                // Emit many images
                // Add in linked images as well
                val files: MutableList<File> = mutableListOf()
                var curr: Location? = location
                while (curr != null) {
                    val file = curr.file
                    if (SdkUtils.isBitmapFile(file)) {
                        files.add(file)
                    }
                    curr = curr.secondary
                }
                files.sortWith(ICON_DENSITY_COMPARATOR)
                val urls: MutableList<String> = mutableListOf()
                for (file in files) {
                    val imageUrl = getUrl(file)
                    if (imageUrl != null) {
                        urls.add(imageUrl)
                    }
                }
                if (urls.isNotEmpty()) {
                    append("<table>\n")
                    append("<tr>")
                    for (linkedUrl in urls) {
                        // Image series: align top
                        append("<td>")
                        append("<a href=\"")
                        append(linkedUrl)
                        append("\">")
                        append("<img border=\"0\" align=\"top\" src=\"")
                        append(linkedUrl)
                        append("\" /></a>\n")
                        append("</td>")
                    }
                    append("</tr>")
                    append("<tr>")
                    for (linkedUrl in urls) {
                        append("<th>")
                        var index = linkedUrl.lastIndexOf("drawable-")
                        if (index != -1) {
                            index += "drawable-".length
                            val end = linkedUrl.indexOf('/', index)
                            if (end != -1) {
                                append(linkedUrl.substring(index, end))
                            }
                        }
                        append("</th>")
                    }
                    append("</tr>\n")
                    append("</table>\n")
                }
            } else {
                // Just this image: float to the right
                append("<img class=\"embedimage\" align=\"right\" src=\"")
                append(url)
                append("\" />")
            }
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun writeProjectList(
        stats: LintStats,
        projects: List<ProjectEntry>
    ) {
        startReport(stats)
        writeNavigationHeader(
            stats
        ) {
            for (entry in projects) {
                val href = XmlUtils.toXmlAttributeValue(entry.fileName)
                val path = entry.path
                val count = entry.errorCount + entry.warningCount
                append(
                    "      <a class=\"mdl-navigation__link\" href=\"$href\">$path ($count)</a>\n"
                )
            }
        }
        if (stats.errorCount == 0 && stats.warningCount == 0) {
            writeCard(
                title = "No Issues Found",
                cardId = "NoIssuesCard"
            ) { append("Congratulations!") }
            return
        }
        writeCard(
            title = "Projects",
            cardId = "OverviewCard"
        ) {
            // Write issue id summary
            append("<table class=\"overview\">\n")
            append("<tr><th>")
            append("Project")
            append("</th><th class=\"countColumn\">")
            append("Errors")
            append("</th><th class=\"countColumn\">")
            append("Warnings")
            append("</th></tr>\n")
            for (entry in projects) {
                append("<tr><td>")
                append("<a href=\"")
                append(XmlUtils.toXmlAttributeValue(entry.fileName))
                append("\">")
                append(entry.path)
                append("</a></td><td class=\"countColumn\">")
                append(entry.errorCount.toString())
                append("</td><td class=\"countColumn\">")
                append(entry.warningCount.toString())
                append("</td></tr>\n")
                append("<tr>\n")
            }
            append("</table>\n")
            append("<br/>")
        }
        finishReport()
        writeReport()
    }

    @Throws(IOException::class)
    private fun writeReport() {
        writer.write(sb.toString())
        writer.close()
        sb = null
        builder = null
    }

    private fun getHighlighter(
        file: File,
        contents: CharSequence
    ): LintSyntaxHighlighter {
        if (highlightedFile == null || highlightedFile != file.path) {
            val highlighter = LintSyntaxHighlighter(file.name, contents.toString())
            this.highlighter = highlighter
            highlighter.isPadCaretLine = true
            highlighter.isDedent = true
            highlightedFile = file.path
        }
        return highlighter!!
    }

    /** Insert syntax highlighted XML. */
    private fun appendCodeBlock(
        file: File,
        contents: CharSequence,
        startOffset: Int,
        endOffset: Int,
        severity: Severity
    ) {
        val builder = builder ?: return
        val start = max(0, startOffset)
        val end = max(start, min(endOffset, contents.length))
        getHighlighter(file, contents).generateHtml(builder, start, end, severity.isError)
    }

    companion object {
        /**
         * Compare icons - first in descending density order, then by
         * name.
         */
        val ICON_DENSITY_COMPARATOR =
            Comparator { file1: File, file2: File ->
                val density1 = getDensity(file1)
                val density2 = getDensity(file2)
                val densityDelta = density1 - density2
                if (densityDelta != 0) {
                    densityDelta
                } else {
                    file1.name.compareTo(file2.name, ignoreCase = true)
                }
            }

        /**
         * Maximum number of warnings allowed for a single issue type
         * before we split up and hide all but the first [.SHOWN_COUNT]
         * items.
         */
        private var SPLIT_LIMIT = 0

        /** Maximum number of incidents shown per issue type */
        private var MAX_COUNT = 0

        /**
         * When a warning has at least [SPLIT_LIMIT] items, then we
         * show the following number of items before the "Show more"
         * button/link.
         */
        private var SHOWN_COUNT = 0

        /** Number of lines to show around code snippets. */
        @JvmField
        var CODE_WINDOW_SIZE = 0

        private const val REPORT_PREFERENCE_ENV_VAR = "LINT_HTML_PREFS"
        const val REPORT_PREFERENCE_PROPERTY = "lint.html.prefs"
        private var USE_WAVY_UNDERLINES_FOR_ERRORS = false

        /**
         * Whether we should try to use browser support for wavy
         * underlines. Underlines are not working well; see
         * https://bugs.chromium.org/p/chromium/issues/detail?id=165462
         * for when to re-enable. If false we're using a CSS
         * trick with repeated images instead. (Only applies
         * if [USE_WAVY_UNDERLINES_FOR_ERRORS] is true.)
         */
        private const val USE_CSS_DECORATION_FOR_WAVY_UNDERLINES = false
        private var preferredThemeName = "light"

        /**
         * CSS themes for syntax highlighting. The following classes map
         * to an IntelliJ color theme like this:
         * * pre.errorlines: General > Text > Default Text
         * * .prefix: XML > Namespace Prefix
         * * .attribute: XML > Attribute name
         * * .value: XML > Attribute value
         * * .tag: XML > Tag name
         * * .comment: XML > Comment
         * * .javado: Comments > JavaDoc > Text
         * * .annotation: Java > Annotations > Annotation name
         * * .string: Java > String > String text
         * * .number: Java > Numbers
         * * .keyword: Java > Keyword
         * * .caretline: General > Editor > Caret row (Background)
         * * .lineno: For color, General > Code > Line number,
         *   Foreground, and for background-color,
         *   Editor > Gutter background
         * * .error: General > Errors and Warnings > Error
         * * .warning: General > Errors and Warnings > Warning
         * * text-decoration: none;\n"
         */
        @Suppress("ConstantConditionIf")
        private val cssSyntaxColorsLightTheme: String
            get() = (
                    """
pre.errorlines {
    background-color: white;
    font-family: monospace;
    border: 1px solid #e0e0e0;
    line-height: 0.9rem;
    font-size: 0.9rem;    padding: 1px 0px 1px; 1px;
    overflow: scroll;
}
.prefix {
    color: #660e7a;
    font-weight: bold;
}
.attribute {
    color: #0000ff;
    font-weight: bold;
}
.value {
    color: #008000;
    font-weight: bold;
}
.tag {
    color: #000080;
    font-weight: bold;
}
.comment {
    color: #808080;
    font-style: italic;
}
.javadoc {
    color: #808080;
    font-style: italic;
}
.annotation {
    color: #808000;
}
.string {
    color: #008000;
    font-weight: bold;
}
.number {
    color: #0000ff;
}
.keyword {
    color: #000080;
    font-weight: bold;
}
.caretline {
    background-color: #fffae3;
}
.lineno {
    color: #999999;
    background-color: #f0f0f0;
}
.error {
""" +
                            (
                                    if (USE_WAVY_UNDERLINES_FOR_ERRORS) if (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES) """    text-decoration: underline wavy #ff0000;
    text-decoration-color: #ff0000;
    -webkit-text-decoration-color: #ff0000;
    -moz-text-decoration-color: #ff0000;
""" else """    display: inline-block;
    position:relative;
    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFR4T/3uLMgAAADxJREFUCNdNyLERQEAABMCjL4lQwIzcjErpguAL+C9AvgKJDbeD/PRpLdm35Hm+MU+cB+tCKaJW4L4YBy+CAiLJrFs9mgAAAABJRU5ErkJggg==) bottom repeat-x;
""" else """    text-decoration: none;
    background-color: #f8d8d8;
"""
                                    ) +
                            "}\n" +
                            ".warning {\n" +
                            "    text-decoration: none;\n" +
                            "    background-color: #f6ebbc;\n" +
                            "}\n"
                    )

        @Suppress("ConstantConditionIf")
        private val cssSyntaxColorsDarcula: String
            get() = (
                    """pre.errorlines {
    background-color: #2b2b2b;
    color: #a9b7c6;
    font-family: monospace;
    font-size: 0.9rem;    line-height: 0.9rem;
    padding: 6px;
    border: 1px solid #e0e0e0;
    overflow: scroll;
}
.prefix {
    color: #9876aa;
}
.attribute {
    color: #BABABA;
}
.value {
    color: #6a8759;
}
.tag {
    color: #e8bf6a;
}
.comment {
    color: #808080;
}
.javadoc {
    font-style: italic;
    color: #629755;
}
.annotation {
    color: #BBB529;
}
.string {
    color: #6a8759;
}
.number {
    color: #6897bb;
}
.keyword {
    color: #cc7832;
}
.caretline {
    background-color: #323232;
}
.lineno {
    color: #606366;
    background-color: #313335;
}
.error {
""" +
                            (
                                    if (USE_WAVY_UNDERLINES_FOR_ERRORS) if (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES) """    text-decoration: underline wavy #ff0000;
    text-decoration-color: #ff0000;
    -webkit-text-decoration-color: #ff0000;
    -moz-text-decoration-color: #ff0000;
""" else """    display: inline-block;
    position:relative;
    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFR46vckTXgAAAEBJREFUCNdj1NbW/s+ABJj4mJgYork5GNgZGSECYVzsDKd+/WaI5uZgEGVmYmBZ9e0nw6d//xg+/vvJEM7FwQAAPnUOmQBDSmAAAAAASUVORK5CYII=) bottom repeat-x;
""" else """    text-decoration: none;
    background-color: #52503a;
"""
                                    ) +
                            "}\n" +
                            ".warning {\n" +
                            "    text-decoration: none;\n" +
                            "    background-color: #52503a;\n" +
                            "}\n"
                    )

        /** Solarized theme. */
        @Suppress("ConstantConditionIf")
        private val cssSyntaxColorsSolarized: String
            get() = (
                    """pre.errorlines {
    background-color: #FDF6E3;
    color: #586E75;
    font-family: monospace;
    font-size: 0.9rem;    line-height: 0.9rem;
    padding: 0px;
    border: 1px solid #e0e0e0;
    overflow: scroll;
}
.prefix {
    color: #6C71C4;
}
.attribute {
}
.value {
    color: #2AA198;
}
.tag {
    color: #268BD2;
}
.comment {
    color: #DC322F;
}
.javadoc {
    font-style: italic;
    color: #859900;
}
.annotation {
    color: #859900;
}
.string {
    color: #2AA198;
}
.number {
    color: #CB4B16;
}
.keyword {
    color: #B58900;
}
.caretline {
    background-color: #EEE8D5;
}
.lineno {
    color: #93A1A1;
    background-color: #EEE8D5;
}
.error {
""" + // General > Errors and Warnings > Error
                            (
                                    if (USE_WAVY_UNDERLINES_FOR_ERRORS) if (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES) """    text-decoration: underline wavy #DC322F;
    text-decoration-color: #DC322F;
    -webkit-text-decoration-color: #DC322F;
    -moz-text-decoration-color: #DC322F;
""" else """    display: inline-block;
    position:relative;
    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAYAAAC09K7GAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFRgHs/v4yQAAAD5JREFUCNcBMwDM/wDqe2//++zZ//324v/75NH/AgxKRgDuho8A/OTnAO2KkwAA/fbi//nXxf/mZlz/++TR/4EMI0ZH4MfyAAAAAElFTkSuQmCC) bottom repeat-x;
""" else """    text-decoration: none;
    color: #073642;
    background-color: #FFA0A3;
"""
                                    ) + // not from theme
                            "}\n" +
                            ".warning {\n" + // General > Errors and Warnings > Warning
                            "    text-decoration: none;\n" +
                            "    color: #073642;\n" +
                            "    background-color: #FFDF80;\n" +
                            "}\n"
                    )
        private var cssSyntaxColors: String =
            "" // set by initializePreferences() called from init { }

        /**
         * Stylesheet for the HTML report. Note that the
         * [LintSyntaxHighlighter] also depends on these class names.
         */
        val cssStyles: String
            get() =
                """section.section--center {
    max-width: 860px;
}
.mdl-card__supporting-text + .mdl-card__actions {
    border-top: 1px solid rgba(0, 0, 0, 0.12);
}
main > .mdl-layout__tab-panel {
  padding: 8px;
  padding-top: 48px;
}

.mdl-card__actions {
    margin: 0;
    padding: 4px 40px;
    color: inherit;
}
.mdl-card > * {
    height: auto;
}
.mdl-card__actions a {
    color: #00BCD4;
    margin: 0;
}
.error-icon {
    color: #bb7777;
    vertical-align: bottom;
}
.warning-icon {
    vertical-align: bottom;
}
.mdl-layout__content section:not(:last-of-type) {
  position: relative;
  margin-bottom: 48px;
}

.mdl-card .mdl-card__supporting-text {
  margin: 40px;
  -webkit-flex-grow: 1;
      -ms-flex-positive: 1;
          flex-grow: 1;
  padding: 0;
  color: inherit;
  width: calc(100% - 80px);
}
div.mdl-layout__drawer-button .material-icons {
    line-height: 48px;
}
.mdl-card .mdl-card__supporting-text {
    margin-top: 0px;
}
.chips {
    float: right;
    vertical-align: middle;
}
$cssSyntaxColors.overview {
    padding: 10pt;
    width: 100%;
    overflow: auto;
    border-collapse:collapse;
}
.overview tr {
    border-bottom: solid 1px #eeeeee;
}
.categoryColumn a {
     text-decoration: none;
     color: inherit;
}
.countColumn {
    text-align: right;
    padding-right: 20px;
    width: 50px;
}
.issueColumn {
   padding-left: 16px;
}
.categoryColumn {
   position: relative;
   left: -50px;
   padding-top: 20px;
   padding-bottom: 5px;
}
.options {
   padding-left: 16px;
}
"""

        /**
         * Sorts the list of warnings into a list of lists where each
         * list contains warnings for the same base issue type.
         */
        private fun computeIssueLists(issues: List<Incident>): List<List<Incident>> {
            var previousIssue: Issue? = null
            val related: MutableList<List<Incident>> = ArrayList()
            if (issues.isNotEmpty()) {
                var currentList: MutableList<Incident>? = null
                for (incident in issues) {
                    if (incident.issue !== previousIssue) {
                        previousIssue = incident.issue
                        currentList = ArrayList()
                        related.add(currentList)
                    }
                    currentList?.add(incident)
                }
            }
            return related
        }

        private fun getCardId(cardNumber: Int): String {
            return "card$cardNumber"
        }

        private fun getExplanationId(issue: Issue): String {
            return "explanation" + issue.id
        }

        /**
         * Returns the density for the given file, if known (e.g. in a
         * density folder, such as drawable-mdpi.
         */
        private fun getDensity(file: File): Int {
            val parent = file.parentFile
            if (parent != null) {
                val name = parent.name
                val configuration =
                    FolderConfiguration.getConfigForFolder(name)
                if (configuration != null) {
                    val qualifier = configuration.densityQualifier
                    if (qualifier != null && !qualifier.hasFakeValue()) {
                        val density = qualifier.value
                        if (density != null) {
                            return density.dpiValue
                        }
                    }
                }
            }
            return 0
        }

        init {
            initializePreferences()
        }

        fun initializePreferences() {
            val preferences = System.getenv(REPORT_PREFERENCE_ENV_VAR)
                ?: System.getProperty(REPORT_PREFERENCE_PROPERTY)
            var codeWindowSize = 3
            var splitLimit = 8
            var maxCount = 50
            var underlineErrors = true
            if (preferences != null) {
                for (
                pref in Splitter.on(',')
                    .omitEmptyStrings().split(preferences)
                ) {
                    val index: Int = pref.indexOf('=')
                    if (index != -1) {
                        val key: String = pref.substring(0, index).trim()
                        val value: String = pref.substring(index + 1).trim()
                        when (key) {
                            "theme" -> preferredThemeName = value
                            "window" -> {
                                try {
                                    val size =
                                        Integer.decode(value)
                                    if (size in 1..3000) {
                                        codeWindowSize = size
                                    }
                                } catch (ignore: NumberFormatException) {
                                }
                            }

                            "maxPerIssue", "splitLimit" -> {
                                try {
                                    val count =
                                        Integer.decode(value)
                                    if (count in 1..3000) {
                                        splitLimit = count
                                    }
                                } catch (ignore: NumberFormatException) {
                                }
                            }

                            "maxIncidents" -> {
                                try {
                                    maxCount = max(1, Integer.decode(value))
                                } catch (ignore: NumberFormatException) {
                                }
                            }

                            "underlineErrors" -> underlineErrors = value.toBoolean()
                        }
                    }
                }
            }

            SPLIT_LIMIT = splitLimit
            MAX_COUNT = maxCount
            SHOWN_COUNT = max(1, SPLIT_LIMIT - 3)
            CODE_WINDOW_SIZE = codeWindowSize
            USE_WAVY_UNDERLINES_FOR_ERRORS = underlineErrors

            val css: String = when (preferredThemeName) {
                "darcula" -> cssSyntaxColorsDarcula
                "solarized" -> cssSyntaxColorsSolarized
                "light" -> cssSyntaxColorsLightTheme
                else -> cssSyntaxColorsLightTheme
            }
            cssSyntaxColors = css
        }
    }
}
