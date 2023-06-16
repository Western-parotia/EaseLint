/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.VALUE_TRUE
import com.android.Version
import com.android.ide.common.repository.GradleVersion
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.FileStreamProvider
import com.android.manifmerger.ManifestMerger2.MergeFailureException
import com.android.manifmerger.MergingReport.MergedManifestKind
import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.LintCliFlags.ERRNO_APPLIED_SUGGESTIONS
import com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE
import com.android.tools.lint.LintCliFlags.ERRNO_ERRORS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.LintStats.Companion.create
import com.android.tools.lint.UastEnvironment.Companion.create
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.WrongThreadInterproceduralDetector
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.DefaultConfiguration
import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.client.api.UastParser
import com.android.tools.lint.client.api.XmlParser
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Severity.Companion.min
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeCounts
import com.android.tools.lint.detector.api.getEncodedString
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.isJdkFolder
import com.android.tools.lint.helpers.DefaultUastParser
import com.android.utils.CharSequences
import com.android.utils.StdLogger
import com.google.common.annotations.Beta
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl
import org.jetbrains.kotlin.cli.jvm.index.SingleJavaFileRootsIndex
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.util.PerformanceCounter.Companion.resetAllCounters
import org.w3c.dom.Document
import org.xml.sax.SAXException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.net.URL
import java.util.ArrayList
import java.util.HashMap
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.max

/**
 * Lint client for command line usage. Supports the flags in [LintCliFlags], and offers text,
 * HTML and XML reporting, etc.
 *
 * Minimal example:
 *
 * <pre>
 * // files is a list of java.io.Files, typically a directory containing
 * // lint projects or direct references to project root directories
 * IssueRegistry registry = new BuiltinIssueRegistry();
 * LintCliFlags flags = new LintCliFlags();
 * LintCliClient client = new LintCliClient(flags);
 * int exitCode = client.run(registry, files);
 * </pre>
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.**
 */
@Beta
open class LintCliClient : LintClient {
    constructor(clientName: String) : super(clientName) {
        flags = LintCliFlags()
        @Suppress("LeakingThis")
        val reporter =
            TextReporter(this, flags, PrintWriter(System.out, true), false)
        flags.reporters.add(reporter)
        initialize()
    }

    @Deprecated("Specify client explicitly by calling {@link LintCliClient(String)} ")
    constructor() : this(CLIENT_UNIT_TESTS)

    constructor(flags: LintCliFlags, clientName: String) : super(clientName) {
        this.flags = flags
        initialize()
    }

    /** Returns the issue registry used by this client  */
    open var registry: IssueRegistry? = null
        protected set

    /** Returns the driver running the lint checks  */
    lateinit var driver: LintDriver
        protected set

    /** Returns the configuration used by this client  */
    var configuration: Configuration? = null
        get() {
            if (field == null) {
                if (overrideConfiguration != null) {
                    field = overrideConfiguration
                    return field
                }
                val configFile = flags.defaultConfiguration
                if (configFile != null) {
                    if (!configFile.exists()) {
                        val warned = ourAlreadyWarned ?: run {
                            val new = mutableSetOf<File>()
                            ourAlreadyWarned = new
                            new
                        }
                        if (!warned.contains(configFile)) {
                            log(
                                Severity.ERROR,
                                null,
                                "Warning: Configuration file %1\$s does not exist",
                                configFile
                            )
                            warned.add(configFile)
                        }
                    }
                    field = createConfigurationFromFile(configFile)
                }
            }
            return field
        }
        private set

    /** Flags configuring the lint runs */
    val flags: LintCliFlags

    private var validatedIds = false
    private var kotlinPerformanceManager: LintCliKotlinPerformanceManager? = null
    protected var overrideConfiguration: DefaultConfiguration? = null
    var uastEnvironment: UastEnvironment? = null
    var ideaProject: com.intellij.openapi.project.Project? = null
        private set
    private var projectDisposer: Disposable? = null
    protected val warnings: MutableList<Warning> = ArrayList()
    private var hasErrors = false
    protected var errorCount = 0
    protected var warningCount = 0

    private fun initialize() {
        var configuration = System.getenv(LINT_OVERRIDE_CONFIGURATION_ENV_VAR)
        if (configuration == null) {
            configuration = System.getProperty(LINT_CONFIGURATION_OVERRIDE_PROP)
        }
        if (configuration != null) {
            val file = File(configuration)
            if (file.exists()) {
                overrideConfiguration = createConfigurationFromFile(file)
                println("Overriding configuration from $file")
            } else {
                log(
                    Severity.ERROR,
                    null,
                    "Configuration override requested but does not exist: $file"
                )
            }
        }
    }

    protected open val baselineVariantName: String?
        get() {
            if (flags.isFatalOnly) {
                return LintBaseline.VARIANT_FATAL
            }
            val projects: Collection<Project> = driver.projects
            for (project in projects) {
                return project.buildVariant?.name ?: continue
            }
            return LintBaseline.VARIANT_ALL
        }

    /**
     * Runs the static analysis command line driver. You need to add at least one error reporter to
     * the command line flags.
     */
    @Throws(IOException::class)
    fun run(registry: IssueRegistry, files: List<File>): Int {
        val request = createLintRequest(files)
        return run(registry, request)
    }

