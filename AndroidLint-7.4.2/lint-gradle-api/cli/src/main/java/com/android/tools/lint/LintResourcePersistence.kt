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

import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.ArrayResourceValueImpl
import com.android.ide.common.rendering.api.AttrResourceValueImpl
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.DensityBasedResourceValue
import com.android.ide.common.rendering.api.DensityBasedResourceValueImpl
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.PluralsResourceValueImpl
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.rendering.api.StyleableResourceValueImpl
import com.android.ide.common.rendering.api.TextResourceValueImpl
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.PathVariables
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ListMultimap
import java.io.File
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min

/**
 * Persists a [LintResourceRepository] (and later reconstitutes it),
 * intended for caching of resources for projects, libraries and
 * frameworks.
 *
 * This is temporary; the plan is to extract
 * tools/adt/idea/resources-base code into tools/base and use that
 * binary format directly.
 */
object LintResourcePersistence {
    /**
     * Serializes the lint resource repository; can be deserialized with
     * [deserialize]. The [pathVariables] help write relative paths. If
     * [sort] is true, elements will be sorted by name; this is used in
     * tests to ensure stable output.
     */
    fun serialize(
        repository: LintResourceRepository,
        pathVariables: PathVariables,
        root: File?,
        sort: Boolean = false
    ): String {
        val typeToMap = repository.typeToMap
        if (typeToMap.isEmpty()) {
            return ""
        }

        val namespace = repository.namespace
        val framework = namespace == ResourceNamespace.ANDROID
        val stringBuilder = StringBuilder(if (framework) 20000000 else 1000)
        val writer = SerializationWriter(stringBuilder)
        writer.write(namespace.xmlNamespaceUri)
        writer.write(';')
        repository.libraryName?.let {
            writer.write(it)
        }
        writer.write(';')

        val fileMap: BiMap<PathString, Int> = HashBiMap.create(if (framework) 11000 else 100)
        var fileCount = 0
        for (multimap in typeToMap.values) {
            for (item in multimap.values()) {
                val source = item.source
                fileMap[source] ?: run {
                    fileMap[source] = fileCount++
                }
            }
        }

        val rootPath = root?.path
        val indexToFile = fileMap.inverse()
        for (i in 0 until fileCount) {
            val source = indexToFile[i] ?: continue
            writer.writePath(pathVariables, rootPath, source.rawPath)
            writer.write(',')
        }

        val entries = if (sort) {
            typeToMap.entries.sortedWith(compareBy { it.key })
        } else {
            typeToMap.entries
        }

        for ((type, map) in entries) {
            if (map.isEmpty) {
                continue
            }
            writer.write('+')
            writer.write(type.getName())
            writer.write(':')

            val values = if (sort) {
                map.values().sortedWith(compareBy { it?.name })
            } else {
                map.values()
            }

            for (item in values) {
                writer.escape(item.name)
                writer.write(',')
                writer.write(fileMap[item.source].toString())
                writer.write(',')
                if (item.isFileBased) { // This could be stored in the file list instead
                    writer.write('F')
                } else {
                    writer.write('V')
                }
                item.resourceValue?.let { resourceValue ->
                    when {
                        item.isFileBased -> {}
                        item.type == ResourceType.ARRAY && resourceValue is ArrayResourceValue -> {
                            for (i in 0 until resourceValue.elementCount) {
                                writer.escape(resourceValue.getElement(i))
                                writer.write(',')
                            }
                        }

                        item.type == ResourceType.PLURALS && resourceValue is PluralsResourceValue -> {
                            for (i in 0 until resourceValue.pluralsCount) {
                                writer.write(resourceValue.getQuantity(i))
                                writer.write(':')
                                writer.escape(resourceValue.getValue(i))
                                writer.write(',')
                            }
                        }

                        item.type == ResourceType.STYLE && resourceValue is StyleResourceValue -> {
                            val parentStyleName = resourceValue.parentStyleName
                            when {
                                // Need to distinguish between empty (no parent) and null
                                // (can inherit from implied parent, e.g. Foo.Bar will
                                // inherit from "Foo" is parent is not set, but
                                // won't if parent=""
                                parentStyleName == null -> writer.write("N")
                                parentStyleName.isEmpty() -> writer.write("E")
                                else -> writer.write("D").escape(parentStyleName).write(',')
                            }
                            for (styleItem in resourceValue.definedItems) {
                                writer.escape(styleItem.attrName)
                                writer.write(':')
                                // or null?
                                writer.escape(styleItem.value ?: "")
                                writer.write(',')
                            }
                        }

                        item.type == ResourceType.ATTR && resourceValue is AttrResourceValueImpl -> {
                            // Descriptions, group names etc are only supported for the framework
                            if (resourceValue.formats.isNotEmpty()) {
                                writer.write(resourceValue.formats.joinToString("|") { it.getName() })
                            }
                            writer.write(':')
                            for ((key, value) in resourceValue.attributeValues) {
                                writer.escape(key)
                                writer.write(':')
                                // or null?
                                writer.escape(value?.toString() ?: "")
                                writer.write(',')
                            }
                        }

                        item.type == ResourceType.STYLEABLE && resourceValue is StyleableResourceValue -> {
                            for (attribute in resourceValue.allAttributes) {
                                writer.write('-').escape(attribute.name).write(':')
                                if (attribute.formats.isNotEmpty()) {
                                    writer.write(attribute.formats.joinToString("|") { it.getName() })
                                }
                                writer.write(':')
                                // Descriptions, group names etc are only supported for the framework
                                for ((key, value) in attribute.attributeValues) {
                                    writer.escape(key)
                                    writer.write(':')
                                    // or null?
                                    writer.escape(value?.toString() ?: "")
                                    writer.write(',')
                                }
                            }
                        }

                        DensityBasedResourceValue.isDensityBasedResourceType(type) &&
                                resourceValue is DensityBasedResourceValue -> {
                            val density = resourceValue.resourceDensity.resourceValue
                            writer.write(density)
                        }

                        else -> {
                            resourceValue.value?.let {
                                writer.write('\"')
                                writer.escape(it)
                                writer.write('\"')
                                val rawSource: String? = item.resourceValue?.rawXmlValue
                                if (rawSource != null && it != rawSource) {
                                    writer.escape(rawSource)
                                }
                            }
                        }
                    }
                }
                writer.write(';')
            }
        }

        return stringBuilder.toString()
    }

