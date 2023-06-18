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
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.currentPlatform
import com.android.Version
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.resources.ResourceRepository
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.FileStreamProvider
import com.android.manifmerger.ManifestMerger2.MergeFailureException
import com.android.manifmerger.MergingReport.MergedManifestKind
import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.LintCliFlags.ERRNO_APPLIED_SUGGESTIONS
import com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE
import com.android.tools.lint.LintCliFlags.ERRNO_ERRORS
import com.android.tools.lint.LintCliFlags.ERRNO_INTERNAL_CONTINUE
import com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.LintStats.Companion.create
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.GradleVisitor
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.JarFileIssueRegistry
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintDriver.Companion.KEY_CONDITION
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.client.api.LintXmlConfiguration
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.client.api.UastParser
import com.android.tools.lint.client.api.XmlParser
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.describeCounts
import com.android.tools.lint.detector.api.getEncodedString
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.guessGradleLocationForFile
import com.android.tools.lint.detector.api.isJdkFolder
import com.android.tools.lint.gradle.GroovyGradleVisitor
import com.android.tools.lint.helpers.DefaultJavaEvaluator
import com.android.tools.lint.helpers.DefaultUastParser
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.PathVariables
import com.android.utils.CharSequences
import com.android.utils.StdLogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.mock.MockProject
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.PERF_MANAGER
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.util.PerformanceCounter.Companion.resetAllCounters
import org.w3c.dom.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import kotlin.math.max

/**
 * Lint client for command line usage. Supports the flags in
 * [LintCliFlags], and offers text, HTML and XML reporting, etc.
 *
 * Minimal example:
 * <pre>
 * // files is a list of java.io.Files, typically a directory containing
 * // lint projects or direct references to project root directories
 * IssueRegistry registry = new BuiltinIssueRegistry();
 * LintCliFlags flags = new LintCliFlags();
 * LintCliClient client = new LintCliClient(flags);
 * int exitCode = client.run(registry, files);
 * </pre>
 */
open class LintCliClient : LintClient {
    constructor(clientName: String) : super(clientName) {
        flags = LintCliFlags()
        @Suppress("LeakingThis")
        val reporter =
            TextReporter(this, flags, System.out.printWriter(), false)
        flags.reporters.add(reporter)
        initialize()
    }

    @Deprecated("Specify client explicitly by calling {@link LintCliClient(String)} ")
    constructor() : this(CLIENT_UNIT_TESTS)

    constructor(flags: LintCliFlags, clientName: String) : super(clientName) {
        this.flags = flags
        initialize()
    }

    /** Returns the issue registry used by this client. */
    open var registry: IssueRegistry? = null
        protected set

    /** Returns the driver running the lint checks. */
    lateinit var driver: LintDriver
        protected set

    /** Flags configuring the lint runs. */
    val flags: LintCliFlags

    protected var validatedIds = false
    private var kotlinPerformanceManager: LintCliKotlinPerformanceManager? = null
    private var jdkHome: File? = null
    var uastEnvironment: UastEnvironment? = null
    val ideaProject: MockProject? get() = uastEnvironment?.ideaProject
    private var hasErrors = false
    protected var errorCount = 0
    protected var warningCount = 0

    /**
     * Definite incidents; these should be unconditionally reported.
     */
    val definiteIncidents: MutableList<Incident> = ArrayList()

    /**
     * Any conditionally reported incidents. These can have either
     * a [LintMap] with custom attributes for the detector to
     * process during reporting, or a [Constraint] stashed in the
     * [Incident.clientProperties] to be checked.
     */
    val provisionalIncidents: MutableList<Incident> = ArrayList()

    /**
     * Any data recorded into the map from
     * [LintClient.getPartialResults] for later analysis and potential
     * reporting via [Detector.checkPartialResults]
     */
    private var partialResults: MutableMap<Issue, PartialResult>? = null

    private fun initialize() {
        addGlobalXmlFallbackConfiguration()
    }