    /**
     * Runs the static analysis command line driver. You need to add at least one error reporter to
     * the command line flags.
     */
    @Throws(IOException::class)
    fun run(registry: IssueRegistry, lintRequest: LintRequest): Int {
        val startTime = System.currentTimeMillis()
        this.registry = registry
        val kotlinPerfReport = System.getenv("KOTLIN_PERF_REPORT")
        if (kotlinPerfReport != null && kotlinPerfReport.isNotEmpty()) {
            kotlinPerformanceManager = LintCliKotlinPerformanceManager(kotlinPerfReport)
        }
        driver = createDriver(registry, lintRequest)
        driver.analysisStartTime = startTime
        addProgressPrinter()
        validateIssueIds()
        driver.analyze()
        kotlinPerformanceManager?.report(lintRequest)
        warnings.sort()
        val baseline = driver.baseline
        val stats = create(warnings, baseline)
        for (reporter in flags.reporters) {
            reporter.write(stats, warnings)
        }
        var projects: Collection<Project>? = lintRequest.getProjects()
        if (projects == null) {
            projects = knownProjects
        }
        if (!projects.isEmpty()) {
            val analytics = LintBatchAnalytics()
            analytics.logSession(registry, flags, driver, projects, warnings)
        }
        if (flags.isAutoFix) {
            val statistics = !flags.isQuiet
            val performer = LintFixPerformer(this, statistics)
            val fixed = performer.fix(warnings)
            if (fixed && isGradle) {
                val message =
                    """
                       One or more issues were fixed in the source code.
                       Aborting the build since the edits to the source files were performed **after** compilation, so the outputs do not contain the fixes. Re-run the build.
                   """.trimIndent()
                System.err.println(message)
                return ERRNO_APPLIED_SUGGESTIONS
            }
        }
        val baselineFile = flags.baselineFile
        if (baselineFile != null && baseline != null) {
            emitBaselineDiagnostics(baseline, baselineFile, stats)
        }
        if (baselineFile != null && !baselineFile.exists() && flags.isWriteBaselineIfMissing) {
            val dir = baselineFile.parentFile
            var ok = true
            if (dir != null && !dir.isDirectory) {
                ok = dir.mkdirs()
            }
            if (!ok) {
                System.err.println("Couldn't create baseline folder $dir")
            } else {
                val reporter = Reporter.createXmlReporter(this, baselineFile, true, false)
                reporter.setBaselineAttributes(this, baselineVariantName)
                reporter.write(stats, warnings)
                System.err.println(getBaselineCreationMessage(baselineFile))
                return ERRNO_CREATED_BASELINE
            }
        } else if (baseline != null &&
            baseline.writeOnClose &&
            baseline.fixedCount > 0 &&
            flags.isRemoveFixedBaselineIssues
        ) {
            baseline.close()
            return ERRNO_CREATED_BASELINE
        } else if (baseline != null && flags.isUpdateBaseline) {
            baseline.close()
            return ERRNO_CREATED_BASELINE
        }
        return if (flags.isSetExitCode) if (hasErrors) ERRNO_ERRORS else ERRNO_SUCCESS else ERRNO_SUCCESS
    }

    fun getBaselineCreationMessage(baselineFile: File): String {
        val summary = "Created baseline file $baselineFile"

        if (continueAfterBaseLineCreated()) {
            return summary
        }

        val gradlePostScript = if (isGradle) """
            |You can run lint with -Dlint.baselines.continue=true
            |if you want to create many missing baselines in one go.
            """ else ""

        return """
            |$summary
            |
            |Also breaking the build in case this was not intentional. If you
            |deliberately created the baseline file, re-run the build and this
            |time it should succeed without warnings.
            |
            |If not, investigate the baseline path in the lintOptions config
            |or verify that the baseline file has been checked into version
            |control.
            |$gradlePostScript
            """.trimMargin()
    }

    fun emitBaselineDiagnostics(baseline: LintBaseline, baselineFile: File, stats: LintStats) {
        var hasConsoleOutput = false
        for (reporter in flags.reporters) {
            if (reporter is TextReporter && reporter.isWriteToConsole) {
                hasConsoleOutput = true
                break
            }
        }
        if (!flags.isQuiet && !hasConsoleOutput) {
            if (stats.baselineErrorCount > 0 || stats.baselineWarningCount > 0) {
                if (errorCount == 0 && warningCount == 1) {
                    // the warning is the warning about baseline issues having been filtered
                    // out, don't list this as "1 warning"
                    print("Lint found no new issues")
                } else {
                    val count = describeCounts(
                        errorCount,
                        max(0, warningCount - 1),
                        comma = true,
                        capitalize = false
                    )
                    print("Lint found $count")
                    if (stats.autoFixedCount > 0) {
                        print(" (${stats.autoFixedCount} of these were automatically fixed)")
                    }
                }
                val count = describeCounts(
                    stats.baselineErrorCount,
                    stats.baselineWarningCount,
                    comma = true,
                    capitalize = true
                )
                print(" ($count filtered by baseline ${baselineFile.name})")
            } else {
                val count = describeCounts(
                    errorCount,
                    warningCount, comma = true, capitalize = false
                )
                print("Lint found $count")
            }
            println()
            if (stats.baselineFixedCount > 0) {
                println(
                    "" +
                            "\n${stats.baselineFixedCount} errors/warnings were listed in the " +
                            "baseline file ($baselineFile) but not found in the project; " +
                            "perhaps they have been fixed?"
                )
            }
            val checkVariant = baselineVariantName
            val creationVariant = baseline.getAttribute("variant")
            if (creationVariant != null && creationVariant != checkVariant) {
                println("\nNote: The baseline was created using a different target/variant than it was checked against.")
                println("Creation variant: " + getTargetName(creationVariant))
                println("Current variant: " + if (checkVariant != null) getTargetName(checkVariant) else "none")
            }
            // TODO: If the versions don't match, emit some additional diagnostic hints, such as
            // the possibility that newer versions of lint have newer checks not included in
            // older ones, have existing checks that cover more areas, etc.
            if (stats.baselineFixedCount > 0) {
                val checkVersion = getClientDisplayRevision()
                val checkClient = clientName
                val creationVersion = baseline.getAttribute("version")
                val creationClient = baseline.getAttribute("client")
                if (checkClient == creationClient && creationVersion != null && checkVersion != null && creationVersion != checkVersion) {
                    val created = GradleVersion.tryParse(creationVersion)
                    val current = GradleVersion.tryParse(checkVersion)
                    if (created != null && current != null && created > current) {
                        println(
                            """
                            Note: The baseline was created with a newer version of $checkClient ($creationVersion) than the current version ($checkVersion)
                            This means that some of the issues marked as fixed in the baseline may not actually be fixed, but may
                            be new issues uncovered by the more recent version of lint.
                            """.trimIndent()
                        )
                    }
                }
            }
        }
    }

