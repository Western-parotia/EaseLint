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

package com.android.tools.lint;

import static com.android.SdkConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.model.LintModelSeverity;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flags used by the {@link LintCliClient}
 */
public class LintCliFlags {
    private final Set<String> suppress = new HashSet<>();
    private final Set<String> enabled = new HashSet<>();
    private Set<String> check = null;
    private Set<Category> disabledCategories = null;
    private Set<Category> enabledCategories = null;
    private Set<Category> checkCategories = null;
    private Map<String, LintModelSeverity> severities;
    private List<String> skipAnnotated = null;
    private boolean setExitCode;
    private boolean fullPath;
    private boolean showLines = true;
    private final List<Reporter> reporters = Lists.newArrayList();
    private boolean quiet;
    private boolean warnAll;
    private boolean checkTests;
    private boolean ignoreTests;
    private boolean ignoreTestFixtures;
    private boolean checkGenerated;
    private boolean checkDependencies = true;
    private boolean noWarnings;
    private boolean allErrors;
    private boolean fatalOnly;
    private boolean explainIssues;
    private File projectDescriptor;
    private List<File> sources;
    private List<File> classes;
    private List<File> libraries;
    private List<File> resources;
    private List<File> lintRuleJars;
    private String compileSdkVersion;
    private File baselineFile;
    private File outputBaselineFile;

    private File lintConfig;
    private File lintOverrideConfig;
    private boolean showAll;
    private boolean removedFixedBaselineIssues;
    private boolean missingBaselineIsEmptyBaseline;
    private boolean updateBaseline;
    private boolean continueAfterBaselineCreated;
    private boolean autoFix = VALUE_TRUE.equals(System.getProperty("lint.autofix"));
    private boolean abortOnAutoFix;
    private boolean includeXmlFixes;
    private boolean allowSuppress;
    private boolean printInternalErrorStackTrace;
    private boolean allowBaselineSuppress;
    private boolean offline;
    private File cacheDir;

    public static final int ERRNO_SUCCESS = 0;
    public static final int ERRNO_ERRORS = 1;
    public static final int ERRNO_USAGE = 2;
    public static final int ERRNO_EXISTS = 3;
    public static final int ERRNO_HELP = 4;
    public static final int ERRNO_INVALID_ARGS = 5;
    public static final int ERRNO_CREATED_BASELINE = 6;
    public static final int ERRNO_APPLIED_SUGGESTIONS = 7;
    public static final int ERRNO_INTERNAL_CONTINUE = 100;

    /**
     * Returns the set of issue id's to suppress. Callers are allowed to modify this collection. To
     * suppress a given issue, add the {@link Issue#getId()} to the returned set.
     */
    @NonNull
    public Set<String> getSuppressedIds() {
        return suppress;
    }

    /**
     * Returns the set of issue id's to enable. Callers are allowed to modify this collection. To
     * enable a given issue, add the {@link Issue#getId()} to the returned set.
     */
    @NonNull
    public Set<String> getEnabledIds() {
        return enabled;
    }

    /**
     * Returns the set of categories to enable, if any.
     */
    @Nullable
    public Set<Category> getEnabledCategories() {
        return enabledCategories;
    }

    /**
     * Returns the set of categories to disable, if any.
     */
    @Nullable
    public Set<Category> getDisabledCategories() {
        return disabledCategories;
    }

    /**
     * Returns the set of exact categories to check, if any.
     */
    @Nullable
    public Set<Category> getExactCategories() {
        return checkCategories;
    }

    /**
     * Returns a map of manually configured severities to use
     *
     * @return the severity to use for a given issue id
     */
    @NonNull
    public Map<String, LintModelSeverity> getSeverityOverrides() {
        return severities == null ? Collections.emptyMap() : severities;
    }

    /**
     * Returns the exact set of issues to check, or null to run the issues that are enabled by
     * default plus any issues enabled via {@link #getEnabledIds} and without issues disabled via
     * {@link #getSuppressedIds}. If non-null, callers are allowed to modify this collection.
     */
    @Nullable
    public Set<String> getExactCheckedIds() {
        return check;
    }

    /**
     * Sets the <b>exact</b> set of issues to check.
     *
     * @param check the set of issue id's to check
     */
    public void setExactCheckedIds(@Nullable Set<String> check) {
        this.check = check;
    }

    /**
     * Adds an id to check (will disable everything else)
     */
    public void addExactId(@NonNull String id) {
        if (check == null) {
            check = new HashSet<>();
        }
        check.add(id);
    }

    /**
     * Adds a category to enable
     */
    public void addEnabledCategory(@NonNull Category category) {
        if (enabledCategories == null) {
            enabledCategories = new HashSet<>();
        }
        enabledCategories.add(category);
    }

