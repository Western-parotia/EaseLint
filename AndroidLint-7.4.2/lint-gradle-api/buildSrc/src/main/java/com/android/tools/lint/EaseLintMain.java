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

import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.VALUE_NONE;
import static com.android.tools.lint.LintCliClient.printWriter;
import static com.android.tools.lint.LintCliFlags.ERRNO_APPLIED_SUGGESTIONS;
import static com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE;
import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_EXISTS;
import static com.android.tools.lint.LintCliFlags.ERRNO_HELP;
import static com.android.tools.lint.LintCliFlags.ERRNO_INTERNAL_CONTINUE;
import static com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.LintCliFlags.ERRNO_USAGE;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.EaseLintServerityUtils;
import com.android.tools.lint.checks.DesugaredMethodLookup;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.ConfigurationHierarchy;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.LintXmlConfiguration;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintModelModuleProject;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Option;
import com.android.tools.lint.detector.api.Platform;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelSerialization;
import com.android.tools.lint.model.LintModelSeverity;
import com.android.tools.lint.model.LintModelSourceProvider;
import com.android.tools.lint.model.LintModelVariant;
import com.android.tools.lint.model.PathVariables;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.intellij.pom.java.LanguageLevel;

import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import kotlin.io.FilesKt;
import kotlin.text.StringsKt;

/**
 * Command line driver for the lint framework
 */
public class EaseLintMain {
    static final int MAX_LINE_WIDTH = 78;
    private static final String ARG_ENABLE = "--enable";
    private static final String ARG_DISABLE = "--disable";
    private static final String ARG_CHECK = "--check";
    private static final String ARG_AUTO_FIX = "--apply-suggestions";
    private static final String ARG_DESCRIBE_FIXES = "--describe-suggestions";
    private static final String ARG_ABORT_IF_SUGGESTIONS_APPLIED = "--abort-if-suggestions-applied";
    private static final String ARG_FATAL = "--fatal";
    private static final String ARG_ERROR = "--error";
    private static final String ARG_WARNING = "--warning";
    private static final String ARG_INFO = "--info";
    private static final String ARG_IGNORE = "--ignore";
    private static final String ARG_LIST_IDS = "--list";
    private static final String ARG_SHOW = "--show";
    private static final String ARG_QUIET = "--quiet";
    private static final String ARG_GENERATE_DOCS = "--generate-docs";
    private static final String ARG_CLIENT_ID = "--client-id";
    private static final String ARG_CLIENT_NAME = "--client-name";
    private static final String ARG_CLIENT_VERSION = "--client-version";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_FULL_PATH = "--fullpath";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_SHOW_ALL = "--showall";

    private static final String ARG_HELP = "--help";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_NO_LINES = "--nolines";

    private static final String ARG_HTML = "--html";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_SIMPLE_HTML = "--simplehtml";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_SARIF = "--sarif";

    private static final String ARG_XML = "--xml";
    private static final String ARG_TEXT = "--text";
    private static final String ARG_CONFIG = "--config";
    private static final String ARG_OVERRIDE_CONFIG = "--override-config";
    private static final String ARG_URL = "--url";
    private static final String ARG_VERSION = "--version";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_EXIT_CODE = "--exitcode";

    private static final String ARG_SDK_HOME = "--sdk-home";
    private static final String ARG_JDK_HOME = "--jdk-home";
    private static final String ARG_FATAL_ONLY = "--fatalOnly";
    private static final String ARG_PROJECT = "--project";
    private static final String ARG_LINT_MODEL = "--lint-model";
    private static final String ARG_PATH_VARIABLES = "--path-variables";
    private static final String ARG_LINT_RULE_JARS = "--lint-rule-jars";
    private static final String ARG_VARIANT = "--variant";
    private static final String ARG_CLASSES = "--classpath";
    private static final String ARG_SOURCES = "--sources";
    private static final String ARG_RESOURCES = "--resources";
    private static final String ARG_LIBRARIES = "--libraries";
    private static final String ARG_BUILD_API = "--compile-sdk-version";
    private static final String ARG_JAVA_LANGUAGE_LEVEL = "--java-language-level";
    private static final String ARG_KOTLIN_LANGUAGE_LEVEL = "--kotlin-language-level";
    private static final String ARG_BASELINE = "--baseline";
    private static final String ARG_REMOVE_FIXED = "--remove-fixed";
    private static final String ARG_UPDATE_BASELINE = "--update-baseline";
    private static final String ARG_CONTINUE_AFTER_BASELINE_CREATED =
            "--continue-after-baseline-created";
    private static final String ARG_WRITE_REF_BASELINE = "--write-reference-baseline";
    private static final String ARG_MISSING_BASELINE_IS_EMPTY_BASELINE =
            "--missing-baseline-is-empty-baseline";
    private static final String ARG_ALLOW_SUPPRESS = "--allow-suppress";
    private static final String ARG_RESTRICT_SUPPRESS = "--restrict-suppress";
    private static final String ARG_PRINT_INTERNAL_ERROR_STACKTRACE = "--stacktrace";
    private static final String ARG_ANALYZE_ONLY = "--analyze-only";
    private static final String ARG_REPORT_ONLY = "--report-only";
    private static final String ARG_CACHE_DIR = "--cache-dir";
    private static final String ARG_SKIP_ANNOTATED = "--skip-annotated";
    private static final String ARG_OFFLINE = "--offline";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_NO_WARN_2 = "--nowarn";
    // GCC style flag names for options
    private static final String ARG_NO_WARN_1 = "-w";
    private static final String ARG_WARN_ALL = "-Wall";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String ARG_ALL_ERROR = "-Werror";

    private static final String PROP_WORK_DIR = "com.android.tools.lint.workdir";

    private final LintCliFlags flags = new LintCliFlags();
    private IssueRegistry globalIssueRegistry;
    @Nullable
    private File sdkHome;
    @Nullable
    private File jdkHome;

    /**
     * Creates a CLI driver
     */
    public EaseLintMain() {
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        int exitCode = new Main().run(args);
        System.exit(exitCode);
    }

    /**
     * Hook intended for tests
     */
    protected void initializeDriver(@NonNull LintDriver driver) {
    }

    /**
     * State read from command line flags to be shared by various initialization methods. This is
     * similar to {@link LintCliFlags} which we're also initializing during startup, but the
     * semantic difference between the two is that {@link LintCliFlags} represents user level
     * concepts, and this class represents more implementation-level state, such as which driver
     * mode to use etc.
     */
    static class ArgumentState {
        @Nullable
        String clientVersion;
        @Nullable
        String clientName;
        @Nullable
        LanguageLevel javaLanguageLevel = null;
        @Nullable
        LanguageVersionSettings kotlinLanguageLevel = null;
        @NonNull
        List<LintModelModule> modules = new ArrayList<>();
        @Nullable
        String variantName = null;
        // Mapping from file path prefix to URL. Applies only to HTML reports
        @Nullable
        String urlMap = null;
        @NonNull
        List<File> files = new ArrayList<>();
        @NonNull
        LintDriver.DriverMode mode = LintDriver.DriverMode.GLOBAL;
        @Nullable
        PathVariables pathVariables = null;
        @Nullable
        List<String> desugaredMethodsPaths = null;
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     * @return the return code for lint
     */
    public int run(String[] args) {
        if (args.length < 1) {
            printUsage(printWriter(System.err));
            return ERRNO_USAGE;
        }

        // When debugging build-system invocations, the below is helpful; leaving
        // here for a while:
        // logArguments(args, new File("lint-invocation.txt"));

        LintClient.setClientName(LintClient.CLIENT_CLI);

        ArgumentState argumentState = new ArgumentState();
        LintCliClient client = createClient(argumentState);
        int exitCode = parseArguments(args, client, argumentState);
        if (exitCode != ERRNO_INTERNAL_CONTINUE) {
            return exitCode;
        }

        initializePathVariables(argumentState, client);

        initializeConfigurations(client, argumentState);
        exitCode = initializeReporters(client, argumentState);
        if (exitCode != ERRNO_INTERNAL_CONTINUE) {
            return exitCode;
        }
        LintRequest lintRequest = createLintRequest(client, argumentState);

        exitCode = run(client, lintRequest, argumentState);

        // If the user has not asked for the exit code to be set, don't
        // fail the build on errors. However, this only applies to the
        // found-errors exit code; for things like incorrect arguments
        // or baseline created, we always set the exit code.
        if (exitCode == ERRNO_ERRORS
                && (!client.getFlags().isSetExitCode()
                || argumentState.mode == LintDriver.DriverMode.ANALYSIS_ONLY)) {
            exitCode = ERRNO_SUCCESS;
        }

        return exitCode;
    }

    private void initializePathVariables(ArgumentState argumentState, LintCliClient client) {
        PathVariables pathVariables = client.getPathVariables();
        if (argumentState.pathVariables != null) {
            pathVariables.add(argumentState.pathVariables);
            pathVariables.normalize();
        }
        createDefaultPathVariables(argumentState, client);
    }