    protected fun validateIssueIds() {
        driver.addLintListener(object : LintListener {
            override fun update(
                driver: LintDriver,
                type: LintListener.EventType,
                project: Project?,
                context: Context?
            ) {
                if (type === LintListener.EventType.SCANNING_PROJECT && !validatedIds) {
                    // Make sure all the id's are valid once the driver is all set up and
                    // ready to run (such that custom rules are available in the registry etc)
                    validateIssueIds(project)
                }
            }
        })
    }

    protected open fun createDriver(registry: IssueRegistry, request: LintRequest): LintDriver {
        this.registry = registry
        val driver = LintDriver(registry, this, request)
        driver.isAbbreviating = !flags.isShowEverything
        driver.checkTestSources = flags.isCheckTestSources
        driver.ignoreTestSources = flags.isIgnoreTestSources
        driver.checkGeneratedSources = flags.isCheckGeneratedSources
        driver.fatalOnlyMode = flags.isFatalOnly
        driver.checkDependencies = flags.isCheckDependencies
        driver.allowSuppress = flags.allowSuppress
        val baselineFile = flags.baselineFile
        if (baselineFile != null) {
            val baseline = LintBaseline(this, baselineFile)
            driver.baseline = baseline
            if (flags.isRemoveFixedBaselineIssues) {
                baseline.writeOnClose = true
                baseline.removeFixed = true
            } else if (flags.isUpdateBaseline) {
                baseline.writeOnClose = true
            }
        }
        this.driver = driver
        return driver
    }

    protected open fun addProgressPrinter() {
        if (!flags.isQuiet) {
            driver.addLintListener(ProgressPrinter())
        }
    }

    /** Creates a lint request  */
    protected open fun createLintRequest(files: List<File>): LintRequest {
        return LintRequest(this, files).also { configureLintRequest(it) }
    }

    /** Configures a lint request  */
    protected open fun configureLintRequest(lintRequest: LintRequest) {
    }

    override fun log(severity: Severity, exception: Throwable?, format: String?, vararg args: Any) {
        System.out.flush()
        if (!flags.isQuiet) {
            // Place the error message on a line of its own since we're printing '.' etc
            // with newlines during analysis
            System.err.println()
        }
        if (format != null) {
            System.err.println(String.format(format, *args))
        }
        exception?.printStackTrace()
    }

    override val xmlParser: XmlParser
        get() = LintCliXmlParser(this)

    override fun getConfiguration(project: Project, driver: LintDriver?): Configuration {
        return overrideConfiguration ?: CliConfiguration(configuration, project, flags.isFatalOnly)
    }

    /** File content cache  */
    private val fileContents: MutableMap<File, CharSequence> = HashMap(100)

    /** Read the contents of the given file, possibly cached  */
    private fun getContents(file: File): CharSequence {
        return fileContents.computeIfAbsent(file) { readFile(file) }
    }

    override fun getUastParser(project: Project?): UastParser = LintCliUastParser(project)

    override fun getGradleVisitor(): GradleVisitor = GradleVisitor()

    override fun report(
        context: Context,
        issue: Issue,
        severity: Severity,
        location: Location,
        message: String,
        format: TextFormat,
        fix: LintFix?
    ) {
        if (severity.isError) {
            hasErrors = true
            errorCount++
        } else if (severity === Severity.WARNING) { // Don't count informational as a warning
            warningCount++
        }
        // Store the message in the raw format internally such that we can
        // convert it to text for the text reporter, HTML for the HTML reporter
        // and so on.
        val rawMessage = format.convertTo(message, TextFormat.RAW)
        val warning = Warning(issue, rawMessage, severity, context.project)
        warnings.add(warning)
        warning.location = location
        val file = location.file
        warning.file = file
        warning.path = getDisplayPath(context.project, file)
        warning.quickfixData = fix
        val startPosition = location.start
        if (startPosition != null) {
            val line = startPosition.line
            warning.line = line
            warning.offset = startPosition.offset
            val endPosition = location.end
            if (endPosition != null) {
                warning.endOffset = endPosition.offset
            }
            if (line >= 0) {
                if (context.file === location.file) {
                    warning.fileContents = context.getContents()
                }
                if (warning.fileContents == null) {
                    warning.fileContents = getContents(location.file)
                }
                if (flags.isShowSourceLines) {
                    // Compute error line contents
                    warning.errorLine = getLine(warning.fileContents, line)
                    if (warning.errorLine != null) {
                        // Replace tabs with spaces such that the column
                        // marker (^) lines up properly:
                        warning.errorLine = warning.errorLine.replace('\t', ' ')
                        var column = startPosition.column
                        if (column < 0) {
                            column = 0
                            var i = 0
                            while (i < warning.errorLine.length) {
                                if (!Character.isWhitespace(warning.errorLine[i])) {
                                    break
                                }
                                i++
                                column++
                            }
                        }
                        val sb = StringBuilder(100)
                        sb.append(warning.errorLine)
                        sb.append('\n')
                        for (i in 0 until column) {
                            sb.append(' ')
                        }
                        var displayCaret = true
                        if (endPosition != null) {
                            val endLine = endPosition.line
                            val endColumn = endPosition.column
                            if (endLine == line && endColumn > column) {
                                for (i in column until endColumn) {
                                    sb.append('~')
                                }
                                displayCaret = false
                            }
                        }
                        if (displayCaret) {
                            sb.append('^')
                        }
                        sb.append('\n')
                        warning.errorLine = sb.toString()
                    }
                }
            }
        }
    }

    override fun readFile(file: File): CharSequence {
        val contents = try {
            getEncodedString(this, file, false)
        } catch (e: IOException) {
            ""
        }
        val path = file.path
        if ((path.endsWith(DOT_JAVA) ||
                    path.endsWith(DOT_KT) ||
                    path.endsWith(DOT_KTS)) &&
            CharSequences.indexOf(contents, '\r') != -1
        ) {
            // Offsets in these files will be relative to PSI's text offsets (which may
            // have converted line offsets); make sure we use the same offsets.
            // (Can't just do this on Windows; what matters is whether the file contains
            // CRLF's.)
            val vFile = StandardFileSystems.local().findFileByPath(path)
            if (vFile != null) {
                val project = ideaProject
                if (project != null) {
                    val psiFile = PsiManager.getInstance(project).findFile(vFile)
                    if (psiFile != null) {
                        return psiFile.text
                    }
                }
            }
        }
        return contents
    }