    /**
     * Adds a category to disable
     */
    public void addDisabledCategory(@NonNull Category category) {
        if (disabledCategories == null) {
            disabledCategories = new HashSet<>();
        }
        disabledCategories.add(category);
    }

    /**
     * Adds a category to check exactly
     */
    public void addExactCategory(@NonNull Category category) {
        if (checkCategories == null) {
            checkCategories = new HashSet<>();
        }
        checkCategories.add(category);
    }

    /**
     * Whether lint should set the exit code of the process if errors are found
     */
    public boolean isSetExitCode() {
        return setExitCode;
    }

    /**
     * Sets whether lint should set the exit code of the process if errors are found
     */
    public void setSetExitCode(boolean setExitCode) {
        this.setExitCode = setExitCode;
    }

    /**
     * Whether lint should display full paths in the error output. By default the paths are relative
     * to the path lint was invoked from.
     */
    public boolean isFullPath() {
        return fullPath;
    }

    /**
     * Sets whether lint should display full paths in the error output. By default the paths are
     * relative to the path lint was invoked from.
     */
    public void setFullPath(boolean fullPath) {
        this.fullPath = fullPath;
    }

    /**
     * Whether lint should include the source lines in the output where errors occurred (true by
     * default)
     */
    public boolean isShowSourceLines() {
        return showLines;
    }

    /**
     * Sets whether lint should include the source lines in the output where errors occurred (true
     * by default)
     */
    public void setShowSourceLines(boolean showLines) {
        this.showLines = showLines;
    }

    /**
     * Returns the list of error reports to generate. Clients can modify the returned list and add
     * additional reporters such as {@link XmlReporter} and {@link HtmlReporter}.
     */
    @NonNull
    public List<Reporter> getReporters() {
        return reporters;
    }

    /**
     * Returns whether lint should be quiet (for example, not show progress dots for each analyzed
     * file)
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Sets whether lint should be quiet (for example, not show progress dots for each analyzed
     * file)
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * Returns whether lint should check all warnings, including those off by default
     */
    public boolean isCheckAllWarnings() {
        return warnAll;
    }

    /**
     * Sets whether lint should check all warnings, including those off by default
     */
    public void setCheckAllWarnings(boolean warnAll) {
        this.warnAll = warnAll;
    }

    /**
     * Returns whether lint will only check for errors (ignoring warnings)
     */
    public boolean isIgnoreWarnings() {
        return noWarnings;
    }

    /**
     * Sets whether lint will only check for errors (ignoring warnings)
     */
    public void setIgnoreWarnings(boolean noWarnings) {
        this.noWarnings = noWarnings;
    }

    /**
     * Returns whether lint should treat all warnings as errors
     */
    public boolean isWarningsAsErrors() {
        return allErrors;
    }

    /**
     * Sets whether lint should treat all warnings as errors
     */
    public void setWarningsAsErrors(boolean allErrors) {
        this.allErrors = allErrors;
    }

    /**
     * Returns whether lint should run all checks on test sources, instead of just the lint checks
     * that have been specifically written to include tests (e.g. checks looking for specific test
     * errors, or checks that need to consider testing code such as the unused resource detector)
     *
     * @return true to check tests, defaults to false
     */
    public boolean isCheckTestSources() {
        return checkTests;
    }

    /**
     * Sets whether lint should run all the normal checks on test sources
     */
    public void setCheckTestSources(boolean checkTests) {
        this.checkTests = checkTests;
        if (checkTests) {
            this.ignoreTests = false;
        }
    }

    /**
     * Like {@link #isCheckTestSources()}, but always skips analyzing tests -- meaning that it also
     * ignores checks that have explicitly asked to look at test sources, such as the unused
     * resource check.
     */
    public boolean isIgnoreTestSources() {
        return ignoreTests;
    }

    /**
     * Sets whether we should completely skip test sources.
     *
     * @param ignoreTests if true, ignore tests completely
     */
    public void setIgnoreTestSources(boolean ignoreTests) {
        this.ignoreTests = ignoreTests;
        if (ignoreTests) {
            this.checkTests = false;
        }
    }

    /**
     * Whether we should completely skip testFixtures sources.
     */
    public boolean isIgnoreTestFixturesSources() {
        return ignoreTestFixtures;
    }

    /**
     * Sets whether we should completely skip testFixtures sources.
     *
     * @param ignoreTestFixtures if true, ignore testFixtures completely
     */
    public void setIgnoreTestFixturesSources(boolean ignoreTestFixtures) {
        this.ignoreTestFixtures = ignoreTestFixtures;
    }

