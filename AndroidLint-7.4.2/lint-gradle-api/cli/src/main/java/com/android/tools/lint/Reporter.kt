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

import com.android.SdkConstants
import com.android.tools.lint.MultiProjectHtmlReporter.ProjectEntry
import com.android.tools.lint.checks.AccessibilityDetector
import com.android.tools.lint.checks.ActionsXmlDetector
import com.android.tools.lint.checks.AlwaysShowActionDetector
import com.android.tools.lint.checks.AndroidAutoDetector
import com.android.tools.lint.checks.AndroidTvDetector
import com.android.tools.lint.checks.AnnotationDetector
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.AppCompatCallDetector
import com.android.tools.lint.checks.AppCompatCustomViewDetector
import com.android.tools.lint.checks.AppCompatResourceDetector
import com.android.tools.lint.checks.AppLinksValidDetector
import com.android.tools.lint.checks.AssertDetector
import com.android.tools.lint.checks.AutofillDetector
import com.android.tools.lint.checks.ButtonDetector
import com.android.tools.lint.checks.ByteOrderMarkDetector
import com.android.tools.lint.checks.CallSuperDetector
import com.android.tools.lint.checks.CanvasSizeDetector
import com.android.tools.lint.checks.CheckResultDetector
import com.android.tools.lint.checks.ChromeOsDetector
import com.android.tools.lint.checks.ChromeOsSourceDetector
import com.android.tools.lint.checks.CleanupDetector
import com.android.tools.lint.checks.CommentDetector
import com.android.tools.lint.checks.DataBindingDetector
import com.android.tools.lint.checks.DuplicateResourceDetector
import com.android.tools.lint.checks.EllipsizeMaxLinesDetector
import com.android.tools.lint.checks.ExportedFlagDetector
import com.android.tools.lint.checks.FontDetector
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.checks.GridLayoutDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.IgnoreWithoutReasonDetector
import com.android.tools.lint.checks.IncludeDetector
import com.android.tools.lint.checks.InefficientWeightDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.JavaPerformanceDetector
import com.android.tools.lint.checks.KeyboardNavigationDetector
import com.android.tools.lint.checks.LabelForDetector
import com.android.tools.lint.checks.LintDetectorDetector
import com.android.tools.lint.checks.LocaleDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.MissingClassDetector
import com.android.tools.lint.checks.MissingIdDetector
import com.android.tools.lint.checks.MissingPrefixDetector
import com.android.tools.lint.checks.MotionLayoutDetector
import com.android.tools.lint.checks.MotionSceneDetector
import com.android.tools.lint.checks.NamespaceDetector
import com.android.tools.lint.checks.NetworkSecurityConfigDetector
import com.android.tools.lint.checks.ObjectAnimatorDetector
import com.android.tools.lint.checks.ObsoleteLayoutParamsDetector
import com.android.tools.lint.checks.ParcelDetector
import com.android.tools.lint.checks.PermissionDetector
import com.android.tools.lint.checks.PropertyFileDetector
import com.android.tools.lint.checks.PxUsageDetector
import com.android.tools.lint.checks.ReadParcelableDetector
import com.android.tools.lint.checks.RtlDetector
import com.android.tools.lint.checks.SamDetector
import com.android.tools.lint.checks.ScrollViewChildDetector
import com.android.tools.lint.checks.SdkIntDetector
import com.android.tools.lint.checks.SecurityDetector
import com.android.tools.lint.checks.ServiceCastDetector
import com.android.tools.lint.checks.SignatureOrSystemDetector
import com.android.tools.lint.checks.StringEscapeDetector
import com.android.tools.lint.checks.SyntheticAccessorDetector
import com.android.tools.lint.checks.TextFieldDetector
import com.android.tools.lint.checks.TextViewDetector
import com.android.tools.lint.checks.TileProviderDetector
import com.android.tools.lint.checks.TitleDetector
import com.android.tools.lint.checks.ToastDetector
import com.android.tools.lint.checks.TranslationDetector
import com.android.tools.lint.checks.TypoDetector
import com.android.tools.lint.checks.TypographyDetector
import com.android.tools.lint.checks.UnsafeBroadcastReceiverDetector
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.checks.UselessViewDetector
import com.android.tools.lint.checks.Utf8Detector
import com.android.tools.lint.checks.VectorPathDetector
import com.android.tools.lint.checks.ViewTypeDetector
import com.android.tools.lint.checks.WakelockDetector
import com.android.tools.lint.checks.WatchFaceEditorDetector
import com.android.tools.lint.checks.WatchFaceForAndroidXDetector
import com.android.tools.lint.checks.WearStandaloneAppDetector
import com.android.tools.lint.checks.WrongCallDetector
import com.android.tools.lint.checks.WrongCaseDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.SdkUtils
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.io.Writer
import java.net.MalformedURLException
import java.net.URLEncoder