    private void createDefaultPathVariables(
            @NonNull ArgumentState argumentState, @NonNull LintCliClient client) {
        PathVariables pathVariables = client.getPathVariables();
        // It's tempting to define a $PROJECT variable here to
        // refer to the common parent director of all the project
        // modules, but this does not work because when invoked
        // in --analyze-only we'll take an individual module's
        // folder, whereas with --report-only we'll take the parent
        // directory, so the path resolves to different values
        for (LintModelModule module : argumentState.modules) {
            // Add project directory path variable
            pathVariables.add(
                    "{" + module.getModulePath() + "*projectDir}", module.getDir(), false);
            // Add build directory path variable
            pathVariables.add(
                    "{" + module.getModulePath() + "*buildDir}", module.getBuildFolder(), false);
            for (LintModelVariant variant : module.getVariants()) {
                int sourceProviderIndex = 0;
                for (LintModelSourceProvider sourceProvider : variant.getSourceProviders()) {
                    addSourceProviderPathVariables(
                            pathVariables,
                            sourceProvider,
                            "sourceProvider",
                            sourceProviderIndex++,
                            module.getModulePath(),
                            variant.getName());
                }
                int testSourceProviderIndex = 0;
                for (LintModelSourceProvider testSourceProvider :
                        variant.getTestSourceProviders()) {
                    addSourceProviderPathVariables(
                            pathVariables,
                            testSourceProvider,
                            "testSourceProvider",
                            testSourceProviderIndex++,
                            module.getModulePath(),
                            variant.getName());
                }
                int testFixturesSourceProviderIndex = 0;
                for (LintModelSourceProvider testFixturesSourceProvider :
                        variant.getTestFixturesSourceProviders()) {
                    addSourceProviderPathVariables(
                            pathVariables,
                            testFixturesSourceProvider,
                            "testFixturesSourceProvider",
                            testFixturesSourceProviderIndex++,
                            module.getModulePath(),
                            variant.getName());
                }
            }
        }

        pathVariables.sort();
    }

    /**
     * Adds necessary path variables to pathVariables.
     */
    private static void addSourceProviderPathVariables(
            PathVariables pathVariables,
            LintModelSourceProvider sourceProvider,
            String sourceProviderType,
            int sourceProviderIndex,
            String modulePath,
            String variantName) {
        addSourceProviderPathVariables(
                pathVariables,
                Arrays.asList(sourceProvider.getManifestFile()),
                modulePath,
                variantName,
                sourceProviderType,
                sourceProviderIndex,
                "manifest");
        addSourceProviderPathVariables(
                pathVariables,
                sourceProvider.getJavaDirectories(),
                modulePath,
                variantName,
                sourceProviderType,
                sourceProviderIndex,
                "javaDir");
        addSourceProviderPathVariables(
                pathVariables,
                sourceProvider.getResDirectories(),
                modulePath,
                variantName,
                sourceProviderType,
                sourceProviderIndex,
                "resDir");
        addSourceProviderPathVariables(
                pathVariables,
                sourceProvider.getAssetsDirectories(),
                modulePath,
                variantName,
                sourceProviderType,
                sourceProviderIndex,
                "assetsDir");
    }

    /**
     * Adds necessary path variables to pathVariables.
     */
    private static void addSourceProviderPathVariables(
            PathVariables pathVariables,
            Collection<File> files,
            String modulePath,
            String variantName,
            String sourceProviderType,
            int sourceProviderIndex,
            String sourceType) {
        int index = 0;
        for (File file : files) {
            String name =
                    "{"
                            + modulePath
                            + "*"
                            + variantName
                            + "*"
                            + sourceProviderType
                            + "*"
                            + sourceProviderIndex
                            + "*"
                            + sourceType
                            + "*"
                            + index++
                            + "}";
            pathVariables.add(name, file, false);
        }
    }

    // Debugging utility
    @SuppressWarnings("unused")
    private void logArguments(String[] args, File log) {
        File parent = log.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(java.util.Calendar.getInstance().getTime()).append("\n");
        sb.append("pwd=").append(System.getProperty("user.dir")).append("\narguments: ");
        for (String arg : args) {
            String s = arg.replace("\"", "\\\"");
            if (s.contains(" ")) {
                s = '"' + s + '"';
            }
            sb.append(s).append(' ');
        }
        sb.append("\n");
        FilesKt.appendText(log, sb.toString(), Charsets.UTF_8);
    }

    private LintCliClient createClient(ArgumentState argumentState) {
        return new MainLintClient(flags, argumentState);
    }

    class MainLintClient extends LintCliClient {
        final ArgumentState argumentState;

        MainLintClient(LintCliFlags flags, ArgumentState argumentState) {
            super(flags, LintClient.CLIENT_CLI);
            this.argumentState = argumentState;
        }

        private Project unexpectedGradleProject = null;

        @Override
        public boolean supportsPartialAnalysis() {
            return argumentState.mode != LintDriver.DriverMode.GLOBAL;
        }

        @NonNull
        @Override
        public String getClientDisplayName() {
            String name = argumentState.clientName;
            if (name != null) {
                // We include the client version number in the client display name;
                // we DON'T return it from getClientRevision() since there we want
                // to see the version of lint that is actually running, not the version
                // of the tool that invoked it (e.g. to make sense of baseline files)
                String version = argumentState.clientVersion;
                if (version != null && !name.contains(version)) {
                    return name + " (" + version + ")";
                }
                return name;
            }
            return super.getClientDisplayName();
        }

        @Override
        @NonNull
        protected LintDriver createDriver(
                @NonNull IssueRegistry registry, @NonNull LintRequest request) {
            LintDriver driver = super.createDriver(registry, request);

            Project project = unexpectedGradleProject;
            if (project != null) {
                String message =
                        String.format(
                                "\"`%1$s`\" is a Gradle project. To correctly "
                                        + "analyze Gradle projects, you should run \"`gradlew lint`\" "
                                        + "instead.",
                                project.getName());
                Location location = EaseLintUtils.guessGradleLocation(this, project.getDir(), null);
                LintClient.Companion.report(
                        this, IssueRegistry.LINT_ERROR, message, driver, project, location, null);
            }

            initializeDriver(driver);

            return driver;
        }

        @NonNull
        @Override
        protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
            Project project = super.createProject(dir, referenceDir);
            if (project.isGradleProject()) {
                // Can't report error yet; stash it here so we can report it after the
                // driver has been created
                unexpectedGradleProject = project;
            }

            return project;
        }

        @NonNull
        @Override
        public LanguageLevel getJavaLanguageLevel(@NonNull Project project) {
            LanguageLevel level = argumentState.javaLanguageLevel;
            if (level != null) {
                return level;
            }
            return super.getJavaLanguageLevel(project);
        }

        @NonNull
        @Override
        public LanguageVersionSettings getKotlinLanguageLevel(@NonNull Project project) {
            LanguageVersionSettings settings = argumentState.kotlinLanguageLevel;
            if (settings != null) {
                return settings;
            }
            return super.getKotlinLanguageLevel(project);
        }

        @NonNull
        @Override
        public Configuration getConfiguration(
                @NonNull final Project project, @Nullable LintDriver driver) {
            if (project.isGradleProject() && !(project instanceof LintModelModuleProject)) {
                // Don't report any issues when analyzing a Gradle project from the
                // non-Gradle runner; they are likely to be false, and will hide the
                // real problem reported above. We also need to turn off overrides
                // and fallbacks such that we don't inherit any re-enabled issues etc.
                ConfigurationHierarchy configurations = getConfigurations();
                configurations.setOverrides(null);
                configurations.setFallback(null);
                return new CliConfiguration(configurations, flags, true) {
                    @NonNull
                    @Override
                    public Severity getDefinedSeverity(
                            @NonNull Issue issue,
                            @NonNull Configuration source,
                            @NonNull Severity visibleDefault) {
                        return issue == IssueRegistry.LINT_ERROR ? Severity.FATAL : Severity.IGNORE;
                    }

                    @Override
                    public boolean isIgnored(@NonNull Context context, @NonNull Incident incident) {
                        // If you've deliberately ignored IssueRegistry.LINT_ERROR
                        // don't flag that one either
                        Issue issue = incident.getIssue();
                        if (issue == IssueRegistry.LINT_ERROR
                                && new LintCliClient(flags, LintClient.getClientName())
                                .isSuppressed(IssueRegistry.LINT_ERROR)) {
                            return true;
                        } else if (issue == IssueRegistry.LINT_WARNING
                                && new LintCliClient(flags, LintClient.getClientName())
                                .isSuppressed(IssueRegistry.LINT_WARNING)) {
                            return true;
                        }

                        return issue != IssueRegistry.LINT_ERROR
                                && issue != IssueRegistry.LINT_WARNING;
                    }
                };
            }

            return super.getConfiguration(project, driver);
        }

        private byte[] readSrcJar(@NonNull File file) {
            String path = file.getPath();
            //noinspection SpellCheckingInspection
            int srcJarIndex = path.indexOf("srcjar!");
            if (srcJarIndex != -1) {
                File jarFile = new File(path.substring(0, srcJarIndex + 6));
                if (jarFile.exists()) {
                    try (ZipFile zipFile = new ZipFile(jarFile)) {
                        String name =
                                path.substring(srcJarIndex + 8).replace(File.separatorChar, '/');
                        ZipEntry entry = zipFile.getEntry(name);
                        if (entry != null) {
                            try (InputStream is = zipFile.getInputStream(entry)) {
                                return ByteStreams.toByteArray(is);
                            } catch (Exception e) {
                                log(e, null);
                            }
                        }
                    } catch (ZipException e) {
                        EaseLintMain.this.log(e, "Could not unzip %1$s", jarFile);
                    } catch (IOException e) {
                        EaseLintMain.this.log(e, "Could not read %1$s", jarFile);
                    }
                }
            }
            return null;
        }

        @NonNull
        @Override
        public CharSequence readFile(@NonNull File file) {
            // .srcjar file handle?
            byte[] srcJarBytes = readSrcJar(file);
            if (srcJarBytes != null) {
                return new String(srcJarBytes, Charsets.UTF_8);
            }

            return super.readFile(file);
        }

        @NonNull
        @Override
        public byte[] readBytes(@NonNull File file) throws IOException {
            // .srcjar file handle?
            byte[] srcJarBytes = readSrcJar(file);
            if (srcJarBytes != null) {
                return srcJarBytes;
            }

            return super.readBytes(file);
        }

