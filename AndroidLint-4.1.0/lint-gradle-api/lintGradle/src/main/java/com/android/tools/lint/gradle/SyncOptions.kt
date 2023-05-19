package com.android.tools.lint.gradle

import com.android.SdkConstants
import com.android.builder.core.BuilderConstants.FD_REPORTS
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.Reporter
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.detector.api.Category.Companion.getCategory
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.*
import java.io.File.separator
import com.android.tools.lint.model.LintModelSeverity as ModelSeverity

// Operations related to syncing LintOptions to lint's internal state

fun syncTo(
    options: LintModelLintOptions,
    client: LintCliClient,
    flags: LintCliFlags,
    variantName: String?,
    project: Project?,
    reportsDir: File?,
    report: Boolean
) {
    val disabled = options.disable
    if (disabled.isNotEmpty()) {
        for (id in disabled) {
            val category = getCategory(id)
            if (category != null) {
                // Disabling a whole category
                flags.addDisabledCategory(category)
            } else {
                flags.suppressedIds.add(id)
            }
        }
    }
    val enabled = options.enable
    if (enabled.isNotEmpty()) {
        for (id in enabled) {
            val category = getCategory(id)
            if (category != null) {
                // Enabling a whole category
                flags.addEnabledCategory(category)
            } else {
                flags.enabledIds.add(id)
            }
        }
    }
    val check = options.check
    if (check != null && check.isNotEmpty()) {
        for (id in check) {
            val category = getCategory(id)
            if (category != null) {
                // Checking a whole category
                flags.addExactCategory(category)
            } else {
                flags.addExactId(id)
            }
        }
    }
    flags.isSetExitCode = options.abortOnError
    flags.isFullPath = options.absolutePaths
    flags.isShowSourceLines = !options.noLines
    flags.isQuiet = options.quiet
    flags.isCheckAllWarnings = options.checkAllWarnings
    flags.isIgnoreWarnings = options.ignoreWarnings
    flags.isWarningsAsErrors = options.warningsAsErrors
    flags.isCheckTestSources = options.checkTestSources
    flags.isIgnoreTestSources = options.ignoreTestSources
    flags.isCheckGeneratedSources = options.checkGeneratedSources
    flags.isCheckDependencies = options.checkDependencies
    flags.isShowEverything = options.showAll
    flags.defaultConfiguration = options.lintConfig
    flags.isExplainIssues = options.explainIssues
    flags.baselineFile = options.baselineFile
    val severityOverrides = options.severityOverrides
    if (severityOverrides != null) {
        val map: MutableMap<String, Severity> = mutableMapOf()
        val registry = BuiltinIssueRegistry()
        for ((id, severityInt) in severityOverrides) {
            val issue = registry.getIssue(id)
            val severity = issue?.let { getSeverity(it, severityInt) } ?: Severity.WARNING
            val category = getCategory(id)
            if (category != null) {
                for (current in registry.issues) {
                    val currentCategory = current.category
                    if (currentCategory === category || currentCategory.parent === category) {
                        map[current.id] = severity
                    }
                }
            } else {
                map[id] = severity
            }
        }
        flags.severityOverrides = map
    } else {
        flags.severityOverrides = emptyMap()
    }
    if (report || flags.isFatalOnly && options.abortOnError) {
        if (options.textReport || flags.isFatalOnly) {
            var output = options.textOutput
            if (output == null) {
                output = File(if (flags.isFatalOnly) Reporter.STDERR else Reporter.STDOUT)
            } else if (!output.isAbsolute &&
                !isStdOut(output) &&
                !isStdErr(output) &&
                project != null
            ) {
                output = project.file(output.path)
            }
            output = validateOutputFile(output!!)
            val writer: Writer
            var file: File? = null
            val closeWriter: Boolean
            when {
                isStdOut(output) -> {
                    writer = PrintWriter(System.out, true)
                    closeWriter = false
                }
                isStdErr(output) -> {
                    writer = PrintWriter(System.err, true)
                    closeWriter = false
                }
                else -> {
                    file = output
                    writer = try {
                        BufferedWriter(FileWriter(output))
                    } catch (e: IOException) {
                        throw GradleException("Text invalid argument.", e)
                    }
                    closeWriter = true
                }
            }
            val reporter = Reporter.createTextReporter(client, flags, file, writer, closeWriter)
            flags.reporters.add(reporter)
        }
        if (options.htmlReport) {
            var output = options.htmlOutput
            if (output == null || flags.isFatalOnly) {
                output = createOutputPath(
                    project, variantName, ".html", reportsDir, flags.isFatalOnly
                )
            } else if (!output.isAbsolute && project != null) {
                output = project.file(output.path)
            }
            output = validateOutputFile(output!!)
            try {
                flags.reporters.add(Reporter.createHtmlReporter(client, output, flags))
            } catch (e: IOException) {
                throw GradleException("HTML invalid argument.", e)
            }
        }
        if (options.xmlReport) {
            var output = options.xmlOutput
            if (output == null || flags.isFatalOnly) {
                output = createOutputPath(
                    project,
                    variantName,
                    SdkConstants.DOT_XML,
                    reportsDir,
                    flags.isFatalOnly
                )
            } else if (!output.isAbsolute && project != null) {
                output = project.file(output.path)
            }
            output = validateOutputFile(output!!)
            try {
                flags.reporters
                    .add(
                        Reporter.createXmlReporter(
                            client, output, false, flags.isIncludeXmlFixes
                        )
                    )
            } catch (e: IOException) {
                throw GradleException("XML invalid argument.", e)
            }
        }
    }
}

private fun getSeverity(
    issue: Issue,
    modelSeverity: ModelSeverity
): Severity {
    return when (modelSeverity) {
        ModelSeverity.FATAL -> Severity.FATAL
        ModelSeverity.ERROR -> Severity.ERROR
        ModelSeverity.WARNING -> Severity.WARNING
        ModelSeverity.INFORMATIONAL -> Severity.INFORMATIONAL
        ModelSeverity.IGNORE -> Severity.IGNORE
        ModelSeverity.DEFAULT_ENABLED -> issue.defaultSeverity
        else -> Severity.WARNING
    }
}

private fun isStdOut(output: File): Boolean = Reporter.STDOUT == output.path

private fun isStdErr(output: File): Boolean = Reporter.STDERR == output.path

fun validateOutputFile(outputFile: File): File {
    var output = outputFile
    if (isStdOut(output) || isStdErr(
            output
        )
    ) {
        return output
    }
    val parent = output.parentFile
    if (!parent.exists()) {
        val ok = parent.mkdirs()
        if (!ok) {
            throw GradleException("Could not create directory $parent")
        }
    }
    output = output.absoluteFile
    if (output.exists()) {
        val delete = output.delete()
        if (!delete) {
            throw GradleException("Could not delete old $output")
        }
    }
    if (output.parentFile != null && !output.parentFile.canWrite()) {
        throw GradleException("Cannot write output file $output")
    }
    return output
}

fun createOutputPath(
    project: Project?,
    variantName: String?,
    extension: String,
    reportsDir: File?,
    fatalOnly: Boolean
): File {
    val base = StringBuilder()
    base.append("lint-results")
    if (!variantName.isNullOrBlank()) {
        base.append("-")
        base.append(variantName)
    }
    if (fatalOnly) {
        base.append("-fatal")
    }
    base.append(extension)
    return when {
        reportsDir != null -> File(reportsDir, base.toString())
        project == null -> File(base.toString())
        else -> File(project.buildDir, FD_REPORTS + separator + base)
    }
}