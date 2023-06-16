/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.repository.ResourceVisibilityLookup
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidTargetHash.PLATFORM_HASH_PREFIX
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelSerialization
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import com.android.utils.usLocaleCapitalize
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.File.pathSeparatorChar
import java.io.File.separator
import java.io.File.separatorChar
import java.io.IOException
import java.util.EnumSet
import java.util.HashSet
import java.util.regex.Pattern
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.math.max

/** Regular expression used to match package statements with [ProjectInitializer.findPackage] */
private val PACKAGE_PATTERN = Pattern.compile("package\\s+([\\S&&[^;]]*)")
private const val TAG_PROJECT = "project"
private const val TAG_MODULE = "module"
private const val TAG_CLASSES = "classes"
private const val TAG_CLASSPATH = "classpath"
private const val TAG_SRC = "src"
private const val TAG_DEP = "dep"
private const val TAG_ROOT = "root"
private const val TAG_MANIFEST = "manifest"
private const val TAG_RESOURCE = "resource"
private const val TAG_AAR = "aar"
private const val TAG_JAR = "jar"
private const val TAG_SDK = "sdk"
private const val TAG_JDK = "jdk"
private const val TAG_JDK_BOOT_CLASS_PATH = "jdk-boot-classpath"
private const val TAG_LINT_CHECKS = "lint-checks"
private const val TAG_BASELINE = "baseline"
private const val TAG_MERGED_MANIFEST = "merged-manifest"
private const val TAG_CACHE = "cache"
private const val TAG_AIDL = "aidl"
private const val TAG_PROGUARD = "proguard"
private const val TAG_ANNOTATIONS = "annotations"
private const val ATTR_COMPILE_SDK_VERSION = "compile-sdk-version"
private const val ATTR_TEST = "test"
private const val ATTR_GENERATED = "generated"
private const val ATTR_NAME = "name"
private const val ATTR_FILE = "file"
private const val ATTR_EXTRACTED = "extracted"
private const val ATTR_DIR = "dir"
private const val ATTR_JAR = "jar"
private const val ATTR_ANDROID = "android"
private const val ATTR_LIBRARY = "library"
private const val ATTR_MODULE = "module"
private const val ATTR_INCOMPLETE = "incomplete"
private const val ATTR_JAVA8_LIBS = "android_java8_libs"
private const val ATTR_DESUGAR = "desugar"
private const val ATTR_JAVA_LEVEL = "javaLanguage"
private const val ATTR_KOTLIN_LEVEL = "kotlinLanguage"
private const val ATTR_MODEL = "model"
private const val DOT_SRCJAR = ".srcjar"

/**
 * Compute a list of lint [Project] instances from the given XML descriptor files.
 * Each descriptor is considered completely separate from the other (e.g. you can't
 * have library definitions in one referenced from another descriptor.
 */
fun computeMetadata(client: LintClient, descriptor: File): ProjectMetadata {
    val initializer = ProjectInitializer(
        client, descriptor,
        descriptor.parentFile ?: descriptor
    )
    return initializer.computeMetadata()
}

/**
 * Result data passed from parsing a project metadata XML file - returns the
 * set of projects, any SDK or cache directories configured within the file, etc
 */
data class ProjectMetadata(
    /** List of projects. Will be empty if there was an error in the configuration. */
    val projects: List<Project> = emptyList(),
    /** A baseline file to apply, if any */
    val baseline: File? = null,
    /** The SDK to use, if overriding the default */
    val sdk: File? = null,
    /** The JDK to use, if overriding the default */
    val jdk: File? = null,
    /** The cache directory to use, if overriding the default */
    val cache: File? = null,
    /** A map from module to a merged manifest for that module, if any */
    val mergedManifests: Map<Project, File?> = emptyMap(),
    /** A map from module to a baseline to apply to that module, if any */
    val moduleBaselines: Map<Project, File?> = emptyMap(),
    /** List of custom check JAR files to apply everywhere */
    val globalLintChecks: List<File> = emptyList(),
    /** A map from module to a list of custom rule JAR files to apply, if any */
    val lintChecks: Map<Project, List<File>> = emptyMap(),
    /** list of boot classpath jars to use for non-Android projects */
    val jdkBootClasspath: List<File> = emptyList(),
    /** Target platforms we're analyzing  */
    val platforms: EnumSet<Platform>? = null,
    /** Set of external annotations.zip files or external annotation directories */
    val externalAnnotations: List<File> = emptyList(),
    /**
     * If true, the project metadata being passed in only represents a small
     * subset of the real project sources, so only lint checks which can be run
     * without full project context should be attempted. This is what happens for
     * "on-the-fly" checks running in the IDE.
     */
    val incomplete: Boolean = false
)