/** A reporter is an output generator for lint warnings */
abstract class Reporter protected constructor(
    @JvmField
    protected val client: LintCliClient,

    /**
     * The report file, if any (reporters may write to stdout/stderr
     * too)
     */
    val output: File?
) {
    /** Whether this reporter is writing to the console. */
    val isWriteToConsole: Boolean get() = output == null

    /** the title of the report */
    @JvmField
    var title = "Lint Report"

    /**
     * Whether this report should display info if no issues were found.
     */
    var isDisplayEmpty = true

    /**
     * Set mapping of path prefixes to corresponding URLs in the HTML
     * report.
     */
    var urlMap: Map<String, String>? = null

    /**
     * Write the given warnings into the report
     *
     * @param stats the vital statistics for the lint report
     * @param incidents the incidents to be reported @throws IOException
     *     if an error occurs @param registry the issue
     *     registry for all issues used during analysis
     */
    @Throws(IOException::class)
    abstract fun write(
        stats: LintStats,
        incidents: List<Incident>,
        registry: IssueRegistry
    )

    /**
     * Writes a project overview table
     *
     * @param stats the vital statistics for the lint report
     * @param projects the projects to write
     */
    @Throws(IOException::class)
    open fun writeProjectList(
        stats: LintStats,
        projects: List<ProjectEntry>
    ) {
        throw UnsupportedOperationException()
    }

    fun getUrl(file: File): String? {
        val urlMap = urlMap
        if (urlMap != null) {
            val path = file.absolutePath
            // Perform the comparison using URLs such that we properly escape spaces etc.
            val pathUrl = encodeUrl(path)
            for ((prefix, value) in urlMap) {
                val prefixUrl =
                    encodeUrl(prefix)
                if (pathUrl.startsWith(prefixUrl)) {
                    val relative = pathUrl.substring(prefixUrl.length)
                    return value + relative
                }
            }
        }
        if (file.isAbsolute) {
            var relativePath = client.getRelativePath(output?.parentFile, file)
            if (relativePath != null) {
                relativePath = relativePath.replace(File.separatorChar, '/')
                return encodeUrl(relativePath)
            }
        }
        return try {
            SdkUtils.fileToUrlString(file)
        } catch (e: MalformedURLException) {
            null
        }
    }

    private var stripPrefix: String? = null
    protected fun stripPath(path: String): String {
        val stripPrefix = stripPrefix
        if (stripPrefix != null && path.startsWith(stripPrefix) &&
            path.length > stripPrefix.length
        ) {
            var index = stripPrefix.length
            if (path[index] == File.separatorChar) {
                index++
            }
            return path.substring(index)
        }
        return path
    }

    /** Sets path prefix to strip from displayed file names. */
    fun setStripPrefix(prefix: String?) {
        stripPrefix = prefix
    }

    companion object {
        const val STDOUT = "stdout"
        const val STDERR = "stderr"

        /**
         * Creates a new HTML [Reporter]
         *
         * @param client the associated client
         * @param output the output file
         * @param flags the command line flags
         * @throws IOException if an error occurs
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createHtmlReporter(
            client: LintCliClient,
            output: File,
            flags: LintCliFlags
        ): Reporter {
            return HtmlReporter(client, output, flags)
        }

        /**
         * Constructs a new text [Reporter]
         *
         * @param client the client
         * @param flags the flags
         * @param file the file corresponding to the writer, if any
         * @param writer the writer to write into
         * @param close whether the writer should be closed when done
         */
        fun createTextReporter(
            client: LintCliClient,
            flags: LintCliFlags,
            file: File?,
            writer: Writer,
            close: Boolean
        ): TextReporter {
            return TextReporter(client, flags, file, writer, close)
        }

        /**
         * Constructs a new [XmlReporter]
         *
         * @param client the client
         * @param output the output file
         * @param reportType the type of report to generate
         * @throws IOException if an error occurs
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createXmlReporter(
            client: LintCliClient,
            output: File,
            reportType: XmlFileType = XmlFileType.REPORT
        ): XmlReporter {
            return XmlReporter(client, output, reportType)
        }

        /**
         * Creates a new SARIF [Reporter]
         *
         * @param client the associated client
         * @param output the output file
         * @param flags the command line flags
         * @throws IOException if an error occurs
         */
        @JvmStatic
        @Throws(IOException::class)
        fun createSarifReporter(
            client: LintCliClient,
            output: File
        ): Reporter {
            return SarifReporter(client, output)
        }

        /**
         * Encodes the given String as a safe URL substring, escaping
         * spaces etc.
         */
        @JvmStatic
        fun encodeUrl(url: String): String {
            return try {
                val encoded = url.replace('\\', '/')
                URLEncoder.encode(encoded, SdkConstants.UTF_8)
                    .replace("%2F", "/")
            } catch (e: UnsupportedEncodingException) {
                // This shouldn't happen for UTF-8
                System.err.println("Invalid string " + e.localizedMessage)
                url
            }
        }

        private var studioFixes: Set<Issue>? = null

        /**
         * Returns true if the given issue has an automatic IDE fix.
         *
         * @param issue the issue to be checked
         * @return true if the given tool is known to have an automatic
         *     fix for the given issue
         */
        @JvmStatic
        fun hasAutoFix(issue: Issue?): Boolean {
            // List generated by AndroidLintInspectionToolProviderTest in tools/adt/idea;
            // set LIST_ISSUES_WITH_QUICK_FIXES to true; that gives the quickfixes that
            // have class registrations on the IDE side. In tools/base itself many lint
            // fixes provide a fix when reporting. We collect these by inserting this
            // into LintDriver.LintClientWrapper.report:
            //    if (fix != null) {
            //        println("HAS-FIX: ${issue.id}: ${guessField(issue)}")
            //    }
            // which uses this helper code to map from an issue back to the
            // declaring class and field:
            //        private fun guessField(issue: Issue): String {
            //            val detectorClass = issue.implementation.detectorClass
            //            val field = findIssueFromClass(issue, detectorClass) ?: return "\"${issue.id}\""
            //            return "${field.declaringClass.name}.${field.name}"
            //        }
            //
            //        private fun findIssueFromClass(issue: Issue, detectorClass: Class<*>): Field? {
            //            var match = findIssueFromFields(issue, detectorClass.fields)
            //            if (match != null) {
            //                return match
            //            }
            //
            //            // Use getDeclaredFields to also pick up private fields (e.g. backing fields
            //            // for Kotlin properties); we can't *only* use getDeclaredFields since we
            //            // also want to pick up inherited fields (for example used in the GradleDetector
            //            // subclasses.)
            //            match = findIssueFromFields(issue, detectorClass.declaredFields)
            //            if (match != null) {
            //                return match
            //            }
            //
            //            if (!detectorClass.name.endsWith("Kt")) {
            //                try {
            //                    return findIssueFromClass(issue, Class.forName(detectorClass.name + "Kt"))
            //                } catch (ignore: ClassNotFoundException) {
            //                }
            //            }
            //
            //            return null
            //        }
            //
            //        private fun findIssueFromFields(issue: Issue, fields: Array<Field>): Field? {
            //            for (field in fields) {
            //                if (field.modifiers and Modifier.STATIC != 0
            //                        && !field.name.startsWith("_")
            //                        && field.type == Issue::class.java) {
            //                    try {
            //                        field.isAccessible = true
            //                        val newIssue = field.get(null) as Issue
            //                        if (newIssue.id == issue.id) {
            //                            return field
            //                        }
            //                    } catch (ignore: IllegalAccessException) {
            //                    }
            //                }
            //            }
            //
            //            return null
            //        }
            // Then run the testsuite, grep the output for HAS-FIX and pick out the
            // field names, then merge with the below list.
            //
            // The above picks up quickfixes provided from the IDE test side. These days,
            // most quickfixes are tested from the lint library side. See ReporterTest
            // for updated code to generate this.
            if (studioFixes == null) {
                studioFixes = setOf(
                    AccessibilityDetector.ISSUE,
                    ActionsXmlDetector.ISSUE,
                    AlwaysShowActionDetector.ISSUE,
                    AndroidAutoDetector.INVALID_USES_TAG_ISSUE,
                    AndroidTvDetector.MISSING_BANNER,
                    AndroidTvDetector.MISSING_LEANBACK_SUPPORT,
                    AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    AndroidTvDetector.UNSUPPORTED_TV_HARDWARE,
                    AnnotationDetector.FLAG_STYLE,
                    AnnotationDetector.SWITCH_TYPE_DEF,
                    ApiDetector.INLINED,
                    ApiDetector.OBSOLETE_SDK,
                    ApiDetector.OVERRIDE,
                    ApiDetector.UNSUPPORTED,
                    ApiDetector.UNUSED,
                    AppCompatCallDetector.ISSUE,
                    AppCompatCustomViewDetector.ISSUE,
                    AppCompatResourceDetector.ISSUE,
                    AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES,
                    AppLinksValidDetector.VALIDATION,
                    AssertDetector.EXPENSIVE,
                    AutofillDetector.ISSUE,
                    ButtonDetector.STYLE,
                    ByteOrderMarkDetector.BOM,
                    CallSuperDetector.ISSUE,
                    CanvasSizeDetector.ISSUE,
                    CheckResultDetector.CHECK_PERMISSION,
                    CheckResultDetector.CHECK_RESULT,
                    ChromeOsDetector.NON_RESIZEABLE_ACTIVITY,
                    ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    ChromeOsDetector.SETTING_ORIENTATION_ON_ACTIVITY,
                    ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE,
                    ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE,
                    ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION,
                    CleanupDetector.APPLY_SHARED_PREF,
                    CleanupDetector.SHARED_PREF,
                    CommentDetector.STOP_SHIP,
                    DataBindingDetector.ESCAPE_XML,
                    DuplicateResourceDetector.TYPE_MISMATCH,
                    EllipsizeMaxLinesDetector.ISSUE,
                    ExportedFlagDetector.ISSUE,
                    FontDetector.FONT_VALIDATION,
                    GradleDetector.AGP_DEPENDENCY,
                    GradleDetector.ANNOTATION_PROCESSOR_ON_COMPILE_PATH,
                    GradleDetector.COMPATIBILITY,
                    GradleDetector.DEPENDENCY,
                    GradleDetector.DEPRECATED,
                    GradleDetector.DEPRECATED_CONFIGURATION,
                    GradleDetector.DEPRECATED_LIBRARY,
                    GradleDetector.DUPLICATE_CLASSES,
                    GradleDetector.EXPIRED_TARGET_SDK_VERSION,
                    GradleDetector.EXPIRING_TARGET_SDK_VERSION,
                    GradleDetector.JAVA_PLUGIN_LANGUAGE_LEVEL,
                    GradleDetector.JCENTER_REPOSITORY_OBSOLETE,
                    GradleDetector.KTX_EXTENSION_AVAILABLE,
                    GradleDetector.MIN_SDK_TOO_LOW,
                    GradleDetector.NOT_INTERPOLATED,
                    GradleDetector.PATH,
                    GradleDetector.PLUS,
                    GradleDetector.REMOTE_VERSION,
                    GradleDetector.RISKY_LIBRARY,
                    GradleDetector.STRING_INTEGER,
                    GridLayoutDetector.ISSUE,
                    IconDetector.WEBP_ELIGIBLE,
                    IconDetector.WEBP_UNSUPPORTED,
                    IgnoreWithoutReasonDetector.ISSUE,
                    IncludeDetector.ISSUE,
                    InefficientWeightDetector.BASELINE_WEIGHTS,
                    InefficientWeightDetector.INEFFICIENT_WEIGHT,
                    InefficientWeightDetector.ORIENTATION,
                    InteroperabilityDetector.PLATFORM_NULLNESS,
                    JavaPerformanceDetector.USE_VALUE_OF,
                    KeyboardNavigationDetector.ISSUE,
                    LabelForDetector.ISSUE,
                    LintDetectorDetector.DOLLAR_STRINGS,
                    LintDetectorDetector.EXISTING_LINT_CONSTANTS,
                    LintDetectorDetector.TEXT_FORMAT,
                    LintDetectorDetector.TRIM_INDENT,
                    LocaleDetector.STRING_LOCALE,
                    ManifestDetector.APPLICATION_ICON,
                    ManifestDetector.DATA_EXTRACTION_RULES,
                    ManifestDetector.MIPMAP,
                    ManifestDetector.MOCK_LOCATION,
                    ManifestDetector.SET_VERSION,
                    ManifestDetector.TARGET_NEWER,
                    MissingClassDetector.INNERCLASS,
                    MissingIdDetector.ISSUE,
                    MissingPrefixDetector.MISSING_NAMESPACE,
                    MotionLayoutDetector.INVALID_SCENE_FILE_REFERENCE,
                    MotionSceneDetector.MOTION_SCENE_FILE_VALIDATION_ERROR,
                    NamespaceDetector.REDUNDANT,
                    NamespaceDetector.RES_AUTO,
                    NamespaceDetector.TYPO,
                    NetworkSecurityConfigDetector.INSECURE_CONFIGURATION,
                    NetworkSecurityConfigDetector.ISSUE,
                    ObjectAnimatorDetector.MISSING_KEEP,
                    ObsoleteLayoutParamsDetector.ISSUE,
                    ParcelDetector.ISSUE,
                    PermissionDetector.MISSING_PERMISSION,
                    PropertyFileDetector.ESCAPE,
                    PropertyFileDetector.HTTP,
                    PxUsageDetector.DP_ISSUE,
                    PxUsageDetector.PX_ISSUE,
                    ReadParcelableDetector.ISSUE,
                    RtlDetector.COMPAT,
                    RtlDetector.USE_START,
                    SamDetector.ISSUE,
                    ScrollViewChildDetector.ISSUE,
                    SdkIntDetector.ISSUE,
                    SecurityDetector.EXPORTED_PROVIDER,
                    SecurityDetector.EXPORTED_RECEIVER,
                    SecurityDetector.EXPORTED_SERVICE,
                    ServiceCastDetector.WIFI_MANAGER,
                    ServiceCastDetector.WIFI_MANAGER_UNCERTAIN,
                    SignatureOrSystemDetector.ISSUE,
                    StringEscapeDetector.STRING_ESCAPING,
                    SyntheticAccessorDetector.ISSUE,
                    TextFieldDetector.ISSUE,
                    TextViewDetector.SELECTABLE,
                    TileProviderDetector.TILE_PROVIDER_PERMISSIONS,
                    TitleDetector.ISSUE,
                    ToastDetector.ISSUE,
                    TranslationDetector.EXTRA,
                    TranslationDetector.MISSING,
                    TranslationDetector.MISSING_BASE,
                    TranslationDetector.TRANSLATED_UNTRANSLATABLE,
                    TypoDetector.ISSUE,
                    TypographyDetector.DASHES,
                    TypographyDetector.ELLIPSIS,
                    TypographyDetector.FRACTIONS,
                    TypographyDetector.OTHER,
                    TypographyDetector.QUOTES,
                    UnsafeBroadcastReceiverDetector.BROADCAST_SMS,
                    UnusedResourceDetector.ISSUE,
                    UnusedResourceDetector.ISSUE_IDS,
                    UselessViewDetector.USELESS_LEAF,
                    Utf8Detector.ISSUE,
                    VectorPathDetector.PATH_VALID,
                    ViewTypeDetector.ADD_CAST,
                    ViewTypeDetector.WRONG_VIEW_CAST,
                    WakelockDetector.TIMEOUT,
                    WatchFaceForAndroidXDetector.ISSUE,
                    WatchFaceEditorDetector.ISSUE,
                    WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE,
                    WrongCallDetector.ISSUE,
                    WrongCaseDetector.WRONG_CASE
                )
            }
            return studioFixes?.contains(issue) ?: false
        }
    }
}