    /**
     * Allow configuration override to be injected with a system
     * property.
     */
    private fun addGlobalXmlFallbackConfiguration() {
        var configuration = System.getenv(LINT_OVERRIDE_CONFIGURATION_ENV_VAR)
        if (configuration == null) {
            configuration = System.getProperty(LINT_CONFIGURATION_OVERRIDE_PROP)
        }
        if (configuration != null) {
            val file = File(configuration)
            if (file.exists()) {
                val xmlConfiguration = LintXmlConfiguration.create(configurations, file)
                // This XML configuration is not associated with its location so remove its
                // directory scope
                xmlConfiguration.dir = null
                configurations.addGlobalConfigurations(fallback = xmlConfiguration)
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
     * Runs the static analysis command line driver. You need to add at
     * least one error reporter to the command line flags.
     */
    @Throws(IOException::class)
    fun run(registry: IssueRegistry, lintRequest: LintRequest): Int {
        return run(
            registry, lintRequest,
            analyze = { driver.analyze() }, finish = { performReporting() }
        )
    }

    /**
     * Analyzes a specific project in isolation; stores partial results
     * for that library, which will later be loaded and merged with
     * other projects in [mergeOnly].
     */
    fun analyzeOnly(registry: IssueRegistry, lintRequest: LintRequest): Int {
        assert(supportsPartialAnalysis())
        return run(registry, lintRequest, analyze = { driver.analyzeOnly() })
    }

    /**
     * Loads in all the partial results for a set of module dependencies
     * and creates a single report based on both taking the definite
     * results as well as conditionally processing the provisional
     * results.
     */
    fun mergeOnly(registry: IssueRegistry, lintRequest: LintRequest): Int {
        // We perform some additional validation during reporting
        validatedIds = false
        return run(
            registry, lintRequest,
            analyze = { driver.mergeOnly() }, finish = { performReporting() }
        )
    }

    /**
     * Creates a lint driver, sets up analytics, and performs the
     * analysis provided by [analyze] before cleaning up. An optional
     * [finish] lambda can be supplied. This is where for reporting
     * tasks the reports are actually written, and for errors halting
     * the build if set exit code is true, and so on.
     */
    private fun run(
        registry: IssueRegistry,
        lintRequest: LintRequest,
        analyze: () -> Unit,
        finish: () -> Int = { ERRNO_INTERNAL_CONTINUE }
    ): Int {
        val startTime = System.currentTimeMillis()
        this.registry = registry
        val kotlinPerfReport = System.getenv("KOTLIN_PERF_REPORT")
        if (kotlinPerfReport != null && kotlinPerfReport.isNotEmpty()) {
            kotlinPerformanceManager = LintCliKotlinPerformanceManager(kotlinPerfReport)
        }
        driver = createDriver(registry, lintRequest)
        driver.analysisStartTime = startTime
        addCancellationChecker()
        validateIssueIds()

        analyze()

        kotlinPerformanceManager?.report(lintRequest)
        sortResults()
        var projects: Collection<Project>? = lintRequest.getProjects()
        if (projects == null) {
            projects = knownProjects
        }
        if (!projects.isEmpty()) {
            val analytics = LintBatchAnalytics()
            analytics.logSession(registry, flags, driver, projects, definiteIncidents)
        }

        val exitCode = finish()
        if (exitCode != ERRNO_INTERNAL_CONTINUE) {
            return exitCode
        }

        return if (flags.isSetExitCode) if (hasErrors) ERRNO_ERRORS else ERRNO_SUCCESS else ERRNO_SUCCESS
    }

    private fun performReporting(): Int {
        val baseline = driver.baseline
        val stats = create(definiteIncidents, baseline)
        writeReports(stats)
        if (flags.isAutoFix) {
            val statistics = !flags.isQuiet
            val performer = LintFixPerformer(this, statistics)
            val fixed = performer.fix(definiteIncidents)
            if (fixed && flags.isAbortOnAutoFix) {
                val message =
                    """
                    One or more issues were fixed in the source code.
                    Aborting the build since the edits to the source files were performed **after** compilation, so the outputs do not contain the fixes. Re-run the build.
                    """.trimIndent()
                System.err.println(message)
                return ERRNO_APPLIED_SUGGESTIONS
            }
        }

        // Baselines only consulted during reporting
        val baselineFile = flags.outputBaselineFile ?: flags.baselineFile
        if (baselineFile != null && baseline != null) {
            emitBaselineDiagnostics(baseline, baseline.file, stats)
        }
        val outputBaselineFile = flags.outputBaselineFile
        val writeBaselineIfMissing = !flags.missingBaselineIsEmptyBaseline || flags.isUpdateBaseline
        if (outputBaselineFile != null && baseline != null) {
            baseline.file = outputBaselineFile
            baseline.close()
            // Not setting exit code to ERRNO_CREATED_BASELINE; that's the contract for this flag
        } else if (baselineFile != null && !baselineFile.exists() && writeBaselineIfMissing ||
            outputBaselineFile != null && baseline == null
        ) {
            val fileToWrite = outputBaselineFile ?: baselineFile!!
            val exitCode =
                writeNewBaselineFile(
                    stats,
                    file = fileToWrite,
                    writeEmptyBaseline =
                    !flags.missingBaselineIsEmptyBaseline || outputBaselineFile != null
                )
            if (exitCode != ERRNO_INTERNAL_CONTINUE) {
                return exitCode
            }
            if (!flags.isUpdateBaseline && outputBaselineFile == null) {
                System.err.println(getBaselineCreationMessage(fileToWrite))
            }
            when {
                outputBaselineFile != null -> {
                    // With --write-reference-baseline we continue even if the baseline was written
                }

                flags.isUpdateBaseline || !flags.isContinueAfterBaselineCreated -> {
                    return ERRNO_CREATED_BASELINE
                }

                else -> return ERRNO_SUCCESS
            }
        } else if (baseline != null &&
            baseline.writeOnClose &&
            baseline.fixedCount > 0 &&
            flags.isRemoveFixedBaselineIssues
        ) {
            baseline.close()
            return ERRNO_CREATED_BASELINE
        } else if (baseline != null && flags.isUpdateBaseline) {
            if (flags.missingBaselineIsEmptyBaseline && definiteIncidents.isEmpty()) {
                flags.baselineFile?.toPath()?.let { Files.deleteIfExists(it) }
            } else {
                baseline.close()
            }
            return ERRNO_CREATED_BASELINE
        }

        // If failing the build on exit: print at least one error to the
        // console to help pinpoint the problem.
        if (hasErrors && !reportingToConsole() && flags.isSetExitCode && !flags.isQuiet) {
            val writer = System.out.printWriter()
            val count = describeCounts(
                stats.errorCount,
                stats.warningCount,
                comma = false,
                capitalize = false
            )
            println("Lint found $count. First failure:")
            val reporter = Reporter.createTextReporter(this, LintCliFlags(), null, writer, false)
            reporter.setWriteStats(false)
            reporter.write(
                stats,
                listOf(definiteIncidents.first { it.severity.isError }),
                driver.registry
            )
        }

        return if (flags.isSetExitCode) if (hasErrors) ERRNO_ERRORS else ERRNO_SUCCESS else ERRNO_SUCCESS
    }

    /**
     * Writes a new baseline file.
     *
     * In the case when [writeEmptyBaseline] is false and there are no
     * incidents, does not write a new baseline file and deletes any
     * existing baseline file.
     *
     * @param stats the [LintStats]
     * @param file the file to write to.
     * @param writeEmptyBaseline whether to write a file when there are
     *     no lint issues.
     * @return [ERRNO_INTERNAL_CONTINUE] or another exit code in case of
     *     a problem
     */
    private fun writeNewBaselineFile(
        stats: LintStats,
        file: File,
        writeEmptyBaseline: Boolean
    ): Int {
        if (writeEmptyBaseline || definiteIncidents.isNotEmpty()) {
            val dir = file.parentFile
            if (dir != null && !dir.isDirectory) {
                if (!dir.mkdirs()) {
                    System.err.println("Couldn't create baseline folder $dir")
                    return ERRNO_INVALID_ARGS
                }
            }
            val reporter = Reporter.createXmlReporter(this, file, XmlFileType.BASELINE)
            reporter.pathVariables = pathVariables.filter(PathVariables::isPrivatePathVariable)
            reporter.setBaselineAttributes(this, baselineVariantName, flags.isCheckDependencies)
            reporter.write(stats, definiteIncidents, driver.registry)
        } else {
            Files.deleteIfExists(file.toPath())
        }
        return ERRNO_INTERNAL_CONTINUE
    }

    protected open fun sortResults() {
        definiteIncidents.sort()
    }

    /**
     * Writes out the various incidents to all the configured reporters.
     */
    protected open fun writeReports(stats: LintStats) {
        for (reporter in flags.reporters) {
            reporter.write(stats, definiteIncidents, driver.registry)
        }
    }

    /**
     * Stores the various analysis state (incidents, conditional
     * incidents, partial state, etc).
     */
    override fun storeState(project: Project) {
        writeIncidents(project, XmlFileType.INCIDENTS, definiteIncidents)
        writeIncidents(project, XmlFileType.CONDITIONAL_INCIDENTS, provisionalIncidents)
        writePartialResults(project)
        writeConfiguredIssues(project)
    }

    private fun writeIncidents(
        project: Project,
        type: XmlFileType,
        incidents: List<Incident>
    ) {
        val incidentsFile = getSerializationFile(project, type)
        if (incidents.isEmpty()) {
            incidentsFile.delete()
        } else {
            incidentsFile.parentFile?.mkdirs()
            XmlWriter(this, incidentsFile, type).writeIncidents(incidents)
        }
    }

    private fun writePartialResults(project: Project) {
        val type = XmlFileType.PARTIAL_RESULTS
        val partialFile = getSerializationFile(project, type)
        partialResults?.let { map: MutableMap<Issue, PartialResult> ->
            partialFile.parentFile?.mkdirs()
            val resultMap = map.mapValues { it.value.map() }
            XmlWriter(this, partialFile, type).writePartialResults(resultMap)
        } ?: partialFile.delete()
    }

    private fun writeConfiguredIssues(project: Project) {
        val type = XmlFileType.CONFIGURED_ISSUES
        val projectConfiguration = configurations.getConfigurationForProject(project)
        val issues = projectConfiguration.getConfiguredIssues(driver.registry, true)
        val issuesFile = getSerializationFile(project, type)
        if (issues.isEmpty()) {
            issuesFile.delete()
        } else {
            issuesFile.parentFile?.mkdirs()
            XmlWriter(this, issuesFile, type).writeConfiguredIssues(issues)
        }
    }

    /**
     * Merge analysis results into a report.
     *
     * This is the heart of the module-independent analysis: here we
     * figure out the module dependency graph, and load in all the state
     * (both definite incidents, as well as conditionally reported
     * incidents and partial data), and pass this data to the detectors
     * to do their own computation. We also check whether issues enabled
     * in this reporting project were disabled in any individual
     * module, since that means the project is configured incorrectly.
     */
    override fun mergeState(root: Project, driver: LintDriver) {
        // Load any partial results from dependencies we've already analyzed
        val projects = HashSet<Project>()
        val dependentsMap: MutableMap<Project, MutableList<Project>> = HashMap()
        projects.add(root)
        if (driver.checkDependencies) {
            for (dependency in root.allLibraries) {
                if (dependency.isExternalLibrary) {
                    continue
                }
                val dependents = dependentsMap[dependency]
                    ?: ArrayList<Project>().also { dependentsMap[dependency] = it }
                dependents.add(root)
                projects.add(dependency)
            }
        } else {
            // Even in non-check-dependencies scenarios we have to add in any dynamic
            // features since we've transferred them in as dependencies instead
            // (see LintModelModuleProject.resolveDependencies)
            for (dependency in root.allLibraries) {
                if (dependency.type == LintModelModuleType.DYNAMIC_FEATURE) {
                    val dependents = dependentsMap[dependency]
                        ?: ArrayList<Project>().also { dependentsMap[dependency] = it }
                    dependents.add(root)
                    projects.add(dependency)
                }
            }
        }

        // Results that were reported unconditionally, per project
        val definiteMap = HashMap<Project, List<Incident>>()
        // Results that were reported with conditions or associated data, per project
        val provisionalMap = HashMap<Project, List<Incident>>()
        // Data that was reported without associated incidents, per project
        val dataMap = HashMap<Issue, MutableMap<Project, LintMap>>()
        // Issues configured away from their defaults during the analysis
        val issueMap: MutableMap<Project, MutableMap<String, Severity>> = HashMap()
        // Read partial and definite results from each dependency and initialize
        // above data structures
        for (project in projects) {
            driver.computeDetectors(project)
            val registry = driver.registry

            val conditional = getSerializationFile(project, XmlFileType.CONDITIONAL_INCIDENTS)
            if (conditional.isFile) {
                provisionalMap[project] =
                    XmlReader(this, registry, project, conditional).getIncidents()
            }

            val definite = getSerializationFile(project, XmlFileType.INCIDENTS)
            if (definite.isFile) {
                definiteMap[project] = XmlReader(this, registry, project, definite).getIncidents()
            }

            val partialFile = getSerializationFile(project, XmlFileType.PARTIAL_RESULTS)
            if (partialFile.isFile) {
                val partial = XmlReader(this, registry, project, partialFile).getPartialResults()

                for ((issue, list) in partial) {
                    val projectMap = dataMap[issue]
                        ?: HashMap<Project, LintMap>().also { dataMap[issue] = it }
                    projectMap[project] = list
                }
            }

            val issuesFile = getSerializationFile(project, XmlFileType.CONFIGURED_ISSUES)
            if (issuesFile.isFile) {
                val issues = XmlReader(this, registry, project, issuesFile).getConfiguredIssues()
                for ((issue: String, severity) in issues) {
                    val projectMap = issueMap[project]
                        ?: HashMap<String, Severity>().also { issueMap[project] = it }
                    projectMap[issue] = severity
                }
            }
        }

        // Merge incidents into the report
        for ((library, dependents) in dependentsMap.entries) {
            val libraryConfig = library.getConfiguration(driver)
            val libraryConfigLeaf = configurations.getScopeLeaf(libraryConfig)
            val libraryConfigPrevParent = libraryConfigLeaf.parent
            try {
                for (main in dependents) {
                    val mainConfig = main.getConfiguration(driver)
                    val mainContext = Context(driver, main, main, main.dir)
                    libraryConfigLeaf.setParent(mainConfig)
                    mergeIncidents(library, main, mainContext, definiteMap, provisionalMap)
                    val libraryIssues = issueMap[library] ?: emptyMap()
                    checkConfigured(library, libraryIssues, main, mainContext)
                }
            } finally {
                configurations.setParent(libraryConfigLeaf, libraryConfigPrevParent)
            }
        }

        // Also merge in results from the main app (same project as the one for the report)
        if (!dependentsMap.containsKey(root)) {
            val rootContext = Context(driver, root, root, root.dir)
            mergeIncidents(root, root, rootContext, definiteMap, provisionalMap)

            if (dataMap.isNotEmpty()) {
                val detectorMap = HashMap<Issue, Detector>()
                for ((issue, map) in dataMap.entries) {
                    val results = PartialResult(issue, map)
                    val detector = detectorMap[issue]
                        ?: issue.implementation.detectorClass.newInstance()
                            .also { detectorMap[issue] = it }
                    detector.checkPartialResults(rootContext, results)
                }
            }

            driver.processMergedProjects(rootContext)
        }
    }

    /**
     * Checks to make sure that for any issues enabled in the reporting
     * project the corresponding issue was enabled in the libraries.
     */
    private fun checkConfigured(
        library: Project,
        libraryConfigured: Map<String, Severity>,
        main: Project,
        mainContext: Context
    ) {
        val registry = registry!!
        val mainConfiguration = main.getConfiguration(driver)
        val mainConfigured: Map<String, Severity> =
            mainConfiguration.getConfiguredIssues(registry, true)
        for ((issue, severity) in mainConfigured) {
            if (severity == Severity.IGNORE) {
                continue
            }
            val librarySeverity = libraryConfigured[issue]
            if (librarySeverity == Severity.IGNORE ||
                // Also flag issues not explicitly listed in the other configuration
                // if they're off by default
                librarySeverity == null && registry.getIssue(issue)?.isEnabledByDefault() == false
            ) {
                val location = mainConfiguration.getIssueConfigLocation(
                    issue,
                    specificOnly = true,
                    severityOnly = true
                ) ?: Location.create(main.dir)
                val appSeverity = severity.toName()
                mainContext.report(
                    Incident(
                        IssueRegistry.CANNOT_ENABLE_HIDDEN, location,
                        "Issue `$issue` was configured with severity `$appSeverity` in ${main.name}, but was not enabled (or was disabled) in library ${library.name}"
                    )
                )
            }
        }
    }

    /**
     * Given maps from projects to lists of definite and provisional
     * incidents, report the definite incidents, and check the
     * conditional incidents and report if they're accepted by the
     * detectors.
     */
    @JvmSuppressWildcards
    protected open fun mergeIncidents(
        library: Project,
        main: Project,
        mainContext: Context,
        definiteMap: Map<Project, List<Incident>>,
        provisionalMap: Map<Project, List<Incident>>
    ) {
        val projectContext = Context(driver, library, main, main.dir)

        val definite = definiteMap[library]
        // Note that we only apply the app project context here, not the library
        // context: we want to only apply the local lint.xml configuration for
        // the app project.
        if (definite != null) {
            for (incident in definite) {
                // We use the normal report mechanism instead of just adding to incidents
                // list and updating the stats (calling countIncident(incident)) because
                // we want to apply any local configuration (such as ignoring issues
                // that are disabled locally).
                projectContext.report(incident)
            }
        }

        // Some of these will be re-reported, which will add them to the incidents list
        val provisional = provisionalMap[library] ?: emptyList()
        driver.mergeConditionalIncidents(projectContext, provisional)
    }

    /**
     * Returns the path to the file containing any data of the given
     * [xmlType]. Defaults to the build folder, possibly with a variant
     * name included.
     */
    open fun getSerializationFile(project: Project, xmlType: XmlFileType): File {
        val variant = project.buildVariant
        val dir = variant?.partialResultsDir
            ?: variant?.module?.buildFolder
            ?: File(project.dir, "build")
        val variantName = variant?.name ?: "all"
        return File(dir, xmlType.getDefaultFileName(variantName))
    }

    private fun getBaselineCreationMessage(baselineFile: File): String {
        val summary = "Created baseline file $baselineFile"

        if (continueAfterBaselineCreated()) {
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

    private fun emitBaselineDiagnostics(
        baseline: LintBaseline,
        baselineFile: File,
        stats: LintStats
    ) {
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
                    comma = false,
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
            val creationVariant = baseline.getAttribute(ATTR_VARIANT)
            if (creationVariant != null && creationVariant != checkVariant) {
                println("\nNote: The baseline was created using a different target/variant than it was checked against.")
                println("Creation variant: " + getTargetName(creationVariant))
                println("Current variant: " + if (checkVariant != null) getTargetName(checkVariant) else "none")
            }
            val baselineTransitive =
                baseline.getAttribute(ATTR_CHECK_DEPS)?.let { it == VALUE_TRUE }
            val transitive = flags.isCheckDependencies
            if (baselineTransitive != null && baselineTransitive != transitive) {
                println("\nNote: The baseline was created using `includeDependencies=$baselineTransitive`,")
                println("but lint is currently running with `includeDependencies=$transitive`.")
                if (transitive) {
                    println("This means that any incidents found in the dependencies are not included")
                    println("in the baseline and will be reported as new incidents.")
                } else {
                    println("This means that any incidents listed in the baseline from dependencies")
                    println("are not included in the current analysis, so lint will consider them")
                    println("\"fixed\" when comparing with the baseline.")
                }
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
                if (!validatedIds && (
                            type === LintListener.EventType.SCANNING_PROJECT ||
                                    type === LintListener.EventType.MERGING
                            )
                ) {
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
        driver.ignoreTestFixturesSources = flags.isIgnoreTestFixturesSources
        driver.checkGeneratedSources = flags.isCheckGeneratedSources
        driver.fatalOnlyMode = flags.isFatalOnly
        driver.checkDependencies = flags.isCheckDependencies
        driver.allowSuppress = flags.allowSuppress
        driver.allowBaselineSuppress = flags.allowBaselineSuppress
        driver.skipAnnotations = flags.skipAnnotations
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

    protected open fun addCancellationChecker() {
        driver.addLintListener(object : LintListener {
            override fun update(
                driver: LintDriver,
                type: LintListener.EventType,
                project: Project?,
                context: Context?
            ) {
                // Some build systems such as Gradle use Thread.interrupt() to cancel workers.
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException()
                }
            }
        })
    }

    /** Creates a lint request. */
    protected open fun createLintRequest(files: List<File>): LintRequest {
        return LintRequest(this, files).also { configureLintRequest(it) }
    }

    /** Configures a lint request. */
    protected open fun configureLintRequest(lintRequest: LintRequest) {
    }

    override fun log(severity: Severity, exception: Throwable?, format: String?, vararg args: Any) {
        System.out.flush()
        if (format != null) {
            System.err.println(String.format(format, *args))
        }
        exception?.printStackTrace()
    }

    override val xmlParser: XmlParser
        get() = LintCliXmlParser(this)

    private var xmlDocuments = HashMap<File, Document>()

    override fun getXmlDocument(file: File, contents: CharSequence?): Document? {
        return xmlDocuments[file] ?: super.getXmlDocument(file, contents)?.also {
            xmlDocuments[file] = it
        }
    }

    override fun getResources(
        project: Project,
        scope: ResourceRepositoryScope
    ): ResourceRepository {
        return LintResourceRepository.get(this, project, scope)
    }

    /** File content cache. */
    private val fileContentCache: MutableMap<File, CharSequence> = HashMap(100)

    /** Read the contents of the given file, possibly cached. */
    fun getSourceText(file: File): CharSequence {
        return fileContentCache.computeIfAbsent(file) { readFile(file) }
    }

    /**
     * Records the given source text as the source to be used for the
     * given file when looked up via [getSourceText].
     */
    fun setSourceText(file: File, text: CharSequence?) {
        text?.let { fileContentCache[file] = it }
    }

    /** Storage for [getClientProperty] */
    private var clientProperties: MutableMap<Any, Any>? = null

    /**
     * Associate the given key and value data with this project. Used
     * to store project specific state without introducing external
     * caching.
     */
    open fun putClientProperty(key: Any, value: Any?) {
        val map = clientProperties
            ?: HashMap<Any, Any>().also { clientProperties = it }
        if (value != null) {
            map[key] = value
        } else {
            map -= key
        }
    }

    /**
     * Retrieve the given key and value data associated with this
     * project. Used to store project specific state without introducing
     * external caching.
     */
    open fun <T> getClientProperty(key: Any): T? {
        @Suppress("UNCHECKED_CAST")
        return clientProperties?.get(key) as? T?
    }

    override fun getUastParser(project: Project?): UastParser = LintCliUastParser(project)

    override fun getGradleVisitor(): GradleVisitor {
        return GroovyGradleVisitor()
    }

    override fun report(
        context: Context,
        incident: Incident,
        format: TextFormat
    ) {
        countIncident(incident.severity)

        // Store the message in the raw format internally such that we can
        // convert it to text for the text reporter, HTML for the HTML reporter
        // and so on.
        incident.message = format.convertTo(incident.message, TextFormat.RAW)
        incident.project = context.project

        val location = incident.location
        // noinspection FileComparisons
        if (context.file === location.file && (location.start?.line ?: -1) >= 0) {
            // Common scenario: the error is in the current source file;
            // we already have that source so make sure we can look it up cheaply when
            // generating the reports
            setSourceText(context.file, context.getContents())
        }

        definiteIncidents.add(incident)
    }

    private fun countIncident(severity: Severity) {
        if (severity.isError) {
            hasErrors = true
            errorCount++
        } else if (severity === Severity.WARNING) { // Don't count informational as a warning
            warningCount++
        }
    }

    override fun report(context: Context, incident: Incident, constraint: Constraint) {
        if (driver.mode == LintDriver.DriverMode.MERGE) {
            // Constraints were intended to be used when you are analyzing
            // a module and storing constraints for later automatic filtering by
            // lint. But there's really no reason why we can't also support
            // them during the merge phase, so process them here.
            if (constraint.accept(context, incident)) {
                context.report(incident)
            }
        } else {
            incident.clientProperties = LintMap().put(KEY_CONDITION, constraint)
            provisionalIncidents.add(incident)
        }
    }

    override fun report(context: Context, incident: Incident, map: LintMap) {
        if (driver.mode == LintDriver.DriverMode.MERGE) {
            // Unlike report(Incident, Constraint) it's definitely always an
            // error to attempt to store more state during merging, so flag
            // these for developers.
            val (detector, issues) = Context.findCallingDetector(driver)
                ?: error("Unexpected call to report(Incident, LintMap) during the merge phase")
            val stack = StringBuilder()
            LintDriver.appendStackTraceSummary(
                RuntimeException(),
                stack,
                skipFrames = 1,
                maxFrames = 20
            )
            val message =
                """
                The lint detector
                    `$detector`
                called `${"report(Incident, LintMap)"}` during the merge phase.

                This does not work correctly; this is already the merge phase, so storing
                data for later processing is pointless and probably not what was intended.

                ${issues.joinToString(separator = ",") { "\"$it\"" }}
                """.trimIndent() + "\nCall stack: $stack"
            report(
                client = driver.client,
                issue = IssueRegistry.LINT_ERROR,
                message = message,
                location = Location.create(incident.file),
                project = incident.project,
                driver = driver
            )
        }

        incident.clientProperties = map
        provisionalIncidents.add(incident)
    }

    override fun getPartialResults(project: Project, issue: Issue): PartialResult {
        val partialResults = partialResults
            ?: run {
                val partialResults = LinkedHashMap<Issue, PartialResult>()
                    .also { this.partialResults = it }
                for (dep in project.allLibraries.filter { !it.isExternalLibrary }) {
                    val file = getSerializationFile(dep, XmlFileType.PARTIAL_RESULTS)
                    if (!file.isFile) {
                        continue
                    }
                    val reader = XmlReader(this, driver.registry, project, file)
                    val results = reader.getPartialResults()
                    for ((loadedIssue: Issue, map) in results) {
                        val target: PartialResult = partialResults[loadedIssue]
                            ?: run {
                                val newMap = HashMap<Project, LintMap>()
                                newMap[dep] = LintMap()
                                PartialResult(loadedIssue, newMap)
                                    .also { partialResults[loadedIssue] = it }
                            }
                        val targetMap = target.map()
                        targetMap.putAll(map)
                    }
                }
                partialResults
            }

        return partialResults[issue]
            ?: run {
                val map = HashMap<Project, LintMap>()
                val default = LintMap()
                map[project] = default
                PartialResult(issue, map).also { partialResults[issue] = it }
            }
    }

    override fun readFile(file: File): CharSequence {
        val contents = try {
            getEncodedString(this, file, false)
        } catch (e: IOException) {
            ""
        }
        val path = file.path
        if ((
                    path.endsWith(DOT_JAVA) ||
                            path.endsWith(DOT_KT) ||
                            path.endsWith(DOT_KTS)
                    ) &&
            CharSequences.indexOf(contents, '\r') != -1
        ) {
            // Offsets in these files will be relative to PSI's text offsets (which may
            // have converted line offsets); make sure we use the same offsets.
            // (Can't just do this on Windows; what matters is whether the file contains CRLF's.)
            val vFile = StandardFileSystems.local().findFileByPath(path)
            if (vFile != null) {
                val document = FileDocumentManager.getInstance().getDocument(vFile)
                if (document != null) {
                    return document.text
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

    override fun findRuleJars(project: Project): Iterable<File> {
        return flags.lintRuleJarsOverride ?: super.findRuleJars(project)
    }

    override fun getResourceFolders(project: Project): List<File> {
        return flags.resourcesOverride ?: return super.getResourceFolders(project)
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
     * Checks that any id's specified by id refer to valid, known,
     * issues. This typically can't be done right away (in for example
     * the Gradle code which handles DSL references to strings, or in
     * the command line parser for the lint command) because the full
     * set of valid id's is not known until lint actually starts running
     * and for example gathers custom rules from all AAR dependencies
     * reachable from libraries, etc.
     */
    private fun validateIssueIds(project: Project?) {
        if (::driver.isInitialized) {
            val registry = driver.registry
            if (!registry.isIssueId(HardcodedValuesDetector.ISSUE.id)) {
                // This should not be necessary, but there have been some strange
                // reports where lint has reported some well known builtin issues
                // to not exist:
                //
                //   Warning: Unknown issue id "DuplicateDefinition" [UnknownIssueId]
                //   Warning: Unknown issue id "GradleIdeError" [UnknownIssueId]
                //   Warning: Unknown issue id "InvalidPackage" [UnknownIssueId]
                //   Warning: Unknown issue id "JavascriptInterface" [UnknownIssueId]
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

            // Only check the override configuration once, during reporting;
            // it gets injected into every module during the analysis there
            // and it would get reported repeatedly, once for each module.
            if (driver.mode != LintDriver.DriverMode.ANALYSIS_ONLY) {
                val override = configurations.overrides
                override?.validateIssueIds(this, driver, project, registry)
            }

            if (project != null) {
                if (driver.mode != LintDriver.DriverMode.MERGE) {
                    val configuration: Configuration = project.getConfiguration(driver)
                    configuration.validateIssueIds(this, driver, project, registry)
                }
            }
        }
    }

    private fun validateIssueIds(
        project: Project?,
        registry: IssueRegistry,
        ids: Collection<String>?,
        file: File? = null
    ) {
        if (ids != null) {
            for (id in ids) {
                if (registry.getIssue(id) == null) {
                    reportNonExistingIssueId(project, registry, id, file)
                }
            }
        }
    }

    private fun reportNonExistingIssueId(
        project: Project?,
        registry: IssueRegistry,
        id: String,
        file: File? = null
    ) {
        if (IssueRegistry.isDeletedIssueId(id)) {
            // Recently deleted, but avoid complaining about leftover configuration
            return
        }
        if (JarFileIssueRegistry.isRejectedIssueId(id)) {
            // Issue was not loaded (perhaps incompatible with this version of lint);
            // we're already complaining about that, so don't also complain that
            // it's an "unknown" issue
            return
        }
        val message = Configuration.getUnknownIssueIdErrorMessage(id, registry)
        if (::driver.isInitialized && project != null && !isSuppressed(IssueRegistry.UNKNOWN_ISSUE_ID)) {
            val location = if (file != null)
                guessGradleLocationForFile(this, file, id)
            else
                guessGradleLocation(this, project.dir, id)
            report(
                this,
                IssueRegistry.UNKNOWN_ISSUE_ID,
                message,
                driver,
                project,
                location,
                LintFix.create().data(ATTR_ID, id)
            )
        } else {
            log(Severity.WARNING, null, "Lint: %1\$s", message)
        }
    }

    override fun getDisplayPath(file: File, project: Project?, format: TextFormat): String {
        return if (project != null) {
            val path = getDisplayPath(project, file, false)
            TextFormat.TEXT.convertTo(path, format)
        } else {
            super.getDisplayPath(file, null, format)
        }
    }

    /**
     * Like getDisplayPath(File, project, format), but emits in
     * TextFormat.TEXT.
     */
    fun getDisplayPath(project: Project?, file: File): String {
        return getDisplayPath(project, file, flags.isFullPath)
    }

    fun getDisplayPath(project: Project?, file: File, fullPath: Boolean): String {
        project ?: return file.path
        val referenceDir = project.referenceDir
        return getDisplayPath(referenceDir, file, fullPath)
    }

    fun getDisplayPath(referenceDir: File, file: File, fullPath: Boolean): String {
        var path = file.path
        if (!fullPath && path.startsWith(referenceDir.path)) {
            var chop = referenceDir.path.length
            if (path.length > chop && path[chop] == File.separatorChar) {
                chop++
                path = path.substring(chop)
                if (path.isEmpty()) {
                    path = file.name
                }
                return path
            } else if (path.length == chop) {
                return file.name
            }
        }

        if (fullPath) {
            path = getCleanPath(file.absoluteFile)
        } else if (file.isAbsolute) {
            path = getRelativePath(referenceDir, file) ?: file.path
            if (containsEmbeddedParentRef(path)) {
                path = getRelativePath(referenceDir.canonicalFile, file.canonicalFile) ?: file.path
            }
        }

        return path
    }

    /**
     * Is there an embedded parent path in the given path? Should
     * return true for "foo/bar/../baz" and "..\\foo\\bar" but not
     * "../../foo/bar".
     */
    private fun containsEmbeddedParentRef(path: String): Boolean {
        var index = 0
        while (index < path.length) {
            if (isParentRef(path, index)) {
                index += 3
            } else {
                while (index < path.length) {
                    val next = path.indexOf("..", index)
                    if (isParentRef(path, next)) {
                        return true
                    } else {
                        index += 2
                    }
                }
            }
        }

        return false
    }

    /**
     * Is the string at the given [index] in the given [path] a parent
     * reference, e.g. "../" or "..\" ?
     */
    private fun isParentRef(path: String, index: Int): Boolean {
        return path.startsWith("..", index) &&
                (index == path.length - 2 || path[index + 2] == '/' || path[index + 2] == '\\')
    }

    /**
     * Returns whether all warnings are enabled, including those
     * disabled by default.
     */
    val isAllEnabled: Boolean
        get() = flags.isCheckAllWarnings

    /**
     * Returns true if the given issue has been explicitly disabled.
     */
    fun isSuppressed(issue: Issue): Boolean {
        val disabledCategories = flags.disabledCategories
        if (disabledCategories != null) {
            val category = issue.category
            if (disabledCategories.contains(category) || category.parent != null &&
                disabledCategories.contains(category.parent)
            ) {
                return true
            }
        }
        return flags.suppressedIds.contains(issue.id)
    }

    /** Returns true if the given issue has been explicitly enabled. */
    fun isExplicitlyEnabled(issue: Issue): Boolean {
        val enabledCategories = flags.enabledCategories
        if (enabledCategories != null) {
            val category = issue.category
            if (enabledCategories.contains(category) || category.parent != null &&
                enabledCategories.contains(category.parent)
            ) {
                return true
            }
        }

        return flags.enabledIds.contains(issue.id)
    }

    /**
     * Returns true if Kotlin scripting may be required based on the
     * [driver]'s [LintDriver.scope] and the files in [allProjects].
     */
    private fun mayNeedKotlinScripting(allProjects: Set<Project>): Boolean {
        if (::driver.isInitialized && !driver.scope.contains(Scope.GRADLE_FILE)) {
            return false
        }

        for (project in allProjects) {
            val files = project.subset ?: project.gradleBuildScripts
            for (file in files) {
                if (file.name.endsWith(DOT_KTS)) {
                    return true
                }
            }
        }
        return false
    }

    public override fun initializeProjects(
        driver: LintDriver?,
        knownProjects: Collection<Project>
    ) {
        if (driver?.mode == LintDriver.DriverMode.MERGE) {
            // The costly parsing environment is not required (or supported!) when merging
            val config = UastEnvironment.Configuration.create(enableKotlinScripting = false)
            val env = UastEnvironment.create(config)
            uastEnvironment = env
            return
        }
        // Initialize the associated idea project to use
        val includeTests = !flags.isIgnoreTestSources
        // knownProject only lists root projects, not dependencies
        val allProjects = Sets.newIdentityHashSet<Project>()
        for (project in knownProjects) {
            allProjects.add(project)
            allProjects.addAll(project.allLibraries)
        }
        val sourceRoots: MutableSet<File> = LinkedHashSet(10)
        val classpathRoots: MutableSet<File> = LinkedHashSet(50)
        for (project in allProjects) {
            // Note that there could be duplicates here since we're including multiple library
            // dependencies that could have the same dependencies (e.g. lib1 and lib2 both
            // referencing guava.jar)
            sourceRoots.addAll(project.javaSourceFolders)
            if (includeTests) {
                sourceRoots.addAll(project.testSourceFolders)
            }
            sourceRoots.addAll(project.generatedSourceFolders)
            classpathRoots.addAll(project.getJavaLibraries(true))
            if (includeTests) {
                classpathRoots.addAll(project.testLibraries)
            }
            if (!flags.isIgnoreTestFixturesSources) {
                sourceRoots.addAll(project.testFixturesSourceFolders)
                classpathRoots.addAll(project.testFixturesLibraries)
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
            // (We also enable this for unit tests where there is no actual compilation;
            // here, the presence of class files is simulating binary-only access
            if (project.isLibrary || isUnitTest) {
                classpathRoots.addAll(project.javaClassFolders)
            } else if (project.isGradleProject) {
                // As of 3.4, R.java is in a special jar file
                for (f in project.javaClassFolders) {
                    if (f.name == SdkConstants.FN_R_CLASS_JAR) {
                        classpathRoots.add(f)
                    }
                }
            }
        }
        addBootClassPath(knownProjects, classpathRoots)
        var maxLevel = LanguageLevel.JDK_1_7
        for (project in knownProjects) {
            val level = project.javaLanguageLevel
            if (maxLevel.isLessThan(level)) {
                maxLevel = level
            }
        }

        for (file in sourceRoots + classpathRoots) {
            // IntelliJ expects absolute file paths, otherwise resolution can fail in subtle ways.
            require(file.isAbsolute) { "Relative Path found: $file. All paths should be absolute." }
        }

        val config = UastEnvironment.Configuration.create(
            enableKotlinScripting = mayNeedKotlinScripting(allProjects)
        )
        config.javaLanguageLevel = maxLevel
        config.addSourceRoots(sourceRoots.toList())
        config.addClasspathRoots(classpathRoots.toList())
        config.kotlinCompilerConfig.putIfNotNull(PERF_MANAGER, kotlinPerformanceManager)
        jdkHome?.let {
            config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
            config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
        }

        val env = UastEnvironment.create(config)
        uastEnvironment = env
        kotlinPerformanceManager?.notifyCompilerInitialized(-1, -1, "Android Lint")

        for (project in allProjects) {
            project.ideaProject = env.ideaProject
        }
        super.initializeProjects(driver, knownProjects)
    }

    protected open fun addBootClassPath(
        knownProjects: Collection<Project>,
        files: MutableSet<File>
    ): Boolean {
        // TODO: Use bootclasspath from Gradle?

        val buildTarget = pickBuildTarget(knownProjects)
        if (buildTarget != null) {
            @Suppress("UNNECESSARY_SAFE_CALL") // because of mocking
            val file: File? = buildTarget.getPath(IAndroidTarget.ANDROID_JAR)?.toFile()
            if (file != null) {
                // because we're partially mocking it in some tests
                files.add(file)
                return true
            }
        }

        val jdkHome = getJdkHome()
        if (jdkHome != null) {
            val isJre = !isJdkFolder(jdkHome)
            val roots = JavaSdkUtil.getJdkClassesRoots(jdkHome.toPath(), isJre)
            for (root in roots) {
                val rootFile = root.toFile()
                if (rootFile.exists()) {
                    files.add(rootFile)
                }
            }

            // TODO: When the JRE/JDK distinction no longer applies, simplify the jdkHome setup.
            if (!isJre) {
                this.jdkHome = jdkHome
            }
            return true
        }

        return false
    }

    /**
     * Return the best build target to use among the given set of
     * projects. This is necessary because we need to pick a single
     * target to use to (for example) configure a boot classpath for the
     * parsing infrastructure, but in theory Gradle lets you configure
     * different compileSdkVersions for different modules, so here we
     * pick the highest of the versions to make sure it's capable of
     * resolving all library calls into the platform.
     */
    private fun pickBuildTarget(knownProjects: Collection<Project>): IAndroidTarget? {
        return knownProjects.asSequence()
            .filter { it.isAndroidProject }
            .mapNotNull { it.buildTarget }
            .maxByOrNull { it.version }
    }

    public override fun disposeProjects(knownProjects: Collection<Project>) {
        uastEnvironment?.dispose()
        uastEnvironment = null
        super.disposeProjects(knownProjects)
    }

    /**
     * Synchronizes any options specified in lint.xml with the
     * [LintCliFlags] object.
     */
    fun syncConfigOptions() {
        val configs = generateSequence(configurations.fallback) {
            configurations.getParentConfiguration(it)
        }
        val config = configs.filterIsInstance(LintXmlConfiguration::class.java).firstOrNull()
            ?: return

        val checkAllWarnings = config.getCheckAllWarnings()
        if (checkAllWarnings != null) {
            flags.isCheckAllWarnings = checkAllWarnings
        }
        val ignoreWarnings = config.getIgnoreWarnings()
        if (ignoreWarnings != null) {
            flags.isIgnoreWarnings = ignoreWarnings
        }
        val warningsAsErrors = config.getWarningsAsErrors()
        if (warningsAsErrors != null) {
            flags.isWarningsAsErrors = warningsAsErrors
        }
        val fatalOnly = config.getFatalOnly()
        if (fatalOnly != null) {
            flags.isFatalOnly = fatalOnly
        }
        val checkTestSources = config.getCheckTestSources()
        if (checkTestSources != null) {
            flags.isCheckTestSources = checkTestSources
        }
        val ignoreTestSources = config.getIgnoreTestSources()
        if (ignoreTestSources != null) {
            flags.isIgnoreTestSources = ignoreTestSources
        }
        val checkGeneratedSources = config.getCheckGeneratedSources()
        if (checkGeneratedSources != null) {
            flags.isCheckGeneratedSources = checkGeneratedSources
        }
        val checkDependencies = config.getCheckDependencies()
        if (checkDependencies != null) {
            flags.isCheckDependencies = checkDependencies
        }
        val explainIssues = config.getExplainIssues()
        if (explainIssues != null) {
            flags.isExplainIssues = explainIssues
        }
        val removeFixedBaselineIssues = config.getRemoveFixedBaselineIssues()
        if (removeFixedBaselineIssues != null) {
            flags.setRemovedFixedBaselineIssues(removeFixedBaselineIssues)
        }
        val abortOnError = config.getAbortOnError()
        if (abortOnError != null) {
            flags.isSetExitCode = abortOnError
        }
        val baselineFile = config.baselineFile
        if (baselineFile != null) {
            flags.baselineFile =
                if (baselineFile.path == SdkConstants.VALUE_NONE) null else baselineFile
        }
        val applySuggestions = config.getApplySuggestions()
        if (applySuggestions != null && applySuggestions) {
            flags.isAutoFix = true
        }
    }

    fun readStamp(): String? {
        val stamp = LintCliClient.javaClass.getResourceAsStream("/resources/stamp.txt")
        return stamp?.readBytes()?.toString(Charsets.UTF_8)?.trim()
    }

    override fun getClientRevision(): String {
        val plugin = Version.ANDROID_GRADLE_PLUGIN_VERSION
        val stamp = readStamp()?.let { " [$it] " } ?: ""
        return (plugin ?: "unknown") + stamp
    }

    fun haveErrors(): Boolean {
        return errorCount > 0
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use the List<File> version")
    override fun createUrlClassLoader(urls: Array<URL>, parent: ClassLoader): ClassLoader {
        return if (isGradle || currentPlatform() == PLATFORM_WINDOWS) {
            createUrlClassLoader(
                urls.map { File(UrlClassLoader.urlToFilePath(it.path)) }.toList(),
                parent
            )
        } else {
            @Suppress("DEPRECATION")
            super.createUrlClassLoader(urls, parent)
        }
    }

    override fun createUrlClassLoader(files: List<File>, parent: ClassLoader): ClassLoader {
        return if (isGradle || currentPlatform() == PLATFORM_WINDOWS) {
            // When lint is invoked from Gradle, it's normally running in the Gradle
            // daemon which sticks around for a while, And URLClassLoader will on
            // Windows lock the jar files which is problematic if the jar files are
            // in build/ -- that will prevent a subsequent ./gradlew clean from
            // succeeding. So here we'll use the IntelliJ platform's UrlClassLoader
            // instead which does not lock files. See
            // JarFileIssueRegistry#loadAndCloseURLClassLoader for more details.
            UrlClassLoader.build().parent(parent).files(files.map { it.toPath() }).get()
        } else {
            super.createUrlClassLoader(files, parent)
        }
    }

    override fun getMergedManifest(project: Project): Document? {
        val manifests: MutableList<File> = ArrayList()
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

        // Earlier we had an optimization here where there's only a single
        // manifest file we'd just return it and pretend it's the merged manifest.
        // That was problematic because in that case we don't get a blame file
        // which messes up the attribution code in some cases.
        // In real and realistic projects there's not a single manifest anyway
        // so this optimization isn't useful.
        try {
            val logger = StdLogger(StdLogger.Level.INFO)
            val type =
                if (project.isLibrary) ManifestMerger2.MergeType.LIBRARY else ManifestMerger2.MergeType.APPLICATION
            val blameFile = File.createTempFile("manifest-blame", ".txt")
            blameFile.deleteOnExit()
            val mergeReport = ManifestMerger2.newMerger(mainManifest, logger, type)
                .withFeatures(
                    // TODO: How do we get the *opposite* of EXTRACT_FQCNS:
                    // ensure that all names are made fully qualified?
                    ManifestMerger2.Invoker.Feature.SKIP_BLAME,
                    ManifestMerger2.Invoker.Feature.SKIP_XML_STRING,
                    ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT
                )
                .addLibraryManifests(*manifests.toTypedArray())
                .withFileStreamProvider(object : FileStreamProvider() {
                    @Throws(FileNotFoundException::class)
                    override fun getInputStream(file: File): InputStream {
                        //noinspection FileComparisons
                        if (injectedFile == file) {
                            return CharSequences.getInputStream(injectedXml.toString())
                        }
                        val text = readFile(file)
                        // TODO: Avoid having to convert back and forth
                        return CharSequences.getInputStream(text)
                    }
                })
                .setMergeReportFile(blameFile)
                .merge()
            val xmlDocument = mergeReport.getMergedXmlDocument(MergedManifestKind.MERGED)
            if (xmlDocument != null) {
                val document = xmlDocument.xml
                if (document != null) {
                    resolveMergeManifestSources(document, blameFile)
                    // resolveMergeManifestSources(document, mergeReport.actions)

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

    override fun getRootDir(): File? {
        if (::driver.isInitialized) {
            driver.request.srcRoot?.let {
                return it
            }
        }

        return super.getRootDir()
    }

    /**
     * True if this client has at least one reporter writing to the
     * console (stdout/stderr)
     */
    fun reportingToConsole(): Boolean {
        return flags.reporters.any { it is TextReporter && it.isWriteToConsole }
    }

    override fun getCacheDir(name: String?, create: Boolean): File? {
        val cacheDir = flags.cacheDir
        if (cacheDir != null) {
            val dir = if (name != null) File(cacheDir, name) else cacheDir
            if (create && !dir.exists()) {
                if (!dir.mkdirs()) {
                    return null
                }
            }
            return dir
        } else {
            return super.getCacheDir(name, create)
        }
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
            // We unconditionally invoke UastEnvironment.analyzeFiles(), even
            // if kotlinFiles is empty -- without this, the machinery in
            // the project (such as the CliLightClassGenerationSupport and
            // the CoreFileManager) will throw exceptions at runtime even
            // for plain class lookup
            val env = uastEnvironment
            if (env != null) {
                if (kotlinLanguageLevel != null) {
                    env.kotlinCompilerConfig.languageVersionSettings = kotlinLanguageLevel
                }
                env.analyzeFiles(kotlinFiles)
            }
            val ok = super.prepare(contexts, javaLanguageLevel, kotlinLanguageLevel)
            if (contexts.isEmpty()) {
                return ok
            }
            // Now that we have a project context, ensure that the annotations manager
            // is up to date
            val annotationsManager =
                ExternalAnnotationsManager.getInstance(ideaProject) as LintExternalAnnotationsManager
            val projects = contexts.first().driver.projects
            val target = pickBuildTarget(projects)
            annotationsManager.updateAnnotationRoots(
                this@LintCliClient, target,
                target == null && projects.isNotEmpty()
            )
            return ok
        }

        override fun createEvaluator(
            project: Project?,
            p: com.intellij.openapi.project.Project
        ): DefaultJavaEvaluator {
            return object : DefaultJavaEvaluator(p, project!!) {
                override fun findClass(qualifiedName: String): PsiClass? {
                    if (::driver.isInitialized && driver.mode == LintDriver.DriverMode.MERGE) {
                        error("Class lookup is not allowed during report merging; see the lint partial analysis documentation")
                    }
                    return super.findClass(qualifiedName)
                }
            }
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

    /**
     * Whether lint should continue running after a baseline has been
     * created.
     */
    private fun continueAfterBaselineCreated(): Boolean {
        return System.getProperty("lint.baselines.continue") == VALUE_TRUE ||
                flags.isContinueAfterBaselineCreated
    }

    companion object {
        // Environment variable, system property and internal system property used to tell lint to
        // override the configuration
        private const val LINT_OVERRIDE_CONFIGURATION_ENV_VAR = "LINT_OVERRIDE_CONFIGURATION"
        private const val LINT_CONFIGURATION_OVERRIDE_PROP = "lint.configuration.override"

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

        /** Creates a print writer for UTF-8 output */
        @JvmStatic
        fun OutputStream.printWriter(): PrintWriter {
            // When we switch to Java 11 this can be
            // return PrintWriter(this, true, Charsets.UTF_8)
            return PrintWriter(OutputStreamWriter(this, Charsets.UTF_8).buffered(), true)
        }

        /**
         * Given a file, it produces a cleaned up path from the file.
         * This will clean up the path such that `foo/./bar` becomes
         * `foo/bar` and `foo/bar/../baz` becomes `foo/baz`.
         *
         * Unlike [java.io.File.getCanonicalPath] however, it will
         * **not** attempt to make the file canonical, such as expanding
         * symlinks and network mounts.
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
            elementLoop@ for (
            element in Splitter.on(File.separatorChar).omitEmptyStrings().split(
                path
            )
            ) {
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
    }

    override val printInternalErrorStackTrace: Boolean
        get() = flags.printInternalErrorStackTrace || super.printInternalErrorStackTrace

    @Throws(IOException::class)
    override fun openConnection(url: URL, timeout: Int): URLConnection? {
        if (flags.isOffline &&
            // Allow file: and jar:file URLs (though these are incredibly unlikely to
            // be called here; normally client code will just read the files directly,
            // but we handle it in case there's code which reads URLs from a resource
            // and then tries to access content
            url.protocol != "file" && (url.protocol != "jar" || !url.path.startsWith("file:"))
        ) {
            return null
        }
        return super.openConnection(url, timeout)
    }
}