    val isCheckingSpecificIssues: Boolean
        get() = flags.exactCheckedIds != null

    private var projectInfoMap: MutableMap<Project, ClassPathInfo>? = null

    override fun getClassPath(project: Project): ClassPathInfo {
        val classPath = super.getClassPath(project)
        val sources = flags.sourcesOverride
        val classes = flags.classesOverride
        val libraries = flags.librariesOverride
        if (classes == null && sources == null && libraries == null) {
            return classPath
        }
        return projectInfoMap?.get(project) ?: run {
            val info = ClassPathInfo(
                sources ?: classPath.sourceFolders,
                classes ?: classPath.classFolders,
                libraries ?: classPath.getLibraries(true),
                classPath.getLibraries(false),
                classPath.testSourceFolders,
                classPath.testLibraries,
                classPath.generatedFolders
            )
            val map = projectInfoMap ?: run {
                val new = HashMap<Project, ClassPathInfo>()
                projectInfoMap = new
                new
            }
            map[project] = info
            info
        }
    }

    override fun getResourceFolders(project: Project): List<File> {
        return flags.resourcesOverride ?: return super.getResourceFolders(project)
    }

    /**
     * Consult the lint.xml file, but override with the --enable and --disable flags supplied on the
     * command line
     */
    protected open inner class CliConfiguration : DefaultConfiguration {
        private val fatalOnly: Boolean

        constructor(
            parent: Configuration?,
            project: Project,
            fatalOnly: Boolean
        ) : super(this@LintCliClient, project, parent) {
            this.fatalOnly = fatalOnly
        }

        constructor(lintFile: File, fatalOnly: Boolean) :
                super(this@LintCliClient, null, null, lintFile) {
            this.fatalOnly = fatalOnly
        }

        protected constructor(
            lintFile: File,
            parent: Configuration?,
            project: Project?,
            fatalOnly: Boolean
        ) : super(this@LintCliClient, project, parent, lintFile) {
            this.fatalOnly = fatalOnly
        }

        override fun getSeverity(issue: Issue): Severity {
            var severity = computeSeverity(issue)
            if (fatalOnly && severity !== Severity.FATAL) {
                return Severity.IGNORE
            }
            if (flags.isWarningsAsErrors && severity === Severity.WARNING) {
                if (issue === IssueRegistry.BASELINE) {
                    // Don't promote the baseline informational issue
                    // (number of issues promoted) to error
                    return severity
                }
                severity = Severity.ERROR
            }
            if (flags.isIgnoreWarnings && severity === Severity.WARNING) {
                severity = Severity.IGNORE
            }
            return severity
        }

        override fun getDefaultSeverity(issue: Issue): Severity {
            return if (flags.isCheckAllWarnings) {
                // Exclude the interprocedural check from the "enable all warnings" flag;
                // it's much slower and still triggers various bugs in UAST that can affect
                // other checks.
                if (issue === WrongThreadInterproceduralDetector.ISSUE) {
                    super.getDefaultSeverity(issue)
                } else issue.defaultSeverity
            } else super.getDefaultSeverity(issue)
        }

        private fun computeSeverity(issue: Issue): Severity {
            val severity = super.getSeverity(issue)
            // Issue not allowed to be suppressed?
            if (issue.suppressNames != null && !flags.allowSuppress) {
                return getDefaultSeverity(issue)
            }
            val id = issue.id
            val suppress = flags.suppressedIds
            if (suppress.contains(id)) {
                return Severity.IGNORE
            }
            val disabledCategories = flags.disabledCategories
            if (disabledCategories != null) {
                val category = issue.category
                if (disabledCategories.contains(category) ||
                    category.parent != null && disabledCategories.contains(category.parent)
                ) {
                    return Severity.IGNORE
                }
            }
            val manual = flags.severityOverrides[id]
            if (manual != null) {
                return if (this.severity != null &&
                    (this.severity.containsKey(id) || this.severity.containsKey(VALUE_ALL))
                ) {
                    // Ambiguity! We have a specific severity override provided
                    // via lint options for the main app module, but a local lint.xml
                    // file in the library (not a lintOptions definition) which also
                    // specifies severity for the same issue.
                    //
                    // Who should win? Should the intent from the main app module
                    // win, such that you have a global way to say "this is the severity
                    // I want during this lint run?". Or should the library-local definition
                    // win, to say "there's a local problem in this library; I need to
                    // change things here?".
                    //
                    // Both are plausible, so for now I'm going with a middle ground: local
                    // definitions should be used to turn of issues that don't work right.
                    // Therefore, we'll take the minimum of the two severities!
                    min(severity, manual)
                } else manual
            }
            val enabled = flags.enabledIds
            val exact = flags.exactCheckedIds
            val enabledCategories = flags.enabledCategories
            val exactCategories = flags.exactCategories
            val category = issue.category
            if (exact != null) {
                if (exact.contains(id)) {
                    return getVisibleSeverity(issue, severity)
                } else if (category !== Category.LINT) {
                    return Severity.IGNORE
                }
            }
            if (exactCategories != null) {
                if (exactCategories.contains(category) ||
                    category.parent != null && exactCategories.contains(category.parent)
                ) {
                    return getVisibleSeverity(issue, severity)
                } else if (category !== Category.LINT ||
                    flags.disabledCategories?.contains(Category.LINT) == true
                ) {
                    return Severity.IGNORE
                }
            }
            return if (enabled.contains(id) ||
                enabledCategories != null && (enabledCategories.contains(category) ||
                        category.parent != null && enabledCategories.contains(category.parent))
            ) {
                getVisibleSeverity(issue, severity)
            } else severity
        }

        /** Returns the given severity, but if not visible, use the default  */
        private fun getVisibleSeverity(issue: Issue, severity: Severity): Severity {
            // Overriding default
            // Detectors shouldn't be returning ignore as a default severity,
            // but in case they do, force it up to warning here to ensure that
            // it's run
            var visibleSeverity = severity
            if (visibleSeverity === Severity.IGNORE) {
                visibleSeverity = issue.defaultSeverity
                if (visibleSeverity === Severity.IGNORE) {
                    visibleSeverity = Severity.WARNING
                }
            }
            return visibleSeverity
        }
    }