    /**
     * Returns whether lint should run checks on generated sources.
     */
    public boolean isCheckGeneratedSources() {
        return checkGenerated;
    }

    /**
     * Sets whether lint should check generated sources
     */
    public void setCheckGeneratedSources(boolean checkGenerated) {
        this.checkGenerated = checkGenerated;
    }

    /**
     * Returns whether lint should check all dependencies too as part of its analysis. Default is
     * true.
     */
    public boolean isCheckDependencies() {
        return checkDependencies;
    }

    /**
     * Sets whether lint should check dependencies too
     */
    public void setCheckDependencies(boolean checkDependencies) {
        this.checkDependencies = checkDependencies;
    }

    /**
     * Returns whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    public boolean isShowEverything() {
        return showAll;
    }

    /**
     * Sets whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    public void setShowEverything(boolean showAll) {
        this.showAll = showAll;
    }

    /**
     * Returns the default XML configuration file to use as a fallback
     */
    @Nullable
    public File getLintConfig() {
        return lintConfig;
    }

    /**
     * Sets the default XML config file to use as an override. This corresponds to a {@code
     * lint.xml} file with severities etc to always consult first, even for projects that have more
     * specific information in lint.xml files.
     */
    public void setLintConfig(@Nullable File lintConfig) {
        this.lintConfig = lintConfig;
    }

    /**
     * Returns the default XML configuration file to use as an override
     */
    @Nullable
    public File getOverrideLintConfig() {
        return lintOverrideConfig;
    }

    /**
     * Sets the default XML config file to use as a fallback. This corresponds to a {@code lint.xml}
     * file with severities etc to use when a project does not have more specific information.
     */
    public void setOverrideLintConfig(@Nullable File lintConfig) {
        this.lintOverrideConfig = lintConfig;
    }

    /**
     * Gets the optional <b>manual override</b> of the source directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    @Nullable
    public List<File> getSourcesOverride() {
        return sources;
    }

    /**
     * Sets the optional <b>manual override</b> of the source directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    public void setSourcesOverride(@Nullable List<File> sources) {
        this.sources = sources;
    }

    /**
     * Gets the optional <b>manual override</b> of the class file directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    @Nullable
    public List<File> getClassesOverride() {
        return classes;
    }

    /**
     * Sets the optional <b>manual override</b> of the class file directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    public void setClassesOverride(@Nullable List<File> classes) {
        this.classes = classes;
    }

    /**
     * Gets the optional <b>manual override</b> of the library directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    @Nullable
    public List<File> getLibrariesOverride() {
        return libraries;
    }

    /**
     * Sets the optional <b>manual override</b> of the library directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    public void setLibrariesOverride(@Nullable List<File> libraries) {
        this.libraries = libraries;
    }

    /**
     * Gets the optional <b>manual override</b> of the resources directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    @Nullable
    public List<File> getResourcesOverride() {
        return resources;
    }

    /**
     * Gets the optional <b>manual override</b> of the resource directories. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can set the
     * source paths explicitly. This is normally done when running lint on raw source code without
     * proper metadata (or when using a build system unknown to lint, such as say {@code make}.
     */
    public void setResourcesOverride(@Nullable List<File> resources) {
        this.resources = resources;
    }

    /**
     * Gets the optional <b>manual override</b> of lint rule jars. Normally null.
     */
    @Nullable
    public List<File> getLintRuleJarsOverride() {
        return lintRuleJars;
    }

    /**
     * Sets the optional <b>manual override</b> of lint rule jars. Normally null.
     */
    public void setLintRuleJarsOverride(@Nullable List<File> lintRuleJars) {
        this.lintRuleJars = lintRuleJars;
    }

    /**
     * Gets the optional <b>manual override</b> of the project hierarchy. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can specify
     * the project hierarchy explicitly. This is normally done when running lint on raw source code
     * without proper metadata (or when using a build system unknown to lint, such as say {@code
     * make}).
     */
    @Nullable
    public File getProjectDescriptorOverride() {
        return projectDescriptor;
    }

    /**
     * Sets the optional <b>manual override</b> of the project hierarchy. Normally null.
     *
     * <p>Normally, the source, library and resource paths for a project should be computed by the
     * {@link LintClient} itself, using available project metadata. However, the user can specify
     * the project hierarchy explicitly. This is normally done when running lint on raw source code
     * without proper metadata (or when using a build system unknown to lint, such as say {@code
     * make}).
     */
    public void setProjectDescriptorOverride(@Nullable File projectDescriptor) {
        this.projectDescriptor = projectDescriptor;
    }