/**
 * Class which handles initialization of a project hierarchy from a config XML file.
 *
 * Note: This code uses both the term "projects" and "modules". That's because
 * lint internally uses the term "project" for what Studio (and these XML config
 * files) refers to as a "module".
 *
 * @param client the lint handler
 * @param file the XML description file
 * @param root the root project directory (relative paths in the config file are considered
 *             relative to this directory)
 */
private class ProjectInitializer(
    val client: LintClient,
    val file: File,
    var root: File
) {

    /** map from module name to module instance */
    private val modules = mutableMapOf<String, ManualProject>()

    /** list of global classpath jars to add to all modules */
    private val globalClasspath = mutableListOf<File>()

    /** map from module instance to names of modules it depends on */
    private val dependencies: Multimap<ManualProject, String> = ArrayListMultimap.create()

    /** map from module to the merged manifest to use, if any */
    private val mergedManifests = mutableMapOf<Project, File?>()

    /** map from module to a list of custom lint rules to apply for that module, if any */
    private val lintChecks = mutableMapOf<Project, List<File>>()

    /** External annotations */
    private val externalAnnotations: MutableList<File> = mutableListOf()

    /** map from module to a baseline to use for a given module, if any */
    private val baselines = mutableMapOf<Project, File?>()

    /**
     * map from aar or jar file to wrapper module name
     * (which in turn can be looked up in [dependencies])
     */
    private val jarAarMap = mutableMapOf<File, String>()

    /**
     * Map from module name to resource visibility lookup
     */
    private val visibility = mutableMapOf<String, ResourceVisibilityLookup>()

    /** A cache directory to use, if specified */
    private var cache: File? = null

    /** Whether we're analyzing an Android project */
    private var android: Boolean = false

    /** Desugaring operations to enable */
    private var desugaring: EnumSet<Desugaring>? = null

    /** Compute a list of lint [Project] instances from the given XML descriptor */
    fun computeMetadata(): ProjectMetadata {
        val document = client.xmlParser.parseXml(file)

        if (document == null || document.documentElement == null) {
            // Lint isn't quite up and running yet, so create a dummy context
            // in order to be able to report an error
            reportError("Failed to parse project descriptor $file")
            return ProjectMetadata()
        }

        return parseModules(document.documentElement)
    }

    /** Reports the given error message as an error to lint, with an optional element location */
    private fun reportError(message: String, node: Node? = null) {
        // To report an error using the lint infrastructure, we have to have
        // an associated "project", but there isn't an actual project yet
        // (that's what this whole class is attempting to compute and initialize).
        // Therefore, we'll make a dummy project. A project has to have an
        // associated directory, so we'll just randomly pick the folder containing
        // the project.xml folder itself. (In case it's not a full path, e.g.
        // just "project.xml", get the current directory instead.)
        val location = when {
            node != null -> client.xmlParser.getLocation(file, node)
            else -> Location.create(file)
        }
        LintClient.report(
            client = client, issue = IssueRegistry.LINT_ERROR, message = message,
            location = location,
            file = file
        )
    }

    private fun parseModules(projectElement: Element): ProjectMetadata {
        if (projectElement.tagName != TAG_PROJECT) {
            reportError("Expected <project> as the root tag", projectElement)
            return ProjectMetadata()
        }

        val incomplete = projectElement.getAttribute(ATTR_INCOMPLETE) == VALUE_TRUE
        android = projectElement.getAttribute(ATTR_ANDROID) == VALUE_TRUE
        desugaring = handleDesugaring(projectElement)

        val globalLintChecks = mutableListOf<File>()

        // First gather modules and sources, and collect dependency information.
        // The dependency information is captured into a separate map since dependencies
        // may refer to modules we have not encountered yet.
        var child = getFirstSubTag(projectElement)
        var sdk: File? = null
        var jdk: File? = null
        var baseline: File? = null
        val jdkBootClasspath = mutableListOf<File>()
        while (child != null) {
            val tag = child.tagName
            when (tag) {
                TAG_MODULE -> {
                    parseModule(child)
                }

                TAG_CLASSPATH -> {
                    globalClasspath.add(getFile(child, this.root))
                }

                TAG_LINT_CHECKS -> {
                    globalLintChecks.add(getFile(child, this.root))
                }

                TAG_ANNOTATIONS -> {
                    externalAnnotations += getFile(child, this.root)
                }

                TAG_SDK -> {
                    sdk = getFile(child, this.root)
                }

                TAG_JDK -> {
                    jdk = getFile(child, this.root)
                }

                TAG_JDK_BOOT_CLASS_PATH -> {
                    val path = child.getAttribute(ATTR_PATH)
                    if (path.isNotEmpty()) {
                        for (s in path.split(pathSeparatorChar)) {
                            jdkBootClasspath += getFile(s, child, this.root)
                        }
                    } else {
                        jdkBootClasspath += getFile(child, this.root)
                    }
                }

                TAG_BASELINE -> {
                    baseline = getFile(child, this.root)
                }

                TAG_CACHE -> {
                    cache = getFile(child, this.root)
                }

                TAG_ROOT -> {
                    val dir = File(child.getAttribute(ATTR_DIR))
                    if (dir.isDirectory) {
                        root = dir
                    } else {
                        reportError("$dir does not exist", child)
                    }
                }

                else -> {
                    reportError("Unexpected top level tag $tag in $file", child)
                }
            }

            child = getNextTag(child)
        }

        // Now that we have all modules recorded, process our dependency map
        // and add dependencies between the modules
        for ((module, dependencyName) in dependencies.entries()) {
            val to = modules[dependencyName]
            if (to != null) {
                module.addDirectDependency(to)
            } else {
                reportError("No module $dependencyName found (depended on by ${module.name}")
            }
        }

        val allModules = modules.values

        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)
        val roots = HashSet(allModules)
        for (project in allModules) {
            roots.removeAll(project.allLibraries)
        }

        val sortedModules = roots.toMutableList()
        sortedModules.sortBy { it.name }

        // Initialize the classpath. We have a single massive jar for all
        // modules instead of individual folders...
        if (!globalClasspath.isEmpty()) {
            var useForAnalysis = true
            for (module in sortedModules) {
                if (module.getJavaLibraries(true).isEmpty()) {
                    module.setClasspath(globalClasspath, false)
                }
                if (module.javaClassFolders.isEmpty()) {
                    module.setClasspath(globalClasspath, useForAnalysis)
                    // Only allow the .class files in the classpath to be bytecode analyzed once;
                    // for the remainder we only use it for type resolution
                    useForAnalysis = false
                }
            }
        }

        computeResourceVisibility()

        return ProjectMetadata(
            projects = sortedModules,
            sdk = sdk,
            jdk = jdk,
            baseline = baseline,
            globalLintChecks = globalLintChecks,
            lintChecks = lintChecks,
            externalAnnotations = externalAnnotations,
            cache = cache,
            moduleBaselines = baselines,
            mergedManifests = mergedManifests,
            incomplete = incomplete,
            jdkBootClasspath = jdkBootClasspath,
            platforms = if (android) Platform.ANDROID_SET else Platform.JDK_SET
        )
    }

    private fun handleDesugaring(element: Element): EnumSet<Desugaring>? {
        var desugaring: EnumSet<Desugaring>? = null

        if (VALUE_TRUE == element.getAttribute(ATTR_JAVA8_LIBS)) {
            desugaring = EnumSet.of(Desugaring.JAVA_8_LIBRARY)
        }

        val s = element.getAttribute(ATTR_DESUGAR)
        if (!s.isEmpty()) {
            for (option in s.split(",")) {
                var found = false
                for (v in Desugaring.values()) {
                    if (option.equals(other = v.name, ignoreCase = true)) {
                        if (desugaring == null) {
                            desugaring = EnumSet.of(v)
                        } else {
                            desugaring.add(v)
                        }
                        found = true
                        break
                    }
                }
                if (!found) {
                    // One of the built-in constants? Desugaring.FULL etc
                    try {
                        val fieldName = option.toUpperCase()
                        val cls = Desugaring::class.java

                        @Suppress("UNCHECKED_CAST")
                        val v =
                            cls.getField(fieldName).get(null) as? EnumSet<Desugaring> ?: continue
                        if (desugaring == null) {
                            desugaring = EnumSet.noneOf(Desugaring::class.java)
                        }
                        desugaring?.addAll(v)
                    } catch (ignore: Throwable) {
                    }
                }
            }
        }

        return desugaring
    }

    private fun computeResourceVisibility() {
        // TODO: We don't have dependency information from one AAR to another; we'll
        // need to assume that all of them are in a flat hierarchy
        for (module in modules.values) {
            val aarDeps = mutableListOf<ResourceVisibilityLookup>()
            for (dependencyName in dependencies.get(module)) {
                val visibility = visibility[dependencyName] ?: continue
                aarDeps.add(visibility)
            }

            if (aarDeps.isEmpty()) {
                continue
            } else {
                val visibilityLookup = if (aarDeps.size == 1) {
                    aarDeps[0]
                } else {
                    // Must create a composite
                    ResourceVisibilityLookup.create(aarDeps)
                }
                module.setResourceVisibility(visibilityLookup)
            }
        }
    }

    private fun parseModule(moduleElement: Element) {
        val name: String = moduleElement.getAttribute(ATTR_NAME)
        val library = moduleElement.getAttribute(ATTR_LIBRARY) == VALUE_TRUE
        val android = moduleElement.getAttribute(ATTR_ANDROID) != VALUE_FALSE
        val buildApi: String = moduleElement.getAttribute(ATTR_COMPILE_SDK_VERSION)
        val desugaring = handleDesugaring(moduleElement) ?: this.desugaring

        if (android) {
            this.android = true
        }

        val javaLanguageLevel = moduleElement.getAttribute(ATTR_JAVA_LEVEL).let { level ->
            if (level.isNotBlank()) {
                val languageLevel = LanguageLevel.parse(level) ?: run {
                    reportError("Invalid Java language level \"$level\"", moduleElement)
                    null
                }
                languageLevel
            } else {
                null
            }
        }

        val kotlinLanguageLevel = moduleElement.getAttribute(ATTR_KOTLIN_LEVEL).let { level ->
            if (level.isNotBlank()) {
                val languageLevel = LanguageVersion.fromVersionString(level) ?: run {
                    reportError("Invalid Kotlin language level \"$level\"", moduleElement)
                    null
                }
                if (languageLevel != null) {
                    LanguageVersionSettingsImpl(
                        languageLevel,
                        ApiVersion.createByLanguageVersion(languageLevel)
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }

        // Special case: if the module is a path (with an optional :suffix),
        // use this as the module directory, otherwise fall back to the default root
        name.indexOf(':', name.lastIndexOf(separatorChar))
        val dir =
            if (name.contains(separator) && name.indexOf(':', name.lastIndexOf(separator)) != -1) {
                File(name.substring(0, name.indexOf(':', name.lastIndexOf(separator))))
            } else {
                root
            }
        val module = ManualProject(client, dir, name, library, android)
        modules[name] = module

        val model = if (moduleElement.hasAttribute(ATTR_MODEL)) {
            LintModelSerialization.readModule(getFile(moduleElement, dir, ATTR_MODEL, false))
        } else {
            null
        }

        val sources = mutableListOf<File>()
        val generatedSources = mutableListOf<File>()
        val testSources = mutableListOf<File>()
        val resources = mutableListOf<File>()
        val manifests = mutableListOf<File>()
        val classes = mutableListOf<File>()
        val classpath = mutableListOf<File>()
        val lintChecks = mutableListOf<File>()
        var baseline: File? = null
        var mergedManifest: File? = null

        var child = getFirstSubTag(moduleElement)
        while (child != null) {
            when (child.tagName) {
                TAG_MANIFEST -> {
                    manifests.add(getFile(child, dir))
                }

                TAG_MERGED_MANIFEST -> {
                    mergedManifest = getFile(child, dir)
                }

                TAG_SRC -> {
                    val file = getFile(child, dir)
                    when (child.getAttribute(ATTR_GENERATED) == VALUE_TRUE) {
                        true -> generatedSources.add(file)
                        false -> when (child.getAttribute(ATTR_TEST) == VALUE_TRUE) {
                            false -> sources.add(file)
                            true -> testSources.add(file)
                        }
                    }
                }

                TAG_RESOURCE -> {
                    resources.add(getFile(child, dir))
                }

                TAG_CLASSES -> {
                    classes.add(getFile(child, dir))
                }

                TAG_CLASSPATH -> {
                    classpath.add(getFile(child, dir))
                }

                TAG_AAR -> {
                    // Specifying an <aar> dependency in the file is an implicit dependency
                    val aar = parseAar(child, dir)
                    aar?.let { dependencies.put(module, aar) }
                }

                TAG_JAR -> {
                    // Specifying a <jar> dependency in the file is an implicit dependency
                    val jar = parseJar(child, dir)
                    jar?.let { dependencies.put(module, jar) }
                }

                TAG_BASELINE -> {
                    baseline = getFile(child, dir)
                }

                TAG_LINT_CHECKS -> {
                    lintChecks.add(getFile(child, dir))
                }

                TAG_ANNOTATIONS -> {
                    externalAnnotations += getFile(child, this.root)
                }

                TAG_DEP -> {
                    val target = child.getAttribute(ATTR_MODULE)
                    if (target.isEmpty()) {
                        reportError("Invalid module dependency in ${module.name}", child)
                    }
                    dependencies.put(module, target)
                }

                TAG_AIDL, TAG_PROGUARD -> {
                    // Not currently checked by lint
                }

                else -> {
                    reportError("Unexpected tag ${child.tagName}", child)
                    return
                }
            }

            child = getNextTag(child)
        }

        // Compute source roots
        val sourceRoots = computeSourceRoots(sources)
        val testSourceRoots = computeUniqueSourceRoots("test", testSources, sourceRoots)
        val generatedSourceRoots =
            computeUniqueSourceRoots("generated", generatedSources, sourceRoots)

        val resourceRoots = mutableListOf<File>()
        if (resources.isNotEmpty()) {
            // Compute resource roots. Note that these directories are not allowed
            // to overlap.
            for (file in resources) {
                val typeFolder = file.parentFile ?: continue
                val res = typeFolder.parentFile ?: continue
                if (!resourceRoots.contains(res)) {
                    resourceRoots.add(res)
                }
            }
        }

        handleSrcJars(sources, resources, manifests, classes, sourceRoots)

        module.setManifests(manifests)
        module.setResources(resourceRoots, resources)
        module.setTestSources(testSourceRoots, testSources)
        module.setGeneratedSources(generatedSourceRoots, generatedSources)
        module.setSources(sourceRoots, sources)
        module.setClasspath(classes, true)
        module.setClasspath(classpath, false)
        module.desugaring = desugaring
        javaLanguageLevel?.let { module.javaLanguageLevel = it }
        kotlinLanguageLevel?.let { module.kotlinLanguageLevel = it }
        module.setCompileSdkVersion(buildApi)

        this.lintChecks[module] = lintChecks
        this.mergedManifests[module] = mergedManifest
        this.baselines[module] = baseline
        module.variant = model?.defaultVariant()

        client.registerProject(module.dir, module)
    }

    private fun handleSrcJars(
        sources: MutableList<File>,
        resources: MutableList<File>,
        manifests: MutableList<File>,
        classes: MutableList<File>,
        sourceRoots: MutableList<File>
    ) {
        // Finds any .srcjar files in the first parameter, and if so, removes it, and
        // then expands the contents (based on the file type) into all the individual lists --
        // sources, manifests, bytecode, etc:
        handleSrcJars(sources, sources, resources, manifests, classes, sourceRoots)
        handleSrcJars(resources, sources, resources, manifests, classes, sourceRoots)
        handleSrcJars(manifests, sources, resources, manifests, classes, sourceRoots)
        handleSrcJars(classes, sources, resources, manifests, classes, sourceRoots)
    }

    private fun handleSrcJars(
        list: MutableList<File>,
        sources: MutableList<File>,
        resources: MutableList<File>,
        manifests: MutableList<File>,
        classes: MutableList<File>,
        sourceRoots: MutableList<File>
    ) {
        val iterator = list.listIterator()
        if (iterator.hasNext()) {
            val file = iterator.next()
            if (file.path.endsWith(DOT_SRCJAR)) {
                iterator.remove()

                sourceRoots.add(file)

                // Expand into child content
                ZipFile(file).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val zipEntry = entries.nextElement()
                        if (zipEntry.isDirectory) {
                            continue
                        }
                        val path = file.path + URLUtil.JAR_SEPARATOR + zipEntry.name
                        val newFile = File(path)
                        if (path.endsWith(ANDROID_MANIFEST_XML)) {
                            manifests.add(newFile)
                        } else if (path.endsWith(DOT_XML)) {
                            resources.add(newFile)
                        } else if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
                            sources.add(newFile)
                        } else if (path.endsWith(DOT_CLASS)) {
                            classes.add(newFile)
                        }
                    }
                }
            }
        }
    }

    private fun parseAar(element: Element, dir: File): String? {
        val aarFile = getFile(element, dir)

        val moduleName = jarAarMap[aarFile]
        if (moduleName != null) {
            // This AAR is already a known module in the module map -- just reference it
            return moduleName
        }

        val name = aarFile.name

        // Find expanded AAR (and cache into cache dir if necessary
        val expanded = run {
            val expanded = getFile(element, dir, ATTR_EXTRACTED, false)
            if (expanded.path.isEmpty()) {
                // Expand into temp dir
                val cacheDir = if (cache != null) {
                    File(cache, "aars")
                } else {
                    client.getCacheDir("aars", true)
                }
                val target = File(cacheDir, name)
                if (!target.isDirectory) {
                    unpackZipFile(aarFile, target)
                }
                target
            } else {
                if (!expanded.isDirectory) {
                    reportError("Expanded AAR path $expanded is not a directory")
                }
                expanded
            }
        }

        // Create module wrapper
        val project = ManualProject(client, expanded, name, true, true)
        project.reportIssues = false
        val manifest = File(expanded, ANDROID_MANIFEST_XML)
        if (manifest.isFile) {
            project.setManifests(listOf(manifest))
        }
        val resources = File(expanded, FD_RES)
        if (resources.isDirectory) {
            project.setResources(listOf(resources), emptyList())
        }

        val jarList = mutableListOf<File>()
        val jarsDir = File(expanded, FD_JARS)
        if (jarsDir.isDirectory) {
            jarsDir.listFiles()?.let {
                jarList.addAll(it.filter { file -> file.name.endsWith(DOT_JAR) }.toList())
            }
        }
        val classesJar = File(expanded, "classes.jar")
        if (classesJar.isFile) {
            jarList.add(classesJar)
        }
        if (jarList.isNotEmpty()) {
            project.setClasspath(jarList, false)
        }

        jarAarMap[aarFile] = name
        modules[name] = project

        val publicResources = File(expanded, FN_PUBLIC_TXT)
        val allResources = File(expanded, FN_RESOURCE_TEXT)
        visibility[name] = ResourceVisibilityLookup.create(publicResources, allResources, name)

        return name
    }

    private fun parseJar(element: Element, dir: File): String? {
        val jarFile = getFile(element, dir)

        val moduleName = jarAarMap[jarFile]
        if (moduleName != null) {
            // This jar is already a known module in the module map -- just reference it
            return moduleName
        }

        val name = jarFile.name

        // Create module wrapper
        val project = ManualProject(client, jarFile, name, true, false)
        project.reportIssues = false
        project.setClasspath(listOf(jarFile), false)
        jarAarMap[jarFile] = name
        modules[name] = project
        return name
    }

    @Throws(ZipException::class, IOException::class)
    fun unpackZipFile(zip: File, dir: File) {
        ZipFile(zip).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val zipEntry = entries.nextElement()
                if (zipEntry.isDirectory) {
                    continue
                }
                val targetFile = File(dir, zipEntry.name)
                Files.createParentDirs(targetFile)
                Files.asByteSink(targetFile).openBufferedStream().use {
                    ByteStreams.copy(zipFile.getInputStream(zipEntry), it)
                }
            }
        }
    }

    private fun computeUniqueSourceRoots(
        type: String,
        typeSources: MutableList<File>,
        sourceRoots: MutableList<File>
    ): List<File> {
        when {
            typeSources.isEmpty() -> return emptyList()
            else -> {
                val typeSourceRoots = computeSourceRoots(typeSources)

                // We don't allow the test sources and product sources to overlap
                for (root in typeSourceRoots) {
                    if (sourceRoots.contains(root)) {
                        reportError(
                            "${type.usLocaleCapitalize()} sources cannot be in the same " +
                                    "source root as production files; " +
                                    "source root $root is also a test root"
                        )
                        break
                    }
                }

                typeSourceRoots.removeAll(sourceRoots)
                return typeSourceRoots
            }
        }
    }

    private fun computeSourceRoots(sources: List<File>): MutableList<File> {
        val sourceRoots = mutableListOf<File>()
        if (!sources.isEmpty()) {
            // Cache for each directory since computing root for a source file is
            // expensive
            val dirToRootCache = mutableMapOf<String, File>()
            for (file in sources) {
                val parent = file.parentFile ?: continue
                val found = dirToRootCache[parent.path]
                if (found != null) {
                    continue
                }

                // Find the source root for a file. There are several scenarios.
                // Let's say the original file path is "/a/b/c/d", and findRoot
                // returns "/a/b" - in that case, great, we'll use it.
                //
                // But let's say the file is in the default package; in that case
                // findRoot returns null. Let's say the file we passed in was
                // "src/Foo.java". In that case, we can just take its parent
                // file, "src", as the source root.
                // But what if the source file itself was just passed as "Foo.java" ?
                // In that case it is relative to the pwd, so we get the *absolute*
                // path of the file instead, and take its parent path.
                val root = findRoot(file) ?: file.parentFile ?: file.absoluteFile.parentFile
                ?: continue

                dirToRootCache[parent.path] = root

                if (!sourceRoots.contains(root)) {
                    sourceRoots.add(root)
                }
            }
        }
        return sourceRoots
    }

    /**
     * Given an element that is expected to have a "file" attribute (or "dir" or "jar"),
     * produces a full path to the file. If [attribute] is specified, only the specific
     * file attribute name is checked.
     */
    private fun getFile(
        element: Element,
        dir: File,
        attribute: String? = null,
        required: Boolean = false
    ): File {
        var path: String
        if (attribute != null) {
            path = element.getAttribute(attribute)
            if (path.isEmpty() && required) {
                reportError("Must specify $attribute= attribute", element)
            }
        } else {
            path = element.getAttribute(ATTR_FILE)
            if (path.isEmpty()) {
                path = element.getAttribute(ATTR_DIR)
                if (path.isEmpty()) {
                    path = element.getAttribute(ATTR_JAR)
                }
            }
        }
        if (path.isEmpty()) {
            if (required) {
                reportError("Must specify file/dir/jar on <${element.tagName}>")
            }
            return File("")
        }

        return getFile(path, element, dir)
    }

    private fun getFile(
        path: String,
        element: Element,
        dir: File
    ): File {
        var source = File(path)
        if (!source.isAbsolute) {
            if (!source.exists()) {
                source = File(dir, path)
                if (!source.exists()) {
                    source = File(root, path)
                }
            } else {
                // Relative path: make it absolute
                return source.absoluteFile
            }
        }

        if (!source.exists()) {
            val relativePath = if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
                dir.canonicalPath.replace(separator, "\\\\")
            else dir.canonicalPath
            reportError(
                "$path ${
                    if (!File(path).isAbsolute) "(relative to " +
                            relativePath + ") " else ""
                }does not exist", element
            )
        }
        return source
    }

    /**
     * If given a full path to a Java or Kotlin source file, produces the path to
     * the source root if possible.
     */
    private fun findRoot(file: File): File? {
        val path = file.path
        if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
            val pkg = findPackage(file) ?: return null
            val parent = file.parentFile ?: return null
            val packageStart = max(0, parent.path.length - pkg.length)
            if (!pathMatchesPackage(pkg, path, packageStart)) {
                val actual = if (path.startsWith(root.path)) {
                    val s = path.substring(root.path.length)
                    val end = max(0, s.length - pkg.length)
                    s.substring(end)
                } else {
                    path.substring(packageStart)
                }
                val expected = "$separator${pkg.replace('.', separatorChar)}$separator${file.name}"
                client.log(
                    Severity.INFORMATIONAL, null,
                    "The source file ${file.name} does not appear to be in the right project location; its package implies ...$expected but it was found in ...$actual"
                )
                return null
            }
            return File(path.substring(0, packageStart))
        }

        return null
    }

    private fun pathMatchesPackage(pkg: String, path: String, packageStart: Int): Boolean {
        var i = 0
        var j = packageStart
        while (i < pkg.length) {
            if (pkg[i] != path[j] && pkg[i] != '.') {
                return false
            }
            i++
            j++
        }

        return true
    }

    /** Finds the package of the given Java/Kotlin source file, if possible */
    private fun findPackage(file: File): String? {
        // Don't use LintClient.readFile; this will attempt to use VFS in some cases
        // (for example, when encountering Windows file line endings, in order to make
        // sure that lint's text offsets matches PSI's text offsets), but since this
        // is still early in the initialization sequence and we haven't set up the
        // IntelliJ environment yet, we're not ready. And for the purposes of this file
        // read, we don't actually care about line offsets at all.
        val source = file.readText()
        val matcher = PACKAGE_PATTERN.matcher(source)
        val foundPackage = matcher.find()
        return if (foundPackage) {
            matcher.group(1).trim { it <= ' ' }
        } else {
            null
        }
    }
}