    /**
     * Writes characters and strings into a string builder,
     * escaping characters which allows later usages of the
     * [DeserializationReader] to pick out substrings while still
     * allowing all kinds of characters to be used in the various string
     * fragments.
     */
    private class SerializationWriter(private val sb: StringBuilder) {
        fun write(char: Char): SerializationWriter {
            sb.append(char)
            return this
        }

        fun write(string: String): SerializationWriter {
            sb.append(string)
            return this
        }

        /**
         * Given a string, replaces all occurrences of the various
         * reserved separator characters (+:;,\) with a preceding \ to
         * indicate that this is a literal occurrence of this character.
         */
        fun escape(s: String): SerializationWriter {
            val n = s.length
            for (i in 0 until n) {
                when (val c = s[i]) {
                    '\\', '+', ',', ';', ':', '"' -> write('\\').write(c)
                    else -> write(c)
                }
            }
            return this
        }

        /**
         * Writes the given path, stripping out the path prefix if under
         * the given (optional) [rootPath]
         */
        fun writePath(
            pathVariables: PathVariables,
            rootPath: String?,
            path: String
        ) {
            escape(pathVariables.toPathString(path, rootPath, unix = true))
        }

        override fun toString(): String {
            return sb.toString()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private class DeserializationReader(private val s: String) {
        private var i = 0
        private val n = s.length

        inline fun peek(): Char {
            return if (i < n) {
                s[i]
            } else {
                0.toChar()
            }
        }

        fun readString(terminator: Char): String {
            // Common scenario 1: nothing
            if (s[i] == terminator) {
                i++
                return ""
            }

            // Common scenario 2:
            // First see if it's a simple substring (e.g. no escapes before we
            // reach the terminator -- that way we don't have to create a new
            // copy in a StringBuilder etc.
            for (index in i until n) {
                val c = s[index]
                if (c == '\\') {
                    break
                } else if (c == terminator) {
                    val string = s.substring(i, index)
                    i = index + 1
                    return string
                }
            }

            // General case: there are escaped characters in the string

            val contentBuilder = StringBuilder()
            while (i < n) {
                val p = s[i++]
                val c = if (p == '\\') {
                    if (i < n) {
                        s[i++]
                    } else {
                        break
                    }
                } else {
                    p
                }
                if (p == terminator) {
                    break
                } else {
                    contentBuilder.append(c)
                }
            }
            return contentBuilder.toString()
        }

        /**
         * Like [readString] but does not remove escape characters. This
         * is intended for cases like the argument lists where we want
         * to pick out the serialized arguments to for example an array,
         * but we will later need to pick out elements from these as
         * well.
         */
        fun readRaw(terminator: Char): String {
            val begin = i
            while (i < n) {
                val p = s[i]
                if (p == terminator) {
                    break
                } else if (p == '\\') {
                    i++
                }
                i++
            }
            return s.substring(begin, i++)
        }

        inline fun next(): Char {
            try {
                return peek()
            } finally {
                i++
            }
        }

        inline fun advance() {
            i++
        }

        inline fun eof(): Boolean {
            return i >= n
        }

        override fun toString(): String {
            val windowSize = 100
            val start = max(0, i - windowSize)
            val end = min(n, i + windowSize)
            return s.substring(start, i) + " | " + s.substring(i, end)
        }
    }

    /**
     * Deserializes a lint resource repository created by [serialize]
     */
    fun deserialize(
        s: String,
        pathVariables: PathVariables,
        root: File? = null,
        project: Project? = null
    ): LintResourceRepository {
        if (s.isEmpty()) {
            return LintResourceRepository.Companion.EmptyRepository
        }

        val map: MutableMap<ResourceType, ListMultimap<String, ResourceItem>> =
            EnumMap(ResourceType::class.java)

        val reader = DeserializationReader(s)
        val namespaceUri = reader.readString(';')
        val namespace = ResourceNamespace.fromNamespaceUri(namespaceUri)
            ?: ResourceNamespace.RES_AUTO
        val libraryName = reader.readString(';').ifBlank { null }

        val size = if (namespace == ResourceNamespace.ANDROID) 11000 else 100
        val fileList = ArrayList<File>(size)

        while (!reader.eof() && reader.peek() != '+') {
            val path = reader.readString(',')
            val file = pathVariables.fromPathString(path, root)
            fileList.add(file)
        }

        val parentConfigMap = HashMap<String, FolderConfiguration>(size / 4)
        val folderConfigMap = HashMap<File, FolderConfiguration>(size)
        for (file in fileList) {
            val folderName = file.parentFile?.name ?: continue
            val config = parentConfigMap[folderName]
                ?: FolderConfiguration.getConfigForFolder(folderName)
                    ?.also {
                        it.normalizeByAddingImpliedVersionQualifier()
                        parentConfigMap[folderName] = it
                    }
                ?: continue
            folderConfigMap[file] = config
        }

        // Map of the values added from each resource file
        val valueItems = HashMap<File, MutableList<LintDeserializedResourceItem>>()

        // Per type lists
        var type = ResourceType.AAPT
        while (!reader.eof()) {
            // Look for resource type
            if (reader.peek() == ';') {
                reader.advance()
                if (reader.eof()) {
                    break
                }
            }

            if (reader.peek() == '+') {
                reader.advance()
                val typeClass = reader.readString(':')
                val typeString = ResourceType.fromClassName(typeClass)
                assert(typeString != null) { typeClass }
                type = typeString!!
            }

            // Find items
            val name = reader.readString(',')
            val fileNumString = reader.readString(',')
            val fileNum = fileNumString.toInt()
            val fileBased = reader.next() == 'F'
            var args: String? = null
            var rawSource: String? = null
            val peek = reader.peek()
            val content = when {
                peek == '\"' -> {
                    reader.advance() // "
                    val content = reader.readString('"')

                    if (reader.peek() != ';') {
                        // raw source provided as well
                        rawSource = reader.readString(';')
                    }
                    content
                }

                peek != ';' -> {
                    // Arguments
                    args = reader.readRaw(';')
                    null
                }

                else -> {
                    null
                }
            }
            val file = fileList[fileNum]
            val config = folderConfigMap[file]!!
            if (fileBased) {
                val item = LintResourceItem(
                    file, name, namespace, type, null,
                    false, libraryName, config, true
                )
                LintResourceRepository.recordItem(map, type, name, item)

                // As a side effect sets item.sourceFile
                ResourceFile(file, item, config)
            } else {
                val item = LintDeserializedResourceItem(
                    file, name, namespace, type,
                    config, false, rawSource, content, args, libraryName
                )
                LintResourceRepository.recordItem(map, type, name, item)
                val list = valueItems[file] ?: ArrayList<LintDeserializedResourceItem>()
                    .also { valueItems[file] = it }
                list.add(item)
            }
        }

        // Initialize resource files for value resources; we couldn't do that
        // during initialization since we need to pass in all items for each
        // file at the same time
        for ((file, items) in valueItems) {
            val config = folderConfigMap[file]!!
            val itemList: List<LintDeserializedResourceItem> = items
            // Constructor has side effect of recording itself on each item
            ResourceFile(file, itemList, config)
        }

        return LintResourceRepository(project, map, namespace, libraryName)
    }

    /** Serializes a lint resource repository. */
    fun serialize(repository: LintResourceRepository, pathVariables: PathVariables): String {
        return serialize(repository, pathVariables, null)
    }

    private class LintDeserializedResourceItem(
        private val sourceFile: File,
        name: String,
        namespace: ResourceNamespace,
        type: ResourceType,
        private val config: FolderConfiguration,
        private val fileBased: Boolean,
        private val rawSource: String?,
        /** Source text. */
        private val text: String?,
        /**
         * Additional serialized data, used to deserialize a specific
         * resource value. This is done lazily since lint almost never
         * consults resource values for anything other than strings and
         * dimensions (and only usually when some other potentially
         * triggering issue is there.)
         */
        private val arguments: String?,
        private val library: String?
    ) : ResourceMergerItem(name, namespace, type, null, false, null) {
        override fun getConfiguration(): FolderConfiguration {
            return config
        }

        override fun getFile(): File {
            return sourceFile
        }

        override fun getSource(): PathString {
            return PathString(sourceFile)
        }

        override fun getValueText(): String {
            return text ?: ""
        }

        override fun getResourceValue(): ResourceValue {
            return mResourceValue ?: createResourceValue().also { mResourceValue = it }
        }

        override fun getLibraryName(): String? {
            return library
        }

        private fun createResourceValue(): ResourceValue {
            // Lazily construct resource value from value data
            return if (arguments == null) {
                when {
                    type == ResourceType.ATTR -> {
                        AttrResourceValueImpl(namespace, name, library)
                    }

                    type == ResourceType.STRING && text != null -> {
                        TextResourceValueImpl(namespace, name, text, rawSource, library)
                    }

                    type == ResourceType.ARRAY -> {
                        ArrayResourceValueImpl(namespace, name, library)
                    }

                    type == ResourceType.STYLEABLE -> {
                        StyleableResourceValueImpl(namespace, name, null, library)
                    }

                    type == ResourceType.STYLE -> {
                        StyleItemResourceValueImpl(namespace, name, null, library)
                    }

                    text != null -> {
                        ResourceValueImpl(namespace, type, name, text, library)
                    }

                    isFileBased -> {
                        ResourceValueImpl(namespace, type, name, sourceFile.path, library)
                    }

                    else -> {
                        ResourceValueImpl(namespace, type, name, library)
                    }
                }
            } else {
                assert(arguments.isNotEmpty())
                val reader = DeserializationReader(arguments)
                when {
                    type == ResourceType.ARRAY -> {
                        // Array
                        val array = ArrayResourceValueImpl(namespace, name, library)
                        while (!reader.eof()) {
                            val element = reader.readString(',')
                            array.addElement(element)
                        }
                        array
                    }

                    type == ResourceType.PLURALS -> {
                        val plural = PluralsResourceValueImpl(namespace, name, text, library)
                        while (!reader.eof()) {
                            val quantity = reader.readString(':')
                            val value = reader.readString(',')
                            plural.addPlural(quantity, value)
                        }
                        plural
                    }

                    type == ResourceType.STYLE -> {
                        val parent = when (reader.next()) {
                            'E' -> ""
                            'N' -> null
                            else -> reader.readString(',')
                        }
                        val style = StyleResourceValueImpl(namespace, name, parent, library)
                        while (!reader.eof()) {
                            val name = reader.readString(':')
                            val value = reader.readString(',')
                            val item = StyleItemResourceValueImpl(namespace, name, value, library)
                            style.addItem(item)
                        }
                        style
                    }

                    type == ResourceType.STYLEABLE -> {
                        val style = StyleableResourceValueImpl(namespace, name, null, library)
                        val separator = reader.next()
                        assert(separator == '-')
                        while (!reader.eof()) {
                            val attrName = reader.readString(':')
                            val attr = AttrResourceValueImpl(namespace, attrName, library)
                            style.addValue(attr)
                            val format = reader.readString(':')
                            if (format.isNotEmpty()) {
                                val formats = AttributeFormat.parse(format)
                                attr.setFormats(formats)
                            }
                            while (!reader.eof()) {
                                if (reader.peek() == '-') {
                                    reader.advance()
                                    break
                                }
                                val name = reader.readString(':')
                                val value = reader.readString(',')
                                attr.addValue(
                                    name,
                                    if (value.isNotBlank()) value.toInt() else null,
                                    null
                                )
                            }
                        }
                        style
                    }

                    type == ResourceType.ATTR -> {
                        val attr = AttrResourceValueImpl(namespace, name, library)
                        val format = reader.readString(':')
                        if (format.isEmpty()) {
                            // Only specified format, not arguments
                            val formats = listOf(AttributeFormat.REFERENCE)
                            attr.setFormats(formats)
                        } else {
                            val formats = AttributeFormat.parse(format)
                            attr.setFormats(formats)
                        }

                        while (!reader.eof()) {
                            val name = reader.readString(':')
                            val value = reader.readString(',')
                            attr.addValue(
                                name,
                                if (value.isNotBlank()) value.toInt() else null,
                                null
                            )
                        }
                        attr
                    }

                    DensityBasedResourceValue.isDensityBasedResourceType(type) -> {
                        val density = Density.getEnum(arguments)!!
                        // value path or null?
                        DensityBasedResourceValueImpl(namespace, type, name, null, density, library)
                    }

                    else -> {
                        ResourceValueImpl(namespace, type, name, file.path, library)
                    }
                }
            }
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
}