    override fun createProject(dir: File, referenceDir: File): Project {
        val project = super.createProject(dir, referenceDir)
        val compileSdkVersion = flags.compileSdkVersionOverride
        if (compileSdkVersion != null) {
            project.buildTargetHash = compileSdkVersion
        }
        project.ideaProject = ideaProject
        return project
    }

    /**
     * Checks that any id's specified by id refer to valid, known, issues. This typically can't be
     * done right away (in for example the Gradle code which handles DSL references to strings, or
     * in the command line parser for the lint command) because the full set of valid id's is not
     * known until lint actually starts running and for example gathers custom rules from all AAR
     * dependencies reachable from libraries, etc.
     */
    private fun validateIssueIds(project: Project?) {
        if (::driver.isInitialized) {
            val registry = driver.registry
            if (!registry.isIssueId(HardcodedValuesDetector.ISSUE.id)) {
                // This should not be necessary, but there have been some strange
                // reports where lint has reported some well known builtin issues
                // to not exist:
                //
                //   Error: Unknown issue id "DuplicateDefinition" [LintError]
                //   Error: Unknown issue id "GradleIdeError" [LintError]
                //   Error: Unknown issue id "InvalidPackage" [LintError]
                //   Error: Unknown issue id "JavascriptInterface" [LintError]
                //   ...
                //
                // It's not clear how this can happen, though it's probably related
                // to using 3rd party lint rules (where lint will create new composite
                // issue registries to wrap the various additional issues) - but
                // we definitely don't want to validate issue id's if we can't find
                // well known issues.
                return
            }
            validatedIds = true
            validateIssueIds(project, registry, flags.exactCheckedIds)
            validateIssueIds(project, registry, flags.enabledIds)
            validateIssueIds(project, registry, flags.suppressedIds)
            validateIssueIds(project, registry, flags.severityOverrides.keys)
            if (project != null) {
                val configuration = project.getConfiguration(driver)
                configuration.validateIssueIds(this, driver, project, registry)
            }
        }
    }

    private fun validateIssueIds(
        project: Project?,
        registry: IssueRegistry,
        ids: Collection<String>?
    ) {
        if (ids != null) {
            for (id in ids) {
                if (registry.getIssue(id) == null) {
                    reportNonExistingIssueId(project, id)
                }
            }
        }
    }

    private fun reportNonExistingIssueId(project: Project?, id: String) {
        if (id == "MissingRegistered") {
            // Recently renamed to MissingClass, but avoid complaining about leftover
            // configuration
            return
        }
        val message = "Unknown issue id \"$id\""
        if (::driver.isInitialized && project != null && !isSuppressed(IssueRegistry.LINT_ERROR)) {
            val location = guessGradleLocation(this, project.dir, id)
            report(
                this,
                IssueRegistry.LINT_ERROR,
                message,
                driver,
                project,
                location,
                LintFix.create().data(id)
            )
        } else {
            log(Severity.ERROR, null, "Lint: %1\$s", message)
        }
    }

    private class ProgressPrinter : LintListener {
        override fun update(
            driver: LintDriver,
            type: LintListener.EventType,
            project: Project?,
            context: Context?
        ) {
            when (type) {
                LintListener.EventType.SCANNING_PROJECT -> {
                    val name = context?.project?.name ?: "?"
                    if (driver.phase > 1) {
                        print("\nScanning $name (Phase ${driver.phase}): ")
                    } else {
                        print("\nScanning $name: ")
                    }
                }

                LintListener.EventType.SCANNING_LIBRARY_PROJECT -> {
                    val name = context?.project?.name ?: "?"
                    print("\n         - $name: ")
                }

                LintListener.EventType.SCANNING_FILE -> print('.')
                LintListener.EventType.NEW_PHASE -> {
                }

                LintListener.EventType.CANCELED, LintListener.EventType.COMPLETED -> println()
                LintListener.EventType.REGISTERED_PROJECT, LintListener.EventType.STARTING -> {
                }
            }
        }
    }

    fun getDisplayPath(project: Project, file: File): String {
        return getDisplayPath(project, file, flags.isFullPath)
    }

    fun getDisplayPath(project: Project, file: File, fullPath: Boolean): String {
        var path = file.path
        if (!fullPath && path.startsWith(project.referenceDir.path)) {
            var chop = project.referenceDir.path.length
            if (path.length > chop && path[chop] == File.separatorChar) {
                chop++
            }
            path = path.substring(chop)
            if (path.isEmpty()) {
                path = file.name
            }
        } else if (fullPath) {
            path = getCleanPath(file.absoluteFile)
        } else if (file.isAbsolute && file.exists()) {
            path = getRelativePath(project.referenceDir, file) ?: file.path
        }
        return path
    }

    /** Returns whether all warnings are enabled, including those disabled by default  */
    val isAllEnabled: Boolean
        get() = flags.isCheckAllWarnings

    /** Returns true if the given issue has been explicitly disabled  */
    fun isSuppressed(issue: Issue): Boolean {
        val disabledCategories = flags.disabledCategories
        if (disabledCategories != null) {
            val category = issue.category
            if (disabledCategories.contains(category) || category.parent != null && disabledCategories.contains(
                    category.parent
                )
            ) {
                return true
            }
        }
        return flags.suppressedIds.contains(issue.id)
    }

    fun createConfigurationFromFile(file: File): DefaultConfiguration {
        return CliConfiguration(file, flags.isFatalOnly)
    }

    val projectEnvironment: JavaCoreProjectEnvironment?
        get() = uastEnvironment?.projectEnvironment

