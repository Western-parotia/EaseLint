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

package com.android.tools.lint;

import static com.android.SdkConstants.UTF_8;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.AlwaysShowActionDetector;
import com.android.tools.lint.checks.AndroidAutoDetector;
import com.android.tools.lint.checks.AndroidTvDetector;
import com.android.tools.lint.checks.AnnotationDetector;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.checks.AppCompatCallDetector;
import com.android.tools.lint.checks.AppCompatCustomViewDetector;
import com.android.tools.lint.checks.AppCompatResourceDetector;
import com.android.tools.lint.checks.AppIndexingApiDetector;
import com.android.tools.lint.checks.AppLinksValidDetector;
import com.android.tools.lint.checks.ByteOrderMarkDetector;
import com.android.tools.lint.checks.CheckResultDetector;
import com.android.tools.lint.checks.ChromeOsDetector;
import com.android.tools.lint.checks.CleanupDetector;
import com.android.tools.lint.checks.CommentDetector;
import com.android.tools.lint.checks.DetectMissingPrefix;
import com.android.tools.lint.checks.DuplicateResourceDetector;
import com.android.tools.lint.checks.EllipsizeMaxLinesDetector;
import com.android.tools.lint.checks.FontDetector;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.checks.GridLayoutDetector;
import com.android.tools.lint.checks.IconDetector;
import com.android.tools.lint.checks.IncludeDetector;
import com.android.tools.lint.checks.InefficientWeightDetector;
import com.android.tools.lint.checks.JavaPerformanceDetector;
import com.android.tools.lint.checks.KeyboardNavigationDetector;
import com.android.tools.lint.checks.LabelForDetector;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.checks.MissingClassDetector;
import com.android.tools.lint.checks.MissingIdDetector;
import com.android.tools.lint.checks.NamespaceDetector;
import com.android.tools.lint.checks.NetworkSecurityConfigDetector;
import com.android.tools.lint.checks.ObjectAnimatorDetector;
import com.android.tools.lint.checks.ObsoleteLayoutParamsDetector;
import com.android.tools.lint.checks.ParcelDetector;
import com.android.tools.lint.checks.PermissionDetector;
import com.android.tools.lint.checks.PropertyFileDetector;
import com.android.tools.lint.checks.PxUsageDetector;
import com.android.tools.lint.checks.ReadParcelableDetector;
import com.android.tools.lint.checks.RtlDetector;
import com.android.tools.lint.checks.ScrollViewChildDetector;
import com.android.tools.lint.checks.SecurityDetector;
import com.android.tools.lint.checks.ServiceCastDetector;
import com.android.tools.lint.checks.SignatureOrSystemDetector;
import com.android.tools.lint.checks.TextFieldDetector;
import com.android.tools.lint.checks.TextViewDetector;
import com.android.tools.lint.checks.TitleDetector;
import com.android.tools.lint.checks.TypoDetector;
import com.android.tools.lint.checks.TypographyDetector;
import com.android.tools.lint.checks.UnpackedNativeCodeDetector;
import com.android.tools.lint.checks.UnsafeBroadcastReceiverDetector;
import com.android.tools.lint.checks.UnusedResourceDetector;
import com.android.tools.lint.checks.UselessViewDetector;
import com.android.tools.lint.checks.Utf8Detector;
import com.android.tools.lint.checks.VectorPathDetector;
import com.android.tools.lint.checks.ViewTypeDetector;
import com.android.tools.lint.checks.WakelockDetector;
import com.android.tools.lint.checks.WearStandaloneAppDetector;
import com.android.tools.lint.checks.WrongCallDetector;
import com.android.tools.lint.checks.WrongCaseDetector;
import com.android.tools.lint.detector.api.Issue;
import com.android.utils.SdkUtils;
import com.google.common.annotations.Beta;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reporter is an output generator for lint warnings
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public abstract class Reporter {
    public static final String STDOUT = "stdout";
    public static final String STDERR = "stderr";

    protected final LintCliClient client;
    protected final File output;
    protected String title = "Lint Report";
    protected Map<String, String> urlMap;
    protected boolean displayEmpty = true;

    /**
     * Creates a new HTML {@link Reporter}
     *
     * @param client the associated client
     * @param output the output file
     * @param flags the command line flags
     * @throws IOException if an error occurs
     */
    @NonNull
    public static Reporter createHtmlReporter(
            @NonNull LintCliClient client, @NonNull File output, @NonNull LintCliFlags flags)
            throws IOException {
        return new HtmlReporter(client, output, flags);
    }

    /**
     * Constructs a new text {@link Reporter}
     *
     * @param client the client
     * @param flags the flags
     * @param file the file corresponding to the writer, if any
     * @param writer the writer to write into
     * @param close whether the writer should be closed when done
     */
    @NonNull
    public static TextReporter createTextReporter(
            @NonNull LintCliClient client,
            @NonNull LintCliFlags flags,
            @Nullable File file,
            @NonNull Writer writer,
            boolean close) {
        return new TextReporter(client, flags, file, writer, close);
    }

    /**
     * Constructs a new {@link XmlReporter}
     *
     * @param client the client
     * @param output the output file
     * @param intendedForBaseline whether this XML report is used to write a baseline file
     * @param includeFixes whether to include descriptions of quickfixes
     * @throws IOException if an error occurs
     */
    public static XmlReporter createXmlReporter(
            @NonNull LintCliClient client,
            @NonNull File output,
            boolean intendedForBaseline,
            boolean includeFixes)
            throws IOException {
        XmlReporter reporter = new XmlReporter(client, output);
        reporter.setIntendedForBaseline(intendedForBaseline);
        reporter.setIncludeFixes(!intendedForBaseline && includeFixes);
        return reporter;
    }

    /**
     * Write the given warnings into the report
     *
     * @param stats the vital statistics for the lint report
     * @param issues the issues to be reported @throws IOException if an error occurs
     */
    public abstract void write(@NonNull LintStats stats, List<Warning> issues) throws IOException;

    /**
     * Writes a project overview table
     *
     * @param stats the vital statistics for the lint report
     * @param projects the projects to write
     */
    public void writeProjectList(
            @NonNull LintStats stats, @NonNull List<MultiProjectHtmlReporter.ProjectEntry> projects)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    protected Reporter(@NonNull LintCliClient client, @NonNull File output) {
        this.client = client;
        this.output = output;
    }

    /**
     * Sets the report title
     *
     * @param title the title of the report
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /** @return the title of the report */
    public String getTitle() {
        return title;
    }

    /** The report file, if any (reporters may write to stdout/stderr too) */
    @Nullable
    public File getOutput() {
        return output;
    }

    String getUrl(File file) {
        if (urlMap != null) {
            String path = file.getAbsolutePath();
            // Perform the comparison using URLs such that we properly escape spaces etc.
            String pathUrl = encodeUrl(path);
            for (Map.Entry<String, String> entry : urlMap.entrySet()) {
                String prefix = entry.getKey();
                String prefixUrl = encodeUrl(prefix);
                if (pathUrl.startsWith(prefixUrl)) {
                    String relative = pathUrl.substring(prefixUrl.length());
                    return entry.getValue() + relative;
                }
            }
        }

        if (file.isAbsolute()) {
            String relativePath = client.getRelativePath(output.getParentFile(), file);
            if (relativePath != null) {
                relativePath = relativePath.replace(separatorChar, '/');
                return encodeUrl(relativePath);
            }
        }

        try {
            return SdkUtils.fileToUrlString(file);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /** Encodes the given String as a safe URL substring, escaping spaces etc */
    static String encodeUrl(String url) {
        try {
            url = url.replace('\\', '/');
            return URLEncoder.encode(url, UTF_8).replace("%2F", "/");
        } catch (UnsupportedEncodingException e) {
            // This shouldn't happen for UTF-8
            System.err.println("Invalid string " + e.getLocalizedMessage());
            return url;
        }
    }

    /** Set mapping of path prefixes to corresponding URLs in the HTML report */
    public void setUrlMap(@Nullable Map<String, String> urlMap) {
        this.urlMap = urlMap;
    }

    /** Returns whether this report should display info if no issues were found */
    public boolean isDisplayEmpty() {
        return displayEmpty;
    }

    /** Sets whether this report should display info if no issues were found */
    public void setDisplayEmpty(boolean displayEmpty) {
        this.displayEmpty = displayEmpty;
    }

    private static Set<Issue> studioFixes;

    /**
     * Returns true if the given issue has an automatic IDE fix.
     *
     * @param issue the issue to be checked
     * @return true if the given tool is known to have an automatic fix for the given issue
     */
    public static boolean hasAutoFix(Issue issue) {
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
        if (studioFixes == null) {
            studioFixes =
                    Sets.newHashSet(
                            AccessibilityDetector.ISSUE,
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
                            AppIndexingApiDetector.ISSUE_APP_INDEXING,
                            AppIndexingApiDetector.ISSUE_APP_INDEXING_API,
                            AppLinksValidDetector.VALIDATION,
                            ByteOrderMarkDetector.BOM,
                            CheckResultDetector.CHECK_PERMISSION,
                            CheckResultDetector.CHECK_RESULT,
                            ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                            ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE,
                            CleanupDetector.APPLY_SHARED_PREF,
                            CleanupDetector.SHARED_PREF,
                            CommentDetector.STOP_SHIP,
                            DetectMissingPrefix.MISSING_NAMESPACE,
                            DuplicateResourceDetector.STRING_ESCAPING,
                            DuplicateResourceDetector.TYPE_MISMATCH,
                            EllipsizeMaxLinesDetector.ISSUE,
                            FontDetector.FONT_VALIDATION_ERROR,
                            FontDetector.FONT_VALIDATION_WARNING,
                            GradleDetector.ANNOTATION_PROCESSOR_ON_COMPILE_PATH,
                            GradleDetector.COMPATIBILITY,
                            GradleDetector.DEPENDENCY,
                            GradleDetector.DEPRECATED,
                            GradleDetector.DEPRECATED_CONFIGURATION,
                            GradleDetector.DUPLICATE_CLASSES,
                            GradleDetector.MIN_SDK_TOO_LOW,
                            GradleDetector.NOT_INTERPOLATED,
                            GradleDetector.PLUS,
                            GradleDetector.REMOTE_VERSION,
                            GradleDetector.STRING_INTEGER,
                            GridLayoutDetector.ISSUE,
                            IconDetector.WEBP_ELIGIBLE,
                            IconDetector.WEBP_UNSUPPORTED,
                            IncludeDetector.ISSUE,
                            InefficientWeightDetector.BASELINE_WEIGHTS,
                            InefficientWeightDetector.INEFFICIENT_WEIGHT,
                            InefficientWeightDetector.ORIENTATION,
                            JavaPerformanceDetector.USE_VALUE_OF,
                            KeyboardNavigationDetector.ISSUE,
                            LabelForDetector.ISSUE,
                            ManifestDetector.ALLOW_BACKUP,
                            ManifestDetector.APPLICATION_ICON,
                            ManifestDetector.MIPMAP,
                            ManifestDetector.MOCK_LOCATION,
                            ManifestDetector.SET_VERSION,
                            ManifestDetector.TARGET_NEWER,
                            MissingClassDetector.INNERCLASS,
                            MissingIdDetector.ISSUE,
                            NamespaceDetector.RES_AUTO,
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
                            ScrollViewChildDetector.ISSUE,
                            SecurityDetector.EXPORTED_PROVIDER,
                            SecurityDetector.EXPORTED_RECEIVER,
                            SecurityDetector.EXPORTED_SERVICE,
                            ServiceCastDetector.WIFI_MANAGER,
                            ServiceCastDetector.WIFI_MANAGER_UNCERTAIN,
                            SignatureOrSystemDetector.ISSUE,
                            TextFieldDetector.ISSUE,
                            TextViewDetector.SELECTABLE,
                            TitleDetector.ISSUE,
                            TypoDetector.ISSUE,
                            TypographyDetector.DASHES,
                            TypographyDetector.ELLIPSIS,
                            TypographyDetector.FRACTIONS,
                            TypographyDetector.OTHER,
                            TypographyDetector.QUOTES,
                            UnpackedNativeCodeDetector.ISSUE,
                            UnsafeBroadcastReceiverDetector.BROADCAST_SMS,
                            UnusedResourceDetector.ISSUE,
                            UnusedResourceDetector.ISSUE_IDS,
                            UselessViewDetector.USELESS_LEAF,
                            Utf8Detector.ISSUE,
                            VectorPathDetector.PATH_VALID,
                            ViewTypeDetector.ADD_CAST,
                            WakelockDetector.TIMEOUT,
                            WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE,
                            WrongCallDetector.ISSUE,
                            WrongCaseDetector.WRONG_CASE);
        }
        return studioFixes.contains(issue);
    }

    private String stripPrefix;

    protected String stripPath(@NonNull String path) {
        if (stripPrefix != null
                && path.startsWith(stripPrefix)
                && path.length() > stripPrefix.length()) {
            int index = stripPrefix.length();
            if (path.charAt(index) == File.separatorChar) {
                index++;
            }
            return path.substring(index);
        }

        return path;
    }

    /** Sets path prefix to strip from displayed file names */
    public void setStripPrefix(@Nullable String prefix) {
        stripPrefix = prefix;
    }
}