    /**
     * Gets the optional compileSdkVersion override. Normally null.
     *
     * <p>Normally, the compileSdkVersion (e.g. the build target version) is known by lint from the
     * build system (it's specified explicitly in build.gradle, and in older Eclipse-based projects,
     * in the project.properties file). However, when using third party / unsupported build systems,
     * there's a fallback mechanism you can use specifying the set of manifests, resources and
     * sources via dedicated flags. In those cases the compileSdkVersion is unknown. This flag lets
     * you provide a specific dedicated version to use.
     */
    @Nullable
    public String getCompileSdkVersionOverride() {
        return compileSdkVersion;
    }

    /**
     * Sets the optional compileSdkVersion override. Normally null.
     *
     * <p>Normally, the compileSdkVersion (e.g. the build target version) is known by lint from the
     * build system (it's specified explicitly in build.gradle, and in older Eclipse-based projects,
     * in the project.properties file). However, when using third party / unsupported build systems,
     * there's a fallback mechanism you can use specifying the set of manifests, resources and
     * sources via dedicated flags. In those cases the compileSdkVersion is unknown. This flag lets
     * you provide a specific dedicated version to use.
     */
    public void setCompileSdkVersionOverride(@Nullable String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
    }

    /**
     * Returns true if we should only check fatal issues
     *
     * @return true if we should only check fatal issues
     */
    public boolean isFatalOnly() {
        return fatalOnly;
    }

    /**
     * Sets whether we should only check fatal issues
     *
     * @param fatalOnly if true, only check fatal issues
     */
    public void setFatalOnly(boolean fatalOnly) {
        this.fatalOnly = fatalOnly;
    }

    /**
     * Sets a map of severities to use
     *
     * @param severities map from issue id to severity
     */
    public void setSeverityOverrides(@NonNull Map<String, LintModelSeverity> severities) {
        this.severities = severities;
    }

    /**
     * Whether text reports should include full explanation texts. (HTML and XML reports always do,
     * unconditionally.)
     *
     * @return true if text reports should include explanation text
     */
    public boolean isExplainIssues() {
        return explainIssues;
    }

    /**
     * Sets whether text reports should include full explanation texts. (HTML and XML reports always
     * do, unconditionally.)
     *
     * @param explainText true if text reports should include explanation text
     */
    public void setExplainIssues(boolean explainText) {
        explainIssues = explainText;
    }

    /**
     * Returns the baseline file to use, if any. The baseline file is an XML report previously
     * created by lint, and any warnings and errors listed in that report will be ignored from
     * analysis.
     *
     * <p>If you have a project with a large number of existing warnings, this lets you set a
     * baseline and only see newly introduced warnings until you get a chance to go back and address
     * the "technical debt" of the earlier warnings.
     *
     * @return the baseline file, if any
     */
    @Nullable
    public File getBaselineFile() {
        return baselineFile;
    }

    /**
     * Sets the baseline file, if any.
     *
     * @see #getBaselineFile()
     */
    public void setBaselineFile(@Nullable File baselineFile) {
        this.baselineFile = baselineFile;
    }

    /**
     * Returns the output baseline file to use, if any
     */
    @Nullable
    public File getOutputBaselineFile() {
        return outputBaselineFile;
    }

    /**
     * Sets the output baseline file, if any.
     *
     * @see #getOutputBaselineFile()
     */
    public void setOutputBaselineFile(@Nullable File outputBaselineFile) {
        this.outputBaselineFile = outputBaselineFile;
    }

    /**
     * Whether lint will update the baseline file to remove any issues that are no longer present in
     * the codebase. This will only remove fixed issues, it will not insert any newly found issues.
     *
     * <p>Only applies when a baseline file has been configured.
     *
     * @return whether to update the baseline file.
     */
    public boolean isRemoveFixedBaselineIssues() {
        return removedFixedBaselineIssues;
    }

    /**
     * Sets whether lint should remove fixed baseline issues.
     *
     * @see #isRemoveFixedBaselineIssues()
     */
    public void setRemovedFixedBaselineIssues(boolean removeFixed) {
        removedFixedBaselineIssues = removeFixed;
    }

    /**
     * If true, treat a missing baseline file as an empty baseline file.
     */
    public boolean getMissingBaselineIsEmptyBaseline() {
        return missingBaselineIsEmptyBaseline;
    }

    /**
     * If true, treat a missing baseline file as an empty baseline file.
     */
    public void setMissingBaselineIsEmptyBaseline(boolean missingBaselineIsEmptyBaseline) {
        this.missingBaselineIsEmptyBaseline = missingBaselineIsEmptyBaseline;
    }