    public override fun initializeProjects(knownProjects: Collection<Project>) {
        // Initialize the associated idea project to use
        val includeTests = !flags.isIgnoreTestSources
        // knownProject only lists root projects, not dependencies
        val allProjects = Sets.newIdentityHashSet<Project>()
        for (project in knownProjects) {
            allProjects.add(project)
            allProjects.addAll(project.allLibraries)
        }
        val classpath: MutableSet<File> = LinkedHashSet(50)
        for (project in allProjects) {
            // Note that there could be duplicates here since we're including multiple library
            // dependencies that could have the same dependencies (e.g. lib1 and lib2 both
            // referencing guava.jar)
            classpath.addAll(project.javaSourceFolders)
            if (includeTests) {
                classpath.addAll(project.testSourceFolders)
            }
            classpath.addAll(project.generatedSourceFolders)
            classpath.addAll(project.getJavaLibraries(true))
            if (includeTests) {
                classpath.addAll(project.testLibraries)
            }
            // Don't include all class folders:
            //  files.addAll(project.getJavaClassFolders());
            // These are the outputs from the sources and generated sources, which we will
            // parse directly with PSI/UAST anyway. Including them here leads lint to do
            // a lot more work (e.g. when resolving symbols it looks at both .java and .class
            // matches).
            // However, we *do* need them for libraries; otherwise, type resolution into
            // compiled libraries will not work; see
            // https://issuetracker.google.com/72032121
            if (project.isLibrary) {
                classpath.addAll(project.javaClassFolders)
            } else if (project.isGradleProject) {
                // As of 3.4, R.java is in a special jar file
                for (f in project.javaClassFolders) {
                    if (f.name == SdkConstants.FN_R_CLASS_JAR) {
                        classpath.add(f)
                    }
                }
            }
        }
        addBootClassPath(knownProjects, classpath)
        var maxLevel = LanguageLevel.JDK_1_7
        for (project in knownProjects) {
            val level = project.javaLanguageLevel
            if (maxLevel.isLessThan(level)) {
                maxLevel = level
            }
        }
        val parentDisposable = Disposer.newDisposable()
        projectDisposer = parentDisposable
        val environment = create(parentDisposable)
        uastEnvironment = environment
        val projectEnvironment = environment.projectEnvironment
        val mockProject = projectEnvironment.project
        ideaProject = mockProject
        val languageLevelProjectExtension =
            mockProject.getComponent(LanguageLevelProjectExtension::class.java)
        if (languageLevelProjectExtension != null) {
            languageLevelProjectExtension.languageLevel = maxLevel
        }
        projectEnvironment.registerPaths(classpath.toList())
        val manager = ServiceManager.getService(
            mockProject,
            JavaFileManager::class.java
        ) as KotlinCliJavaFileManagerImpl
        val roots: MutableList<JavaRoot> = ArrayList()
        for (file in classpath) {
            // IntelliJ's framework requires absolute path to JARs, name resolution might fail
            // otherwise. We defensively ensure all paths are absolute.
            require(file.isAbsolute) {
                "Relative Path found: ${file.path}. All paths should be absolute."
            }
            if (file.isDirectory) {
                val vFile = StandardFileSystems.local().findFileByPath(file.path)
                if (vFile != null) {
                    roots.add(JavaRoot(vFile, JavaRoot.RootType.SOURCE, null))
                }
            } else {
                val pathString = file.path
                if (pathString.endsWith(SdkConstants.DOT_SRCJAR)) {
                    // Mount as source files
                    val root =
                        StandardFileSystems.jar().findFileByPath(pathString + URLUtil.JAR_SEPARATOR)
                    if (root != null) {
                        roots.add(JavaRoot(root, JavaRoot.RootType.SOURCE, null))
                    }
                }
            }
        }
        val index = JvmDependenciesIndexImpl(roots)
        manager.initialize(index, emptyList(), SingleJavaFileRootsIndex(emptyList()), false)
        for (project in allProjects) {
            project.ideaProject = ideaProject
        }
        super.initializeProjects(knownProjects)
    }

    protected open fun addBootClassPath(
        knownProjects: Collection<Project>,
        files: MutableSet<File>
    ): Boolean {
        // TODO: Use bootclasspath from Gradle?

        val buildTarget = pickBuildTarget(knownProjects)
        if (buildTarget != null) {
            val file: File? = buildTarget.getFile(IAndroidTarget.ANDROID_JAR)
            if (file != null) {
                // because we're partially mocking it in some tests
                files.add(file)
                return true
            }
        }

        val jdkHome = getJdkHome()
        if (jdkHome != null) {
            val isJre = !isJdkFolder(jdkHome)
            val roots = JavaSdkUtil.getJdkClassesRoots(jdkHome, isJre)
            for (root in roots) {
                if (root.exists()) {
                    files.add(root)
                }
            }

            return true
        }

        return false
    }

    /**
     * Return the best build target to use among the given set of projects.
     * This is necessary because we need to pick a single target to use to
     * (for example) configure a boot classpath for the parsing infrastructure,
     * but in theory Gradle lets you configure different compileSdkVersions for
     * different modules, so here we pick the highest of the versions to make
     * sure it's capable of resolving all library calls into the platform.
     */
    private fun pickBuildTarget(knownProjects: Collection<Project>): IAndroidTarget? {
        return knownProjects.asSequence()
            .filter { it.isAndroidProject }
            .mapNotNull { it.buildTarget }
            .maxBy { it.version }
    }

    public override fun disposeProjects(knownProjects: Collection<Project>) {
        projectDisposer?.let { Disposer.dispose(it) }
        ideaProject = null
        projectDisposer = null
        super.disposeProjects(knownProjects)
    }

    val isOverridingConfiguration: Boolean
        get() = overrideConfiguration != null

