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

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.ReplaceString
import com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_BEGINNING
import com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_END
import com.android.tools.lint.detector.api.LintFix.SetAttribute
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.utils.PositionXmlParser
import com.android.utils.XmlUtils
import com.google.common.annotations.VisibleForTesting
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.regex.Pattern
import javax.xml.parsers.ParserConfigurationException

/** Support for applying quickfixes directly */
open class LintFixPerformer constructor(
    val client: LintCliClient,
    private val printStatistics: Boolean = true
) {
    private fun getFileData(
        fileMap: MutableMap<File, PendingEditFile>,
        warning: Warning
    ): PendingEditFile {
        val location = getLocation(warning)
        val file = location.file
        return fileMap[file] ?: run {
            val fileData = PendingEditFile(client, file, warning.fileContents.toString())
            fileMap[file] = fileData
            fileData
        }
    }

    private fun getLocation(warning: Warning): Location {
        val fix = warning.quickfixData
        if (fix is ReplaceString) {
            val range = fix.range
            if (range != null) {
                return range
            }
        } else if (fix is SetAttribute) {
            val range = fix.range
            if (range != null) {
                return range
            }
        }
        return warning.location
    }

    private fun registerFix(
        fileMap: MutableMap<File, PendingEditFile>,
        warning: Warning,
        lintFix: LintFix
    ) {
        val fileData = getFileData(fileMap, warning)
        if (addEdits(fileData, warning.location, lintFix)) {
            warning.wasAutoFixed = true
        }
    }

    fun fix(warnings: List<Warning>): Boolean {
        val files = findApplicableFixes(warnings)
        return applyEdits(files)
    }

    @VisibleForTesting
    fun fix(
        file: File,
        fixes: List<LintFix>,
        text: String = file.readText(Charsets.UTF_8)
    ): Boolean {
        val pendingEditFile = PendingEditFile(client, file, text)
        fixes.filter { canAutoFix(it) }.forEach {
            addEdits(pendingEditFile, null, it)
        }
        return applyEdits(listOf(pendingEditFile))
    }

    private fun applyEdits(files: List<PendingEditFile>): Boolean {
        var appliedEditCount = 0
        var editedFileCount = 0
        val editMap: MutableMap<String, Int> = mutableMapOf()

        for (fileData in files) {
            // Sort fixes in descending order from location
            if (fileData.edits.isEmpty()) {
                continue
            }

            // Remove duplicates. Duplicates can happen because sometimes there
            // are multiple references to a single problem, and each fix will repeat
            // the same fix. For example, multiple references to the same private field
            // might all flag that field as generating a synthetic accessor and suggest making
            // it package private, and we only want to perform that modification once, not
            // repeatedly for each field *reference*.
            val edits = fileData.edits.asSequence().sorted().distinct().toList()

            // Look for overlapping edit regions. This can happen if two quickfixes
            // are conflicting.
            if (findConflicts(edits)) {
                continue
            }

            var fileContents = fileData.initialText
            for (edit in edits) {
                appliedEditCount++
                val key = edit.fixName()
                val count: Int = editMap[key] ?: 0
                editMap[key] = count + 1

                fileContents = edit.apply(fileContents)
            }

            writeFile(fileData, fileContents)
            editedFileCount++
        }

        if (printStatistics && editedFileCount > 0) {
            printStatistics(PrintWriter(System.out), editMap, appliedEditCount, editedFileCount)
        }

        return editedFileCount > 0
    }

    protected open fun printStatistics(
        writer: PrintWriter,
        editMap: MutableMap<String, Int>,
        appliedEditCount: Int,
        editedFileCount: Int
    ) {
        if (editMap.keys.size == 1) {
            writer.println("Applied $appliedEditCount edits across $editedFileCount files for this fix: ${editMap.keys.first()}")
        } else {
            writer.println("Applied $appliedEditCount edits across $editedFileCount files")
            editMap.forEach { name, count ->
                writer.println("$count: $name")
            }
        }
    }

    protected open fun writeFile(
        file: PendingEditFile,
        contents: String
    ) {
        file.file.writeText(contents, Charsets.UTF_8)
    }

    private fun findApplicableFixes(warnings: List<Warning>): List<PendingEditFile> {
        val fileMap = mutableMapOf<File, PendingEditFile>()
        for (warning in warnings) {
            val data = warning.quickfixData ?: continue
            if (data is LintFixGroup) {
                if (data.type == GroupType.COMPOSITE) {
                    // separated out again in applyFix
                    var all = true
                    for (sub in data.fixes) {
                        if (!canAutoFix(sub)) {
                            all = false
                            break
                        }
                    }
                    if (all) {
                        for (sub in data.fixes) {
                            registerFix(fileMap, warning, sub)
                        }
                    }
                }
                // else: for GroupType.ALTERNATIVES, we don't auto fix: user must pick
                // which one to apply.
            } else if (canAutoFix(data)) {
                registerFix(fileMap, warning, data)
            }
        }
        return fileMap.values.toList()
    }

    private fun findConflicts(fixes: List<PendingEdit>): Boolean {
        // Make sure there are no overlaps
        // Since we have sorted by start offsets we can just scan over
        // the ranges and make sure that each new range ends after the
        // previous fix' end range
        var prev = fixes[fixes.size - 1]
        for (index in fixes.size - 2 downTo 0) {
            val fix = fixes[index]
            if (fix.startOffset < prev.endOffset) {
                client.log(
                    Severity.WARNING, null,
                    "Overlapping edits in quickfixes; skipping. " +
                            "Involved fixes: ${prev.fix.displayName} in [" +
                            "${prev.startOffset}-${prev.endOffset}] and ${
                                fix.fix.displayName
                            } in [${fix.startOffset}-${fix.endOffset}]"
                )
                return true
            }
            prev = fix
        }
        return false
    }

    private fun addEdits(
        file: PendingEditFile,
        location: Location?,
        lintFix: LintFix
    ): Boolean {
        return if (lintFix is ReplaceString) {
            addReplaceString(file, lintFix, location)
        } else if (lintFix is SetAttribute) {
            addSetAttribute(file, lintFix, location)
        } else if (lintFix is LintFixGroup && lintFix.type == GroupType.COMPOSITE) {
            var all = true
            for (nested in lintFix.fixes) {
                if (!addEdits(file, location, nested)) {
                    all = false
                }
            }
            all
        } else {
            false
        }
    }

    private fun addSetAttribute(
        file: PendingEditFile,
        setFix: SetAttribute,
        fixLocation: Location?
    ): Boolean {
        val contents = file.initialText
        val location = setFix.range ?: fixLocation ?: return false
        val start = location.start ?: return false

        val document = file.getXmlDocument() ?: return false

        var node = PositionXmlParser.findNodeAtOffset(document, start.offset)
            ?: error("No node found at offset " + start.offset)
        if (node.nodeType == Node.ATTRIBUTE_NODE) {
            node = (node as Attr).ownerElement
        } else if (node.nodeType != Node.ELEMENT_NODE) {
            // text, comments
            node = node.parentNode
        }
        if (node == null || node.nodeType != Node.ELEMENT_NODE) {
            throw IllegalArgumentException(
                "Didn't find element at offset " +
                        start.offset +
                        " (line " +
                        start.line + 1 +
                        ", column " +
                        start.column + 1 +
                        ") in " +
                        file.file.path +
                        ":\n" +
                        contents
            )
        }
        val element = node as Element
        val value = setFix.value
        val namespace = setFix.namespace
        if (value == null) {
            // Delete attribute
            val attr =
                if (namespace != null) {
                    element.getAttributeNodeNS(namespace, setFix.attribute)
                } else {
                    element.getAttributeNode(setFix.attribute)
                }

            if (attr != null) {
                val position = PositionXmlParser.getPosition(attr)
                val startOffset: Int = position.startOffset
                val endOffset: Int = position.endOffset
                // Ideally we'd remove surrounding whitespace too, but that's risky
                // when we're also inserting attributes around the same place
                // val padding = if (contents[endOffset] == ' ') 1 else 0
                val padding = 0
                file.edits.add(PendingEdit(setFix, contents, startOffset, endOffset + padding, ""))
                return true
            }
            return false
        } else {
            var attributeName = setFix.attribute
            val insertOffset = findAttributeInsertionOffset(
                file.initialText,
                element,
                attributeName
            )

            if (namespace != null) {
                var prefix: String? = document.lookupPrefix(namespace)
                if (prefix == null) {
                    var base = "ns"
                    when {
                        ANDROID_URI == namespace -> base = ANDROID_NS_NAME
                        TOOLS_URI == namespace -> base = "tools"
                        AUTO_URI == namespace -> base = "app"
                    }
                    val root = document.documentElement
                    var index = 1
                    while (true) {
                        prefix = base + if (index == 1) "" else Integer.toString(index)
                        if (!root.hasAttribute(XMLNS_PREFIX + prefix)) {
                            break
                        }
                        index++
                    }

                    // Insert prefix declaration
                    val namespaceAttribute = XMLNS_PREFIX + prefix
                    val rootInsertOffset = findAttributeInsertionOffset(
                        file.initialText,
                        document.documentElement, namespaceAttribute
                    )
                    val padLeft = if (!contents[rootInsertOffset - 1].isWhitespace()) " " else ""
                    val padRight =
                        if (contents[rootInsertOffset] != '/' && contents[rootInsertOffset] != '>') " " else ""
                    file.edits.add(
                        PendingEdit(
                            setFix, contents, rootInsertOffset, rootInsertOffset,
                            "$padLeft$namespaceAttribute=\"$namespace\"$padRight"
                        )
                    )
                }

                attributeName = "$prefix:$attributeName"
            }

            val padLeft = if (!contents[insertOffset - 1].isWhitespace()) " " else ""
            val padRight =
                if (contents[insertOffset] != '/' && contents[insertOffset] != '>') " " else ""
            file.edits.add(
                PendingEdit(
                    setFix, contents, insertOffset, insertOffset,
                    "$padLeft$attributeName=\"${XmlUtils.toXmlAttributeValue(value)}\"$padRight"
                )
            )
            return true
        }
    }

    private fun findAttributeInsertionOffset(
        xml: String,
        element: Element,
        attributeName: String
    ): Int {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            val delta =
                compareAttributeNames(attributeName, attribute.localName ?: attribute.nodeName)
            if (delta < 0) {
                // Found it; use this position
                return PositionXmlParser.getPosition(attribute).startOffset
            }
        }

        if (attributes.length > 0) {
            // After last attribute
            return PositionXmlParser.getPosition(attributes.item(attributes.length - 1)).endOffset
        }

        // Easiest: Insert at the beginning.
        // Harder: Insert at the end (because we don't have direct pointer to
        // end of open tag, only whole element range, and searching for ">" isn't
        // safe since that is allowed (though discouraged) in attribute values. Best
        // approach is to take offset of first child, if present.
        // Hardest: Insert based on logical attribute order (essentially alphabetical
        // but with android conventions, such as id first, then layout params, then
        // others, and with width before height in "alphabetical" sorting.
        val position = PositionXmlParser.getPosition(element)
        val startOffset: Int = position.startOffset
        val tagEnd = startOffset + element.tagName.length
        var offset = tagEnd
        while (offset < xml.length) {
            val c = xml[offset]
            if (Character.isWhitespace(c) || c == '>' || c == '/') {
                return if (c == ' ') offset + 1 else offset
            }
            offset++
        }
        return xml.length
    }

    private fun addReplaceString(
        file: PendingEditFile,
        replaceFix: ReplaceString,
        fixLocation: Location?
    ): Boolean {
        val contents: String = file.initialText
        val oldPattern = replaceFix.oldPattern
        val oldString = replaceFix.oldString
        val location = replaceFix.range ?: fixLocation ?: return false

        val start = location.start ?: return false
        val end = location.end ?: return false
        val locationRange = contents.substring(start.offset, end.offset)
        var startOffset: Int
        var endOffset: Int
        var replacement = replaceFix.replacement

        if (oldString == null && oldPattern == null) {
            // Replace the whole range
            startOffset = start.offset
            endOffset = end.offset

            // See if there's nothing left on the line; if so, delete the whole line
            var allSpace = true
            for (offset in 0 until replacement.length) {
                val c = contents[offset]
                if (!Character.isWhitespace(c)) {
                    allSpace = false
                    break
                }
            }

            if (allSpace) {
                var lineBegin = startOffset
                while (lineBegin > 0) {
                    val c = contents[lineBegin - 1]
                    if (c == '\n') {
                        break
                    } else if (!Character.isWhitespace(c)) {
                        allSpace = false
                        break
                    }
                    lineBegin--
                }

                var lineEnd = endOffset
                while (lineEnd < contents.length) {
                    val c = contents[lineEnd]
                    lineEnd++
                    if (c == '\n') {
                        break
                    }
                }
                if (allSpace) {
                    startOffset = lineBegin
                    endOffset = lineEnd
                }
            }
        } else if (oldString != null) {
            val index = locationRange.indexOf(oldString)
            when {
                index != -1 -> {
                    startOffset = start.offset + index
                    endOffset = start.offset + index + oldString.length
                }

                oldString == INSERT_BEGINNING -> {
                    startOffset = start.offset
                    endOffset = startOffset
                }

                oldString == INSERT_END -> {
                    startOffset = end.offset
                    endOffset = startOffset
                }

                else -> throw IllegalArgumentException(
                    "Did not find \"" +
                            oldString +
                            "\" in \"" +
                            locationRange +
                            "\" as suggested in the quickfix. Consider calling " +
                            "ReplaceStringBuilder#range() to set a larger range to " +
                            "search than the default highlight range."
                )
            }
        } else {
            assert(oldPattern != null)
            val pattern = Pattern.compile(oldPattern!!)
            val matcher = pattern.matcher(locationRange)
            if (!matcher.find()) {
                throw IllegalArgumentException(
                    "Did not match pattern \"" +
                            oldPattern +
                            "\" in \"" +
                            locationRange +
                            "\" as suggested in the quickfix"
                )
            } else {
                startOffset = start.offset
                endOffset = startOffset

                if (matcher.groupCount() > 0) {
                    if (oldPattern.contains("target")) {
                        try {
                            startOffset += matcher.start("target")
                            endOffset += matcher.end("target")
                        } catch (ignore: IllegalArgumentException) {
                            // Occurrence of "target" not actually a named group
                            startOffset += matcher.start(1)
                            endOffset += matcher.end(1)
                        }
                    } else {
                        startOffset += matcher.start(1)
                        endOffset += matcher.end(1)
                    }
                } else {
                    startOffset += matcher.start()
                    endOffset += matcher.end()
                }

                replacement = replaceFix.expandBackReferences(matcher)
            }
        }

        val edit = PendingEdit(replaceFix, contents, startOffset, endOffset, replacement)
        file.edits.add(edit)
        return true
    }

    fun computeEdits(warning: Warning, lintFix: LintFix): List<PendingEditFile>? {
        val fileMap = mutableMapOf<File, PendingEditFile>()
        registerFix(fileMap, warning, lintFix)
        return fileMap.values.toList()
    }

    companion object {
        /** Not all fixes are eligible for auto-fix; this function checks whether a given fix is. */
        fun canAutoFix(lintFix: LintFix): Boolean {
            if (lintFix is LintFixGroup) {
                when {
                    lintFix.type == GroupType.ALTERNATIVES ->
                        // More than one type: we don't know which to apply
                        return false

                    lintFix.type == GroupType.COMPOSITE -> {
                        // All nested fixes must be auto-fixable
                        for (nested in lintFix.fixes) {
                            if (!canAutoFix(nested)) {
                                return false
                            }
                        }
                        return true
                    }
                }
            }

            if (!lintFix.robot) {
                return false
            }
            if (!lintFix.independent) {
                // For now. TODO: Support these, via repeated analysis runs
                return false
            }

            return true
        }

        fun compareAttributeNames(n1: String, n2: String): Int {
            val rank1 = rankAttributeNames(n1)
            val rank2 = rankAttributeNames(n2)
            val delta = rank1 - rank2
            if (delta != 0) {
                return delta
            }
            return n1.compareTo(n2)
        }

        private fun rankAttributeNames(name: String): Int {
            return when {
                name == ATTR_ID || name == ATTR_NAME -> 0
                name == ATTR_STYLE -> 10
                name == ATTR_LAYOUT -> 20
                name == ATTR_LAYOUT_WIDTH -> 30
                name == ATTR_LAYOUT_HEIGHT -> 40
                name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) -> 50
                else -> 60
            }
        }
    }

    class PendingEditFile(val client: LintCliClient, val file: File, val initialText: String) {
        val edits: MutableList<PendingEdit> = mutableListOf()

        private var document: Document? = null

        fun getXmlDocument(): Document? {
            if (document == null) {
                try {
                    document = PositionXmlParser.parse(initialText)
                } catch (e: ParserConfigurationException) {
                    handleXmlError(e)
                } catch (e: SAXException) {
                    handleXmlError(e)
                } catch (e: IOException) {
                    handleXmlError(e)
                }
            }

            return document
        }

        // because Kotlin does not have multi-catch:
        private fun handleXmlError(e: Throwable) {
            client.log(Severity.WARNING, null, "Ignoring $file: Failed to parse XML: $e")
        }
    }

    class PendingEdit(
        val fix: LintFix,
        val source: String,
        val startOffset: Int,
        val endOffset: Int,
        val replacement: String
    ) : Comparable<PendingEdit> {
        override fun compareTo(other: PendingEdit): Int {
            val delta = other.startOffset - this.startOffset
            if (delta != 0) {
                return delta
            } else {
                // Same offset: Sort deletions before insertions
                // (we might delete some text and insert some new text as separate
                // operations, e.g. one attribute getting removed, another getting inserted.
                // We need to apply the deletions first since these are referencing the
                // old text, not newly inserted text.)
                val d1 = if (this.isDelete()) 0 else 1
                val d2 = if (other.isDelete()) 0 else 1
                val deleteDelta = d1 - d2
                if (deleteDelta != 0) {
                    return deleteDelta
                }
            }

            return other.endOffset - this.endOffset
        }

        fun apply(contents: String): String {
            return contents.substring(0, startOffset) + replacement + contents.substring(endOffset)
        }

        private fun isDelete(): Boolean = endOffset > startOffset

        fun fixName(): String {
            return fix.familyName ?: return fix.displayName ?: fix.javaClass.simpleName
        }

        override fun toString(): String {
            return when {
                replacement.isEmpty() -> "At $startOffset, delete \"${
                    source.substring(
                        startOffset,
                        endOffset
                    )
                }\""

                startOffset == endOffset -> "At $startOffset, insert \"$replacement\""
                else -> "At $startOffset, change \"${
                    source.substring(
                        startOffset,
                        endOffset
                    )
                }\" to \"$replacement\""
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PendingEdit

            if (startOffset != other.startOffset) return false
            if (endOffset != other.endOffset) return false
            if (replacement != other.replacement) return false

            return true
        }

        override fun hashCode(): Int {
            var result = startOffset
            result = 31 * result + endOffset
            result = 31 * result + replacement.hashCode()
            return result
        }
    }
}
