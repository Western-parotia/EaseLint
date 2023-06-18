/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.getBaseName
import com.android.tools.lint.detector.api.isXmlFile
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.PathVariables
import com.android.utils.iterator
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.EnumMap

/**
 * Resource repository implementation for use by lint.
 *
 * It is backed by a map constructed from one of:
 * - the same DOM XML parse trees we're sharing with lint's resource
 *   visitor
 * - deserialized strings
 *
 * For lint internal use only. It does not support all operations that
 * the general resource repository does, such as namespaces or public
 * resources, only those relevant to lint analysis.
 */
open class LintResourceRepository constructor(
    private val project: Project?,
    internal val typeToMap: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
    private val namespace: ResourceNamespace,
    val libraryName: String?
) : AbstractResourceRepository(), SingleNamespaceResourceRepository {

    override fun getResourcesInternal(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem> {
        // We could enforce namespace == this.namespace here, but
        // right now there's a mixture of RES_AUTO and so we'd
        // need to check all the combos
        if (namespace != this.namespace) {
            return ImmutableListMultimap.of()
        }
        return typeToMap[resourceType] ?: ImmutableListMultimap.of()
    }

    override fun getNamespaces(): Set<ResourceNamespace> {
        return setOf(namespace)
    }

    override fun getNamespace(): ResourceNamespace {
        return namespace
    }

    override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
        return listOf(this)
    }

    fun serialize(pathVariables: PathVariables, root: File? = null, sort: Boolean = false): String {
        return LintResourcePersistence.serialize(this, pathVariables, root, sort)
    }

    override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
        if (visitor.shouldVisitNamespace(namespace)) {
            if (acceptByResources(typeToMap, visitor) == ResourceVisitor.VisitResult.ABORT) {
                return ResourceVisitor.VisitResult.ABORT
            }
        }
        return ResourceVisitor.VisitResult.CONTINUE
    }

    // Unsupported: Some resource repository operations are not supported from lint

    override fun getPackageName(): String? {
        unsupported()
    }

    override fun getPublicResources(
        namespace: ResourceNamespace,
        type: ResourceType
    ): MutableCollection<ResourceItem> {
        unsupported()
    }

    override fun toString(): String {
        project?.name?.let { return "Resources for $it without dependencies" }
            ?: return super.toString()
    }

    internal class MergedResourceRepository(
        private val project: Project,
        private val repositories: List<LintResourceRepository>,
        private val namespace: ResourceNamespace
    ) : AbstractResourceRepository() {
        override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
            for (type in ResourceType.values()) {
                if (!visitor.shouldVisitResourceType(type)) {
                    continue
                }
                // We don't just call getResourcesInternal(namespace, type) and iterate
                // on it here; we call it on each of the leaves since there's no
                // merging necessary, so we might as well avoid the map merge.
                for (repository in repositories) {
                    val map = repository.getResourcesInternal(namespace, type)
                    for ((_: String, item: ResourceItem) in map.entries()) {
                        if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
                            return ResourceVisitor.VisitResult.ABORT
                        }
                    }
                }
            }
            return ResourceVisitor.VisitResult.CONTINUE
        }

        override fun getPublicResources(
            namespace: ResourceNamespace,
            type: ResourceType
        ): MutableCollection<ResourceItem> {
            unsupported()
        }

        override fun getNamespaces(): MutableSet<ResourceNamespace> {
            unsupported()
        }

        override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
            return repositories
        }

        private val cache: EnumMap<ResourceType, ListMultimap<String, ResourceItem>> =
            EnumMap(ResourceType::class.java)

        override fun getResourcesInternal(
            namespace: ResourceNamespace,
            type: ResourceType
        ): ListMultimap<String, ResourceItem> {
            return cache[type] ?: run {
                val keyCount = 100
                val valuesPerKey = 5
                val map = ArrayListMultimap.create<String, ResourceItem>(keyCount, valuesPerKey)
                repositories.forEach { repository ->
                    val repoMap: ListMultimap<String, ResourceItem>? = repository.typeToMap[type]
                    repoMap?.let { map.putAll(it) }
                }

                cache[type] = map
                map
            }
        }

        override fun getResources(
            namespace: ResourceNamespace,
            resourceType: ResourceType,
            resourceName: String
        ): MutableList<ResourceItem> {
            // Optimized because most of the time, only
            // hasResources(type,name) and getResources(type,name) are called,
            // and for a specific name, only once, for a pretty small subset of
            // the overall resources, so we don't need to compute a merged map;
            // we'll never need the caching, and we don't need to compute merged
            // lists for the vast majority of resources that will never be looked
            // at.
            val list = ArrayList<ResourceItem>()
            repositories.forEach { repository ->
                list.addAll(repository.getResources(namespace, resourceType, resourceName))
            }
            return list
        }

        override fun hasResources(
            namespace: ResourceNamespace,
            resourceType: ResourceType,
            resourceName: String
        ): Boolean {
            // Optimized because most of the time, only
            // hasResources(type,name) and getResources(type,name) are called,
            // and for a specific name, only once, for a pretty small subset of
            // the overall resources, so we don't need to compute a merged map.
            // by directly just looking these up we can save effort. Also,
            // since we don't need to return the list we can stop searching
            // the repositories as soon as it's found.
            for (repository in repositories) {
                if (repository.hasResources(namespace, resourceType, resourceName)) {
                    return true
                }
            }
            return false
        }

        override fun hasResources(
            namespace: ResourceNamespace,
            resourceType: ResourceType
        ): Boolean {
            // See comment in hasResources(namespace,type)
            for (repository in repositories) {
                if (repository.hasResources(namespace, resourceType)) {
                    return true
                }
            }
            return false
        }

        override fun toString(): String {
            return "Resources for ${project.name} with dependencies"
        }
    }

    /** A repository wrapping an AAR backed library. */
    class LintLibraryRepository(
        private val client: LintCliClient,
        private val library: LintModelAndroidLibrary,
        namespace: ResourceNamespace
    ) : LintResourceRepository(
        null, EnumMap(ResourceType::class.java), namespace,
        library.resolvedCoordinates.toString()
    ) {
        // Just passing in an empty mutable map to the super which we'll populate
        // lazily in getResourcesInternal

        init {
            val listFiles: Array<File>? = library.resFolder.listFiles()
            listFiles?.filter(skipHidden)?.sorted()?.forEach { folder ->
                scanTypeFolder(client, folder, libraryName, namespace, typeToMap)
            }
        }

        override fun getResourcesInternal(
            namespace: ResourceNamespace,
            resourceType: ResourceType
        ): ListMultimap<String, ResourceItem> {
            // Populate lazily since in libraries there tend to be a LOT
            // of resources
            return typeToMap[resourceType]
                ?: run {
                    val folderTypes = FolderTypeRelationship.getRelatedFolders(resourceType)
                    val folders = library.resFolder.listFiles { folder ->
                        val folderType = ResourceFolderType.getFolderType(folder.name)
                        folderType != null && folderTypes.contains(folderType)
                    } ?: emptyArray()

                    val libraryName = library.resolvedCoordinates.toString()
                    for (folder in folders.sorted()) { // sorted: offer stable order in resource lists
                        // TODO: For @id, cheat and just look at the R.txt file! We don't need
                        // to parse through ALL The resources for this
                        scanTypeFolder(client, folder, libraryName, namespace, typeToMap)
                    }

                    // Make sure we record not just this specific type but any types for any
                    // folders we scanned. E.g. if the map request came in for a @dimen, we
                    // ended up scanning all the values/ folders, which also populates for example
                    // the @string entries found there -- make sure we record that we've done
                    // scanning these now (since if no strings were found, the entry would still be
                    // null, and we'd try scanning again.)
                    for (scannedFolderTypes in folderTypes) {
                        val scannedTypes =
                            FolderTypeRelationship.getRelatedResourceTypes(scannedFolderTypes)
                        for (scannedType in scannedTypes) {
                            if (typeToMap[scannedType] == null) {
                                typeToMap[scannedType] = ArrayListMultimap.create()
                            }
                        }
                    }

                    typeToMap[resourceType]!!
                }
        }

        // If we get the .aar file (need to add to model (LintModelLibrary.kt) and pass in
        // from ExternalLintModelArtifactHandler) I can read in just the resource id's
        // by simply reading the R.txt file within the .aar file. I could have a lazy-loading
        // resource value which lazily reads the stuff out of the res folders!

        // Note that what I need is already available on disk via library.getFolder/res --
        // I could simply reuse the current machinery. No need to get all fancy reading
        // binary resources. Just make sure it's cached.

        override fun toString(): String {
            return "Resources for ${library.resolvedCoordinates}"
        }
    }

    companion object {
        private val skipHidden: (File) -> Boolean = { file -> !file.name.startsWith('.') }

        /**
         * An empty repository which is only used if you request the
         * resource repository for a non-Android project or when the
         * compilation target can't be found etc.
         */
        object EmptyRepository : LintResourceRepository(
            null, EnumMap(ResourceType::class.java), ResourceNamespace.RES_AUTO,
            null
        ) {
            override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
                return ResourceVisitor.VisitResult.ABORT
            }

            override fun getNamespaces(): Set<ResourceNamespace> {
                return emptySet()
            }

            override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
                return emptyList()
            }

            override fun getResourcesInternal(
                namespace: ResourceNamespace,
                resourceType: ResourceType
            ): ListMultimap<String, ResourceItem> {
                return ImmutableListMultimap.of()
            }

            override fun toString(): String {
                return this::class.java.simpleName
            }
        }

        // Caching keys on LintClient to reuse framework and library resources
        private const val KEY_LIBRARY_CACHE = "libraryCache"
        private const val KEY_FRAMEWORK_CACHE = "frameworkCache"

        fun clearCaches(client: LintCliClient, project: Project) {
            for (s in ResourceRepositoryScope.values()) {
                project.putClientProperty(s, null)
            }
            client.putClientProperty(KEY_LIBRARY_CACHE, null)
            client.putClientProperty(KEY_FRAMEWORK_CACHE, null)
        }

        /**
         * Returns the resource repository for the given [project] with
         * the given scope.
         */
        fun get(
            client: LintCliClient,
            project: Project,
            scope: ResourceRepositoryScope
        ): ResourceRepository {
            // Repository with dependencies
            return project.getClientProperty<ResourceRepository>(scope)
                ?: createRepository(client, project, scope)
                    .also { project.putClientProperty(scope, it) }
        }

        private fun createRepository(
            client: LintCliClient,
            project: Project,
            scope: ResourceRepositoryScope
        ): ResourceRepository {

            if (scope == ResourceRepositoryScope.ANDROID) {
                project.buildTarget?.let { target ->
                    return getForFramework(client, target)
                }
                return EmptyRepository
            }

            val projectRepository = getForProjectOnly(client, project)
            if (scope == ResourceRepositoryScope.PROJECT_ONLY) {
                return projectRepository
            }

            val repositories = mutableListOf<LintResourceRepository>()
            repositories += projectRepository

            if (scope.includesDependencies()) {
                // All dependencies, not just libraries -- we want the leaf repository
                // list to only include leaves, not nested ones
                project.allLibraries
                    .filter { !it.isExternalLibrary }
                    .map { getForProjectOnly(client, it) }
                    .forEach { repositories += it }

                // If we're only computing local dependencies, not library and
                // framework (this is common) don't bother creating a composite
                // resource repository if there are no dependencies
                if (scope == ResourceRepositoryScope.LOCAL_DEPENDENCIES &&
                    repositories.size == 1
                ) { // 1: the project
                    return projectRepository
                }
            }

            if (scope.includesLibraries()) {
                val libs: List<LintModelAndroidLibrary> =
                    project.buildVariant?.mainArtifact?.dependencies?.getAll()
                        ?.filterIsInstance<LintModelAndroidLibrary>()
                        ?.toList()
                        ?: project.buildLibraryModel?.let { listOf(it) }
                        ?: emptyList()

                // No libraries? Just share the same repository instance as
                // local only
                if (scope == ResourceRepositoryScope.ALL_DEPENDENCIES && libs.isEmpty()) {
                    return get(client, project, ResourceRepositoryScope.LOCAL_DEPENDENCIES)
                }

                libs.asSequence()
                    .map { getForLibrary(client, it) }
                    .filter { it !== EmptyRepository }
                    .forEach { repositories += it }
            }

            return MergedResourceRepository(project, repositories, ResourceNamespace.TODO())
        }

        /** Whether we've already flagged a persisence problem */
        private var warned = false

        private fun getOrCreateRepository(
            serializedFile: File,
            client: LintClient,
            root: File?,
            project: Project?,
            factory: () -> LintResourceRepository
        ): LintResourceRepository {
            // For leaf repositories, try to load from storage
            if (serializedFile.isFile) {
                val serialized = serializedFile.readText()
                try {
                    return LintResourcePersistence.deserialize(
                        serialized,
                        client.pathVariables,
                        root,
                        project
                    )
                } catch (e: Throwable) {
                    // Some sort of problem deserializing the lint resource repository. Try to gracefully recover
                    // and also generate a warning for this. See issues b/204437054 and b/204437195
                    if (!warned) {
                        warned = true
                        val sb = StringBuilder()
                        sb.append(
                            "Failed to deserialize cached resource repository.\n" +
                                    "This is an internal lint error which typically means that lint is being passed a\n" +
                                    "serialized file that was created with an older version of lint or with a different\n" +
                                    "set of path variable names. Attempting to gracefully recover.\n"
                        )

                        sb.append("The serialized content was:\n")
                        sb.append(serialized)
                        sb.append("\nStack: `")
                        sb.append(e.toString())
                        sb.append("`:")
                        LintDriver.appendStackTraceSummary(e, sb)
                        LintClient.report(
                            client = client,
                            issue = IssueRegistry.LINT_ERROR,
                            message = sb.toString(),
                            file = serializedFile,
                            project = project
                        )
                    }

                    // Continue to gracefully recover: this means we'll unnecessarily recreate the repository
                    // but this is better than a hard failure. We still flag it since this is not an ideal
                    // situation.
                }
            }

            // Must construct from files now and cache for future use
            val repository = factory()

            // Write for future usage
            serializedFile.parentFile?.mkdirs()
            val serialized = LintResourcePersistence.serialize(repository, client.pathVariables)
            serializedFile.writeText(serialized)
            return repository
        }

        /**
         * Returns the resource repository for the given [project],
         * *not* including dependencies.
         */
        private fun getForProjectOnly(
            client: LintCliClient,
            project: Project
        ): LintResourceRepository {
            return project.getClientProperty<LintResourceRepository>(ResourceRepositoryScope.PROJECT_ONLY)
                ?: getOrCreateRepository(
                    client.getSerializationFile(project, XmlFileType.RESOURCE_REPOSITORY),
                    client, project.dir, project
                ) {
                    createFromFolder(client, project, ResourceNamespace.TODO())
                }.also { project.putClientProperty(ResourceRepositoryScope.PROJECT_ONLY, it) }
        }

        private fun getLibraryResourceCacheFile(
            client: LintCliClient,
            library: LintModelAndroidLibrary
        ): File {
            return File(
                client.getCacheDir("library-resources-v1", true),
                // avoid ":" in filenames for Windows
                library.identifier.replace(':', '_')
            )
        }

        private fun getFrameworkResourceCacheFile(
            client: LintCliClient,
            hash: String
        ): File {
            return File(
                client.getCacheDir("framework-resources-v1", true),
                hash
            )
        }

        /**
         * Returns a repository for a library consumed by the given
         * project.
         */
        private fun getForLibrary(
            client: LintCliClient,
            library: LintModelAndroidLibrary
        ): LintResourceRepository {
            val cache =
                client.getClientProperty<MutableMap<LintModelAndroidLibrary, LintResourceRepository>>(
                    KEY_LIBRARY_CACHE
                )
                    ?: HashMap<LintModelAndroidLibrary, LintResourceRepository>().also {
                        client.putClientProperty(KEY_LIBRARY_CACHE, it)
                    }
            return getForLibrary(client, library, cache)
        }

        private fun getForFramework(
            client: LintCliClient,
            target: IAndroidTarget
        ): LintResourceRepository {
            val res = target.getPath(IAndroidTarget.RESOURCES).toFile()
            return getForFramework(target.hashString(), res, client)
        }

        /**
         * Returns a repository for a given framework resource folder
         * which corresponds to an [IAndroidTarget] res folder.
         */
        private fun getForFramework(
            hash: String,
            res: File,
            client: LintCliClient
        ): LintResourceRepository {
            // Cache from target hash, such as "android-12" or "android-S"
            val cache =
                client.getClientProperty<MutableMap<String, LintResourceRepository>>(
                    KEY_FRAMEWORK_CACHE
                )
                    ?: HashMap<String, LintResourceRepository>().also {
                        client.putClientProperty(KEY_FRAMEWORK_CACHE, it)
                    }
            return getForFramework(client, hash, res, cache)
        }

        /**
         * Returns a resource repository for an Android SDK resource
         * folder (and the SDK always has exactly one; there are no
         * source sets)
         */
        private fun getForFramework(
            client: LintCliClient,
            hash: String,
            res: File,
            cache: MutableMap<String, LintResourceRepository>
        ): LintResourceRepository {
            return cache[hash]
                ?: getOrCreateRepository(
                    getFrameworkResourceCacheFile(client, hash),
                    client,
                    null,
                    null
                ) {
                    createFromFolder(client, sequenceOf(res), null, null, ResourceNamespace.ANDROID)
                }.also { cache[hash] = it }
        }

        /** Returns a resource repository for an AAR library. */
        private fun getForLibrary(
            client: LintCliClient,
            library: LintModelAndroidLibrary,
            cache: MutableMap<LintModelAndroidLibrary, LintResourceRepository>
        ): LintResourceRepository {
            return cache[library]
            // TODO: Handle relative paths over in AAR folders under ~/.gradle/
                ?: getOrCreateRepository(
                    getLibraryResourceCacheFile(client, library),
                    client,
                    null,
                    null
                ) {
                    LintLibraryRepository(client, library, ResourceNamespace.TODO())
                }.also { cache[library] = it }
        }

        private fun createFromFolder(
            client: LintClient,
            project: Project,
            namespace: ResourceNamespace
        ): LintResourceRepository {
            return createFromFolder(
                client,
                project.resourceFolders.asSequence() + project.generatedResourceFolders,
                project,
                null,
                namespace
            )
        }

        fun createFromFolder(
            client: LintClient,
            resourceFolders: Sequence<File>,
            project: Project?,
            libraryName: String?,
            namespace: ResourceNamespace
        ): LintResourceRepository {
            val map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>> =
                EnumMap(ResourceType::class.java)

            for (resourceFolder in resourceFolders) {
                val folders = resourceFolder.listFiles() ?: continue
                for (folder in folders.filter(skipHidden)
                    .sorted()) { // sorted: offer stable order in resource lists
                    scanTypeFolder(client, folder, libraryName, namespace, map)
                }
            }

            return LintResourceRepository(project, map, namespace, libraryName)
        }

        private fun scanTypeFolder(
            client: LintClient,
            folder: File,
            libraryName: String?,
            namespace: ResourceNamespace,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>
        ) {
            if (!folder.isDirectory) return

            val folderName = folder.name
            val folderType = ResourceFolderType.getFolderType(folderName) ?: return
            val config = FolderConfiguration.getConfigForFolder(folderName) ?: return
            config.normalizeByAddingImpliedVersionQualifier()
            val files = folder.listFiles()?.filter(skipHidden)?.sorted() ?: return
            if (folderType == ResourceFolderType.VALUES) {
                for (file in files) {
                    processValues(client, namespace, map, config, libraryName, file)
                }
            } else {
                for (file in files) {
                    processFiles(client, map, folderType, config, libraryName, namespace, file)
                }
            }
        }

        private fun processValues(
            client: LintClient,
            namespace: ResourceNamespace,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            config: FolderConfiguration,
            libraryName: String?,
            file: File
        ) {
            if (!isXmlFile(file)) {
                return
            }

            // See ResourceMergerItem.doParseXmlToResourceValue:
            val resources = client.getXmlDocument(file)?.documentElement ?: return
            val items = ArrayList<LintResourceItem>()
            for (element in resources) {
                val type = ResourceType.fromXmlTag(element) ?: continue
                if (type.isSynthetic) continue // e.g. boolean
                val name = element.getAttribute(ATTR_NAME)
                if (name.isEmpty()) {
                    if (type == ResourceType.PUBLIC) {
                        addItems(
                            file,
                            "",
                            type,
                            element,
                            config,
                            libraryName,
                            namespace,
                            map,
                            items
                        )
                    }
                    // erroneous source
                    continue
                }
                addItems(file, name, type, element, config, libraryName, namespace, map, items)
            }

            // Side effect: sets sourceFile on all the items
            ResourceFile(file, items as List<ResourceMergerItem>, config)
        }

        private fun addItems(
            file: File,
            name: String,
            type: ResourceType,
            element: Element,
            config: FolderConfiguration,
            libraryName: String?,
            namespace: ResourceNamespace,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            added: MutableList<LintResourceItem>?
        ) {
            // See ValueResourceParser2
            val item = LintResourceItem(
                file, name, namespace, type, element, libraryName != null,
                libraryName, config, false
            )
            recordItem(map, type, name, item)
            added?.add(item)

            if (type == ResourceType.STYLEABLE) {
                // Need to also create ATTR items for its children
                addStyleableItems(file, element, namespace, map, config, added)
            }
        }

        private fun addStyleableItems(
            file: File,
            styleableNode: Element,
            namespace: ResourceNamespace,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            config: FolderConfiguration,
            added: MutableList<LintResourceItem>?
        ) {
            assert(styleableNode.nodeName == SdkConstants.TAG_DECLARE_STYLEABLE)
            for (element in styleableNode) {
                val name = element.getAttribute(ATTR_NAME)
                if (name.isEmpty()) continue
                val type = ResourceType.fromXmlTag(element) ?: continue
                assert(type == ResourceType.ATTR)

                val attr = LintResourceItem(
                    file, name, namespace, type, element,
                    isFromDependency = false, libraryName = null, config = config, fileBased = false
                )
                recordItem(map, type, name, attr)
                added?.add(attr)
            }
        }

        private fun addIds(
            element: Element,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            folderType: ResourceFolderType,
            config: FolderConfiguration,
            file: File,
            namespace: ResourceNamespace,
            added: MutableList<ResourceMergerItem>
        ) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i)
                val value = attribute.nodeValue
                if (value.startsWith(NEW_ID_PREFIX)) {
                    val name = value.substring(NEW_ID_PREFIX.length)
                    val item = LintResourceItem(
                        file, name, namespace, ResourceType.ID,
                        value = null,
                        isFromDependency = false,
                        libraryName = null,
                        config = config,
                        fileBased = true
                    )
                    recordItem(map, ResourceType.ID, name, item)
                    added.add(item)
                }
            }

            for (child in element) {
                addIds(child, map, folderType, config, file, namespace, added)
            }
        }

        private fun processFiles(
            client: LintClient,
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            folderType: ResourceFolderType,
            config: FolderConfiguration,
            libraryName: String?,
            namespace: ResourceNamespace,
            file: File
        ) {
            if (isXmlFile(file) && FolderTypeRelationship.isIdGeneratingFolderType(folderType)) {
                client.getXmlDocument(file)?.documentElement?.let {
                    // Search for lazily creates id entries (@+id as a value for any attribute,
                    // not just android:id) in file types which supports it, such as layouts.
                    // TODO: As an optimization, maybe only do this if a quick document
                    // string search for @+id/ shows there are occurrences in the document.
                    val items = mutableListOf<ResourceMergerItem>()
                    addIds(it, map, folderType, config, file, namespace, items)
                    if (items.isNotEmpty()) {
                        // Side effect: sets item source file
                        ResourceFile(file, items, config)
                    }
                }
            }

            val name = getBaseName(file.name)
            val type = FolderTypeRelationship.getNonIdRelatedResourceType(folderType)
            val item = LintResourceItem(
                file, name, namespace, type,
                value = null,
                isFromDependency = libraryName != null,
                libraryName = libraryName,
                config = config,
                fileBased = true
            )
            recordItem(map, type, name, item)
            // Side effect: sets item source file
            ResourceFile(file, item, config)
        }

        internal fun recordItem(
            map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>>,
            type: ResourceType,
            name: String,
            item: ResourceItem
        ) {
            val typeMap = map[type] ?: run {
                val newMap: ListMultimap<String, ResourceItem> =
                    ArrayListMultimap.create() // TODO: Default size
                map[type] = newMap
                newMap
            }
            typeMap.put(name, item)
        }
    }
}

internal class LintResourceItem(
    private val sourceFile: File,
    name: String,
    namespace: ResourceNamespace,
    type: ResourceType,
    value: Node?,
    isFromDependency: Boolean?,
    libraryName: String?,
    private val config: FolderConfiguration,
    private val fileBased: Boolean
) : ResourceMergerItem(name, namespace, type, value, isFromDependency, libraryName) {

    override fun getConfiguration(): FolderConfiguration {
        return config
    }

    override fun getFile(): File {
        return sourceFile
    }

    override fun getSource(): PathString {
        return PathString(sourceFile)
    }

    override fun isFileBased(): Boolean {
        return fileBased
    }

    override fun toString(): String {
        val path = file.path
        val parentPath: String? = file.parentFile?.parentFile?.path
        return if (parentPath != null) {
            "${this::class.java.simpleName}(${path.substring(parentPath.length + 1)})"
        } else {
            super.toString()
        }
    }
}

private fun unsupported(): Nothing =
    error("This resource repository operation not supported in lint")