        private ProjectMetadata metadata;

        @Override
        protected void configureLintRequest(@NonNull LintRequest request) {
            super.configureLintRequest(request);
            File descriptor = flags.getProjectDescriptorOverride();
            if (descriptor != null) {
                metadata = ProjectInitializerKt.computeMetadata(this, descriptor);

                String clientName = metadata.getClientName();
                if (clientName != null) {
                    //noinspection ResultOfObjectAllocationIgnored
                    new LintCliClient(clientName); // constructor has side effect
                }

                List<Project> projects = metadata.getProjects();
                if (!projects.isEmpty()) {
                    request.setProjects(projects);

                    if (metadata.getSdk() != null) {
                        sdkHome = metadata.getSdk();
                    }

                    if (metadata.getJdk() != null) {
                        jdkHome = metadata.getJdk();
                    }

                    if (metadata.getBaseline() != null) {
                        flags.setBaselineFile(metadata.getBaseline());
                    }

                    EnumSet<Scope> scope = EnumSet.copyOf(Scope.ALL);
                    if (metadata.getIncomplete()) {
                        scope.remove(Scope.ALL_CLASS_FILES);
                        scope.remove(Scope.ALL_JAVA_FILES);
                        scope.remove(Scope.ALL_RESOURCE_FILES);
                    }
                    request.setScope(scope);

                    request.setPlatform(metadata.getPlatforms());
                }
            }
        }

        @NonNull
        @Override
        public Iterable<File> findRuleJars(@NonNull Project project) {
            if (metadata != null) {
                List<File> jars = metadata.getLintChecks().get(project);
                if (jars != null) {
                    return jars;
                }
            }

            return super.findRuleJars(project);
        }

        @NonNull
        @Override
        public List<File> findGlobalRuleJars(@Nullable LintDriver driver, boolean warnDeprecated) {
            if (metadata != null) {
                List<File> jars = metadata.getGlobalLintChecks();
                if (!jars.isEmpty()) {
                    return jars;
                }
            }

            return super.findGlobalRuleJars(driver, warnDeprecated);
        }

        @Nullable
        @Override
        public File getCacheDir(@Nullable String name, boolean create) {
            if (metadata != null) {
                File dir = metadata.getCache();
                if (dir != null) {
                    if (name != null) {
                        dir = new File(dir, name);
                    }

                    if (create && !dir.exists()) {
                        if (!dir.mkdirs()) {
                            return null;
                        }
                    }
                    return dir;
                }
            }

            return super.getCacheDir(name, create);
        }

        @Nullable
        @Override
        public Document getMergedManifest(@NonNull Project project) {
            if (metadata != null) {
                File manifest = metadata.getMergedManifests().get(project);
                if (manifest != null && manifest.exists()) {
                    try {
                        // We can't call
                        //   resolveMergeManifestSources(document, manifestReportFile)
                        // here since we don't have the merging log.
                        return XmlUtils.parseUtfXmlFile(manifest, true);
                    } catch (IOException | SAXException e) {
                        log(e, "Could not read/parse %1$s", manifest);
                    }
                }
            }

            return super.getMergedManifest(project);
        }

        @Nullable
        @Override
        public File getSdkHome() {
            if (EaseLintMain.this.sdkHome != null) {
                return EaseLintMain.this.sdkHome;
            }
            return super.getSdkHome();
        }

        @Nullable
        @Override
        public File getJdkHome(@Nullable Project project) {
            if (EaseLintMain.this.jdkHome != null) {
                return EaseLintMain.this.jdkHome;
            }
            return super.getJdkHome(project);
        }

        @Override
        protected boolean addBootClassPath(
                @NonNull Collection<? extends Project> knownProjects, @NonNull Set<File> files) {
            if (metadata != null && !metadata.getJdkBootClasspath().isEmpty()) {
                boolean isAndroid = false;
                for (Project project : knownProjects) {
                    if (project.isAndroidProject()) {
                        isAndroid = true;
                        break;
                    }
                }
                if (!isAndroid) {
                    files.addAll(metadata.getJdkBootClasspath());
                    return true;
                }

                boolean ok = super.addBootClassPath(knownProjects, files);
                if (!ok) {
                    files.addAll(metadata.getJdkBootClasspath());
                }
                return ok;
            }

            return super.addBootClassPath(knownProjects, files);
        }

        @NonNull
        @Override
        public List<File> getExternalAnnotations(@NonNull Collection<? extends Project> projects) {
            List<File> externalAnnotations = super.getExternalAnnotations(projects);
            if (metadata != null) {
                externalAnnotations.addAll(metadata.getExternalAnnotations());
            }
            return externalAnnotations;
        }
    }

    private int parseArguments(String[] args, LintCliClient client, ArgumentState argumentState) {
        // Mapping from file path prefix to URL. Applies only to HTML reports
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];

