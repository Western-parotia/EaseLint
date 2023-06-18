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

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_LINT_JAR
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_RESOURCES
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.LintCliClient.Companion.printWriter
import com.android.tools.lint.LintCliFlags.ERRNO_ERRORS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.LintCliFlags.ERRNO_USAGE
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.CompositeIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.JarFileIssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.formatList
import com.android.tools.lint.detector.api.splitPath
import com.android.utils.SdkUtils.wrap
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.File
import java.io.File.pathSeparator
import java.io.File.separator
import java.io.File.separatorChar
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.EnumSet
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

/**
 * Generates markdown pages documenting lint checks.
 *
 * There is more documentation for this tool in
 * lint/docs/internal/document-checks.md.html
 */
class LintIssueDocGenerator constructor(
    private val output: File,
    private val registry: IssueRegistry,
    private val onlyIssues: List<String>,
    private val singleDoc: Boolean,
    private val format: DocFormat,
    private val includeStats: Boolean,
    private val sourcePath: Map<String, List<File>>,
    private val testPath: Map<String, List<File>>,
    private var includeIndices: Boolean,
    private var includeSuppressInfo: Boolean,
    private var includeExamples: Boolean,
    private var includeSourceLinks: Boolean,
    private var includeSeverityColor: Boolean
) {
    private val aliases: Map<String, Issue>

    init {
        val aliases = mutableMapOf<String, Issue>()
        for (issue in registry.issues) {
            issue.getAliases()?.forEach { aliases[it] = issue }
        }
        this.aliases = aliases
    }

    private val singleIssueDetectors: Set<Issue>

    init {
        val all = mutableMapOf<Class<*>, Int>()
        val singleIssueDetectors: MutableSet<Issue> = mutableSetOf()
        for (issue in registry.issues) {
            val clz = issue.implementation.detectorClass
            all[clz] = (all[clz] ?: 0) + 1
        }
        for (issue in registry.issues) {
            val clz = issue.implementation.detectorClass
            val count = all[clz] ?: 0
            if (count == 1) {
                singleIssueDetectors.add(issue)
            }
        }
        this.singleIssueDetectors = singleIssueDetectors
    }

    private val knownIds: Set<String> = registry.issues.map { it.id }.toSet()
    private val issues = registry.issues.filter { !skipIssue(it) }.toList()
    private var environment: UastEnvironment = createUastEnvironment()
    private val issueMap = analyzeSource()

    fun generate() {
        checkIssueFilter()

        if (singleDoc) {
            writeSinglePage()
        } else {
            for (issue in issues.filter { !skipIssue(it) }) {
                writeIssuePage(issue)
            }

            if (includeIndices) {
                for (type in IndexType.values()) {
                    writeIndexPage(type)
                }
            }

            if (includeStats) {
                writeStatsPage()
            }

            for (id in registry.deletedIssues.filter { !skipIssue(it) }) {
                writeDeletedIssuePage(id)
            }
        }

        disposeUastEnvironment()
    }

    private fun checkIssueFilter() {
        for (id in onlyIssues) {
            if (!knownIds.contains(id)) {
                println("Warning: The issue registry does not contain an issue with the id `$id`")
            }
        }
    }

    private fun skipIssue(issue: Issue): Boolean = skipIssue(issue.id)

    private fun skipIssue(id: String): Boolean {
        return onlyIssues.isNotEmpty() && !onlyIssues.contains(id)
    }

    private fun writeSinglePage() {
        val sb = StringBuilder()
        sb.append(format.header)

        sb.append("# Lint Issues\n")

        if (registry is BuiltinIssueRegistry) {
            sb.append("This document lists the built-in issues for Lint. Note that lint also reads additional\n")
            sb.append("checks directly bundled with libraries, so this is a subset of the checks lint will\n")
            sb.append("perform.\n")
        }

        val categories = getCategories(issues)

        if (includeStats) {
            writeStats(sb, categories, issues)
        }

        for (category in categories.keys.sorted()) {
            sb.append("\n## ${category.fullName}\n\n")
            val categoryIssues = categories[category]?.sorted() ?: continue
            for (issue in categoryIssues) {
                describeIssue(sb, issue)
            }
        }

        sb.append(format.footer)
        output.writeText(sb.trim().toString())
    }

    private fun getCategories(issues: List<Issue>): HashMap<Category, MutableList<Issue>> {
        val categories = HashMap<Category, MutableList<Issue>>()
        for (issue in issues) {
            val category = issue.category
            val list = categories[category]
                ?: ArrayList<Issue>().also { categories[category] = it }
            list.add(issue)
        }
        return categories
    }

    private fun getVendors(issues: List<Issue>): HashMap<Vendor, MutableList<Issue>> {
        val unknown = Vendor("Unknown Vendor")
        val vendors = HashMap<Vendor, MutableList<Issue>>()
        for (issue in issues) {
            val vendor = issue.vendor ?: issue.registry?.vendor ?: unknown
            val list = vendors[vendor]
                ?: ArrayList<Issue>().also { vendors[vendor] = it }
            list.add(issue)
        }
        return vendors
    }

    private fun getYears(issues: List<Issue>): HashMap<Int, MutableList<Issue>> {
        val years = HashMap<Int, MutableList<Issue>>()
        for (issue in issues) {
            val year = issueMap[issue.id]?.copyrightYear ?: -1
            val list = years[year]
                ?: ArrayList<Issue>().also { years[year] = it }
            list.add(issue)
        }
        return years
    }

    private fun writeStats(
        sb: StringBuilder,
        categories: HashMap<Category, MutableList<Issue>>,
        issues: List<Issue>
    ) {
        sb.append("\n## Vital Stats\n")

        sb.append(
            "Current As Of\n:   ${
                DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
            }\n"
        )
        sb.append("Category Count\n:   ${categories.keys.size}\n")
        sb.append("Issue Count\n:   ${issues.size}\n")
        sb.append("Informational\n:   ${issues.count { it.defaultSeverity == Severity.INFORMATIONAL }}\n")
        sb.append("Warnings\n:   ${issues.count { it.defaultSeverity == Severity.WARNING }}\n")
        sb.append("Errors\n:   ${issues.count { it.defaultSeverity == Severity.ERROR }}\n")
        sb.append("Fatal\n:   ${issues.count { it.defaultSeverity == Severity.FATAL }}\n")
        sb.append("Disabled By Default\n:   ${issues.count { !it.isEnabledByDefault() }}\n")
        sb.append("Android Specific\n:   ${issues.count { it.isAndroidSpecific() }}\n")
        sb.append("General\n:   ${issues.count { !it.isAndroidSpecific() }}\n")
    }

    private fun wrap(text: String): String {
        val lineWidth = 72
        val lines = text.split("\n")
        val sb = StringBuilder()
        var inPreformat = false
        var prev = ""
        for (line in lines) {
            if (line.startsWith("```")) {
                inPreformat = !inPreformat
                sb.append(line).append('\n')
            } else if (inPreformat) {
                sb.append(line).append('\n')
            } else if (line.isBlank()) {
                sb.append('\n')
            } else if (line.length < lineWidth) {
                sb.append(line).append('\n')
            } else {
                if (line.isListItem()) {
                    if (format == DocFormat.MARKDEEP && prev.isNotBlank() && !prev.isListItem()) {
                        sb.append('\n')
                    }
                    val hangingIndent = "  "
                    val nextLineWidth = lineWidth - hangingIndent.length
                    sb.append(wrap(line, lineWidth, nextLineWidth, hangingIndent, false))
                } else {
                    sb.append(wrap(line, lineWidth, lineWidth, "", false))
                }
            }
            prev = line
        }

        return sb.toString()
    }

    private fun writeIssuePage(issue: Issue) {
        assert(!singleDoc)
        val file = File(output, format.getFileName(issue))
        val sb = StringBuilder(1000)
        sb.append(format.header)
        describeIssue(sb, issue)
        sb.append("\n")
        sb.append(format.footer)
        file.writeText(sb.trim().toString())
    }

    private fun writeDeletedIssuePage(id: String) {
        assert(!singleDoc)
        val file = File(output, format.getFileName(id))
        val sb = StringBuilder(1000)
        sb.append(format.header)
        if (format == DocFormat.MARKDEEP) {
            sb.append("(#)")
        } else {
            sb.append("#")
        }
        sb.append(" $id\n\n")

        val now = aliases[id]

        if (now != null) {
            sb.append(
                wrap(
                    "This issue id is an alias for [${now.id}](${now.id}${format.extension})."
                )
            )
        } else {
            sb.append(
                wrap(
                    "The issue for this id has been deleted or marked obsolete and can " +
                            "now be ignored."
                )
            )
        }

        registry.vendor?.let { vendor ->
            vendor.vendorName?.let { "Vendor: $it\n" }
            vendor.identifier?.let { "Identifier: $it\n" }
            vendor.contact?.let { "Contact: $it\n" }
            vendor.feedbackUrl?.let { "Feedback: $it\n" }
        }
        sb.append("\n(Additional metadata not available.)\n")
        sb.append(format.footer)
        file.writeText(sb.trim().toString())
    }

    private enum class IndexType(val label: String, val filename: String) {
        ALPHABETICAL("Alphabetical", "index"),
        CATEGORY("By category", "categories"),
        VENDOR("By vendor", "vendors"),
        SEVERITY("By severity", "severity"),
        YEAR("By year", "year")
    }

    private fun writeIndexPage(type: IndexType) {
        val sb = StringBuilder()
        sb.append(format.header)
        if (format == DocFormat.MARKDEEP) {
            sb.append("(#) ")
        } else {
            sb.append("# ")
        }
        sb.append("Lint Issue Index\n\n")
        sb.append("Order: ")

        val bullet = "* "

        for (t in IndexType.values()) {
            if (t != type) {
                sb.append("[${t.label}]")
                sb.append("(${t.filename}${format.extension})")
            } else {
                sb.append(t.label)
            }
            sb.append(" | ")
        }
        sb.setLength(sb.length - 3) // truncate last " | "
        sb.append("\n")

        if (type == IndexType.CATEGORY) {
            val categories = getCategories(issues)
            for (category in categories.keys.sorted()) {
                val categoryIssues = categories[category]?.sorted() ?: continue
                sb.append(
                    "\n$bullet${
                        category.fullName.replace(
                            ":",
                            ": "
                        )
                    } (${categoryIssues.size})\n\n"
                )
                for (issue in categoryIssues) {
                    val id = issue.id
                    val summary = issue.getBriefDescription(TextFormat.RAW)
                    sb.append("  - [$id: $summary]($id${format.extension})\n")
                }
            }
        } else if (type == IndexType.ALPHABETICAL) {
            sb.append("\n")
            for (issue in issues.sortedBy { it.id }) {
                val id = issue.id
                val summary = issue.getBriefDescription(TextFormat.RAW)
                sb.append("  - [$id: $summary]($id${format.extension})\n")
            }
        } else if (type == IndexType.SEVERITY) {
            sb.append("\n")
            for (severity in Severity.values().reversed()) {
                val applicable = issues.filter { it.defaultSeverity == severity }.toList()
                if (applicable.isNotEmpty()) {
                    sb.append("\n$bullet${severity.description} (${applicable.size})\n\n")
                    for (issue in applicable.sorted()) {
                        val id = issue.id
                        val summary = issue.getBriefDescription(TextFormat.RAW)
                        sb.append("  - [$id: $summary]($id${format.extension})\n")
                    }
                }
            }

            val disabled = issues.filter { !it.isEnabledByDefault() }.sortedBy { it.id }.toList()
            if (disabled.isNotEmpty()) {
                sb.append("\n${bullet}Disabled By Default (${disabled.size})\n\n")
                for (id in disabled) {
                    sb.append("  - [$id]($id${format.extension})\n")
                }
            }
        } else if (type == IndexType.VENDOR) {
            val vendors = getVendors(issues)
            for (vendor in vendors.keys.sortedBy { it.describe(TextFormat.RAW) }) {
                val vendorIssues = vendors[vendor]?.sortedBy { it.id } ?: continue
                val vendorName = vendor.vendorName
                val identifier = vendor.identifier
                sb.append("\n$bullet")
                if (vendor == IssueRegistry.AOSP_VENDOR && vendors[vendor]?.first()?.registry is BuiltinIssueRegistry) {
                    sb.append("Built In (${vendorIssues.size})")
                } else {
                    sb.append(vendorName ?: vendor.contact ?: identifier ?: vendor.feedbackUrl)
                    if (vendorName != null && identifier != null && !vendorName.contains(identifier)) {
                        sb.append(" ($identifier)")
                    }
                    sb.append(" (${vendorIssues.size})")
                }
                sb.append("\n\n")
                for (issue in vendorIssues) {
                    val id = issue.id
                    val summary = issue.getBriefDescription(TextFormat.RAW)
                    sb.append("  - [$id: $summary]($id${format.extension})\n")
                }
            }
        } else if (type == IndexType.YEAR) {
            val years = getYears(issues)
            for (year in years.keys.sortedByDescending { it }) {
                val issuesFromYear = years[year]?.sortedBy { it.id } ?: continue
                sb.append("\n$bullet")
                if (year == -1) {
                    sb.append("Unknown (${issuesFromYear.size})")
                } else {
                    sb.append("$year (${issuesFromYear.size})")
                }
                sb.append("\n\n")
                for (issue in issuesFromYear) {
                    val id = issue.id
                    val summary = issue.getBriefDescription(TextFormat.RAW)
                    sb.append("  - [$id: $summary]($id${format.extension})\n")
                }
            }
        }

        val deleted = registry.deletedIssues.filter { !skipIssue(it) }
        if (deleted.isNotEmpty()) {
            sb.append("\n${bullet}Withdrawn or Obsolete Issues (${deleted.size})\n\n")
            for (id in deleted) {
                sb.append("  - [$id]($id${format.extension})\n")
            }
        }

        sb.append(format.footer)
        File(output, "${type.filename}${format.extension}").writeText(sb.trim().toString())
    }

    private fun writeStatsPage() {
        val categories = getCategories(issues)
        val sb = StringBuilder()
        sb.append(format.header)
        writeStats(sb, categories, issues)
        sb.append(format.footer)
        File(output, "stats${format.extension}").writeText(sb.trim().toString().trimIndent())
    }

    @Suppress("DefaultLocale")
    private fun describeIssue(sb: StringBuilder, issue: Issue) {
        val id = issue.id
        val vendor = issue.vendor ?: issue.registry?.vendor
        val implementation = issue.implementation

        // TODO: Convert TextFormat.RAW to TextFormat.MARKDEEP
        val isMarkDeep = this.format == DocFormat.MARKDEEP
        val format = TextFormat.RAW
        val description = issue.getBriefDescription(format)

        val enabledByDefault = issue.isEnabledByDefault()
        // Note that priority isn't very useful in practice (the one remaining
        // use is to include it in result sorting) so we don't include it here
        val severity = issue.defaultSeverity.description
        val category = issue.category.fullName.replace(":", ": ")
        val vendorInfo = listOfNotNull(
            vendor?.vendorName?.let { "Vendor" to it },
            vendor?.identifier?.let { "Identifier" to it },
            vendor?.contact?.let {
                if (vendor != IssueRegistry.AOSP_VENDOR) "Contact" to it else null
            },
            vendor?.feedbackUrl?.let { "Feedback" to it },
        ).toTypedArray()
        val explanation = issue.getExplanation(format).let {
            if (it.trimEnd().lastOrNull()?.isLetter() == true) {
                "$it."
            } else {
                it
            }
        }
        val copyrightYear = issueMap[issue.id]?.copyrightYear ?: -1
        val copyrightYearInfo = if (copyrightYear != -1) {
            arrayOf("Copyright Year" to copyrightYear.toString())
        } else {
            emptyArray()
        }

        val platforms = when (issue.platforms) {
            Platform.ANDROID_SET -> "Android"
            Platform.JDK_SET -> "JDK"
            else -> "Any"
        }

        val appliesTo = mutableListOf<String>()
        val scope = implementation.scope
        for (type in scope) {
            val desc = when (type!!) {
                Scope.ALL_RESOURCE_FILES,
                Scope.RESOURCE_FILE -> "resource files"

                Scope.BINARY_RESOURCE_FILE -> "binary resource files"
                Scope.RESOURCE_FOLDER -> "resource folders"
                Scope.ALL_JAVA_FILES,
                Scope.JAVA_FILE -> "Kotlin and Java files"

                Scope.ALL_CLASS_FILES,
                Scope.CLASS_FILE -> "class files"

                Scope.MANIFEST -> "manifest files"
                Scope.PROGUARD_FILE -> "shrinking configuration files"
                Scope.JAVA_LIBRARIES -> "library bytecode"
                Scope.GRADLE_FILE -> "Gradle build files"
                Scope.PROPERTY_FILE -> "property files"
                Scope.TEST_SOURCES -> "test sources"
                Scope.OTHER -> continue
            }
            if (!appliesTo.contains(desc)) {
                appliesTo.add(desc)
            }
        }
        val scopeDescription = if (!scope.contains(Scope.OTHER) && scope != Scope.ALL) {
            formatList(appliesTo, useConjunction = true).capitalize()
        } else {
            null
        }

        val enable = if (!enabledByDefault) {
            "**This issue is disabled by default**; use `--enable $id`"
        } else {
            null
        }

        val onTheFly = canAnalyzeInEditor(issue)
        val inEditor = if (onTheFly)
            "This check runs on the fly in the IDE editor"
        else
            "This check can *not* run live in the IDE editor"

        val moreInfo = issue.moreInfo
        if (moreInfo.isNotEmpty()) {
            // Ensure that the links are unique
            if (moreInfo.size != moreInfo.toSet().size) {
                println("Warning: Multiple identical moreInfo links for issue $id")
            }
        }

        val deleted = registry.deletedIssues
        val aliases = aliases.filter { it.value == issue }.map {
            val name = if (deleted.contains(it.key)) "Previously" else "Alias"
            Pair(name, it.key)
        }.toTypedArray()
        val moreInfoUrls = moreInfo.map { Pair("See", it) }.toTypedArray()
        val sourceUrls = if (includeSourceLinks) findSourceFiles(issue) else emptyArray()

        val array = arrayOf(
            "Id" to "`$id`",
            *aliases,
            "Summary" to description,
            "Note" to enable,
            "Severity" to severity,
            "Category" to category,
            "Platform" to platforms,
            *vendorInfo,
            "Affects" to scopeDescription,
            "Editing" to inEditor,
            *moreInfoUrls,
            *sourceUrls,
            *copyrightYearInfo
        )

        val table = if (isMarkDeep) markdeepTable(*array) else markdownTable(*array)
        if (singleDoc) {
            sb.append("###")
        } else {
            if (isMarkDeep) {
                sb.append("(#)")
            } else {
                sb.append("#")
            }
        }
        sb.append(" $description\n")
        sb.append("\n")

        if (isMarkDeep && includeSeverityColor) {
            when (issue.defaultSeverity) {
                Severity.FATAL -> sb.append(
                    """
                        !!! ERROR: $description
                           This is an error, and is also enforced at build time when
                           supported by the build system. For Android this means it will
                           run during release builds.
                    """.trimIndent()
                )

                Severity.ERROR -> sb.append(
                    """
                        !!! ERROR: $description
                           This is an error.
                    """.trimIndent()
                )

                Severity.WARNING -> sb.append(
                    """
                        !!! WARNING: $description
                           This is a warning.
                    """.trimIndent()
                )

                Severity.INFORMATIONAL -> sb.append(
                    """
                        !!! Tip: $description
                           Advice from this check is just a tip.
                    """.trimIndent()
                )

                Severity.IGNORE -> {
                }
            }
            sb.append("\n\n")
        }

        sb.append(table)
        sb.append("\n")

        sb.append(wrap(explanation))
        sb.append("\n")

        val issueData = issueMap[id]
        if (issueData?.quickFixable == true) {
            sb.append(
                "!!! Tip\n" +
                        "   This lint check has an associated quickfix available in the IDE.\n\n"
            )
        }

        val options = issue.getOptions()
        if (options.isNotEmpty()) {
            writeOptions(sb, issue, options)
        }

        if (includeExamples) {
            issueData?.example?.let { example ->
                writeExample(sb, issueData, example, issue)
                sb.append("\n")
            }
        }

        if (includeSuppressInfo) {
            writeSuppressInfo(sb, issue, id, issueData?.suppressExample)
        }
    }

    private fun writeOptions(sb: StringBuilder, issue: Issue, options: List<Option>) {
        sb.append("(##) Options\n\n")
        sb.append("You can configure this lint checks using the following options:\n\n")
        for (option in options) {
            sb.append("(###) ").append(option.name).append("\n\n")
            sb.append(option.getDescription()).append(".\n")
            val explanation = option.getExplanation() ?: ""
            if (explanation.isNotBlank()) {
                sb.append(explanation).append("\n")
            }
            sb.append("\n")
            val defaultValue = option.defaultAsString()
            if (defaultValue != null) {
                sb.append("Default is ").append(defaultValue).append(".\n")
            }
            sb.append("\n")
            sb.append("Example `lint.xml`:\n\n")
            sb.append(
                "" +
                        "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~xml linenumbers\n" +
                        "&lt;lint&gt;\n" +
                        "    &lt;issue id=\"${issue.id}\"&gt;\n" +
                        "        &lt;option name=\"${option.name}\" value=\"${defaultValue ?: "some string"}\" /&gt;\n" +
                        "    &lt;/issue&gt;\n" +
                        "&lt;/lint&gt;\n" +
                        "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n\n"
            )
        }
    }

    private fun writeCodeLine(
        sb: StringBuilder,
        language: String = "",
        lineNumbers: Boolean = false
    ) {
        if (format == DocFormat.MARKDEEP) {
            val max = 70 - language.length - if (lineNumbers) " linenumbers".length else 0
            for (i in 0 until max) {
                sb.append('~')
            }
            sb.append(language)
            if (lineNumbers) {
                sb.append(" linenumbers")
            }
            sb.append('\n')
        } else {
            sb.append("```$language\n")
        }
    }

    private fun writeExample(
        sb: StringBuilder,
        issueData: IssueData,
        example: Example,
        issue: Issue
    ) {
        sb.append("(##) Example\n")
        sb.append('\n')
        sb.append("Here is an example of lint warnings produced by this check:\n")
        writeCodeLine(sb, "text")
        sb.append(example.output)
        writeCodeLine(sb)
        sb.append('\n')

        if (example.files.size == 1) {
            sb.append("Here is the source file referenced above:\n\n")
        } else {
            sb.append("Here are the relevant source files:\n\n")
        }
        writeSourceFiles(issue, example, sb)

        if (issueData.testUrl != null && includeSourceLinks) {
            sb.append(
                "" +
                        "You can also visit the\n" +
                        "[source code](${issueData.testUrl})\n" +
                        "for the unit tests for this check to see additional scenarios.\n"
            )
        }

        if (example.inferred) {
            writeInferredExampleMessage(sb, example, issue)
        }
    }

    private fun writeSourceFiles(
        issue: Issue,
        example: Example,
        sb: StringBuilder
    ) {
        for (file in example.files) {
            val contents = file.source.let {
                // A lot of builtin tests use the older android.support.annotation namespace,
                // but in tests we want to show the more recommended name androidx.annotation.
                // We only perform this substitution for built-in checks where we're sure
                // the checks aren't doing anything android.support.annotation-specific.
                if (issue.registry is BuiltinIssueRegistry) {
                    convertToAndroidX(it)
                } else {
                    it
                }
            }
            val lang = file.language

            sb.append("`${file.path}`:\n")
            writeCodeLine(sb, lang, lineNumbers = true)
            sb.append(contents).append('\n')
            writeCodeLine(sb)
            sb.append("\n")
        }
    }

    private fun convertToAndroidX(original: String): String {
        var s = original.replace("android.support.annotation.", "androidx.annotation.")
        if (!s.contains("android.support.")) {
            return s
        }
        while (true) {
            val matcher = ANDROID_SUPPORT_SYMBOL_PATTERN.matcher(s)
            if (!matcher.find()) {
                return s
            }

            val name = matcher.group(0)
            val newName = AndroidxNameUtils.getNewName(name)
            if (newName == name) {
                // Couldn't find a replacement; give up
                return s
            }
            s = s.substring(0, matcher.start()) + newName + s.substring(matcher.end())
        }
    }

    private fun writeInferredExampleMessage(
        sb: StringBuilder,
        example: Example,
        issue: Issue
    ) {
        sb.append(
            "\nThe above example was automatically extracted from the first unit test\n" +
                    "found for this lint check, `${example.testClass}.${example.testMethod}`.\n"
        )
        val vendor = issue.vendor ?: issue.registry?.vendor
        if (vendor != null) {
            sb.append("To report a problem with this extracted sample, ")
            val url = vendor.feedbackUrl
            if (url != null) {
                sb.append("visit\n$url.\n")
            } else {
                val contact = vendor.contact ?: vendor.vendorName
                sb.append("contact\n$contact.\n")
            }
        }
    }

    private fun writeSuppressInfo(
        sb: StringBuilder,
        issue: Issue,
        id: String,
        example: Example?
    ) {
        sb.append("(##) Suppressing\n\n")

        val suppressNames = issue.suppressNames
        if (suppressNames != null) {
            if (suppressNames.isEmpty()) {
                sb.append(
                    wrap(
                        "This check has been explicitly marked as **not suppressible** by the check " +
                                "author. This is usually only done in cases where a company has a check in place to " +
                                "enforce a no-exceptions policy."
                    )
                )
            } else {
                sb.append(
                    wrap(
                        "This check has been explicitly exempted from the normal suppression " +
                                "mechanisms in lint (`@Suppress`, `lint.xml`, baselines, etc). However, it can be " +
                                "disabled by annotating the element with " +
                                "${
                                    suppressNames.toList().sorted().joinToString(" or ") { "`$it`" }
                                }."
                    )
                )
            }
            return
        }

        fun listIndent(s: String) = wrap(s, 70, "  ")

        val issueScope = issue.implementation.scope
        sb.append("You can suppress false positives using one of the following mechanisms:\n")

        val issueData = issueMap[issue.id]
        val language =
            issueData?.suppressExample?.files?.firstOrNull {
                containsSuppress(
                    it.source,
                    issue
                )
            }?.language
                ?: issueData?.example?.files?.firstOrNull {
                    containsSuppress(
                        it.source,
                        issue
                    )
                }?.language
                ?: issueData?.example?.files?.firstOrNull()?.language

        val kotlinOrJava = issueScope.contains(Scope.JAVA_FILE)
        val resourceFile = issueScope.contains(Scope.RESOURCE_FILE)
        val manifestFile = issueScope.contains(Scope.MANIFEST)
        val gradle = issueScope.contains(Scope.GRADLE_FILE)
        val properties = issueScope.contains(Scope.PROPERTY_FILE)

        var annotation: String? = null
        var comment: String? = null
        var attribute: String? = null

        val detector = try {
            issue.implementation.detectorClass.getDeclaredConstructor().newInstance()
        } catch (ignore: Throwable) {
            null
        }

        if (kotlinOrJava) {
            val statement = detector?.getApplicableMethodNames()?.firstOrNull()?.let { "$it(...)" }
                ?: detector?.getApplicableConstructorTypes()?.firstOrNull()?.let {
                    "new ${it.substring(it.lastIndexOf('.') + 1)}(...)"
                }
                ?: "problematicStatement()"

            annotation =
                """
                        * Using a suppression annotation like this on the enclosing
                          element:

                          ```kt
                          // Kotlin
                          @Suppress("$id")
                          fun method() {
                             ${statement.replace("new ", "")}
                          }
                          ```

                          or

                          ```java
                          // Java
                          @SuppressWarnings("$id")
                          void method() {
                             $statement;
                          }
                          ```
                """.trimIndent()
        }

        if (kotlinOrJava || gradle) {
            comment =
                """
                        * Using a suppression comment like this on the line above:

                          ```kt
                          //noinspection $id
                          problematicStatement()
                          ```
                """.trimIndent()
        } else if (properties) {
            comment =
                """
                        * Using a suppression comment like this on the line above:

                          ```kt
                          #noinspection $id
                          key = problematic-value
                          ```
                """.trimIndent()
        }

        if (resourceFile || manifestFile) {
            attribute =
                listIndent(
                    "" +
                            "* Adding the suppression attribute `tools:ignore=\"$id\"` on the " +
                            "problematic XML element (or one of its enclosing elements). You may " +
                            "also need to add the following namespace declaration on the root " +
                            "element in the XML file if it's not already there: " +
                            "`xmlns:tools=\"http://schemas.android.com/tools\"`."
                ).trim()

            val tag: String? = detector?.getApplicableElements()?.firstOrNull()
            if (tag != null) {
                val root = if (resourceFile) {
                    when {
                        (detector as? ResourceXmlDetector)?.appliesTo(ResourceFolderType.VALUES) == true -> {
                            TAG_RESOURCES
                        }

                        manifestFile -> {
                            TAG_MANIFEST
                        }

                        else -> {
                            tag
                        }
                    }
                } else {
                    TAG_MANIFEST
                }
                val snippet = StringBuilder("\n\n")
                snippet.append("  ```xml\n")
                snippet.append("  $lt?xml version=\"1.0\" encoding=\"UTF-8\"?$gt\n")
                snippet.append("  $lt")
                snippet.append(root)
                snippet.append(" xmlns:tools=\"http://schemas.android.com/tools\"")
                if (root != tag) {
                    snippet.append("$gt\n")
                    snippet.append("      ...\n")
                    snippet.append("      $lt")
                    snippet.append(tag)
                    snippet.append(" ")
                    detector.getApplicableAttributes()?.firstOrNull()?.let {
                        snippet.append(it).append("=\"...\" ")
                    }
                } else {
                    snippet.append("\n      ")
                }
                snippet.append("tools:ignore=\"").append(id).append("\" ...")
                if (root != tag) {
                    snippet.append("/")
                }
                snippet.append("$gt\n")

                snippet.append("    ...\n")

                snippet.append("  $lt/")
                snippet.append(root)
                snippet.append("$gt\n")
                snippet.append("  ```")
                attribute += snippet
            }
        }

        val lintXml =
            """<?xml version="1.0" encoding="UTF-8"?>
                      <lint>
                          <issue id="$id" severity="ignore" />
                      </lint>"""

        val general =
            """
                    * Using a special `lint.xml` file in the source tree which turns off
                      the check in that folder and any sub folder. A simple file might look
                      like this:
                      ```xml
                      ${escapeXml(lintXml)}
                      ```
                      Instead of `ignore` you can also change the severity here, for
                      example from `error` to `warning`. You can find additional
                      documentation on how to filter issues by path, regular expression and
                      so on
                      [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

                    * In Gradle projects, using the DSL syntax to configure lint. For
                      example, you can use something like
                      ```gradle
                      lintOptions {
                          disable '$id'
                      }
                      ```
                      In Android projects this should be nested inside an `android { }`
                      block.

                    * For manual invocations of `lint`, using the `--ignore` flag:
                      ```
                      $ lint --ignore $id ...`
                      ```

                    * Last, but not least, using baselines, as discussed
                      [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).
            """.trimIndent()

        // Start with most likely suppress language, based on examples (because in some
        // cases the detector considers multiple types of files but only flags issues
        // in one file type, so we want to make it maximally likely that the message
        // here meets that expectation)
        val sorted = if (language == "java" || language == "kotlin")
            listOfNotNull(annotation, comment, attribute, general)
        else if (language == "xml") {
            listOfNotNull(attribute, annotation, comment, general)
        } else if (language == "groovy") {
            listOfNotNull(comment, annotation, attribute, general)
        } else {
            listOfNotNull(annotation, comment, attribute, general)
        }

        sorted.forEach {
            sb.append('\n').append(it).append('\n')
        }

        if (example != null) {
            sb.append("\n(###) Suppress Example\n\n")
            writeSourceFiles(issue, example, sb)
        }
    }

    private fun findSourceFiles(root: File): List<File> {
        val files = mutableListOf<File>()
        fun addSourceFile(file: File) {
            if (file.isFile) {
                val path = file.path
                if (path.endsWith(DOT_KT) || path.endsWith(DOT_JAVA)) {
                    files.add(file)
                }
            } else if (file.isDirectory) {
                file.listFiles()?.forEach {
                    addSourceFile(it)
                }
            }
        }
        addSourceFile(root)
        return files
    }

    private fun findSourceFiles(): Pair<Map<String, Map<File, List<File>>>, Map<String, Map<File, List<File>>>> {
        return Pair(
            findSourceFiles(sourcePath),
            findSourceFiles(testPath)
        )
    }

    /**
     * Map from URL prefixes (where an empty string is allowed) to
     * source roots to relative files below that root
     */
    private fun findSourceFiles(prefixMap: Map<String, List<File>>): Map<String, Map<File, List<File>>> {
        val map = mutableMapOf<String, MutableMap<File, MutableList<File>>>()
        for ((prefix, path) in prefixMap) {
            for (dir in path) {
                val sources = findSourceFiles(dir)
                val dirMap =
                    map[prefix] ?: mutableMapOf<File, MutableList<File>>().also { map[prefix] = it }
                val list = dirMap[dir] ?: mutableListOf<File>().also { dirMap[dir] = it }
                list.addAll(sources)
            }
        }
        return map
    }

    private fun initializeSources(
        issueData: IssueData,
        sources: Pair<Map<String, Map<File, List<File>>>, Map<String, Map<File, List<File>>>>
    ) {
        println("Analyzing ${issueData.issue.id}")
        val (sourceFiles, testFiles) = sources
        val (detectorClass, detectorName) = getDetectorRelativePath(issueData.issue)
        findSource(detectorClass, detectorName, sourceFiles) { file, url ->
            issueData.sourceUrl = url
            if (file != null) {
                issueData.detectorSource = file
                issueData.copyrightYear = findCopyrightYear(file)
            }
        }

        findSource(detectorClass, detectorName + "Test", testFiles) { file, url ->
            issueData.testUrl = url
            if (file != null) {
                issueData.detectorTestSource = file
                initializeExamples(issueData)
            }
        }
    }

    private fun findCopyrightYear(file: File): Int {
        val lines = file.readLines()
        for (line in lines) {
            if (line.contains("opyright") ||
                line.contains("(C)") ||
                line.contains("(c)") ||
                line.contains('\u00a9') // Copyright unicode symbol, ©
            ) {
                val matcher = YEAR_PATTERN.matcher(line)
                var start = 0
                var maxYear = -1
                // Match on all years on the line and take the max -- that way we handle
                //   (c) 2020 Android
                //   Copyright 2007-2020 Android
                //   © 2018, 2019, 2020, 2021 Android
                // etc.
                while (matcher.find(start)) {
                    val year = matcher.group(1).toInt()
                    maxYear = max(maxYear, year)
                    start = matcher.end()
                }

                if (maxYear != -1) {
                    return maxYear
                }
            }
        }

        // Couldn't find a copyright
        val prefix = lines.subList(0, min(8, lines.size)).joinToString("\n")
        println("Couldn't find copyright year in ${file.name} (${file.path}):\n$prefix\n\n")

        return -1
    }

    private fun findSourceFiles(issue: Issue): Array<Pair<String, String>> {
        val issueData = issueMap[issue.id] ?: return emptyArray()
        return listOfNotNull(
            issueData.sourceUrl?.let { url -> Pair("Implementation", "[Source Code]($url)") },
            issueData.testUrl?.let { url -> Pair("Tests", "[Source Code]($url)") }
        ).toTypedArray()
    }

    private fun getDetectorRelativePath(issue: Issue): Pair<Class<out Detector>, String> {
        val detectorClass = issue.implementation.detectorClass
        val detectorName = detectorClass.name.replace('.', '/').let {
            val innerClass = it.indexOf('$')
            if (innerClass == -1) it else it.substring(0, innerClass)
        }
        return Pair(detectorClass, detectorName)
    }

    private fun findSource(
        detectorClass: Class<out Detector>,
        detectorName: String,
        sourcePath: Map<String, Map<File, List<File>>>,
        store: (file: File?, url: String?) -> Unit
    ) {
        val relative = detectorName.replace('/', separatorChar)
        val relativeKt = relative + DOT_KT
        val relativeJava = relative + DOT_JAVA
        for ((prefix, path) in sourcePath) {
            for ((root, files) in path) {
                for (file in files) {
                    val filePath = file.path
                    if (filePath.endsWith(relativeKt) || filePath.endsWith(relativeJava)) {
                        assert(file.path.startsWith(root.path) && file.path.length > root.path.length)
                        val urlRelative =
                            file.path.substring(root.path.length).replace(separatorChar, '/')
                        // TODO: escape URL characters in the path?
                        val url = if (prefix.endsWith("/") && urlRelative.startsWith("/")) {
                            prefix + urlRelative.substring(1)
                        } else {
                            prefix + urlRelative
                        }
                        store(file, if (prefix.isEmpty()) null else url)
                        return
                    }
                }
            }
        }

        // Fallback for when we don't have sources but we have a source URI
        for ((prefix, path) in sourcePath) {
            if (prefix.isEmpty()) {
                continue
            }
            if (path.isEmpty()) {
                // If there is a prefix but no URI, the assumption is that the relative
                // path for the detector should be appended to the URI as is
                val full = if (detectorClass.isKotlinClass()) {
                    relative + DOT_KT
                } else {
                    relative + DOT_JAVA
                }
                store(null, prefix + full)
                return
            }
        }
    }

    private fun Class<*>.isKotlinClass(): Boolean =
        this.declaredAnnotations.any { it.annotationClass == Metadata::class }

    private fun initializeExamples(issueData: IssueData) {
        val source = issueData.detectorTestSource ?: return
        val ktFiles = if (source.path.endsWith(DOT_KT)) listOf(source) else emptyList()
        environment.analyzeFiles(ktFiles)
        val local = StandardFileSystems.local()
        val virtualFile =
            local.findFileByPath(source.path) ?: error("Could not find virtual file for $source")
        val psiFile = PsiManager.getInstance(environment.ideaProject).findFile(virtualFile)
            ?: error("Could not find PSI file for $source")
        val file = psiFile.toUElementOfType<UFile>()
            ?: error("Could not create UAST file for $source")

        val methods: MutableList<UMethod> = mutableListOf()

        for (testClass in file.classes) {
            val className = testClass.name ?: continue
            if (!className.endsWith("Test")) {
                continue
            }

            for (method in testClass.methods) {
                methods.add(method)
            }
        }

        val id = issueData.issue.id
        val curated1 = setOf("testDocumentationExample$id", "testExample$id", "example$id")
        val curated2 = setOf("testDocumentationExample", "testExample", "example", "testSample")
        val preferred = setOf("testBasic", "test")

        fun UMethod.rank(): Int {
            val name = this.name
            return when {
                curated1.contains(name) -> 1
                curated2.contains(name) -> 2
                preferred.contains(name) -> 3
                else -> 4
            }
        }

        val suppressExampleNames =
            setOf("testSuppressExample", "testSuppressExample$id", "suppressExample")
        val suppressExample = methods.firstOrNull {
            suppressExampleNames.contains(it.name)
        }

        if (suppressExample != null) {
            methods.remove(suppressExample)
            issueData.suppressExample = findExampleInMethod(
                suppressExample, source, issueData,
                inferred = false,
                suppress = true
            )
        }

        methods.sortWith(object : Comparator<UMethod> {
            override fun compare(o1: UMethod, o2: UMethod): Int {
                val rank1 = o1.rank()
                val rank2 = o2.rank()
                val delta = rank1 - rank2
                if (delta != 0) {
                    return delta
                }
                return (o1.sourcePsi?.startOffset ?: 0) - (o2.sourcePsi?.startOffset ?: 0)
            }
        })

        for (method in methods) {
            val inferred = method.rank() >= 3
            issueData.example = findExampleInMethod(method, source, issueData, inferred, false)
            if (issueData.example != null) {
                break
            }
        }

        // if (issueData.example == null && issueData.issue.implementation.detectorClass != IconDetector::class.java) {
        //    println("  ** Couldn't extract sample for ${issueData.issue.id}")
        // }
    }

    private fun findExampleInMethod(
        method: UMethod,
        source: File,
        issueData: IssueData,
        inferred: Boolean,
        suppress: Boolean
    ): Example? {
        val issue = issueData.issue
        var example: Example? = null
        method.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName ?: node.methodIdentifier?.name
                if (example == null && name == "expect") {
                    example = findExample(source, issue, method, node, inferred, suppress)
                }
                if ((name == "expectFixDiffs" || name == "verifyFixes") && singleIssueDetector(issue)) {
                    // If we find a unit test for quickfixes, we assume that this issue
                    // has a quickfix (though we only do this when a detector analyzes a
                    // single issue, since the quickfix output has id in the output
                    // to attribute the fix to one issue id or another)
                    node.valueArguments.firstOrNull()?.let {
                        val output = evaluateString(it)
                        if (output != null && output.contains("Show URL for")) {
                            // Quickfix just mentions URLs; let's not consider this a "fix" to
                            // highlight in the report
                            return super.visitCallExpression(node)
                        }
                    }
                    issueData.quickFixable = true
                }
                return super.visitCallExpression(node)
            }
        })

        return example
    }

    private fun singleIssueDetector(issue: Issue): Boolean {
        return singleIssueDetectors.contains(issue)
    }

    private fun createUastEnvironment(): UastEnvironment {
        val config = UastEnvironment.Configuration.create()
        config.addSourceRoots(testPath.values.flatten())

        val libs = mutableListOf<File>()
        val classPath: String = System.getProperty("java.class.path")
        for (path in classPath.split(pathSeparator)) {
            val file = File(path)
            val name = file.name
            if (name.endsWith(DOT_JAR)) {
                libs.add(file)
            } else if (!file.path.endsWith("android.sdktools.base.lint.checks-base") &&
                !file.path.endsWith("android.sdktools.base.lint.studio-checks")
            ) {
                libs.add(file)
            }
        }
        config.addClasspathRoots(libs)

        return UastEnvironment.create(config)
    }

    private fun disposeUastEnvironment() {
        environment.dispose()
    }

    private fun analyzeSource(): Map<String, IssueData> {
        val map = mutableMapOf<String, IssueData>()

        if (sourcePath.isNotEmpty() || testPath.isNotEmpty()) {
            println("Searching through source path")
        }
        val sources = this.findSourceFiles()

        for (issue in issues) {
            val data = IssueData(issue)
            data.quickFixable = Reporter.hasAutoFix(issue)
            map[issue.id] = data

            if (!(includeSuppressInfo || includeExamples || includeSourceLinks)) {
                continue
            }
            initializeSources(data, sources)
        }

        return map
    }

    // Given an expect() call in a lint unit test, returns the example metadata
    private fun findExample(
        file: File,
        issue: Issue,
        method: UMethod,
        node: UCallExpression,
        inferred: Boolean,
        suppress: Boolean
    ): Example? {
        val valueArgument = node.valueArguments.firstOrNull() ?: return null
        val expected = evaluateString(valueArgument) ?: return null
        if (!suppress && expected.contains("No warnings")) {
            return null
        }

        val map = computeResultMap(issue, expected)
        if (!suppress && map.isEmpty()) {
            return null
        }

        var example: Example? = null
        node.receiver?.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName ?: node.methodIdentifier?.name
                if (example == null && name == "files") {
                    example = findExample(file, node, map, issue, method, inferred, suppress)
                }
                return super.visitCallExpression(node)
            }
        })

        return example
    }

    private fun getTestFileDeclaration(argument: UExpression?): UCallExpression? {
        argument ?: return null

        if (argument is UParenthesizedExpression) {
            return getTestFileDeclaration(argument.expression)
        }

        if (argument is UCallExpression) {
            val name = argument.methodName ?: argument.methodIdentifier?.name
            if (name == "compiled") {
                // use the source instead
                return getTestFileDeclaration(argument.valueArguments[1])
            }
            return argument
        }

        if (argument is UQualifiedReferenceExpression) {
            val selector = argument.selector
            return if (selector is UCallExpression &&
                (selector.methodName ?: selector.methodIdentifier?.name) == "indented"
            ) {
                getTestFileDeclaration(selector.receiver)
            } else {
                getTestFileDeclaration(selector)
            }
        }

        if (argument is USimpleNameReferenceExpression) {
            val element = argument.resolve()?.toUElement()
            if (element is UVariable) {
                return getTestFileDeclaration(element.uastInitializer)
            }
        }

        return null
    }

    private fun findExample(
        file: File,
        node: UCallExpression,
        outputMap: MutableMap<String, MutableMap<Int, MutableList<String>>>,
        issue: Issue,
        method: UMethod,
        inferred: Boolean,
        suppress: Boolean
    ): Example? {
        val exampleFiles = mutableListOf<ExampleFile>()
        for (argument in node.valueArguments) {
            val testFile = getTestFileDeclaration(argument) ?: continue
            val fileType = testFile.methodName ?: testFile.methodIdentifier?.name
            val testArgs = testFile.valueArguments
            if (testArgs.isNotEmpty()) {
                val source = evaluateString(testArgs.last()) ?: continue
                var path: String? = null
                if (testArgs.size > 1) {
                    val first = testArgs.first()
                    path = evaluateString(first)
                    if (path == null) {
                        // Attempt some other guesses
                        val text = first.sourcePsi?.text
                        if (text?.toUpperCase(Locale.US)?.contains("MANIFEST") == true) {
                            path = "AndroidManifest.xml"
                            if (outputMap.containsKey("src/main/AndroidManifest.xml"))
                                path = "src/main/AndroidManifest.xml"
                        }
                    }
                } else {
                    if (fileType == "gradle") {
                        path = "build.gradle"
                    } else {
                        var relative: String? = null
                        when (fileType) {
                            "kotlin" -> {
                                val className = ClassName(source)
                                relative = className.relativePath(DOT_KT)
                            }

                            "java" -> {
                                val className = ClassName(source)
                                relative = className.relativePath(DOT_JAVA)
                            }

                            "manifest" -> {
                                relative = "AndroidManifest.xml"
                            }
                        }
                        if (relative != null) {
                            path = "src/$relative"
                            // Handle Gradle source set conversion, e.g. due to the
                            // presence of a Gradle file a manifest is located in src/main/
                            // instead of / (and a Java file in src/main/java/ instead of just src/, etc.)
                            if (outputMap[path] == null) {
                                for (p in outputMap.keys) {
                                    if (p.endsWith(relative)) {
                                        path = p
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                if (source.contains("HIDE-FROM-DOCUMENTATION")) {
                    continue
                }

                val lang = getLanguage(path)
                val contents: String = if (lang == "xml") escapeXml(source) else source
                val exampleFile = ExampleFile(path, contents, lang)
                exampleFiles.add(exampleFile)
            }
        }

        if (suppress) {
            if (exampleFiles.any { containsSuppress(it.source, issue) }) {
                return Example(
                    testClass = issue.implementation.detectorClass.simpleName,
                    testMethod = method.name,
                    file = file,
                    files = exampleFiles,
                    output = null,
                    inferred = inferred
                )
            }
            return null
        }

        val errors = StringBuilder()

        for (exampleFile in exampleFiles) {
            val path = exampleFile.path ?: continue
            val warnings = outputMap[path]
                ?: run {
                    val empty: Map<Int, List<String>> = emptyMap()
                    var m = empty
                    for ((p, v) in outputMap) {
                        if (p.endsWith(path)) {
                            m = v
                            break
                        }
                    }
                    // folder errors? Error message just shows folder path but specific source files
                    // are *in* that folder
                    if (m === empty) {
                        for ((p, v) in outputMap) {
                            if (path.startsWith(p)) {
                                m = v
                                break
                            }
                        }
                    }
                    m
                }

            if (inferred && warnings.isEmpty()) {
                continue
            }

            // Found warnings for this file!
            for (line in warnings.keys.sorted()) {
                val list = warnings[line] ?: continue
                val wrapped = if (line != -1)
                    wrap("$path:$line:${list[0]}")
                else
                    wrap("$path:${list[0]}")
                errors.append(wrapped).append('\n')

                // Dedent the source code on the error line (as well as the corresponding underline)
                if (list.size >= 3) {
                    var sourceLine = list[1]
                    var underline = list[2]
                    val index1 = sourceLine.indexOfFirst { !it.isWhitespace() }
                    val index2 = underline.indexOfFirst { !it.isWhitespace() }
                    val index = min(index1, index2)
                    val minIndent = 4
                    if (index > minIndent) {
                        sourceLine =
                            sourceLine.substring(0, minIndent) + sourceLine.substring(index)
                        underline = underline.substring(0, minIndent) + underline.substring(index)
                    }
                    errors.appendXml(sourceLine).append('\n')
                    if (this.format == DocFormat.MARKDEEP) {
                        errors.append(underline.replace('~', '-')).append('\n')
                    } else {
                        errors.append(underline)
                    }
                }
                errors.append("\n\n")
            }

            if (inferred) {
                return Example(
                    testClass = issue.implementation.detectorClass.simpleName,
                    testMethod = method.name,
                    file = file,
                    files = listOf(exampleFile),
                    output = errors.toString(),
                    inferred = true
                )
            }
        }

        if (!inferred && exampleFiles.isNotEmpty()) {
            return Example(
                testClass = issue.implementation.detectorClass.simpleName,
                testMethod = method.name,
                file = file,
                files = exampleFiles,
                output = errors.toString(),
                inferred = false
            )
        }

        return null
    }

    private fun evaluateString(element: UElement): String? {
        val evaluator = ConstantEvaluator()
        evaluator.allowUnknowns()
        evaluator.allowFieldInitializers()
        val value = evaluator.evaluate(element) as? String ?: return null
        return value.trimIndent().replace('＄', '$')
    }

    private fun containsSuppress(source: String, issue: Issue): Boolean {
        return source.contains(issue.id) && (
                source.contains("@Suppress") ||
                        source.contains(":ignore") ||
                        source.contains("noinspection")
                )
    }

    private fun getLanguage(path: String?): String {
        path ?: return "text"
        return if (path.endsWith(DOT_KT) || path.endsWith(DOT_KTS)) "kotlin"
        else if (path.endsWith(DOT_JAVA)) "java"
        else if (path.endsWith(DOT_GRADLE)) "groovy"
        else if (path.endsWith(DOT_XML)) "xml"
        else ""
    }

    private val lt = if (format == DocFormat.MARKDEEP) "&lt;" else "<"
    private val gt = if (format == DocFormat.MARKDEEP) "&gt;" else ">"

    private fun StringBuilder.appendXml(s: String): StringBuilder {
        append(escapeXml(s))
        return this
    }

    private fun escapeXml(s: String): String {
        return if (format == DocFormat.MARKDEEP) {
            s.replace("<", "&lt;").replace(">", "&gt;")
        } else {
            s
        }
    }

    private fun computeResultMap(
        issue: Issue,
        expected: String
    ): MutableMap<String, MutableMap<Int, MutableList<String>>> {
        // Map from path to map from line number to errors and sources
        val map = HashMap<String, MutableMap<Int, MutableList<String>>>()

        var index = 0
        while (true) {
            index = expected.indexOf("[${issue.id}]", index + 1)
            if (index == -1) {
                break
            }

            val lineBegin = expected.lastIndexOf('\n', index) + 1
            val lineEnd =
                expected.indexOf('\n', index).let { if (it == -1) expected.length else it }
            val line = expected.substring(lineBegin, lineEnd)
            val matcher = MESSAGE_PATTERN.matcher(line)
            if (matcher.find()) {
                // group 2 is severity, group 3 is message and group 4 is the id
                val path: String
                val lineNumber: Int
                val location = matcher.group(1)
                val locationMatcher = LOCATION_PATTERN.matcher(location)
                if (locationMatcher.find()) {
                    path = locationMatcher.group(1)
                    lineNumber = locationMatcher.group(2).toInt()
                } else {
                    path = location
                    lineNumber = -1
                }
                val nextStart = lineEnd + 1
                val nextEnd =
                    expected.indexOf('\n', nextStart).let { if (it == -1) expected.length else it }
                val sourceLine1 = expected.substring(nextStart, nextEnd)
                val strings = ArrayList<String>()
                strings.add(line.substring(matcher.start(2))) // Text from "Error:" and on
                if (!MESSAGE_PATTERN.matcher(sourceLine1)
                        .matches()
                ) { // else: no source included in error output
                    val nextStart2 = min(expected.length, nextEnd + 1)
                    val nextEnd2 = expected.indexOf('\n', nextStart2)
                        .let { if (it == -1) expected.length else it }
                    val sourceLine2 = expected.substring(nextStart2, nextEnd2)
                    strings.add(sourceLine1)
                    strings.add(sourceLine2)
                }
                val lineNumberMap = map[path]
                    ?: HashMap<Int, MutableList<String>>().also { map[path] = it }
                lineNumberMap[lineNumber] = strings
            }
        }

        return map
    }

    private fun canAnalyzeInEditor(issue: Issue): Boolean {
        val implementation = issue.implementation
        val allScopes: Array<EnumSet<Scope>> = implementation.analysisScopes + implementation.scope
        return allScopes.any { Scope.checkSingleFile(it) }
    }

    companion object {
        val MESSAGE_PATTERN: Pattern =
            Pattern.compile("""(.+): (Error|Warning|Information): (.+) \[(.+)]""")
        val LOCATION_PATTERN: Pattern = Pattern.compile("""(.+):(\d+)""")
        private val YEAR_PATTERN = Pattern.compile("""\b(\d\d\d\d)\b""")
        private val ANDROID_SUPPORT_SYMBOL_PATTERN =
            Pattern.compile("\\b(android.support.[a-zA-Z0-9_.]+)\\b")

        @Suppress("SpellCheckingInspection")
        private const val AOSP_CS =
            "https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main"

        private val PACKAGE_PATTERN = Pattern.compile("""package\s+([\S&&[^;]]*)""")

        @Suppress("SpellCheckingInspection")
        private val CLASS_PATTERN = Pattern.compile(
            """(\bclass\b|\binterface\b|\benum class\b|\benum\b|\bobject\b)+?\s*([^\s:(]+)""",
            Pattern.MULTILINE
        )

        private val NUMBER_PATTERN = Pattern.compile("^\\d+\\. ")
        private fun String.isListItem(): Boolean {
            return startsWith("- ") || startsWith("* ") || startsWith("+ ") ||
                    firstOrNull()?.isDigit() == true && NUMBER_PATTERN.matcher(this).find()
        }

        private fun getRegistry(
            client: LintCliClient,
            jars: List<File>,
            includeBuiltins: Boolean
        ): IssueRegistry? {
            if (jars.isEmpty()) {
                return BuiltinIssueRegistry()
            }
            val registries = JarFileIssueRegistry.get(client, jars, skipVerification = true) +
                    if (includeBuiltins) listOf(BuiltinIssueRegistry()) else emptyList()

            return when {
                registries.isEmpty() -> {
                    println("Could not find any lint issue registries in ${jars.joinToString(",")}")
                    null
                }

                registries.size == 1 -> registries[0]
                else -> CompositeIssueRegistry(registries)
            }
        }

        private fun findStudioSource(): File? {
            val root = System.getenv("ADT_SOURCE_TREE")
            if (root != null) {
                return File(root)
            }

            val source = LintIssueDocGenerator::class.java.protectionDomain.codeSource
            if (source != null) {
                val location = source.location
                try {
                    var dir: File? = File(location.toURI())
                    while (dir != null) {
                        if (File(dir, "tools/base/lint").isDirectory) {
                            return dir
                        }
                        dir = dir.parentFile
                    }
                } catch (ignore: Exception) {
                }
            }

            return null
        }

        /**
         * Add some URLs for pointing to the AOSP source code if nothing
         * has been specified for the built-in checks. If we have
         * access to the source code, we provide a source path for more
         * accurate search; we only include a test fallback if we're not
         * in unit tests since in unit tests we may or may not find the
         * source code based on the build system the test is run from.
         */
        @Suppress("SpellCheckingInspection")
        private fun addAospUrls(
            sourcePath: MutableMap<String, MutableList<File>>,
            testPath: MutableMap<String, MutableList<File>>
        ) {
            if (!LintClient.isUnitTest) {
                val lintRoot = findStudioSource()?.let { File("tools/base/lint") }
                if (lintRoot != null) {
                    testPath["$AOSP_CS:lint/libs/lint-tests/src/test/java/"] =
                        mutableListOf(File("$lintRoot/libs/lint-tests/src/test/java"))
                    sourcePath["$AOSP_CS:lint/libs/lint-checks/src/main/java/"] =
                        mutableListOf(
                            File(
                                "$lintRoot/libs/lint-checks/src/main/java",
                                "$lintRoot/studio-checks/src/main/java"
                            )
                        )
                }
            } else {
                // We can't have a default URL for tests because we have no way to look up
                // whether it's in Kotlin or Java since it's not on the classpath (which
                // is needed to make the URL suffix correct)
                sourcePath["$AOSP_CS:lint/libs/lint-checks/src/main/java/"] = mutableListOf()
            }
            // TODO: offer some kind of search-based fallback? e.g. for AlarmDetector, a URL like this:
            // https://cs.android.com/search?q=AlarmDetector.kt&sq=&ss=android-studio%2Fplatform%2Ftools%2Fbase
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = run(args)
            exitProcess(exitCode)
        }

        @JvmStatic
        fun run(args: Array<String>, fromLint: Boolean = false): Int {
            var format = DocFormat.MARKDEEP
            var singleDoc = false
            var jarPath: String? = null
            var includeStats = false
            var includeIndices = true
            var outputPath: String? = null
            val issues = mutableListOf<String>()
            val sourcePath = mutableMapOf<String, MutableList<File>>()
            val testPath = mutableMapOf<String, MutableList<File>>()
            var includeSuppressInfo = true
            var includeExamples = true
            var includeSourceLinks = true
            var includeBuiltins = false
            var includeSeverityColor = true

            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    ARG_HELP, "-h", "-?" -> {
                        printUsage(fromLint)
                        return ERRNO_USAGE
                    }

                    ARG_SINGLE_DOC -> {
                        singleDoc = true; includeSuppressInfo = false
                    }

                    ARG_INCLUDE_BUILTINS -> includeBuiltins = true
                    ARG_MD -> format = DocFormat.MARKDOWN
                    ARG_NO_SEVERITY -> includeSeverityColor = false
                    ARG_INCLUDE_STATS -> includeStats = true
                    ARG_NO_INDEX -> includeIndices = false
                    ARG_NO_SUPPRESS_INFO -> includeSuppressInfo = false
                    ARG_NO_EXAMPLES -> includeExamples = false
                    ARG_NO_SOURCE_LINKS -> includeSourceLinks = false
                    ARG_LINT_JARS -> {
                        if (index == args.size - 1) {
                            System.err.println("Missing lint jar path")
                            return ERRNO_ERRORS
                        }
                        val path = args[++index]
                        if (jarPath != null) {
                            jarPath += pathSeparator + path
                        } else {
                            jarPath = path
                        }
                    }

                    ARG_ISSUES -> {
                        if (index == args.size - 1) {
                            System.err.println("Missing list of lint issue id's")
                            return ERRNO_ERRORS
                        }
                        val issueList = args[++index]
                        issues.addAll(issueList.split(","))
                    }

                    ARG_SOURCE_URL -> {
                        if (index == args.size - 1) {
                            System.err.println("Missing source URL prefix")
                            return ERRNO_ERRORS
                        }
                        val prefix = args[++index].let {
                            if (it.isEmpty()) it else if (it.last().isLetter()) "$it/" else it
                        }
                        if (index == args.size - 1) {
                            System.err.println("Missing source path")
                            return ERRNO_ERRORS
                        }
                        val path = args[++index]
                        val list =
                            sourcePath[prefix] ?: ArrayList<File>().also { sourcePath[prefix] = it }
                        list.addAll(
                            splitPath(path).map {
                                val file = File(it)
                                // UAST does not support relative paths
                                if (file.isAbsolute) file else file.absoluteFile
                            }
                        )
                    }

                    ARG_TEST_SOURCE_URL -> {
                        if (index == args.size - 1) {
                            System.err.println("Missing source URL prefix")
                            return ERRNO_ERRORS
                        }
                        val prefix = args[++index].let {
                            if (it.isEmpty()) it else if (it.last().isLetter()) "$it/" else it
                        }
                        if (index == args.size - 1) {
                            System.err.println("Missing source path")
                            return ERRNO_ERRORS
                        }
                        val path = args[++index]
                        val list =
                            testPath[prefix] ?: ArrayList<File>().also { testPath[prefix] = it }
                        list.addAll(
                            splitPath(path).map {
                                val file = File(it)
                                // UAST does not support relative paths
                                if (file.isAbsolute) file else file.absoluteFile
                            }
                        )
                    }

                    ARG_OUTPUT -> {
                        if (index == args.size - 1) {
                            System.err.println("Missing source path")
                            return ERRNO_ERRORS
                        }
                        val path = args[++index]
                        if (outputPath != null) {
                            println("Only one output expected; found both $outputPath and $path")
                            return ERRNO_ERRORS
                        }
                        outputPath = path
                    }

                    else -> {
                        println("Unknown flag $arg")
                        printUsage(fromLint)
                        return ERRNO_ERRORS
                    }
                }
                index++
            }

            if (outputPath == null) {
                println("Must specify an output file or folder")
                printUsage(fromLint)
                return ERRNO_ERRORS
            }

            // Collect registries, using jarpath && for (path in splitPath(args[++index])) {

            val output = File(outputPath)
            if (output.exists()) {
                if (!singleDoc) {
                    println("$output already exists")
                } else if (output.exists()) {
                    // Ok to delete a specific file but not a whole directory in case
                    // it's not clear whether you specify a new directory or the
                    // directory to place the output in, e.g. home.
                    output.delete()
                }
            } else if (!singleDoc) {
                val created = output.mkdirs()
                if (!created) {
                    println("Couldn't create $output")
                    exitProcess(10)
                }
            }

            val jars: List<File> = if (jarPath == null) {
                println("Note: No lint jars specified: creating documents for the built-in lint checks instead ($ARG_INCLUDE_BUILTINS)")
                emptyList()
            } else {
                findLintJars(jarPath)
            }

            val client = if (LintClient.isClientNameInitialized() && LintClient.isUnitTest) {
                LintCliClient(LintClient.CLIENT_UNIT_TESTS)
            } else {
                LintCliClient("generate-docs")
            }
            val registry = getRegistry(client, jars, includeBuiltins) ?: return ERRNO_ERRORS

            if (sourcePath.isEmpty() && registry is BuiltinIssueRegistry) {
                addAospUrls(sourcePath, testPath)
            }

            val generator = LintIssueDocGenerator(
                output,
                registry,
                issues,
                singleDoc,
                format,
                includeStats,
                sourcePath,
                testPath,
                includeIndices,
                includeSuppressInfo,
                includeExamples,
                includeSourceLinks,
                includeSeverityColor
            )
            generator.generate()

            println("Wrote issue docs to $output${if (!singleDoc) separator else ""}")
            return ERRNO_SUCCESS
        }

        private fun findLintJars(jarPath: String): List<File> {
            val files = mutableListOf<File>()
            splitPath(jarPath).forEach { path ->
                val file = File(path)
                if (file.isFile && file.path.endsWith(DOT_JAR)) {
                    files.add(file)
                } else {
                    addLintJars(files, file)
                }
            }
            return files
        }

        private fun addLintJars(into: MutableList<File>, file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach {
                    addLintJars(into, it)
                }
            } else if (file.isFile) {
                if (file.name == FN_LINT_JAR) {
                    into.add(file)
                }
                // TODO: Support AAR files?
            }
        }

        private const val ARG_HELP = "--help"
        private const val ARG_SINGLE_DOC = "--single-doc"
        private const val ARG_MD = "--md"
        private const val ARG_LINT_JARS = "--lint-jars"
        private const val ARG_ISSUES = "--issues"
        private const val ARG_SOURCE_URL = "--source-url"
        private const val ARG_TEST_SOURCE_URL = "--test-url"
        private const val ARG_INCLUDE_STATS = "--include-stats"
        private const val ARG_NO_SUPPRESS_INFO = "--no-suppress-info"
        private const val ARG_NO_EXAMPLES = "--no-examples"
        private const val ARG_NO_SOURCE_LINKS = "--no-links"
        private const val ARG_INCLUDE_BUILTINS = "--builtins"
        private const val ARG_NO_SEVERITY = "--no-severity"
        private const val ARG_NO_INDEX = "--no-index"
        private const val ARG_OUTPUT = "--output"

        private fun markdownTable(vararg rows: Pair<String, String?>): String {
            val sb = StringBuilder()
            var first = true

            var keyWidth = 0
            var rhsWidth = 0
            for (row in rows) {
                val value = row.second
                value ?: continue
                val key = row.first
                keyWidth = max(keyWidth, key.length)
                rhsWidth = max(rhsWidth, value.length)
            }
            keyWidth = min(keyWidth, 15)

            val formatString = "%-${keyWidth}s | %s\n"

            for (row in rows) {
                val value = row.second ?: continue
                val key = row.first
                val formatted = String.format(formatString, key, value)
                sb.append(formatted)
                if (first) {
                    // Need a separator to get markdown to treat this as a table
                    first = false
                    for (i in 0 until keyWidth + 1) sb.append('-')
                    sb.append('|')
                    for (i in 0 until min(72 - keyWidth - 2, rhsWidth + 1)) sb.append('-')
                    sb.append('\n')
                }
            }

            return sb.toString()
        }

        // In Markdeep, a definition list looks better than a table
        private fun markdeepTable(vararg rows: Pair<String, String?>): String {
            val sb = StringBuilder()
            for (row in rows) {
                val value = row.second ?: continue
                val key = row.first

                sb.append("$key\n:   $value\n")
            }

            return sb.toString()
        }

        fun printUsage(fromLint: Boolean, out: PrintWriter = System.out.printWriter()) {
            val command = if (fromLint) "lint --generate-docs" else "lint-issue-docs-generator"
            out.println("Usage: $command [flags] --output <directory or file>]")
            out.println()
            out.println("Flags:")
            out.println()
            Main.printUsage(
                out,
                arrayOf(
                    ARG_HELP,
                    "This message.",
                    "$ARG_OUTPUT <dir>",
                    "Sets the path to write the documentation to. Normally a directory, unless $ARG_SINGLE_DOC " +
                            "is also specified",
                    ARG_SINGLE_DOC,
                    "Instead of writing one page per issue into a directory, write a single page containing " +
                            "all the issues",
                    ARG_MD,
                    "Write to plain Markdown (.md) files instead of Markdeep (.md.html)",
                    ARG_INCLUDE_BUILTINS,
                    "Generate documentation for the built-in issues. This is implied if $ARG_LINT_JARS is not specified",
                    "$ARG_LINT_JARS <jar-path>",
                    "Read the lint issues from the specific path (separated by $pathSeparator of custom jar files",
                    "$ARG_ISSUES [issues]",
                    "Limits the issues documented to the specific (comma-separated) list of issue id's",
                    "$ARG_SOURCE_URL <url-prefix> <path>",
                    "Searches for the detector source code under the given source folder or folders separated by " +
                            "semicolons, and if found, prefixes the path with the given URL prefix and includes " +
                            "this source link in the issue documentation.",
                    "$ARG_TEST_SOURCE_URL <url-prefix> <path>",
                    "Like $ARG_SOURCE_URL, but for detector unit tests instead. These must be named the same as " +
                            "the detector class, plus `Test` as a suffix.",
                    ARG_NO_INDEX,
                    "Do not include index files",
                    ARG_NO_SUPPRESS_INFO,
                    "Do not include suppression information",
                    ARG_NO_EXAMPLES,
                    "Do not include examples pulled from unit tests, if found",
                    ARG_NO_SOURCE_LINKS,
                    "Do not include hyperlinks to detector source code",
                    ARG_NO_SEVERITY,
                    "Do not include the red, orange or green informational boxes showing the severity of each issue",
                ),
                false
            )
        }

        /**
         * Copied from
         * com.android.tools.lint.checks.infrastructure.ClassName in
         * lint's testing library, but with the extra logic to insert
         * "test.kt" as the default name for Kotlin tests without a top
         * level class
         */
        class ClassName(source: String) {
            val packageName: String?
            val className: String?

            init {
                val withoutComments = stripComments(source)
                packageName = getPackage(withoutComments)
                className = getClassName(withoutComments)
            }

            fun relativePath(extension: String): String? {
                return when {
                    className == null -> if (DOT_KT == extension)
                        if (packageName != null)
                            packageName.replace('.', '/') + "/test.kt"
                        else
                            "test.kt"
                    else
                        null

                    packageName != null -> packageName.replace(
                        '.',
                        '/'
                    ) + '/' + className + extension

                    else -> className + extension
                }
            }

            @Suppress("LocalVariableName")
            fun stripComments(source: String, stripLineComments: Boolean = true): String {
                val sb = StringBuilder(source.length)
                var state = 0
                val INIT = 0
                val INIT_SLASH = 1
                val LINE_COMMENT = 2
                val BLOCK_COMMENT = 3
                val BLOCK_COMMENT_ASTERISK = 4
                val IN_STRING = 5
                val IN_STRING_ESCAPE = 6
                val IN_CHAR = 7
                val AFTER_CHAR = 8
                for (c in source) {
                    when (state) {
                        INIT -> {
                            when (c) {
                                '/' -> state = INIT_SLASH
                                '"' -> {
                                    state = IN_STRING
                                    sb.append(c)
                                }

                                '\'' -> {
                                    state = IN_CHAR
                                    sb.append(c)
                                }

                                else -> sb.append(c)
                            }
                        }

                        INIT_SLASH -> {
                            when {
                                c == '*' -> state = BLOCK_COMMENT
                                c == '/' && stripLineComments -> state = LINE_COMMENT
                                else -> {
                                    state = INIT
                                    sb.append('/') // because we skipped it in init
                                    sb.append(c)
                                }
                            }
                        }

                        LINE_COMMENT -> {
                            when (c) {
                                '\n' -> state = INIT
                            }
                        }

                        BLOCK_COMMENT -> {
                            when (c) {
                                '*' -> state = BLOCK_COMMENT_ASTERISK
                            }
                        }

                        BLOCK_COMMENT_ASTERISK -> {
                            state = when (c) {
                                '/' -> INIT
                                '*' -> BLOCK_COMMENT_ASTERISK
                                else -> BLOCK_COMMENT
                            }
                        }

                        IN_STRING -> {
                            when (c) {
                                '\\' -> state = IN_STRING_ESCAPE
                                '"' -> state = INIT
                            }
                            sb.append(c)
                        }

                        IN_STRING_ESCAPE -> {
                            sb.append(c)
                            state = IN_STRING
                        }

                        IN_CHAR -> {
                            if (c != '\\') {
                                state = AFTER_CHAR
                            }
                            sb.append(c)
                        }

                        AFTER_CHAR -> {
                            sb.append(c)
                            if (c == '\\') {
                                state = INIT
                            }
                        }
                    }
                }

                return sb.toString()
            }
        }

        fun getPackage(source: String): String? {
            val matcher = PACKAGE_PATTERN.matcher(source)
            return if (matcher.find()) {
                matcher.group(1).trim { it <= ' ' }
            } else {
                null
            }
        }

        fun getClassName(source: String): String? {
            val matcher = CLASS_PATTERN.matcher(source.replace('\n', ' '))
            var start = 0
            while (matcher.find(start)) {
                val cls = matcher.group(2)
                val groupStart = matcher.start(1)

                // Make sure this "class" reference isn't part of an annotation on the class
                // referencing a class literal -- Foo.class, or in Kotlin, Foo::class.java)
                if (groupStart == 0 || source[groupStart - 1] != '.' && source[groupStart - 1] != ':') {
                    val trimmed = cls.trim { it <= ' ' }
                    val typeParameter = trimmed.indexOf('<')
                    return if (typeParameter != -1) {
                        trimmed.substring(0, typeParameter)
                    } else {
                        trimmed
                    }
                }
                start = matcher.end(2)
            }

            return null
        }
    }

    class ExampleFile(
        val path: String?,
        val source: String,
        val language: String
    )

    class Example(
        val testClass: String,
        val testMethod: String,
        val file: File,
        val files: List<ExampleFile>,
        val output: String?,
        val inferred: Boolean = true
    )

    class IssueData(val issue: Issue) {
        var detectorSource: File? = null
        var detectorTestSource: File? = null

        var sourceUrl: String? = null
        var testUrl: String? = null

        var example: Example? = null
        var suppressExample: Example? = null
        var quickFixable: Boolean = false
        var copyrightYear: Int = -1
    }

    enum class DocFormat(val extension: String, val header: String = "", val footer: String = "") {
        @Suppress("SpellCheckingInspection")
        MARKDEEP(
            extension = ".md.html",
            header = "<meta charset=\"utf-8\">\n",
            footer = "<!-- Markdeep: -->" +
                    "<style class=\"fallback\">body{visibility:hidden;white-space:pre;font-family:monospace}</style>" +
                    "<script src=\"markdeep.min.js\" charset=\"utf-8\"></script>" +
                    "<script src=\"https://morgan3d.github.io/markdeep/latest/markdeep.min.js\" charset=\"utf-8\"></script>" +
                    "<script>window.alreadyProcessedMarkdeep||(document.body.style.visibility=\"visible\")</script>"
        ),
        MARKDOWN(".md"),
        HTML(".html");

        fun getFileName(issueId: String): String = "$issueId$extension"
        fun getFileName(issue: Issue): String = getFileName(issue.id)
    }
}
