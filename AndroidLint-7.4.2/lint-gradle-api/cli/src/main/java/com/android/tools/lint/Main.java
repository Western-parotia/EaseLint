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
import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_EXISTS;
import static com.android.tools.lint.LintCliFlags.ERRNO_HELP;
import static com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.LintCliFlags.ERRNO_USAGE;
import static com.android.tools.lint.detector.api.Lint.endsWith;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintModelModuleProject;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelSerialization;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Command line driver for the lint framework
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public class Main {
    static final int MAX_LINE_WIDTH = 78;
    private static final String ARG_ENABLE = "--enable";
    private static final String ARG_DISABLE = "--disable";
    private static final String ARG_CHECK = "--check";
    private static final String ARG_AUTO_FIX = "--apply-suggestions";
    private static final String ARG_DESCRIBE_FIXES = "--describe-suggestions";
    private static final String ARG_IGNORE = "--ignore";
    private static final String ARG_LIST_IDS = "--list";
    private static final String ARG_SHOW = "--show";
    private static final String ARG_QUIET = "--quiet";
    private static final String ARG_FULL_PATH = "--fullpath";
    private static final String ARG_SHOW_ALL = "--showall";
    private static final String ARG_HELP = "--help";
    private static final String ARG_NO_LINES = "--nolines";
    private static final String ARG_HTML = "--html";
    private static final String ARG_SIMPLE_HTML = "--simplehtml";
    private static final String ARG_XML = "--xml";
    private static final String ARG_TEXT = "--text";
    private static final String ARG_CONFIG = "--config";
    private static final String ARG_URL = "--url";
    private static final String ARG_VERSION = "--version";
    private static final String ARG_EXIT_CODE = "--exitcode";
    private static final String ARG_SDK_HOME = "--sdk-home";
    private static final String ARG_JDK_HOME = "--jdk-home";
    private static final String ARG_FATAL = "--fatalOnly";
    private static final String ARG_PROJECT = "--project";
    private static final String ARG_LINT_MODEL = "--lint-model";
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
    private static final String ARG_ALLOW_SUPPRESS = "--allow-suppress";
    private static final String ARG_RESTRICT_SUPPRESS = "--restrict-suppress";

    private static final String ARG_NO_WARN_2 = "--nowarn";
    // GCC style flag names for options
    private static final String ARG_NO_WARN_1 = "-w";
    private static final String ARG_WARN_ALL = "-Wall";
    private static final String ARG_ALL_ERROR = "-Werror";

    private static final String PROP_WORK_DIR = "com.android.tools.lint.workdir";
    private final LintCliFlags flags = new LintCliFlags();
    private IssueRegistry globalIssueRegistry;
    @Nullable private File sdkHome;
    @Nullable private File jdkHome;

    /** Creates a CLI driver */
    public Main() {}

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        try {
            new Main().run(args);
        } catch (ExitException exitException) {
            System.exit(exitException.getStatus());
        }
    }

    /** Hook intended for tests */
    protected void initializeDriver(@NonNull LintDriver driver) {}

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void run(String[] args) {
        if (args.length < 1) {
            printUsage(System.err);
            exit(ERRNO_USAGE);
        }

        Ref<LanguageLevel> javaLanguageLevel = new Ref<>(null);
        Ref<LanguageVersionSettings> kotlinLanguageLevel = new Ref<>(null);
        List<LintModelModule> modules = new ArrayList<>();
        String variantName = null;

        // When running lint from the command line, warn if the project is a Gradle project
        // since those projects may have custom project configuration that the command line
        // runner won't know about.
        LintCliClient client =
                new LintCliClient(flags, LintClient.CLIENT_CLI) {

                    private Pattern mAndroidAnnotationPattern;
                    private Project unexpectedGradleProject = null;

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
                            Location location =
                                    Lint.guessGradleLocation(this, project.getDir(), null);
                            LintClient.Companion.report(
                                    this,
                                    IssueRegistry.LINT_ERROR,
                                    message,
                                    driver,
                                    project,
                                    location,
                                    null);
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
                        LanguageLevel level = javaLanguageLevel.get();
                        if (level != null) {
                            return level;
                        }
                        return super.getJavaLanguageLevel(project);
                    }

                    @NonNull
                    @Override
                    public LanguageVersionSettings getKotlinLanguageLevel(
                            @NonNull Project project) {
                        LanguageVersionSettings settings = kotlinLanguageLevel.get();
                        if (settings != null) {
                            return settings;
                        }
                        return super.getKotlinLanguageLevel(project);
                    }

                    @NonNull
                    @Override
                    public Configuration getConfiguration(
                            @NonNull final Project project, @Nullable LintDriver driver) {
                        DefaultConfiguration overrideConfiguration = getOverrideConfiguration();
                        if (overrideConfiguration != null) {
                            return overrideConfiguration;
                        }

                        if (project.isGradleProject()) {
                            // Don't report any issues when analyzing a Gradle project from the
                            // non-Gradle runner; they are likely to be false, and will hide the
                            // real problem reported above
                            //noinspection ReturnOfInnerClass
                            return new CliConfiguration(getConfiguration(), project, true) {
                                @NonNull
                                @Override
                                public Severity getSeverity(@NonNull Issue issue) {
                                    return issue == IssueRegistry.LINT_ERROR
                                            ? Severity.FATAL
                                            : Severity.IGNORE;
                                }

                                @Override
                                public boolean isIgnored(
                                        @NonNull Context context,
                                        @NonNull Issue issue,
                                        @Nullable Location location,
                                        @NonNull String message) {
                                    // If you've deliberately ignored IssueRegistry.LINT_ERROR
                                    // don't flag that one either
                                    if (issue == IssueRegistry.LINT_ERROR
                                            && new LintCliClient(flags, LintClient.getClientName())
                                                    .isSuppressed(IssueRegistry.LINT_ERROR)) {
                                        return true;
                                    }

                                    return issue != IssueRegistry.LINT_ERROR;
                                }
                            };
                        }
                        return super.getConfiguration(project, driver);
                    }

                    private byte[] readSrcJar(@NonNull File file) {
                        String path = file.getPath();
                        int srcJarIndex = path.indexOf("srcjar!");
                        if (srcJarIndex != -1) {
                            File jarFile = new File(path.substring(0, srcJarIndex + 6));
                            if (jarFile.exists()) {
                                try (ZipFile zipFile = new ZipFile(jarFile)) {
                                    String name =
                                            path.substring(srcJarIndex + 8)
                                                    .replace(File.separatorChar, '/');
                                    ZipEntry entry = zipFile.getEntry(name);
                                    if (entry != null) {
                                        try (InputStream is = zipFile.getInputStream(entry)) {
                                            byte[] bytes = ByteStreams.toByteArray(is);
                                            return bytes;
                                        } catch (Exception e) {
                                            log(e, null);
                                        }
                                    }
                                } catch (ZipException e) {
                                    Main.this.log(e, "Could not unzip %1$s", jarFile);
                                } catch (IOException e) {
                                    Main.this.log(e, "Could not read %1$s", jarFile);
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

                        CharSequence contents = super.readFile(file);
                        if (Project.isAospBuildEnvironment()
                                && file.getPath().endsWith(SdkConstants.DOT_JAVA)) {
                            if (mAndroidAnnotationPattern == null) {
                                mAndroidAnnotationPattern = Pattern.compile("android\\.annotation");
                            }
                            return mAndroidAnnotationPattern
                                    .matcher(contents)
                                    .replaceAll("android.support.annotation");
                        } else {
                            return contents;
                        }
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
                    protected void configureLintRequest(@NotNull LintRequest request) {
                        File descriptor = flags.getProjectDescriptorOverride();
                        if (descriptor != null) {
                            metadata = ProjectInitializerKt.computeMetadata(this, descriptor);
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
                    public List<File> findGlobalRuleJars() {
                        if (metadata != null) {
                            List<File> jars = metadata.getGlobalLintChecks();
                            if (!jars.isEmpty()) {
                                return jars;
                            }
                        }

                        return super.findGlobalRuleJars();
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
                        if (Main.this.sdkHome != null) {
                            return Main.this.sdkHome;
                        }
                        return super.getSdkHome();
                    }

                    @Nullable
                    @Override
                    public File getJdkHome(@Nullable Project project) {
                        if (Main.this.jdkHome != null) {
                            return Main.this.jdkHome;
                        }
                        return super.getJdkHome(project);
                    }

                    @Override
                    protected boolean addBootClassPath(
                            @NonNull Collection<? extends Project> knownProjects,
                            @NonNull Set<File> files) {
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
                    public List<File> getExternalAnnotations(
                            @NonNull Collection<? extends Project> projects) {
                        List<File> externalAnnotations = super.getExternalAnnotations(projects);
                        if (metadata != null) {
                            externalAnnotations.addAll(metadata.getExternalAnnotations());
                        }
                        return externalAnnotations;
                    }
                };

        // Mapping from file path prefix to URL. Applies only to HTML reports
        String urlMap = null;

        List<File> files = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];

            if (arg.equals(ARG_HELP) || arg.equals("-h") || arg.equals("-?")) {
                if (index < args.length - 1) {
                    String topic = args[index + 1];
                    if (topic.equals("suppress") || topic.equals("ignore")) {
                        printHelpTopicSuppress();
                        exit(ERRNO_HELP);
                    } else {
                        System.err.println(String.format("Unknown help topic \"%1$s\"", topic));
                        exit(ERRNO_INVALID_ARGS);
                    }
                }
                printUsage(System.out);
                exit(ERRNO_HELP);
            } else if (arg.equals(ARG_LIST_IDS)) {
                IssueRegistry registry = getGlobalRegistry(client);
                // Did the user provide a category list?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) {
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // List all issues with the given category
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category)
                                        || issue.getCategory().getFullName().startsWith(category)) {
                                    listIssue(System.out, issue);
                                }
                            }
                        } else {
                            System.err.println("Invalid category \"" + id + "\".\n");
                            displayValidIds(registry, System.err);
                            exit(ERRNO_INVALID_ARGS);
                        }
                    }
                } else {
                    displayValidIds(registry, System.out);
                }
                exit(ERRNO_SUCCESS);
            } else if (arg.equals(ARG_SHOW)) {
                IssueRegistry registry = getGlobalRegistry(client);
                // Show specific issues?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) {
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // Show all issues in the given category
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
                            displayValidIds(registry, System.err);
                            exit(ERRNO_INVALID_ARGS);
                        }
                    }
                } else {
                    showIssues(registry);
                }
                exit(ERRNO_SUCCESS);
            } else if (arg.equals(ARG_FULL_PATH)
                    || arg.equals(ARG_FULL_PATH + "s")) { // allow "--fullpaths" too
                flags.setFullPath(true);
            } else if (arg.equals(ARG_SHOW_ALL)) {
                flags.setShowEverything(true);
            } else if (arg.equals(ARG_QUIET) || arg.equals("-q")) {
                flags.setQuiet(true);
            } else if (arg.equals(ARG_NO_LINES)) {
                flags.setShowSourceLines(false);
            } else if (arg.equals(ARG_EXIT_CODE)) {
                flags.setSetExitCode(true);
            } else if (arg.equals(ARG_FATAL)) {
                flags.setFatalOnly(true);
            } else if (arg.equals(ARG_VERSION)) {
                printVersion(client);
                exit(ERRNO_SUCCESS);
            } else if (arg.equals(ARG_URL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing URL mapping string");
                    exit(ERRNO_INVALID_ARGS);
                }
                String map = args[++index];
                // Allow repeated usage of the argument instead of just comma list
                if (urlMap != null) {
                    //noinspection StringConcatenationInLoop
                    urlMap = urlMap + ',' + map;
                } else {
                    urlMap = map;
                }
            } else if (arg.equals(ARG_CONFIG)) {
                if (index == args.length - 1 || !endsWith(args[index + 1], DOT_XML)) {
                    System.err.println("Missing XML configuration file argument");
                    exit(ERRNO_INVALID_ARGS);
                }
                File file = getInArgumentPath(args[++index]);
                if (!file.exists()) {
                    System.err.println(file.getAbsolutePath() + " does not exist");
                    exit(ERRNO_INVALID_ARGS);
                }
                flags.setDefaultConfiguration(file);
            } else if (arg.equals(ARG_HTML) || arg.equals(ARG_SIMPLE_HTML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing HTML output file name");
                    exit(ERRNO_INVALID_ARGS);
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
                            exit(ERRNO_EXISTS);
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
                        exit(ERRNO_EXISTS);
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write HTML output file " + output);
                    exit(ERRNO_EXISTS);
                }
                try {
                    Reporter reporter = Reporter.createHtmlReporter(client, output, flags);
                    flags.getReporters().add(reporter);
                } catch (IOException e) {
                    log(e, null);
                    exit(ERRNO_INVALID_ARGS);
                }
            } else if (arg.equals(ARG_XML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing XML output file name");
                    exit(ERRNO_INVALID_ARGS);
                }
                File output = getOutArgumentPath(args[++index]);
                // Get an absolute path such that we can ask its parent directory for
                // write permission etc.
                output = output.getAbsoluteFile();

                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        exit(ERRNO_EXISTS);
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write XML output file " + output);
                    exit(ERRNO_EXISTS);
                }
                try {
                    flags.getReporters()
                            .add(
                                    Reporter.createXmlReporter(
                                            client, output, false, flags.isIncludeXmlFixes()));
                } catch (IOException e) {
                    log(e, null);
                    exit(ERRNO_INVALID_ARGS);
                }
            } else if (arg.equals(ARG_TEXT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing text output file name");
                    exit(ERRNO_INVALID_ARGS);
                }

                Writer writer = null;
                boolean closeWriter;
                String outputName = args[++index];
                if (outputName.equals("stdout")) {
                    //noinspection IOResourceOpenedButNotSafelyClosed,resource
                    writer = new PrintWriter(System.out, true);
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
                            exit(ERRNO_EXISTS);
                        }
                    }
                    if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                        System.err.println("Cannot write text output file " + output);
                        exit(ERRNO_EXISTS);
                    }
                    try {
                        //noinspection IOResourceOpenedButNotSafelyClosed,resource
                        writer = new BufferedWriter(new FileWriter(output));
                    } catch (IOException e) {
                        log(e, null);
                        exit(ERRNO_INVALID_ARGS);
                    }
                    closeWriter = true;
                }
                flags.getReporters().add(new TextReporter(client, flags, writer, closeWriter));
            } else if (arg.equals(ARG_DISABLE) || arg.equals(ARG_IGNORE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to disable");
                    exit(ERRNO_INVALID_ARGS);
                }
                IssueRegistry registry = getGlobalRegistry(client);
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Suppress all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category)
                                    || issue.getCategory().getFullName().startsWith(category)) {
                                flags.getSuppressedIds().add(issue.getId());
                            }
                        }
                    } else {
                        flags.getSuppressedIds().add(id);
                    }
                }
            } else if (arg.equals(ARG_ENABLE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to enable");
                    exit(ERRNO_INVALID_ARGS);
                }
                IssueRegistry registry = getGlobalRegistry(client);
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Enable all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            if (issue.getCategory().getName().startsWith(category)
                                    || issue.getCategory().getFullName().startsWith(category)) {
                                flags.getEnabledIds().add(issue.getId());
                            }
                        }
                        flags.getEnabledIds().add(id);
                    }
                }
            } else if (arg.equals(ARG_CHECK)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to check");
                    exit(ERRNO_INVALID_ARGS);
                }
                Set<String> checkedIds = flags.getExactCheckedIds();
                if (checkedIds == null) {
                    checkedIds = new HashSet<>();
                    flags.setExactCheckedIds(checkedIds);
                }
                IssueRegistry registry = getGlobalRegistry(client);
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Check all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category)
                                    || issue.getCategory().getFullName().startsWith(category)) {
                                checkedIds.add(issue.getId());
                            }
                        }
                    } else {
                        checkedIds.add(id);
                    }
                }
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
                        if (!xmlReporter.isIntendedForBaseline()) {
                            xmlReporter.setIncludeFixes(true);
                        }
                    }
                }
            } else if (arg.equals(ARG_CLASSES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing class folder name");
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Class path entry " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
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
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Source folder " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
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
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Resource folder " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
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
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Library " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
                    }
                    List<File> libraries = flags.getLibrariesOverride();
                    if (libraries == null) {
                        libraries = new ArrayList<>();
                        flags.setLibrariesOverride(libraries);
                    }
                    libraries.add(input);
                }
            } else if (arg.equals(ARG_BUILD_API)) {
                if (index == args.length - 1) {
                    System.err.println("Missing compileSdkVersion");
                    exit(ERRNO_INVALID_ARGS);
                }
                String version = args[++index];
                flags.setCompileSdkVersionOverride(version);
            } else if (arg.equals(ARG_JAVA_LANGUAGE_LEVEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing Java language level");
                    exit(ERRNO_INVALID_ARGS);
                }
                String version = args[++index];
                LanguageLevel level = LanguageLevel.parse(version);
                if (level == null) {
                    System.err.println("Invalid Java language level \"" + version + "\"");
                    exit(ERRNO_INVALID_ARGS);
                }
                javaLanguageLevel.set(level);
            } else if (arg.equals(ARG_KOTLIN_LANGUAGE_LEVEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing Kotlin language level");
                    exit(ERRNO_INVALID_ARGS);
                }
                String version = args[++index];
                LanguageVersion languageLevel = LanguageVersion.fromVersionString(version);
                if (languageLevel == null) {
                    System.err.println("Invalid Kotlin language level \"" + version + "\"");
                    exit(ERRNO_INVALID_ARGS);
                }
                ApiVersion apiVersion = ApiVersion.createByLanguageVersion(languageLevel);
                LanguageVersionSettingsImpl settings =
                        new LanguageVersionSettingsImpl(languageLevel, apiVersion);
                kotlinLanguageLevel.set(settings);
            } else if (arg.equals(ARG_PROJECT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing project description file");
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Project descriptor " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
                    }
                    if (!input.isFile()) {
                        System.err.println(
                                "Project descriptor "
                                        + input
                                        + " should be an XML descriptor file"
                                        + (input.isDirectory() ? ", not a directory" : ""));
                        exit(ERRNO_INVALID_ARGS);
                    }
                    File descriptor = flags.getProjectDescriptorOverride();
                    //noinspection VariableNotUsedInsideIf
                    if (descriptor != null) {
                        System.err.println("Project descriptor should only be specified once");
                        exit(ERRNO_INVALID_ARGS);
                    }
                    flags.setProjectDescriptorOverride(input);
                }
            } else if (arg.equals(ARG_VARIANT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing variant name after " + ARG_VARIANT);
                    exit(ERRNO_INVALID_ARGS);
                }
                variantName = args[++index];
            } else if (arg.equals(ARG_LINT_MODEL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing lint model argument after " + ARG_LINT_MODEL);
                    exit(ERRNO_INVALID_ARGS);
                }
                String paths = args[++index];
                for (String path : Lint.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Lint model " + input + " does not exist.");
                        exit(ERRNO_INVALID_ARGS);
                    }
                    if (!input.isFile()) {
                        System.err.println(
                                "Lint model "
                                        + input
                                        + " should be an XML descriptor file"
                                        + (input.isDirectory() ? ", not a directory" : ""));
                        exit(ERRNO_INVALID_ARGS);
                    }
                    try {
                        LintModelSerialization reader = LintModelSerialization.INSTANCE;
                        LintModelModule module =
                                reader.readModule(
                                        input,
                                        Collections.emptyList(),
                                        // TODO: Define any path variables Gradle may be setting!
                                        true,
                                        Collections.emptyList());
                        modules.add(module);
                    } catch (Throwable error) {
                        System.err.println(
                                "Could not deserialize "
                                        + input
                                        + " to a lint model: "
                                        + error.toString());
                        exit(ERRNO_INVALID_ARGS);
                    }
                }
            } else if (arg.equals(ARG_SDK_HOME)) {
                if (index == args.length - 1) {
                    System.err.println("Missing SDK home directory");
                    exit(ERRNO_INVALID_ARGS);
                }
                sdkHome = new File(args[++index]);
                if (!sdkHome.isDirectory()) {
                    System.err.println(sdkHome + " is not a directory");
                    exit(ERRNO_INVALID_ARGS);
                }
            } else if (arg.equals(ARG_JDK_HOME)) {
                if (index == args.length - 1) {
                    System.err.println("Missing JDK home directory");
                    exit(ERRNO_INVALID_ARGS);
                }
                jdkHome = new File(args[++index]);
                if (!jdkHome.isDirectory()) {
                    System.err.println(jdkHome + " is not a directory");
                    exit(ERRNO_INVALID_ARGS);
                }
                if (!Lint.isJreFolder(jdkHome)) {
                    System.err.println(jdkHome + " is not a JRE/JDK");
                    exit(ERRNO_INVALID_ARGS);
                }
            } else if (arg.equals(ARG_BASELINE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing baseline file path");
                    exit(ERRNO_INVALID_ARGS);
                }
                String path = args[++index];
                File input = getInArgumentPath(path);
                flags.setBaselineFile(input);
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
            } else if (arg.equals(ARG_ALLOW_SUPPRESS)) {
                flags.setAllowSuppress(true);
            } else if (arg.equals(ARG_RESTRICT_SUPPRESS)) {
                flags.setAllowSuppress(false);
            } else if (arg.startsWith("--")) {
                System.err.println("Invalid argument " + arg + "\n");
                printUsage(System.err);
                exit(ERRNO_INVALID_ARGS);
            } else {
                String filename = arg;
                File file = getInArgumentPath(filename);

                if (!file.exists()) {
                    System.err.println(String.format("%1$s does not exist.", filename));
                    exit(ERRNO_EXISTS);
                }
                files.add(file);
            }
        }

        if (files.isEmpty() && flags.getProjectDescriptorOverride() == null) {
            System.err.println("No files to analyze.");
            exit(ERRNO_INVALID_ARGS);
        } else if (files.size() > 1
                && (flags.getClassesOverride() != null
                        || flags.getSourcesOverride() != null
                        || flags.getLibrariesOverride() != null
                        || flags.getResourcesOverride() != null)) {
            System.err.println(
                    String.format(
                            "The %1$s, %2$s, %3$s and %4$s arguments can only be used with a single project",
                            ARG_SOURCES, ARG_CLASSES, ARG_LIBRARIES, ARG_RESOURCES));
            exit(ERRNO_INVALID_ARGS);
        }

        client.syncConfigOptions();

        List<Reporter> reporters = flags.getReporters();
        if (reporters.isEmpty()) {
            //noinspection VariableNotUsedInsideIf
            if (urlMap != null) {
                System.err.println(
                        String.format(
                                "Warning: The %1$s option only applies to HTML reports (%2$s)",
                                ARG_URL, ARG_HTML));
            }

            reporters.add(
                    new TextReporter(client, flags, new PrintWriter(System.out, true), false));
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
                        exit(ERRNO_INVALID_ARGS);
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

        LintRequest lintRequest;
        if (!modules.isEmpty()) {
            if (!files.isEmpty()) {
                System.err.println(
                        "Do not specify both files and lint models: lint models should instead include the files");
                exit(ERRNO_INVALID_ARGS);
            }
            List<Project> projects = new ArrayList<>();
            for (LintModelModule module : modules) {
                File dir = module.getDir();
                LintModelVariant variant = null;
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
                LintModelModuleProject project =
                        new LintModelModuleProject(client, dir, dir, variant, null);
                client.registerProject(project.getDir(), project);
                projects.add(project);
            }
            lintRequest = new LintRequest(client, Collections.emptyList());
            lintRequest.setProjects(projects);
            // TODO: What about dynamic features? See LintGradleProject#configureLintRequest
        } else {
            lintRequest = client.createLintRequest(files);
        }

        try {
            // Not using globalIssueRegistry; LintClient will do its own registry merging
            // also including project rules.

            int exitCode = client.run(new BuiltinIssueRegistry(), lintRequest);
            exit(exitCode);
        } catch (IOException e) {
            log(e, null);
            exit(ERRNO_INVALID_ARGS);
        }
    }

    private IssueRegistry getGlobalRegistry(LintCliClient client) {
        if (globalIssueRegistry == null) {
            globalIssueRegistry = client.addCustomLintRules(new BuiltinIssueRegistry());
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
     * <p>The difference with {@code getInArgumentPath} is that we can't check whether the a
     * relative path turned into an absolute compared to lint.workdir actually exists.
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
            System.out.println(String.format("lint: version %1$s", revision));
        } else {
            System.out.println("lint: unknown version");
        }
    }

    private static void displayValidIds(IssueRegistry registry, PrintStream out) {
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

    private static void listIssue(PrintStream out, Issue issue) {
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

        if (!issue.isEnabledByDefault()) {
            System.out.println("NOTE: This issue is disabled by default!");
            System.out.println(
                    String.format(
                            "You can enable it by adding %1$s %2$s", ARG_ENABLE, issue.getId()));
        }

        System.out.println();
        System.out.println(wrap(issue.getExplanation(TEXT)));
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

    static String wrap(String explanation, int lineWidth, String hangingIndent) {
        return SdkUtils.wrap(explanation, lineWidth, hangingIndent);
    }

    private static void printUsage(PrintStream out) {
        // TODO: Look up launcher script name!
        String command = "lint";

        out.println("Usage: " + command + " [flags] <project directories>\n");
        out.println("Flags:\n");

        printUsage(
                out,
                new String[] {
                    ARG_HELP,
                    "This message.",
                    ARG_HELP + " <topic>",
                    "Help on the given topic, such as \"suppress\".",
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
                    ARG_FATAL,
                    "Only check for fatal severity issues",
                    ARG_AUTO_FIX,
                    "Apply suggestions to the source code (for safe fixes)",
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
                    ARG_BASELINE,
                    "Use (or create) the given baseline file to filter out known issues.",
                    ARG_ALLOW_SUPPRESS,
                    "Whether to allow suppressing issues that have been explicitly registered "
                            + "as not suppressible.",
                    "",
                    "\nOutput Options:",
                    ARG_QUIET,
                    "Don't show progress.",
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
                    ARG_TEXT + " <filename>",
                    "Write a text report to the given file. If the filename is just `stdout` (short "
                            + "for standard out), the report is written to the console.",
                    "",
                    "\nProject Options:",
                    ARG_PROJECT + " <file>",
                    "Use the given project layout descriptor file to describe "
                            + "the set of available sources, resources and libraries. Used to drive lint with "
                            + "build systems not natively integrated with lint.",
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
                    "\nExit Status:",
                    "0",
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
                });
    }

    private static void printUsage(PrintStream out, String[] args) {
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
        if (!flags.isQuiet()) {
            // Place the error message on a line of its own since we're printing '.' etc
            // with newlines during analysis
            System.err.println();
        }
        if (format != null) {
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @VisibleForTesting
    static final class ExitException extends RuntimeException {

        private final int status;

        ExitException(int status) {
            this.status = status;
        }

        int getStatus() {
            return status;
        }
    }

    private static void exit(int value) {
        throw new ExitException(value);
    }
}