/**
 * Returns the path to display for a given incident. This is like
 * [LintClient.getDisplayPath], but also takes into account the
 * [LintCliFlags.fullPath] property to use absolute paths if requested.
 */
fun Incident.getPath(client: LintCliClient, file: File = this.file): String {
    return if (project != null) {
        client.getDisplayPath(project, file, client.flags.isFullPath)
    } else {
        client.getDisplayPath(file, null, TextFormat.TEXT)
    }
}

/**
 * Produces the source line containing this error, as well as a second
 * line showing the error range using ~ characters. Suitable for text
 * output.
 */
fun Incident.getErrorLines(textProvider: (File) -> CharSequence?): String? {
    return location.getErrorLines(textProvider)
}

/**
 * Produces the source line containing this error, as well as a second
 * line showing the error range using ~ characters. Suitable for text
 * output.
 */
fun Location.getErrorLines(textProvider: (File) -> CharSequence?): String? {
    val location = this
    val startPosition = location.start
    if (startPosition != null && startPosition.line >= 0) {
        val source = textProvider(file)
        if (source != null) {
            val endPosition = location.end
            // Compute error line contents
            val line = startPosition.line
            var errorLine = source.getLine(line)
            if (errorLine != null) {
                // Replace tabs with spaces such that the column
                // marker (^) lines up properly:
                errorLine = errorLine.replace('\t', ' ')
                var column = startPosition.column
                if (column < 0) {
                    column = 0
                    var i = 0
                    while (i < errorLine.length) {
                        if (!Character.isWhitespace(errorLine[i])) {
                            break
                        }
                        i++
                        column++
                    }
                }
                val sb = StringBuilder(100)
                sb.append(errorLine)
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
                return sb.toString()
            }
        }
    }

    return null
}

/** Look up the contents of the given line. */
private fun CharSequence.getLine(line: Int): String? {
    val index = getLineOffset(line)
    return if (index != -1) {
        getLineOfOffset(index)
    } else {
        null
    }
}

/** Returns the line number for the given offset. */
private fun CharSequence.getLineOfOffset(offset: Int): String {
    var end = indexOf('\n', offset)
    if (end == -1) {
        end = indexOf('\r', offset)
    } else if (end > 0 && this[end - 1] == '\r') {
        end--
    }
    return this.subSequence(offset, if (end != -1) end else this.length).toString()
}

/** Returns the offset of the given line number. */
private fun CharSequence.getLineOffset(line: Int): Int {
    var index = 0
    for (i in 0 until line) {
        index = indexOf('\n', index)
        if (index == -1) {
            return -1
        }
        index++
    }
    return index
}