            if (arg.equals(ARG_HELP) || arg.equals("-h") || arg.equals("-?")) {
                if (index < args.length - 1) {
                    String topic = args[index + 1];
                    if (topic.equals("suppress") || topic.equals("ignore")) {
                        printHelpTopicSuppress();
                        return ERRNO_HELP;
                    } else if (!topic.startsWith("-")) {
                        System.err.printf("Unknown help topic \"%1$s\"%n", topic);
                        return ERRNO_INVALID_ARGS;
                    }
                }
                printUsage(System.out);
                return ERRNO_HELP;
            } else if (arg.equals(ARG_LIST_IDS)) {
                IssueRegistry registry = getGlobalRegistry(client);
                // Did the user provide a category list?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) {
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // List all issues with the given category
                            //noinspection UnnecessaryLocalVariable // clearer code
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category)
                                        || issue.getCategory().getFullName().startsWith(category)) {
                                    listIssue(printWriter(System.out), issue);
                                }
                            }
                        } else {
                            System.err.println("Invalid category \"" + id + "\".\n");
                            displayValidIds(registry, printWriter(System.err));
                            return ERRNO_INVALID_ARGS;
                        }
                    }
                } else {
                    displayValidIds(registry, printWriter(System.out));
                }
                return ERRNO_SUCCESS;
            } else if (arg.equals(ARG_SHOW)) {
                IssueRegistry registry = getGlobalRegistry(client);
                // Show specific issues?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) {
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // Show all issues in the given category
                            //noinspection UnnecessaryLocalVariable // clearer code
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category)
                                        || issue.getCategory().getFullName().startsWith(category)) {
                                    describeIssue(issue);
                                    System.out.println();
                                }
                            }
                        } else if (registry.isIssueId(id)) {
                            describeIssue(registry.getIssue(id));
                            System.out.println();
                        } else {
                            System.err.println("Invalid id or category \"" + id + "\".\n");
                            displayValidIds(registry, printWriter(System.err));
                            return ERRNO_INVALID_ARGS;
                        }
                    }
                } else {
                    showIssues(registry);
                }
                return ERRNO_SUCCESS;
            } else if (arg.equals(ARG_FULL_PATH)
                    || arg.equals(ARG_FULL_PATH + "s")) { // allow "--fullpaths" too
                flags.setFullPath(true);
            } else if (arg.equals(ARG_SHOW_ALL)) {
                flags.setShowEverything(true);
            } else if (arg.equals(ARG_QUIET) || arg.equals("-q")) {
                flags.setQuiet(true);
            } else if (arg.equals(ARG_NO_LINES)) {
                flags.setShowSourceLines(false);
            } else if (arg.equals(ARG_EXIT_CODE) || arg.equals("--exit-code")) {
                flags.setSetExitCode(true);
            } else if (arg.equals(ARG_FATAL_ONLY)) {
                flags.setFatalOnly(true);
            } else if (arg.equals(ARG_VERSION)) {
                printVersion(client);
                return ERRNO_SUCCESS;
            } else if (arg.equals(ARG_URL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing URL mapping string");
                    return ERRNO_INVALID_ARGS;
                }
                String map = args[++index];
                // Allow repeated usage of the argument instead of just comma list
                String urlMap = argumentState.urlMap;
                if (urlMap != null) {
                    urlMap = urlMap + ',' + map;
                } else {
                    urlMap = map;
                }
                argumentState.urlMap = urlMap;
            } else if (arg.equals(ARG_CONFIG) || arg.equals(ARG_OVERRIDE_CONFIG)) {
                if (index == args.length - 1 || !EaseLintUtils.endsWith(args[index + 1], DOT_XML)) {
                    System.err.println("Missing XML configuration file argument");
                    return ERRNO_INVALID_ARGS;
                }
                File file = getInArgumentPath(args[++index]);
                if (!file.exists()) {
                    System.err.println(file.getAbsolutePath() + " does not exist");
                    return ERRNO_INVALID_ARGS;
                }
                if (arg.equals(ARG_CONFIG)) {
                    flags.setLintConfig(file);
                } else {
                    flags.setOverrideLintConfig(file);
                }
            } else if (arg.equals(ARG_HTML) || arg.equals(ARG_SIMPLE_HTML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing HTML output file name");
                    return ERRNO_INVALID_ARGS;
                }
                File output = getOutArgumentPath(args[++index]);
                // Get an absolute path such that we can ask its parent directory for
                // write permission etc.
                output = output.getAbsoluteFile();
                if (output.isDirectory()
                        || (!output.exists() && output.getName().indexOf('.') == -1)) {
                    if (!output.exists()) {
                        boolean mkdirs = output.mkdirs();
                        if (!mkdirs) {
                            log(null, "Could not create output directory %1$s", output);
                            return ERRNO_EXISTS;
                        }
                    }
                    MultiProjectHtmlReporter reporter =
                            new MultiProjectHtmlReporter(client, output, flags);
                    if (arg.equals(ARG_SIMPLE_HTML)) {
                        System.err.println(ARG_SIMPLE_HTML + " ignored: no longer supported");
                    }
                    flags.getReporters().add(reporter);
                    continue;
                }
                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        return ERRNO_EXISTS;
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write HTML output file " + output);
                    return ERRNO_EXISTS;
                }
                try {
                    Reporter reporter = Reporter.createHtmlReporter(client, output, flags);
                    flags.getReporters().add(reporter);
                } catch (IOException e) {
                    log(e, null);
                    return ERRNO_INVALID_ARGS;
                }
            } else if (arg.equals(ARG_XML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing XML output file name");
                    return ERRNO_INVALID_ARGS;
                }
                File output = getOutArgumentPath(args[++index]);
                // Get an absolute path such that we can ask its parent directory for
                // write permission etc.
                output = output.getAbsoluteFile();

                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        return ERRNO_EXISTS;
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write XML output file " + output);
                    return ERRNO_EXISTS;
                }
                try {
                    flags.getReporters()
                            .add(
                                    Reporter.createXmlReporter(
                                            client,
                                            output,
                                            flags.isIncludeXmlFixes()
                                                    ? XmlFileType.REPORT_WITH_FIXES
                                                    : XmlFileType.REPORT));
                } catch (IOException e) {
                    log(e, null);
                    return ERRNO_INVALID_ARGS;
                }
            } else if (arg.equals(ARG_SARIF)) {
                if (index == args.length - 1) {
                    System.err.println("Missing SARIF output file name");
                    return ERRNO_INVALID_ARGS;
                }
                File output = getOutArgumentPath(args[++index]);
                // Get an absolute path such that we can ask its parent directory for
                // write permission etc.
                output = output.getAbsoluteFile();

                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        return ERRNO_EXISTS;
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write SARIF output file " + output);
                    return ERRNO_EXISTS;
                }
                try {
                    flags.getReporters().add(Reporter.createSarifReporter(client, output));
                } catch (IOException e) {
                    log(e, null);
                    return ERRNO_INVALID_ARGS;
                }
            } else if (arg.equals(ARG_TEXT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing text output file name");
                    return ERRNO_INVALID_ARGS;
                }

                Writer writer;
                boolean closeWriter;
                String outputName = args[++index];
                if (outputName.equals("stdout")) {
                    writer = printWriter(System.out);
                    closeWriter = false;
                } else if (outputName.equals("stderr")) {
                    writer = printWriter(System.err);
                    closeWriter = false;
                } else {
                    File output = getOutArgumentPath(outputName);

                    // Get an absolute path such that we can ask its parent directory for
                    // write permission etc.
                    output = output.getAbsoluteFile();

                    if (output.exists()) {
                        boolean delete = output.delete();
                        if (!delete) {
                            System.err.println("Could not delete old " + output);
                            return ERRNO_EXISTS;
                        }
                    }
                    if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                        System.err.println("Cannot write text output file " + output);
                        return ERRNO_EXISTS;
                    }
                    try {
                        writer =
                                new BufferedWriter(
                                        new OutputStreamWriter(
                                                new FileOutputStream(output), Charsets.UTF_8));
                    } catch (IOException e) {
                        log(e, null);
                        return ERRNO_INVALID_ARGS;
                    }
                    closeWriter = true;
                }
                flags.getReporters().add(new TextReporter(client, flags, writer, closeWriter));
            } else if (arg.equals(ARG_DISABLE) || arg.equals(ARG_IGNORE) || arg.equals("--hide")) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to disable");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                String idString = args[++index];
                setSeverity(flags, registry, idString, flags.getSuppressedIds(), null);
            } else if (arg.equals(ARG_ENABLE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to enable");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], flags.getEnabledIds(), null);
            } else if (arg.equals(ARG_CHECK)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to check");
                    return ERRNO_INVALID_ARGS;
                }
                Set<String> checkedIds = flags.getExactCheckedIds();
                if (checkedIds == null) {
                    checkedIds = new HashSet<>();
                    flags.setExactCheckedIds(checkedIds);
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], checkedIds, null);
            } else if (arg.equals(ARG_FATAL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to set to fatal severity");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], null, Severity.FATAL);
            } else if (arg.equals(ARG_ERROR)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to set to error severity");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], null, Severity.ERROR);
            } else if (arg.equals(ARG_WARNING)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to set to warning severity");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], null, Severity.WARNING);
            } else if (arg.equals(ARG_INFO)
                    || arg.equals("--information")
                    || arg.equals("--informational")) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to set to info severity");
                    return ERRNO_INVALID_ARGS;
                }
                IssueRegistry registry = getGlobalRegistry(client);
                setSeverity(flags, registry, args[++index], null, Severity.INFORMATIONAL);
            } else if (arg.equals(ARG_NO_WARN_1) || arg.equals(ARG_NO_WARN_2)) {
                flags.setIgnoreWarnings(true);
            } else if (arg.equals(ARG_WARN_ALL)) {
                flags.setCheckAllWarnings(true);
            } else if (arg.equals(ARG_ALL_ERROR)) {
                flags.setWarningsAsErrors(true);
            } else if (arg.equals(ARG_AUTO_FIX)) {
                flags.setAutoFix(true);
            } else if (arg.equals(ARG_DESCRIBE_FIXES)) {
                flags.setIncludeXmlFixes(true);
                // Make sure we also update any XML reporters we've *already* created before
                // coming across this flag:
                for (Reporter reporter : flags.getReporters()) {
                    if (reporter instanceof XmlReporter) {
                        XmlReporter xmlReporter = (XmlReporter) reporter;
                        if (xmlReporter.getType() == XmlFileType.REPORT) {
                            xmlReporter.setType(XmlFileType.REPORT_WITH_FIXES);
                        }
                    }
                }
            } else if (arg.equals(ARG_ABORT_IF_SUGGESTIONS_APPLIED)) {
                flags.setAbortOnAutoFix(true);
            } else if (arg.equals(ARG_CLASSES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing class folder name");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Class path entry " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    List<File> classes = flags.getClassesOverride();
                    if (classes == null) {
                        classes = new ArrayList<>();
                        flags.setClassesOverride(classes);
                    }
                    classes.add(input);
                }
            } else if (arg.equals(ARG_SOURCES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing source folder name");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Source folder " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    List<File> sources = flags.getSourcesOverride();
                    if (sources == null) {
                        sources = new ArrayList<>();
                        flags.setSourcesOverride(sources);
                    }
                    sources.add(input);
                }
            } else if (arg.equals(ARG_RESOURCES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing resource folder name");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Resource folder " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    List<File> resources = flags.getResourcesOverride();
                    if (resources == null) {
                        resources = new ArrayList<>();
                        flags.setResourcesOverride(resources);
                    }
                    resources.add(input);
                }
            } else if (arg.equals(ARG_LIBRARIES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing library folder name");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Library " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    List<File> libraries = flags.getLibrariesOverride();
                    if (libraries == null) {
                        libraries = new ArrayList<>();
                        flags.setLibrariesOverride(libraries);
                    }
                    libraries.add(input);
                }
            } else if (arg.equals(ARG_SKIP_ANNOTATED)) {
                if (index == args.length - 1) {
                    System.err.println("Missing annotation name");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String annotation : paths.split(",")) {
                    flags.addSkipAnnotation(annotation);
                }
            } else if (arg.equals(ARG_OFFLINE)) {
                flags.setOffline(true);
            } else if (arg.equals(ARG_BUILD_API)) {
                if (index == args.length - 1) {
                    System.err.println("Missing compileSdkVersion");
                    return ERRNO_INVALID_ARGS;
                }
                String version = args[++index];
                flags.setCompileSdkVersionOverride(version);
            } else if (arg.equals(ARG_JAVA_LANGUAGE_LEVEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing Java language level");
                    return ERRNO_INVALID_ARGS;
                }
                String version = args[++index];
                LanguageLevel level = LanguageLevel.parse(version);
                if (level == null) {
                    System.err.println("Invalid Java language level \"" + version + "\"");
                    return ERRNO_INVALID_ARGS;
                }
                argumentState.javaLanguageLevel = level;
            } else if (arg.equals(ARG_KOTLIN_LANGUAGE_LEVEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing Kotlin language level");
                    return ERRNO_INVALID_ARGS;
                }
                String version = args[++index];
                LanguageVersion languageLevel = LanguageVersion.fromVersionString(version);
                if (languageLevel == null) {
                    System.err.println("Invalid Kotlin language level \"" + version + "\"");
                    return ERRNO_INVALID_ARGS;
                }
                ApiVersion apiVersion = ApiVersion.createByLanguageVersion(languageLevel);
                argumentState.kotlinLanguageLevel =
                        new LanguageVersionSettingsImpl(languageLevel, apiVersion);
            } else if (arg.equals(ARG_PROJECT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing project description file");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Project descriptor " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    if (!input.isFile()) {
                        System.err.println(
                                "Project descriptor "
                                        + input
                                        + " should be an XML descriptor file"
                                        + (input.isDirectory() ? ", not a directory" : ""));
                        return ERRNO_INVALID_ARGS;
                    }
                    File descriptor = flags.getProjectDescriptorOverride();
                    //noinspection VariableNotUsedInsideIf
                    if (descriptor != null) {
                        System.err.println("Project descriptor should only be specified once");
                        return ERRNO_INVALID_ARGS;
                    }
                    flags.setProjectDescriptorOverride(input);
                }
            } else if (arg.equals(ARG_VARIANT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing variant name after " + ARG_VARIANT);
                    return ERRNO_INVALID_ARGS;
                }
                argumentState.variantName = args[++index];
            } else if (arg.equals(ARG_PATH_VARIABLES)) {
                if (index == args.length - 1) {
                    System.err.println(
                            "Missing path variable descriptor  after " + ARG_PATH_VARIABLES);
                    return ERRNO_INVALID_ARGS;
                }
                if (argumentState.pathVariables != null) {
                    System.err.println(
                            ARG_PATH_VARIABLES
                                    + " must be specified before "
                                    + ARG_LINT_MODEL
                                    + " and only once");
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                argumentState.pathVariables = PathVariables.Companion.parse(paths);
            } else if (arg.equals(ARG_LINT_MODEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing lint model argument after " + ARG_LINT_MODEL);
                    return ERRNO_INVALID_ARGS;
                }
                String paths = args[++index];
                for (String path : EaseLintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Lint model " + input + " does not exist.");
                        return ERRNO_INVALID_ARGS;
                    }
                    if (!input.isDirectory()) {
                        System.err.println(
                                "Lint model "
                                        + input
                                        + " should be a folder containing the XML descriptor files"
                                        + (input.isDirectory() ? ", not a file" : ""));
                        return ERRNO_INVALID_ARGS;
                    }
                    try {
                        LintModelSerialization reader = LintModelSerialization.INSTANCE;
                        LintModelModule module =
                                reader.readModule(input, null, true, client.getPathVariables());
                        argumentState.modules.add(module);
                    } catch (Throwable error) {
                        System.err.println(
                                "Could not deserialize "
                                        + input
                                        + " to a lint model: "
                                        + error.toString());
                        return ERRNO_INVALID_ARGS;
                    }
                }
            } else if (arg.equals(ARG_LINT_RULE_JARS)) {
                if (index == args.length - 1) {
                    System.err.println("Missing lint rule jar");
                    return ERRNO_INVALID_ARGS;
                }
                List<File> lintRuleJarsOverride = new ArrayList<>();
                List<File> currentOverrides = flags.getLintRuleJarsOverride();
                if (currentOverrides != null) {
                    lintRuleJarsOverride.addAll(currentOverrides);
                }
                for (String path : EaseLintUtils.splitPath(args[++index])) {
                    lintRuleJarsOverride.add(getInArgumentPath(path));
                }
                flags.setLintRuleJarsOverride(lintRuleJarsOverride);
            } else if (arg.equals(ARG_SDK_HOME)) {
                if (index == args.length - 1) {
                    System.err.println("Missing SDK home directory");
                    return ERRNO_INVALID_ARGS;
                }
                sdkHome = new File(args[++index]);
                if (!sdkHome.isDirectory()) {
                    System.err.println(sdkHome + " is not a directory");
                    return ERRNO_INVALID_ARGS;
                }
            } else if (arg.equals(ARG_JDK_HOME)) {
                if (index == args.length - 1) {
                    System.err.println("Missing JDK home directory");
                    return ERRNO_INVALID_ARGS;
                }
                jdkHome = new File(args[++index]);
                if (!jdkHome.isDirectory()) {
                    System.err.println(jdkHome + " is not a directory");
                    return ERRNO_INVALID_ARGS;
                }
                if (!EaseLintUtils.isJreFolder(jdkHome)) {
                    System.err.println(jdkHome + " is not a JRE/JDK");
                    return ERRNO_INVALID_ARGS;
                }
            } else if (arg.equals(ARG_BASELINE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing baseline file path");
                    return ERRNO_INVALID_ARGS;
                }
                String path = args[++index];
                File input = getInArgumentPath(path);
                flags.setBaselineFile(input);
            } else if (arg.equals(ARG_CACHE_DIR)) {
                if (index == args.length - 1) {
                    System.err.println("Missing cache directory");
                    return ERRNO_INVALID_ARGS;
                }
                String path = args[++index];
                File input = getInArgumentPath(path);
                flags.setCacheDir(input);
            } else if (arg.equals(ARG_REMOVE_FIXED)) {
                if (flags.isUpdateBaseline()) {
                    System.err.printf(
                            Locale.US,
                            "Cannot use both %s and %s.%n",
                            ARG_REMOVE_FIXED,
                            ARG_UPDATE_BASELINE);
                }
                flags.setRemovedFixedBaselineIssues(true);
            } else if (arg.equals(ARG_UPDATE_BASELINE)) {
                if (flags.isRemoveFixedBaselineIssues()) {
                    System.err.printf(
                            Locale.US,
                            "Cannot use both %s and %s.%n",
                            ARG_UPDATE_BASELINE,
                            ARG_REMOVE_FIXED);
                }
                flags.setUpdateBaseline(true);
            } else if (arg.equals(ARG_CONTINUE_AFTER_BASELINE_CREATED)) {
                flags.setContinueAfterBaselineCreated(true);
            } else if (arg.equals(ARG_WRITE_REF_BASELINE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing baseline file path");
                    return ERRNO_INVALID_ARGS;
                }
                String path = args[++index];
                File output = getOutArgumentPath(path);
                flags.setOutputBaselineFile(output);
                flags.setUpdateBaseline(true);
                flags.setContinueAfterBaselineCreated(true);
            } else if (arg.equals(ARG_MISSING_BASELINE_IS_EMPTY_BASELINE)) {
                flags.setMissingBaselineIsEmptyBaseline(true);
            } else if (arg.equals(ARG_ALLOW_SUPPRESS)) {
                flags.setAllowSuppress(true);
            } else if (arg.equals(ARG_RESTRICT_SUPPRESS)) {
                flags.setAllowSuppress(false);
            } else if (arg.equals("--XallowBaselineSuppress")) {
                flags.setAllowBaselineSuppress(true);
            } else if (arg.equals("--Xdesugared-methods")) {
                if (index == args.length - 1) {
                    System.err.println("Missing desugared methods file");
                    return ERRNO_INVALID_ARGS;
                }
                String path = args[++index];
                if (argumentState.desugaredMethodsPaths == null) {
                    argumentState.desugaredMethodsPaths = new ArrayList<>();
                }
                argumentState.desugaredMethodsPaths.add(path);
            } else if (arg.equals(ARG_PRINT_INTERNAL_ERROR_STACKTRACE)) {
                flags.setPrintInternalErrorStackTrace(true);
            } else if (arg.equals(ARG_ANALYZE_ONLY)) {
                argumentState.mode = LintDriver.DriverMode.ANALYSIS_ONLY;
            } else if (arg.equals(ARG_REPORT_ONLY)) {
                argumentState.mode = LintDriver.DriverMode.MERGE;
            } else if (arg.equals(ARG_CLIENT_ID)) {
                if (index == args.length - 1) {
                    System.err.println("Missing client id");
                    return ERRNO_INVALID_ARGS;
                }
                LintClient.setClientName(args[++index]);
            } else if (arg.equals(ARG_CLIENT_NAME)) {
                if (index == args.length - 1) {
                    System.err.println("Missing client name");
                    return ERRNO_INVALID_ARGS;
                }
                argumentState.clientName = args[++index];
            } else if (arg.equals(ARG_CLIENT_VERSION)) {
                if (index == args.length - 1) {
                    System.err.println("Missing client version");
                    return ERRNO_INVALID_ARGS;
                }
                argumentState.clientVersion = args[++index];
            } else if (arg.equals(ARG_GENERATE_DOCS)) {
                if (index != 0) {
                    System.err.println(
                            ARG_GENERATE_DOCS + " cannot be used in combination with other flags.");
                    printUsage(System.err);
                    return ERRNO_INVALID_ARGS;
                }
                return LintIssueDocGenerator.run(Arrays.copyOfRange(args, 1, args.length), true);
            } else if (arg.startsWith("--")) {
                System.err.println("Invalid argument " + arg + "\n");
                printUsage(System.err);
                return ERRNO_INVALID_ARGS;
            } else {
                File file = getInArgumentPath(arg);
                if (!file.exists()) {
                    System.err.printf("%1$s does not exist.%n", arg);
                    return ERRNO_EXISTS;
                }
                argumentState.files.add(file);
            }
        }

        List<LintModelModule> modules = argumentState.modules;
        List<File> files = argumentState.files;
        if (!modules.isEmpty()) {
            if (!files.isEmpty()) {
                System.err.println(
                        "Do not specify both files and lint models: lint models should instead include the files");
                return ERRNO_INVALID_ARGS;
            }

            // Sync the first lint model's lint options.
            SyncOptions.syncTo(modules.get(0), flags);
        }

        List<String> paths = argumentState.desugaredMethodsPaths;
        if (paths != null) {
            String firstError = DesugaredMethodLookup.Companion.setDesugaredMethods(paths);
            if (firstError != null) {
                System.err.println("Failed to process --Xdesugared-methods: " + firstError);
                return ERRNO_INVALID_ARGS;
            }
        }

        if (files.isEmpty() && modules.isEmpty() && flags.getProjectDescriptorOverride() == null) {
            System.err.println("No files to analyze.");
            return ERRNO_INVALID_ARGS;
        } else if (files.size() > 1
                && (flags.getClassesOverride() != null
                || flags.getSourcesOverride() != null
                || flags.getLibrariesOverride() != null
                || flags.getResourcesOverride() != null)) {
            System.err.printf(
                    "The %1$s, %2$s, %3$s and %4$s arguments can only be used with a single project%n",
                    ARG_SOURCES, ARG_CLASSES, ARG_LIBRARIES, ARG_RESOURCES);
            return ERRNO_INVALID_ARGS;
        }

        return ERRNO_INTERNAL_CONTINUE;
    }

    private void initializeConfigurations(LintCliClient client, ArgumentState argumentState) {
        ConfigurationHierarchy configurations = client.getConfigurations();

        File overrideConfig = flags.getOverrideLintConfig();
        if (overrideConfig != null) {
            Configuration config = LintXmlConfiguration.create(configurations, overrideConfig);
            configurations.addGlobalConfigurations(null, config);
        }

        CliConfiguration override =
                new CliConfiguration(configurations, flags, flags.isFatalOnly());
        File defaultConfiguration = flags.getLintConfig();
        configurations.addGlobalConfigurationFromFile(defaultConfiguration, override);
        client.syncConfigOptions();

        if (!argumentState.modules.isEmpty()) {
            File dir = argumentState.modules.get(0).getDir();
            override.setAssociatedLocation(Location.create(dir));
        }
    }

    private int initializeReporters(LintCliClient client, ArgumentState argumentState) {
        String urlMap = argumentState.urlMap;
        List<Reporter> reporters = flags.getReporters();
        if (reporters.isEmpty()) {
            //noinspection VariableNotUsedInsideIf
            if (urlMap != null) {
                System.err.printf(
                        "Warning: The %1$s option only applies to HTML reports (%2$s)%n",
                        ARG_URL, ARG_HTML);
            }

            PrintWriter writer = printWriter(System.out);
            reporters.add(new TextReporter(client, flags, writer, false));
        } else {
            if (urlMap != null && !urlMap.equals(VALUE_NONE)) {
                Map<String, String> map = new HashMap<>();
                String[] replace = urlMap.split(",");
                for (String s : replace) {
                    // Allow ='s in the suffix part
                    int index = s.indexOf('=');
                    if (index == -1) {
                        System.err.println(
                                "The URL map argument must be of the form 'path_prefix=url_prefix'");
                        return ERRNO_INVALID_ARGS;
                    }
                    String key = s.substring(0, index);
                    String value = s.substring(index + 1);
                    map.put(key, value);
                }
                for (Reporter reporter : reporters) {
                    reporter.setUrlMap(map);
                }
            }
        }

        return ERRNO_INTERNAL_CONTINUE;
    }

    private static LintRequest createLintRequest(
            LintCliClient client, ArgumentState argumentState) {
        LintRequest lintRequest;
        List<LintModelModule> modules = argumentState.modules;
        if (!modules.isEmpty()) {
            List<LintModelModuleProject> projects = new ArrayList<>();
            for (LintModelModule module : modules) {
                File dir = module.getDir();
                LintModelVariant variant = null;
                String variantName = argumentState.variantName;
                if (variantName != null) {
                    variant = module.findVariant(variantName);
                    if (variant == null) {
                        System.err.println(
                                "Warning: Variant "
                                        + variantName
                                        + " not found in lint model for "
                                        + dir);
                    }
                }
                if (variant == null) {
                    variant = module.defaultVariant();
                }
                assert variant != null;
                LintModelModuleProject project =
                        new LintModelModuleProject(client, dir, dir, variant, null);
                client.registerProject(project.getDir(), project);
                projects.add(project);
            }

            // Set up lint project dependencies based on the model
            boolean isReporting = argumentState.mode == LintDriver.DriverMode.MERGE;
            List<LintModelModuleProject> roots =
                    LintModelModuleProject.resolveDependencies(projects, isReporting);

            lintRequest = new LintRequest(client, Collections.emptyList());
            lintRequest.setProjects(roots);

            if (isReporting) {
                EnumSet<Platform> platforms =
                        roots.get(0).isAndroidProject() ? Platform.ANDROID_SET : Platform.JDK_SET;
                lintRequest.setPlatform(platforms);
            }

            // TODO: What about dynamic features? See LintGradleProject#configureLintRequest
        } else {
            lintRequest = client.createLintRequest(argumentState.files);
        }

        return lintRequest;
    }

    private int run(LintCliClient client, LintRequest lintRequest, ArgumentState argumentState) {
        try {
            // Not using globalIssueRegistry; LintClient will do its own registry merging
            // also including project rules.
            switch (argumentState.mode) {
                case GLOBAL:
                    return client.run(new BuiltinIssueRegistry(), lintRequest);
                case ANALYSIS_ONLY:
                    return client.analyzeOnly(new BuiltinIssueRegistry(), lintRequest);
                case MERGE:
                    return client.mergeOnly(new BuiltinIssueRegistry(), lintRequest);
                default:
                    throw new IllegalStateException("Unexpected value: " + argumentState.mode);
            }
        } catch (IOException e) {
            log(e, null);
            return ERRNO_INVALID_ARGS;
        }
    }

    private static void setSeverity(
            @NonNull LintCliFlags flags,
            @NonNull IssueRegistry registry,
            @NonNull String idString,
            @Nullable Set<String> targetSet,
            @Nullable Severity severity) {
        String[] ids = idString.split(",");
        for (String id : ids) {
            if (registry.isCategoryName(id)) {
                // Suppress all issues with the given category
                for (Issue issue : registry.getIssues()) {
                    // Check prefix such that filtering on the "Usability" category
                    // will match issue category "Usability:Icons" etc.
                    if (issue.getCategory().getName().startsWith(id)
                            || issue.getCategory().getFullName().startsWith(id)) {
                        setSeverity(flags, id, targetSet, severity);
                    }
                }
            } else {
                Issue issue = registry.getIssue(id);
                // Normalize issue names (in case it is an older alias)
                if (issue != null) {
                    id = issue.getId();
                }
                setSeverity(flags, id, targetSet, severity);
            }
        }
    }

    private static void setSeverity(
            @NonNull LintCliFlags flags,
            @NonNull String id,
            @Nullable Set<String> targetSet,
            @Nullable Severity severity) {
        if (targetSet != null) {
            assert severity == null;
            targetSet.add(id);
        } else {
            assert severity != null;
            Map<String, LintModelSeverity> map = new HashMap<>(flags.getSeverityOverrides());
            map.put(id, EaseLintServerityUtils.getModelSeverity(severity));
            flags.setSeverityOverrides(map);
        }
    }

    private IssueRegistry getGlobalRegistry(LintCliClient client) {
        if (globalIssueRegistry == null) {
            globalIssueRegistry =
                    client.addCustomLintRules(new BuiltinIssueRegistry(), null, false);
        }

        return globalIssueRegistry;
    }

    /**
     * Converts a relative or absolute command-line argument into an input file.
     *
     * @param filename The filename given as a command-line argument.
     * @return A File matching filename, either absolute or relative to lint.workdir if defined.
     */
    private static File getInArgumentPath(String filename) {
        File file = new File(filename);

        if (!file.isAbsolute()) {
            File workDir = getLintWorkDir();
            if (workDir != null) {
                File file2 = new File(workDir, filename);
                if (file2.exists()) {
                    try {
                        file = file2.getCanonicalFile();
                    } catch (IOException e) {
                        file = file2;
                    }
                }
            }

            if (!file.isAbsolute()) {
                file = file.getAbsoluteFile();
            }
        }

        return file;
    }

    /**
     * Converts a relative or absolute command-line argument into an output file.
     *
     * <p>The difference with {@code getInArgumentPath} is that we can't check whether the relative
     * path turned into an absolute compared to lint.workdir actually exists.
     *
     * @param filename The filename given as a command-line argument.
     * @return A File matching filename, either absolute or relative to lint.workdir if defined.
     */
    private static File getOutArgumentPath(String filename) {
        File file = new File(filename);

        if (!file.isAbsolute()) {
            File workDir = getLintWorkDir();
            if (workDir != null) {
                File file2 = new File(workDir, filename);
                try {
                    file = file2.getCanonicalFile();
                } catch (IOException e) {
                    file = file2;
                }
            }

            if (!file.isAbsolute()) {
                file = file.getAbsoluteFile();
            }
        }

        return file;
    }

    /**
     * Returns the File corresponding to the system property or the environment variable for {@link
     * #PROP_WORK_DIR}. This property is typically set by the SDK/tools/lint[.bat] wrapper. It
     * denotes the path where the command-line client was originally invoked from and can be used to
     * convert relative input/output paths.
     *
     * @return A new File corresponding to {@link #PROP_WORK_DIR} or null.
     */
    @Nullable
    private static File getLintWorkDir() {
        // Return null if LintClient.isGradle because AGP passes absolute paths
        if (LintClient.isGradle()) {
            return null;
        }
        // First check the Java properties (e.g. set using "java -jar ... -Dname=value")
        String path = System.getProperty(PROP_WORK_DIR);
        if (path == null || path.isEmpty()) {
            // If not found, check environment variables.
            path = System.getenv(PROP_WORK_DIR);
        }
        if (path != null && !path.isEmpty()) {
            return new File(path);
        }
        return null;
    }

    private static void printHelpTopicSuppress() {
        System.out.println(wrap(TextFormat.RAW.convertTo(getSuppressHelp(), TextFormat.TEXT)));
    }

    static String getSuppressHelp() {
        return "Lint errors can be suppressed in a variety of ways:\n"
                + "\n"
                + "1. With a `@SuppressLint` annotation in the Java code\n"
                + "2. With a `tools:ignore` attribute in the XML file\n"
                + "3. With a //noinspection comment in the source code\n"
                + "4. With ignore flags specified in the `build.gradle` file, "
                + "as explained below\n"
                + "5. With a `lint.xml` configuration file in the project\n"
                + "6. With a `lint.xml` configuration file passed to lint "
                + "via the "
                + ARG_CONFIG
                + " flag\n"
                + "7. With the "
                + ARG_IGNORE
                + " flag passed to lint.\n"
                + "\n"
                + "To suppress a lint warning with an annotation, add "
                + "a `@SuppressLint(\"id\")` annotation on the class, method "
                + "or variable declaration closest to the warning instance "
                + "you want to disable. The id can be one or more issue "
                + "id's, such as `\"UnusedResources\"` or `{\"UnusedResources\","
                + "\"UnusedIds\"}`, or it can be `\"all\"` to suppress all lint "
                + "warnings in the given scope.\n"
                + "\n"
                + "To suppress a lint warning with a comment, add "
                + "a `//noinspection id` comment on the line before the statement "
                + "with the error.\n"
                + "\n"
                + "To suppress a lint warning in an XML file, add a "
                + "`tools:ignore=\"id\"` attribute on the element containing "
                + "the error, or one of its surrounding elements. You also "
                + "need to define the namespace for the tools prefix on the "
                + "root element in your document, next to the `xmlns:android` "
                + "declaration:\n"
                + "`xmlns:tools=\"http://schemas.android.com/tools\"`\n"
                + "\n"
                + "To suppress a lint warning in a `build.gradle` file, add a "
                + "section like this:\n"
                + "\n"
                + "```gradle\n"
                + "android {\n"
                + "    lintOptions {\n"
                + "        disable 'TypographyFractions','TypographyQuotes'\n"
                + "    }\n"
                + "}\n"
                + "```\n"
                + "\n"
                + "Here we specify a comma separated list of issue id's after the "
                + "disable command. You can also use `warning` or `error` instead "
                + "of `disable` to change the severity of issues.\n"
                + "\n"
                + "To suppress lint warnings with a configuration XML file, "
                + "create a file named `lint.xml` and place it at the root "
                + "directory of the module in which it applies.\n"
                + "\n"
                + "The format of the `lint.xml` file is something like the "
                + "following:\n"
                + "\n"
                + "```xml\n"
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lint>\n"
                + "    <!-- Ignore everything in the test source set -->\n"
                + "    <issue id=\"all\">\n"
                + "        <ignore path=\"\\*/test/\\*\" />\n"
                + "    </issue>\n"
                + "\n"
                + "    <!-- Disable this given check in this project -->\n"
                + "    <issue id=\"IconMissingDensityFolder\" severity=\"ignore\" />\n"
                + "\n"
                + "    <!-- Ignore the ObsoleteLayoutParam issue in the given files -->\n"
                + "    <issue id=\"ObsoleteLayoutParam\">\n"
                + "        <ignore path=\"res/layout/activation.xml\" />\n"
                + "        <ignore path=\"res/layout-xlarge/activation.xml\" />\n"
                + "        <ignore regexp=\"(foo|bar)\\.java\" />\n"
                + "    </issue>\n"
                + "\n"
                + "    <!-- Ignore the UselessLeaf issue in the given file -->\n"
                + "    <issue id=\"UselessLeaf\">\n"
                + "        <ignore path=\"res/layout/main.xml\" />\n"
                + "    </issue>\n"
                + "\n"
                + "    <!-- Change the severity of hardcoded strings to \"error\" -->\n"
                + "    <issue id=\"HardcodedText\" severity=\"error\" />\n"
                + "</lint>\n"
                + "```\n"
                + "\n"
                + "To suppress lint checks from the command line, pass the "
                + ARG_IGNORE
                + " "
                + "flag with a comma separated list of ids to be suppressed, such as:\n"
                + "`$ lint --ignore UnusedResources,UselessLeaf /my/project/path`\n"
                + "\n"
                + "For more information, see "
                + "https://developer.android.com/studio/write/lint.html#config\n";
    }

    private static void printVersion(LintCliClient client) {
        String revision = client.getClientDisplayRevision();
        if (revision != null) {
            System.out.printf("lint: version %1$s%n", revision);
        } else {
            System.out.println("lint: unknown version");
        }
    }

    private static void displayValidIds(IssueRegistry registry, PrintWriter out) {
        List<Category> categories = registry.getCategories();
        out.println("Valid issue categories:");
        for (Category category : categories) {
            out.println("    " + category.getFullName());
        }
        out.println();
        List<Issue> issues = registry.getIssues();
        out.println("Valid issue id's:");
        for (Issue issue : issues) {
            listIssue(out, issue);
        }
    }

    private static void listIssue(PrintWriter out, Issue issue) {
        out.print(wrapArg("\"" + issue.getId() + "\": " + issue.getBriefDescription(TEXT)));
    }

    private static void showIssues(IssueRegistry registry) {
        List<Issue> issues = registry.getIssues();
        List<Issue> sorted = new ArrayList<>(issues);
        sorted.sort(
                (issue1, issue2) -> {
                    int d = issue1.getCategory().compareTo(issue2.getCategory());
                    if (d != 0) {
                        return d;
                    }
                    d = issue2.getPriority() - issue1.getPriority();
                    if (d != 0) {
                        return d;
                    }

                    return issue1.getId().compareTo(issue2.getId());
                });

        System.out.println("Available issues:\n");
        Category previousCategory = null;
        for (Issue issue : sorted) {
            Category category = issue.getCategory();
            if (!category.equals(previousCategory)) {
                String name = category.getFullName();
                System.out.println(name);
                for (int i = 0, n = name.length(); i < n; i++) {
                    System.out.print('=');
                }
                System.out.println('\n');
                previousCategory = category;
            }

            describeIssue(issue);
            System.out.println();
        }
    }

    private static void describeIssue(Issue issue) {
        System.out.println(issue.getId());
        for (int i = 0; i < issue.getId().length(); i++) {
            System.out.print('-');
        }
        System.out.println();
        System.out.println(wrap("Summary: " + issue.getBriefDescription(TEXT)));
        System.out.println("Priority: " + issue.getPriority() + " / 10");
        System.out.println("Severity: " + issue.getDefaultSeverity().getDescription());
        System.out.println("Category: " + issue.getCategory().getFullName());
        Vendor vendor = issue.getVendor();
        IssueRegistry registry = issue.getRegistry();
        if (vendor == null && registry != null) {
            vendor = registry.getVendor();
        }
        if (vendor != null) {
            String description = vendor.describe(TEXT);
            System.out.print(description);
        }

        if (!issue.isEnabledByDefault()) {
            System.out.println("NOTE: This issue is disabled by default!");
            System.out.printf("You can enable it by adding %1$s %2$s%n", ARG_ENABLE, issue.getId());
        }

        System.out.println();
        System.out.println(wrap(issue.getExplanation(TEXT)));

        List<Option> options = issue.getOptions();
        if (!options.isEmpty()) {
            System.out.println(Option.Companion.describe(options, TextFormat.TEXT, true));
            System.out.println();
        }
        List<String> moreInfo = issue.getMoreInfo();
        if (!moreInfo.isEmpty()) {
            System.out.println("More information: ");
            for (String uri : moreInfo) {
                System.out.println(uri);
            }
        }
    }

    static String wrapArg(String explanation) {
        // Wrap arguments such that the wrapped lines are not showing up in the left column
        return wrap(explanation, MAX_LINE_WIDTH, "      ");
    }

    static String wrap(String explanation) {
        return wrap(explanation, MAX_LINE_WIDTH, "");
    }

    static String wrap(
            String explanation,
            @SuppressWarnings("SameParameterValue") int lineWidth,
            String hangingIndent) {
        return SdkUtils.wrap(explanation, lineWidth, hangingIndent);
    }

    private static void printUsage(PrintStream out) {
        printUsage(printWriter(out));
    }

    private static void printUsage(PrintWriter out) {
        printUsage(out, false);
    }

    static void printUsage(PrintWriter out, boolean markdown) {
        String command = "lint";

        out.println("Usage: " + command + " [flags] <project directories>\n");

        printUsage(
                out,
                new String[]{
                        "",
                        "General:",
                        ARG_HELP,
                        "This message.",
                        ARG_HELP + " <topic>",
                        "Help on the given topic, such as “suppress”.",
                        ARG_LIST_IDS,
                        "List the available issue id's and exit.",
                        ARG_VERSION,
                        "Output version information and exit.",
                        ARG_EXIT_CODE,
                        "Set the exit code to " + ERRNO_ERRORS + " if errors are found.",
                        ARG_SHOW,
                        "List available issues along with full explanations.",
                        ARG_SHOW + " <ids>",
                        "Show full explanations for the given list of issue id's.",
                        ARG_GENERATE_DOCS,
                        "Generates documentation for all the lint checks. This flag cannot be combined "
                                + "with other lint flags, and it has its own sub-flags. Invoke on its own "
                                + "to see what they are.",
                        ARG_FATAL_ONLY,
                        "Only check for fatal severity issues",
                        ARG_AUTO_FIX,
                        "Apply suggestions to the source code (for safe fixes)",
                        ARG_ABORT_IF_SUGGESTIONS_APPLIED,
                        "Set the exit code to an error if any fixes are applied",
                        "[directories]",
                        "You can also pass in directories for files to be analyzed. This "
                                + "was common many years ago when project metadata tended to be stored "
                                + "in simple `.classpath` files from Eclipse; these days, you typically "
                                + "will pass it a `--lint-model` or `--project` description instead.",
                        "",
                        "\nEnabled Checks:",
                        ARG_DISABLE + " <list>",
                        "Disable the list of categories or "
                                + "specific issue id's. The list should be a comma-separated list of issue "
                                + "id's or categories.",
                        ARG_ENABLE + " <list>",
                        "Enable the specific list of issues. "
                                + "This checks all the default issues plus the specifically enabled issues. The "
                                + "list should be a comma-separated list of issue id's or categories.",
                        ARG_CHECK + " <list>",
                        "Only check the specific list of issues. "
                                + "This will disable everything and re-enable the given list of issues. "
                                + "The list should be a comma-separated list of issue id's or categories.",
                        ARG_FATAL + " <list>",
                        "Sets the default severity of the given issue to fatal",
                        ARG_ERROR + " <list>",
                        "Sets the default severity of the given issue to error",
                        ARG_WARNING + " <list>",
                        "Sets the default severity of the given issue to warning",
                        ARG_INFO + " <list>",
                        "Sets the default severity of the given issue to info",
                        ARG_NO_WARN_1 + ", " + ARG_NO_WARN_2,
                        "Only check for errors (ignore warnings)",
                        ARG_WARN_ALL,
                        "Check all warnings, including those off by default",
                        ARG_ALL_ERROR,
                        "Treat all warnings as errors",
                        ARG_CONFIG + " <filename>",
                        "Use the given configuration file to "
                                + "determine whether issues are enabled or disabled. If a project contains "
                                + "a lint.xml file, then this config file will be used as a fallback.",
                        ARG_OVERRIDE_CONFIG + " <filename>",
                        "Like "
                                + ARG_CONFIG
                                + ", but instead of being a fallback, this "
                                + "configuration overrides any local configuration files",
                        ARG_BASELINE,
                        "Use (or create) the given baseline file to filter out known issues.",
                        ARG_UPDATE_BASELINE,
                        "Updates the baselines even if they already exist",
                        ARG_REMOVE_FIXED,
                        "Rewrite the baseline files to remove any issues that have been fixed",
                        ARG_WRITE_REF_BASELINE,
                        "Writes the current results, including issues that were filtered from the "
                                + "input baseline if any. Does not set the exit code to indicate that "
                                + "the baseline is created the way "
                                + ARG_BASELINE
                                + " would. Implies "
                                + ARG_UPDATE_BASELINE
                                + " and "
                                + ARG_CONTINUE_AFTER_BASELINE_CREATED
                                + ".",
                        ARG_MISSING_BASELINE_IS_EMPTY_BASELINE,
                        "Treat a missing baseline file as an empty baseline file. In most cases, this "
                                + "means that if the baseline file does not exist, a new one will not "
                                + "be created. But in the case when "
                                + ARG_UPDATE_BASELINE
                                + " is also used and there are lint issues, a new baseline file will "
                                + "be created, and the lint issues will be written to it.",
                        ARG_ALLOW_SUPPRESS,
                        "Whether to allow suppressing issues that have been explicitly registered "
                                + "as not suppressible.",
                        ARG_RESTRICT_SUPPRESS,
                        "Opposite of "
                                + ARG_ALLOW_SUPPRESS
                                + ": do not allow suppressing restricted issues",
                        ARG_SKIP_ANNOTATED,
                        "Comma separated list of annotations (by fully qualified name) which indicate that "
                                + "lint should ignore this compilation unit (only allowed on top level classes and files)",
                        "",
                        "\nOutput Options:",
                        ARG_QUIET,
                        "Don't show progress.",
                        ARG_PRINT_INTERNAL_ERROR_STACKTRACE,
                        "Print full stacktrace for internal errors.",
                        ARG_FULL_PATH,
                        "Use full paths in the error output.",
                        ARG_SHOW_ALL,
                        "Do not truncate long messages, lists of alternate locations, etc.",
                        ARG_NO_LINES,
                        "Do not include the source file lines with errors "
                                + "in the output. By default, the error output includes snippets of source code "
                                + "on the line containing the error, but this flag turns it off.",
                        ARG_HTML + " <filename>",
                        "Create an HTML report instead. If the filename is a "
                                + "directory (or a new filename without an extension), lint will create a "
                                + "separate report for each scanned project.",
                        ARG_URL + " filepath=url",
                        "Add links to HTML report, replacing local "
                                + "path prefixes with url prefix. The mapping can be a comma-separated list of "
                                + "path prefixes to corresponding URL prefixes, such as "
                                + "C:\\temp\\Proj1=http://buildserver/sources/temp/Proj1.  To turn off linking "
                                + "to files, use "
                                + ARG_URL
                                + " "
                                + VALUE_NONE,
                        ARG_XML + " <filename>",
                        "Create an XML report instead.",
                        ARG_SARIF + " <filename>",
                        "Create a SARIF report instead.",
                        ARG_TEXT + " <filename>",
                        "Write a text report to the given file. If the filename is just `stdout` (short "
                                + "for standard out), the report is written to the console.",
                        "",
                        "\nProject Options:",
                        ARG_RESOURCES + " <dir>",
                        "Add the given folder (or path) as a resource directory "
                                + "for the project. Only valid when running lint on a single project.",
                        ARG_SOURCES + " <dir>",
                        "Add the given folder (or path) as a source directory for "
                                + "the project. Only valid when running lint on a single project.",
                        ARG_CLASSES + " <dir>",
                        "Add the given folder (or jar file, or path) as a class "
                                + "directory for the project. Only valid when running lint on a single project.",
                        ARG_LIBRARIES + " <dir>",
                        "Add the given folder (or jar file, or path) as a class "
                                + "library for the project. Only valid when running lint on a single project.",
                        ARG_BUILD_API + " <version>",
                        "Use the given compileSdkVersion to pick an SDK "
                                + "target to resolve Android API call to",
                        ARG_SDK_HOME + " <dir>",
                        "Use the given SDK instead of attempting to find it "
                                + "relative to the lint installation or via $ANDROID_SDK_ROOT",
                        ARG_JDK_HOME + " <dir>",
                        "Use the given JDK instead of attempting to find it via $JAVA_HOME or java.home",
                        ARG_JAVA_LANGUAGE_LEVEL + " <level>",
                        "Use the given version of the Java programming language",
                        ARG_KOTLIN_LANGUAGE_LEVEL + " <level>",
                        "Use the given version of the Kotlin programming language",
                        "",
                        "\nAdvanced Options (for build system integration):",
                        ARG_PROJECT + " <file>",
                        "Use the given project layout descriptor file to describe "
                                + "the set of available sources, resources and libraries. Used to drive lint with "
                                + "build systems not natively integrated with lint.",
                        ARG_LINT_MODEL + " <path>",
                        "Alternative to " + ARG_PROJECT + " which defines the project layout",
                        ARG_VARIANT + " <name>",
                        "The name of the variant from the lint model to use for analysis",
                        ARG_LINT_RULE_JARS + " <path>",
                        "One or more .jar files to load additional lint checks from",
                        ARG_ANALYZE_ONLY,
                        "Perform only analysis, not reporting, of the given lint model",
                        ARG_REPORT_ONLY,
                        "Perform only reporting of previous analysis results",
                        ARG_PATH_VARIABLES + " <variables>",
                        "Path variables to use in internal persistence files to make lint results cacheable. "
                                + "Use a semi-colon separated list of name=path pairs.",
                        ARG_DESCRIBE_FIXES + " <file>",
                        "Describes all the quickfixes in an XML file expressed as document edits -- insert, replace, delete",
                        ARG_CLIENT_ID,
                        "Sets the id of the client, such as “gradle”",
                        ARG_CLIENT_NAME,
                        "Sets the display name of the client, such as “Android Gradle Plugin”",
                        ARG_CLIENT_VERSION,
                        "Sets the version of the client, such as “7.1.0-alpha01”",
                        ARG_OFFLINE,
                        "Whether lint should attempt to stay offline",
                        "",
                        "\nExit Status:",
                        Integer.toString(ERRNO_SUCCESS),
                        "Success.",
                        Integer.toString(ERRNO_ERRORS),
                        "Lint errors detected.",
                        Integer.toString(ERRNO_USAGE),
                        "Lint usage.",
                        Integer.toString(ERRNO_EXISTS),
                        "Cannot clobber existing file.",
                        Integer.toString(ERRNO_HELP),
                        "Lint help.",
                        Integer.toString(ERRNO_INVALID_ARGS),
                        "Invalid command-line argument.",
                        Integer.toString(ERRNO_CREATED_BASELINE),
                        "A new baseline file was created.",
                        Integer.toString(ERRNO_APPLIED_SUGGESTIONS),
                        "Quickfixes were applied.",
                },
                markdown);
    }

    static void printUsage(PrintWriter out, String[] args, boolean md) {
        int argWidth = 0;
        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            argWidth = Math.max(argWidth, arg.length());
        }
        argWidth += 2;
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < argWidth; i++) {
            sb.append(' ');
        }
        String indent = sb.toString();
        String formatString = "%1$-" + argWidth + "s%2$s";

        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            String description = args[i + 1];
            if (md) {
                if (arg.isEmpty()) {
                    out.print("## ");
                    out.println(StringsKt.removeSuffix(description.trim(), ":"));
                    out.println();
                } else {
                    boolean italics = arg.startsWith("[") && arg.endsWith("]");
                    char decoration = italics ? '*' : '`';
                    if (italics) {
                        arg = arg.substring(1, arg.length() - 1);
                    }
                    out.print(decoration);
                    int index = arg.indexOf(' ');
                    if (index != -1) {
                        out.print(arg.substring(0, index));
                        out.print(decoration);
                        out.print(' ');
                        String remainder = arg.substring(index + 1);
                        // Switch from say <list> to *list*
                        remainder = remainder.replace('<', '*').replace('>', '*');
                        out.print(remainder);
                    } else {
                        out.print(arg);
                        out.print(decoration);
                    }
                    out.println();
                    out.print(wrap(": " + description, 70, "  "));
                    out.println("");
                }
                continue;
            }
            if (arg.isEmpty()) {
                out.println(description);
            } else {
                out.print(
                        wrap(
                                String.format(formatString, arg, description),
                                MAX_LINE_WIDTH,
                                indent));
            }
        }
    }

    public void log(
            @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
        System.out.flush();
        if (format != null) {
            //noinspection RedundantStringFormatCall
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }
}