    /**
     * If true, rewrite the baseline file on exit.
     */
    public boolean isUpdateBaseline() {
        return updateBaseline;
    }

    /**
     * If true, rewrite the baseline file on exit.
     */
    public void setUpdateBaseline(boolean updateBaseline) {
        this.updateBaseline = updateBaseline;
    }

    /**
     * Whether to apply safe suggestions
     */
    public boolean isAutoFix() {
        return autoFix;
    }

    /**
     * Sets whether to apply safe suggestions
     */
    public void setAutoFix(boolean autoFix) {
        this.autoFix = autoFix;
    }

    public boolean isAbortOnAutoFix() {
        return abortOnAutoFix;
    }

    public void setAbortOnAutoFix(boolean abortOnAutoFix) {
        this.abortOnAutoFix = abortOnAutoFix;
    }

    /**
     * Whether XML reports should include descriptions of the quickfixes
     */
    public boolean isIncludeXmlFixes() {
        return includeXmlFixes;
    }

    /**
     * Sets whether XML reports should include descriptions of the quickfixes
     */
    public void setIncludeXmlFixes(boolean includeXmlFixes) {
        this.includeXmlFixes = includeXmlFixes;
    }

    /**
     * Sets whether the user is allowed to suppress issues that have been explicitly restricted by
     * the issue registration via {@link Issue#getSuppressNames()}.
     */
    public void setAllowSuppress(boolean allowSuppress) {
        this.allowSuppress = allowSuppress;
    }

    /**
     * Returns true if the user is allowed to suppress issues that have been explicitly restricted
     * by the issue registration via {@link Issue#getSuppressNames()}.
     */
    public boolean getAllowSuppress() {
        return allowSuppress;
    }

    /**
     * Allows overriding the {@link #getAllowSuppress()} flag specifically for baselines. This is
     * only intended to be configurable by changing the invocation flags to lint -- not via DSL or
     * lint.xml options etc as is the case for the other flags.
     */
    public void setAllowBaselineSuppress(boolean allowBaselineSuppress) {
        this.allowBaselineSuppress = allowBaselineSuppress;
    }

    /**
     * See {@link #setAllowBaselineSuppress}.
     */
    public boolean getAllowBaselineSuppress() {
        return allowBaselineSuppress;
    }

    /**
     * Returns true if internal error stacktraces should be printed to stdout
     */
    public boolean getPrintInternalErrorStackTrace() {
        return printInternalErrorStackTrace;
    }

    /**
     * Sets if internal error stacktraces should be printed to stdout
     */
    public void setPrintInternalErrorStackTrace(boolean printInternalErrorStackTrace) {
        this.printInternalErrorStackTrace = printInternalErrorStackTrace;
    }

    /**
     * Returns the base cache directory used by {@link LintCliClient#getCacheDir(String, boolean)},
     * or null if nothing has been set.
     */
    @Nullable
    public File getCacheDir() {
        return cacheDir;
    }

    /**
     * Sets the base cache directory used by {@link LintCliClient#getCacheDir(String, boolean)}
     */
    public void setCacheDir(@NonNull File cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * If true, continue normally even after a baseline file has been created.
     */
    public boolean isContinueAfterBaselineCreated() {
        return continueAfterBaselineCreated;
    }

    /**
     * If true, continue normally even after a baseline file has been created.
     */
    public void setContinueAfterBaselineCreated(boolean continueAfterBaselineCreated) {
        this.continueAfterBaselineCreated = continueAfterBaselineCreated;
    }

    /**
     * Gets a list of (fully qualified) annotation names that indicate that the class should be
     * skipped in analysis.
     *
     * @return the list, or null if nothing has been configured
     */
    @Nullable
    public List<String> getSkipAnnotations() {
        return skipAnnotated;
    }

    /**
     * Sets an optional list of annotation names marking code to be skipped during analysis.
     *
     * @param skipAnnotated the new list, or null to clear
     */
    public void setSkipAnnotations(@Nullable List<String> skipAnnotated) {
        this.skipAnnotated = skipAnnotated;
    }

    /**
     * Adds a new annotation which indicates that a class should be skipped in analysis.
     *
     * @param annotation the fully qualified name
     */
    public void addSkipAnnotation(@NonNull String annotation) {
        if (skipAnnotated == null) {
            skipAnnotated = new ArrayList<>();
        }
        skipAnnotated.add(annotation);
    }

    /**
     * Returns true if lint should be running in offline mode (off by default).
     *
     * @return the offline state
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Sets whether lint should be running in offline mode.
     *
     * @param offline whether to stay offline
     */
    public void setOffline(boolean offline) {
        this.offline = offline;
    }
}