/**
 * A special subclass of lint's [Project] class which can be manually configured
 * with custom source locations, custom library types, etc.
 */
private class ManualProject
constructor(
    client: LintClient,
    dir: File,
    name: String,
    library: Boolean,
    private var android: Boolean
) : Project(client, dir, dir) {

    var variant: LintModelVariant? = null

    init {
        setName(name)
        directLibraries = mutableListOf()
        this.library = library
        // We don't have this info yet; add support to the XML to specify it
        this.buildSdk = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
        this.mergeManifests = true
    }

    /** Adds the given project as a dependency from this project */
    fun addDirectDependency(project: ManualProject) {
        directLibraries.add(project)
    }

    override fun isAndroidProject(): Boolean = android

    override fun isGradleProject(): Boolean = false

    override fun toString(): String = "Project [name=$name]"

    override fun equals(other: Any?): Boolean {
        // Normally Project.equals checks directory equality, but we can't
        // do that here since we don't have guarantees that the directories
        // won't overlap (and furthermore we don't actually have the directory
        // locations of each module)
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val project = other as Project?
        return name == project!!.name
    }

    override fun hashCode(): Int = name.hashCode()

    fun setJavaLanguageLevel(level: LanguageLevel) {
        this.javaLanguageLevel = level
    }

    fun setKotlinLanguageLevel(level: LanguageVersionSettings) {
        this.kotlinLanguageLevel = level
    }

    /** Sets the given files as the manifests applicable for this module */
    fun setManifests(manifests: List<File>) {
        this.manifestFiles = manifests
        addFilteredFiles(manifests)
    }

    /** Sets the given resource files and their roots for this module */
    fun setResources(resourceRoots: List<File>, resources: List<File>) {
        this.resourceFolders = resourceRoots
        addFilteredFiles(resources)
    }

    /** Sets the given source files and their roots for this module */
    fun setSources(sourceRoots: List<File>, sources: List<File>) {
        this.javaSourceFolders = sourceRoots
        addFilteredFiles(sources)
    }

    /** Sets the given source files and their roots for this module */
    fun setTestSources(sourceRoots: List<File>, sources: List<File>) {
        this.testSourceFolders = sourceRoots
        addFilteredFiles(sources)
    }

    /** Sets the given generated source files and their roots for this module */
    fun setGeneratedSources(sourceRoots: List<File>, sources: List<File>) {
        this.generatedSourceFolders = sourceRoots
        addFilteredFiles(sources)
    }

    /**
     * Adds the given files to the set of filtered files for this project. With
     * a filter applied, lint won't look at all sources in for example the source
     * or resource roots, it will limit itself to these specific files.
     */
    private fun addFilteredFiles(sources: List<File>) {
        if (!sources.isEmpty()) {
            if (files == null) {
                files = mutableListOf()
            }
            files.addAll(sources)
        }
    }

    /** Sets the global class path for this module */
    fun setClasspath(allClasses: List<File>, useForAnalysis: Boolean) =
        if (useForAnalysis) {
            this.javaClassFolders = allClasses
        } else {
            this.javaLibraries = allClasses
        }

    fun setCompileSdkVersion(buildApi: String) {
        if (!buildApi.isEmpty()) {
            buildTargetHash = if (Character.isDigit(buildApi[0]))
                PLATFORM_HASH_PREFIX + buildApi
            else buildApi
            val version = AndroidTargetHash.getPlatformVersion(buildApi)
            if (version != null) {
                buildSdk = version.featureLevel
            } else {
                client.log(
                    Severity.WARNING, null,
                    "Unexpected build target format: %1\$s", buildApi
                )
            }
        }
    }

    fun setDesugaring(desugaring: Set<Desugaring>?) {
        this.desugaring = desugaring
    }

    private var resourceVisibility: ResourceVisibilityLookup? = null

    fun setResourceVisibility(resourceVisibility: ResourceVisibilityLookup?) {
        this.resourceVisibility = resourceVisibility
    }

    override fun getResourceVisibility(): ResourceVisibilityLookup {
        return resourceVisibility ?: super.getResourceVisibility()
    }

    override fun getBuildVariant(): LintModelVariant? = variant
}