    /** Synchronizes any options specified in lint.xml with the [LintCliFlags] object  */
    fun syncConfigOptions() {
        val configuration = configuration
        if (configuration is DefaultConfiguration) {
            val config = configuration
            val checkAllWarnings = config.checkAllWarnings
            if (checkAllWarnings != null) {
                flags.isCheckAllWarnings = checkAllWarnings
            }
            val ignoreWarnings = config.ignoreWarnings
            if (ignoreWarnings != null) {
                flags.isIgnoreWarnings = ignoreWarnings
            }
            val warningsAsErrors = config.warningsAsErrors
            if (warningsAsErrors != null) {
                flags.isWarningsAsErrors = warningsAsErrors
            }
            val fatalOnly = config.fatalOnly
            if (fatalOnly != null) {
                flags.isFatalOnly = fatalOnly
            }
            val checkTestSources = config.checkTestSources
            if (checkTestSources != null) {
                flags.isCheckTestSources = checkTestSources
            }
            val ignoreTestSources = config.ignoreTestSources
            if (ignoreTestSources != null) {
                flags.isIgnoreTestSources = ignoreTestSources
            }
            val checkGeneratedSources = config.checkGeneratedSources
            if (checkGeneratedSources != null) {
                flags.isCheckGeneratedSources = checkGeneratedSources
            }
            val checkDependencies = config.checkDependencies
            if (checkDependencies != null) {
                flags.isCheckDependencies = checkDependencies
            }
            val explainIssues = config.explainIssues
            if (explainIssues != null) {
                flags.isExplainIssues = explainIssues
            }
            val removeFixedBaselineIssues = config.removeFixedBaselineIssues
            if (removeFixedBaselineIssues != null) {
                flags.setRemovedFixedBaselineIssues(removeFixedBaselineIssues)
            }
            val abortOnError = config.abortOnError
            if (abortOnError != null) {
                flags.isSetExitCode = abortOnError
            }
            val baselineFile = config.baselineFile
            if (baselineFile != null) {
                flags.baselineFile =
                    if (baselineFile.path == SdkConstants.VALUE_NONE) null else baselineFile
            }
            val applySuggestions = config.applySuggestions
            if (applySuggestions != null && applySuggestions) {
                flags.isAutoFix = true
            }
        }
    }

    override fun getClientRevision(): String? {
        val plugin = Version.ANDROID_GRADLE_PLUGIN_VERSION
        return plugin ?: "unknown"
    }

    fun haveErrors(): Boolean {
        return errorCount > 0
    }

    @VisibleForTesting
    open fun reset() {
        warnings.clear()
        errorCount = 0
        warningCount = 0
        projectDirs.clear()
        dirToProject.clear()
    }

    override fun createUrlClassLoader(urls: Array<URL>, parent: ClassLoader): ClassLoader {
        return UrlClassLoader.build().parent(parent).urls(*urls).get()
    }

    override fun getMergedManifest(project: Project): Document? {
        val manifests: MutableList<File> = Lists.newArrayList()
        for (dependency in project.allLibraries) {
            manifests.addAll(dependency.manifestFiles)
        }
        val injectedFile = File("injected-from-gradle")
        val injectedXml = StringBuilder()
        val target = project.buildVariant
        if (target != null) {
            val targetSdkVersion = target.targetSdkVersion
            val minSdkVersion = target.minSdkVersion
            if (targetSdkVersion != null || minSdkVersion != null) {
                injectedXml.append(
                    "" +
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                            "    package=\"\${packageName}\">\n" +
                            "    <uses-sdk"
                )
                if (minSdkVersion != null) {
                    injectedXml.append(" android:minSdkVersion=\"").append(minSdkVersion.apiString)
                        .append("\"")
                }
                if (targetSdkVersion != null) {
                    injectedXml.append(" android:targetSdkVersion=\"")
                        .append(targetSdkVersion.apiString).append("\"")
                }
                injectedXml.append(" />\n</manifest>\n")
                manifests.add(injectedFile)
            }
        }
        var mainManifest: File? = null
        if (target != null) {
            for (provider in target.sourceProviders) {
                val manifestFile = provider.manifestFile
                if (manifestFile.exists()) { // model returns path whether or not it exists
                    if (mainManifest == null) {
                        mainManifest = manifestFile
                    } else {
                        manifests.add(manifestFile)
                    }
                }
            }
            if (mainManifest == null) {
                return null
            }
        } else {
            val projectManifests = project.manifestFiles
            if (projectManifests.isEmpty()) {
                return null
            }
            mainManifest = projectManifests[0]
            for (i in 1 until projectManifests.size) {
                manifests.add(projectManifests[i])
            }
        }
        if (mainManifest == null) {
            return null
        }
        if (manifests.isEmpty()) {
            // Only the main manifest: that's easy
            try {
                val document = xmlParser.parseXml(mainManifest)
                document?.let { resolveMergeManifestSources(it, mainManifest) }
                return document
            } catch (e: IOException) {
                log(Severity.WARNING, e, "Could not parse %1\$s", mainManifest)
            } catch (e: SAXException) {
                log(Severity.WARNING, e, "Could not parse %1\$s", mainManifest)
            } catch (e: ParserConfigurationException) {
                log(Severity.WARNING, e, "Could not parse %1\$s", mainManifest)
            }
            return null
        }
        try {
            val logger = StdLogger(StdLogger.Level.INFO)
            val type =
                if (project.isLibrary) ManifestMerger2.MergeType.LIBRARY else ManifestMerger2.MergeType.APPLICATION
            val mergeReport = ManifestMerger2.newMerger(mainManifest, logger, type).withFeatures(
                // TODO: How do we get the *opposite* of EXTRACT_FQCNS:
                // ensure that all names are made fully qualified?
                ManifestMerger2.Invoker.Feature.SKIP_BLAME,
                ManifestMerger2.Invoker.Feature.SKIP_XML_STRING,
                ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT
            ).addLibraryManifests(*manifests.toTypedArray())
                .withFileStreamProvider(object : FileStreamProvider() {
                    @Throws(FileNotFoundException::class)
                    override fun getInputStream(file: File): InputStream {
                        if (injectedFile == file) {
                            return CharSequences.getInputStream(injectedXml.toString())
                        }
                        val text = readFile(file)
                        // TODO: Avoid having to convert back and forth
                        return CharSequences.getInputStream(text)
                    }
                }).merge()
            val xmlDocument = mergeReport.getMergedXmlDocument(MergedManifestKind.MERGED)
            if (xmlDocument != null) {
                val document = xmlDocument.xml
                if (document != null) {
                    resolveMergeManifestSources(document, mergeReport.actions)
                    return document
                }
            } else {
                log(Severity.WARNING, null, mergeReport.reportString)
            }
        } catch (e: MergeFailureException) {
            log(Severity.ERROR, e, "Couldn't parse merged manifest")
        }
        return super.getMergedManifest(project)
    }

    protected open inner class LintCliUastParser(project: Project?) :
        DefaultUastParser(project, ideaProject!!) {
        override fun prepare(
            contexts: List<JavaContext>,
            javaLanguageLevel: LanguageLevel?,
            kotlinLanguageLevel: LanguageVersionSettings?
        ): Boolean {
            // If we're using Kotlin, ensure we initialize the bridge
            val kotlinFiles: MutableList<File> = ArrayList()
            for (context in contexts) {
                val path = context.file.path
                if (path.endsWith(DOT_KT) || path.endsWith(DOT_KTS)) {
                    kotlinFiles.add(context.file)
                }
            }
            // We unconditionally invoke the KotlinLintAnalyzerFacade, even
            // if kotlinFiles is empty -- without this, the machinery in
            // the project (such as the CliLightClassGenerationSupport and
            // the CoreFileManager) will throw exceptions at runtime even
            // for plain class lookup
            val project = ideaProject as? MockProject
            val environment = uastEnvironment
            if (project != null && environment != null) {
                val paths = environment.projectEnvironment.paths
                KotlinLintAnalyzerFacade(kotlinPerformanceManager).analyze(
                    kotlinFiles,
                    paths,
                    project,
                    environment,
                    javaLanguageLevel,
                    kotlinLanguageLevel
                )
            }
            val ok = super.prepare(contexts, javaLanguageLevel, kotlinLanguageLevel)
            if (project == null || contexts.isEmpty()) {
                return ok
            }
            // Now that we have a project context, ensure that the annotations manager
            // is up to date
            val annotationsManager =
                ExternalAnnotationsManager.getInstance(project) as LintExternalAnnotationsManager
            val target = pickBuildTarget(contexts.first().driver.projects)
            annotationsManager.updateAnnotationRoots(this@LintCliClient, target)
            return ok
        }
    }

    private class LintCliKotlinPerformanceManager(private val perfReportName: String) :
        CommonCompilerPerformanceManager("Lint CLI") {
        fun report(request: LintRequest) {
            notifyCompilationFinished()
            val sb = StringBuilder(perfReportName)
            val projects = request.getProjects()
            if (projects != null) {
                for (project in projects) {
                    sb.append('-')
                    sb.append(project.name)
                    project.buildVariant?.name.let { variantName ->
                        sb.append(variantName)
                    }
                }
            }
            sb.append(".txt")
            dumpPerformanceReport(File(sb.toString()))
        }

        init {
            enableCollectingPerformanceStatistics()
            resetAllCounters()
        }
    }

    companion object {
        // Environment variable, system property and internal system property used to tell lint to
        // override the configuration
        private const val LINT_OVERRIDE_CONFIGURATION_ENV_VAR = "LINT_OVERRIDE_CONFIGURATION"
        private const val LINT_CONFIGURATION_OVERRIDE_PROP = "lint.configuration.override"

        /** Whether lint should continue running after a baseline has been created  */
        fun continueAfterBaseLineCreated(): Boolean {
            return System.getProperty("lint.baselines.continue") == VALUE_TRUE
        }

        protected fun getTargetName(baselineVariantName: String): String {
            if (isGradle) {
                if (LintBaseline.VARIANT_ALL == baselineVariantName) {
                    return "lint"
                } else if (LintBaseline.VARIANT_FATAL == baselineVariantName) {
                    return "lintVitalRelease"
                }
            }
            return baselineVariantName
        }

        /** Look up the contents of the given line  */
        fun getLine(contents: CharSequence, line: Int): String? {
            val index = getLineOffset(contents, line)
            return if (index != -1) {
                getLineOfOffset(contents, index)
            } else {
                null
            }
        }

        private fun getLineOfOffset(contents: CharSequence, offset: Int): String {
            var end = CharSequences.indexOf(contents, '\n', offset)
            if (end == -1) {
                end = CharSequences.indexOf(contents, '\r', offset)
            } else if (end > 0 && contents[end - 1] == '\r') {
                end--
            }
            return contents.subSequence(offset, if (end != -1) end else contents.length).toString()
        }

        /** Look up the contents of the given line  */
        private fun getLineOffset(contents: CharSequence, line: Int): Int {
            var index = 0
            for (i in 0 until line) {
                index = CharSequences.indexOf(contents, '\n', index)
                if (index == -1) {
                    return -1
                }
                index++
            }
            return index
        }

        /**
         * Given a file, it produces a cleaned up path from the file. This will clean up the path such
         * that `foo/./bar` becomes `foo/bar` and `foo/bar/../baz` becomes `foo/baz`.
         *
         * Unlike [java.io.File.getCanonicalPath] however, it will **not** attempt to make
         * the file canonical, such as expanding symlinks and network mounts.
         *
         * @param file the file to compute a clean path for
         * @return the cleaned up path
         */
        @JvmStatic
        @VisibleForTesting
        fun getCleanPath(file: File): String {
            val path = file.path
            val sb = StringBuilder(path.length)
            if (path.startsWith(File.separator)) {
                sb.append(File.separator)
            }
            elementLoop@ for (element in Splitter.on(File.separatorChar).omitEmptyStrings().split(
                path
            )) {
                if (element == ".") {
                    continue
                } else if (element == "..") {
                    if (sb.isNotEmpty()) {
                        for (i in sb.length - 1 downTo 0) {
                            val c = sb[i]
                            if (c == File.separatorChar) {
                                sb.setLength(if (i == 0) 1 else i)
                                continue@elementLoop
                            }
                        }
                        sb.setLength(0)
                        continue
                    }
                }
                if (sb.length > 1) {
                    sb.append(File.separatorChar)
                } else if (sb.isNotEmpty() && sb[0] != File.separatorChar) {
                    sb.append(File.separatorChar)
                }
                sb.append(element)
            }
            if (path.endsWith(File.separator) && sb.isNotEmpty() && sb[sb.length - 1] != File.separatorChar) {
                sb.append(File.separator)
            }
            return sb.toString()
        }

        private var ourAlreadyWarned: MutableSet<File>? = null
    }
}
