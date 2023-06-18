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
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.LintCliClient.Companion.printWriter
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.AnnotateFix
import com.android.tools.lint.detector.api.LintFix.CreateFileFix
import com.android.tools.lint.detector.api.LintFix.GroupType
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.ReplaceString
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_BEGINNING
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_END
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Support for applying quickfixes directly. */
open class LintFixPerformer constructor(
    val client: LintCliClient,
    /**
     * Whether to emit statistics about number of files modified and
     * number of edits applied
     */
    private val printStatistics: Boolean = true,
    /**
     * Should applied fixes be limited to those marked as safe to be
     * applied automatically?
     */
    private val requireAutoFixable: Boolean = true,
    /**
     * Should we include markers in the applied files like indicators
     * for the marker and selection?
     */
    private val includeMarkers: Boolean = false
) {
    private fun getFileData(
        fileMap: MutableMap<File, PendingEditFile>,
        file: File
    ): PendingEditFile {
        return fileMap[file] ?: run {
            val source = client.getSourceText(file)
            val fileData = PendingEditFile(client, file, source.toString())
            fileMap[file] = fileData
            fileData
        }
    }

    private fun registerFix(
        fileMap: MutableMap<File, PendingEditFile>,
        incident: Incident,
        lintFix: LintFix
    ) {
        val location = getLocation(incident, lintFix)
        val fileData = getFileData(fileMap, location.file)
        if (addEdits(fileData, location, incident, lintFix)) {
            incident.wasAutoFixed = true
        }
    }

    fun fix(incidents: List<Incident>): Boolean {
        val files = findApplicableFixes(incidents)
        return applyEdits(files)
    }

    fun fix(incident: Incident, fixes: List<LintFix>): Boolean {
        val fileMap = mutableMapOf<File, PendingEditFile>()
        for (fix in fixes) {
            if (canAutoFix(fix, requireAutoFixable)) {
                registerFix(fileMap, incident, fix)
            }
        }
        return applyEdits(fileMap.values.toList())
    }

    @VisibleForTesting
    fun fix(
        file: File,
        incident: Incident,
        fixes: List<LintFix>,
        text: String = file.readText(Charsets.UTF_8)
    ): Boolean {
        val pendingEditFile = PendingEditFile(client, file, text)
        fixes.filter { canAutoFix(it, requireAutoFixable) }.forEach {
            addEdits(pendingEditFile, null, incident, it)
        }
        return applyEdits(listOf(pendingEditFile))
    }

    private fun applyEdits(files: List<PendingEditFile>): Boolean {
        var appliedEditCount = 0
        var editedFileCount = 0
        val editMap: MutableMap<String, Int> = mutableMapOf()

        for (fileData in files) {
            val newText = fileData.createText
            if (newText != null) {
                writeFile(fileData, addMarkers(fileData, newText))
            } else if (fileData.createBytes != null) {
                writeFile(fileData, fileData.createBytes)
            } else if (fileData.delete) {
                writeFile(fileData, null)
                continue
            }
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

                // Move selection offsets accordingly. These aren't just
                // inserted as their own edits since they may apply to text
                // that hasn't been applied yet (e.g. when using a regex
                // pattern from a starting offset to identify what is to be selected)
                fileData.selection?.let {
                    val dot = min(it.dot, it.mark)
                    val mark = max(it.dot, it.mark)
                    if (dot != Integer.MIN_VALUE) {
                        it.dot = edit.adjustOffset(dot, true)
                        if (mark != Integer.MIN_VALUE) {
                            it.mark = edit.adjustOffset(mark, false)
                        }
                    }
                }
            }

            fileContents = addMarkers(fileData, fileContents)

            writeFile(fileData, fileContents)
            editedFileCount++
        }

        if (printStatistics && editedFileCount > 0) {
            printStatistics(System.out.printWriter(), editMap, appliedEditCount, editedFileCount)
        }

        return editedFileCount > 0
    }

    /**
     * Indicates caret position with a `|` and the selection range using
     * square brackets if set by the fix
     */
    private fun addMarkers(
        fileData: PendingEditFile,
        fileContents: String
    ): String {
        if (includeMarkers) {
            fileData.selection?.let {
                val (pattern, offset, mark) = it
                val selectStart: Int
                val selectEnd: Int
                val matcher = pattern?.matcher(fileContents)
                if (matcher != null && matcher.find(offset)) {
                    if (matcher.groupCount() > 0) {
                        selectStart = matcher.start(1)
                        selectEnd = matcher.end(1)
                    } else {
                        selectStart = matcher.start()
                        selectEnd = matcher.end()
                    }
                } else if (pattern == null && offset != Integer.MIN_VALUE) {
                    val effectiveMark = if (mark != Integer.MIN_VALUE) mark else offset
                    selectStart = min(offset, effectiveMark)
                    selectEnd = max(offset, effectiveMark)
                } else {
                    return fileContents
                }
                return if (selectStart == selectEnd) {
                    fileContents.substring(0, selectStart) + "|" + fileContents.substring(selectEnd)
                } else {
                    fileContents.substring(0, selectStart) +
                            "[" +
                            fileContents.substring(selectStart, selectEnd) +
                            "]|" +
                            fileContents.substring(selectEnd)
                }
            }
        }
        return fileContents
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
            editMap.forEach { (name, count) ->
                writer.println("$count: $name")
            }
        }
    }

    protected open fun writeFile(
        pendingFile: PendingEditFile,
        contents: String
    ) {
        writeFile(pendingFile, contents.toByteArray(Charsets.UTF_8))
    }

    protected open fun writeFile(
        pendingFile: PendingEditFile,
        contents: ByteArray?
    ) {
        if (contents == null) {
            pendingFile.file.delete()
        } else {
            pendingFile.file.parentFile?.mkdirs()
            pendingFile.file.writeBytes(contents)
        }
    }

    private fun findApplicableFixes(incidents: List<Incident>): List<PendingEditFile> {
        val fileMap = mutableMapOf<File, PendingEditFile>()
        for (incident in incidents) {
            val data = incident.fix ?: continue
            if (data is LintFixGroup) {
                if (data.type == GroupType.COMPOSITE) {
                    // separated out again in applyFix
                    var all = true
                    for (sub in data.fixes) {
                        if (!canAutoFix(sub, requireAutoFixable)) {
                            all = false
                            break
                        }
                    }
                    if (all) {
                        for (sub in data.fixes) {
                            registerFix(fileMap, incident, sub)
                        }
                    }
                }
                // else: for GroupType.ALTERNATIVES, we don't auto fix: user must pick
                // which one to apply.
            } else if (canAutoFix(data, requireAutoFixable)) {
                registerFix(fileMap, incident, data)
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
                            "Involved fixes: ${prev.fix.getDisplayName()} in [" +
                            "${prev.startOffset}-${prev.endOffset}] and ${
                                fix.fix.getDisplayName()
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
        incident: Incident,
        lintFix: LintFix
    ): Boolean {
        return if (lintFix is ReplaceString) {
            addReplaceString(file, incident, lintFix, location)
        } else if (lintFix is SetAttribute) {
            addSetAttribute(file, lintFix, location)
        } else if (lintFix is AnnotateFix) {
            addAnnotation(file, incident, lintFix, location)
        } else if (lintFix is CreateFileFix) {
            addCreateFile(file, lintFix)
        } else if (lintFix is LintFixGroup && lintFix.type == GroupType.COMPOSITE) {
            var all = true
            for (nested in lintFix.fixes) {
                if (!addEdits(file, location, incident, nested)) {
                    all = false
                }
            }
            all
        } else {
            false
        }
    }

    private fun addAnnotation(
        file: PendingEditFile,
        incident: Incident,
        annotateFix: AnnotateFix,
        fixLocation: Location?
    ): Boolean {
        val replaceFix = createAnnotationFix(
            annotateFix, annotateFix.range ?: fixLocation, file.initialText
        )
        return addReplaceString(file, incident, replaceFix, fixLocation)
    }

    private fun addCreateFile(
        file: PendingEditFile,
        fix: CreateFileFix
    ): Boolean {
        if (fix.delete) {
            file.delete = true
            return true
        }
        file.createText = fix.text
        file.createBytes = fix.binary
        fix.selectPattern?.let { file.selection = Selection(Pattern.compile(it), 0) }
        return true
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

        var node: Node? = PositionXmlParser.findNodeAtOffset(document, start.offset)
            ?: error("No node found at offset " + start.offset)
        if (node != null && node.nodeType == Node.ATTRIBUTE_NODE) {
            node = (node as Attr).ownerElement
        } else if (node != null && node.nodeType != Node.ELEMENT_NODE) {
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
        var attributeName = setFix.attribute

        val attr =
            if (namespace != null) {
                element.getAttributeNodeNS(namespace, attributeName)
            } else {
                element.getAttributeNode(attributeName)
            }

        if (value == null) {
            // Delete attribute
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
            if (attr != null) {
                // Already set; change it
                val position = PositionXmlParser.getPosition(attr)
                val startOffset: Int = position.startOffset
                val endOffset: Int = position.endOffset
                val prefix = attr.name + "=\""
                val replacement = prefix + XmlUtils.toXmlAttributeValue(value) + "\""
                file.edits.add(PendingEdit(setFix, contents, startOffset, endOffset, replacement))
                addValueSelection(value, setFix.dot, setFix.mark, startOffset, prefix.length, file)
                return true
            }

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
                        prefix = base + if (index == 1) "" else index.toString()
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

            val leftPart = "$padLeft$attributeName=\""
            val valuePart = XmlUtils.toXmlAttributeValue(value)
            val rightPart = "\"$padRight"
            file.edits.add(
                PendingEdit(
                    setFix, contents, insertOffset, insertOffset,
                    leftPart + valuePart + rightPart
                )
            )
            addValueSelection(value, setFix.dot, setFix.mark, insertOffset, leftPart.length, file)
            return true
        }
    }

    private fun addValueSelection(
        value: String,
        dot: Int,
        mark: Int,
        startOffset: Int,
        valueOffset: Int,
        file: PendingEditFile
    ) {
        if (dot != Integer.MIN_VALUE) {
            val valueStart = startOffset + valueOffset
            val valueDot = valueStart + dot
            val valueMark = if (mark != Integer.MIN_VALUE) valueStart + mark else valueDot
            val selectionStart = min(valueDot, valueMark)
            val selectedString = value.substring(min(dot, mark), max(dot, mark))
            val pattern = Pattern.compile(Pattern.quote(selectedString))
            file.selection = Selection(pattern, selectionStart)
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
        incident: Incident,
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
            for (element in replacement) {
                if (!Character.isWhitespace(element)) {
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
                    } else if (!Character.isWhitespace(c)) {
                        allSpace = false
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
                            "\" in " +
                            file.client.getDisplayPath(null, file.file) +
                            " as suggested in the quickfix.\n" +
                            "\n" +
                            "Consider calling ReplaceStringBuilder#range() to set a larger range to\n" +
                            "search than the default highlight range.\n" +
                            "\n" +
                            "(This fix is associated with the issue id `${incident.issue.id}`,\n" +
                            "reported via ${incident.issue.implementation.detectorClass.name}.)"
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
                            "\" in " +
                            file.client.getDisplayPath(null, file.file) +
                            " as suggested in the quickfix.\n" +
                            "\n" +
                            "(This fix is associated with the issue id `${incident.issue.id}`,\n" +
                            "reported via ${incident.issue.implementation.detectorClass.name}.)"
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

        // Are we in a unit-testing scenario? If so, perform some cleanup of imports etc
        // (which is normally handled by the IDE)
        if (includeMarkers) {
            if (replaceFix.shortenNames) {
                // This isn't fully shortening names, it's only removing fully qualified names
                // for symbols already imported.
                //
                // Also, this will not correctly handle some conflicts. This is only used for
                // unit testing lint fixes, not for actually operating on code; for that we're using
                // IntelliJ's built in import cleanup when running in the IDE.

                val imported: MutableSet<String> = HashSet()
                for (fullLine in contents.split("\n")) {
                    val line = fullLine.trim()
                    if (line.startsWith("package ")) {
                        var to = line.indexOf(';')
                        if (to == -1) {
                            to = line.length
                        }
                        imported.add(line.substring("package ".length, to).trim() + ".")
                    } else if (line.startsWith("import ")) {
                        var from = "import ".length
                        if (line.startsWith("static ", from)) {
                            from += "static ".length
                        }
                        var to = line.indexOf(';')
                        if (to == -1) {
                            to = line.length
                        }
                        if (line[to - 1] == '*') {
                            to--
                        }
                        imported.add(line.substring(from, to).trim())
                    }
                }
                for (full in imported) {
                    var clz = full
                    if (replacement.contains(clz)) {
                        if (!clz.endsWith(".")) {
                            val index = clz.lastIndexOf('.')
                            if (index == -1) {
                                continue
                            }
                            clz = clz.substring(0, index + 1)
                        }
                        replacement = removePackage(replacement, clz)
                    }
                }
            }

            for (import in replaceFix.imports) {
                val isMethod = import[import.lastIndexOf('.') + 1].isLowerCase()
                val importStatement = if (file.file.path.endsWith(DOT_JAVA)) {
                    "import ${if (isMethod) "static " else ""}$import;\n"
                } else {
                    // Kotlin
                    "import $import\n"
                }
                if (!contents.contains(importStatement)) {
                    val offset = if (contents.startsWith("import "))
                        0
                    else {
                        val firstImport = contents.indexOf("\nimport") + 1
                        if (firstImport == 0) {
                            val pkg = contents.indexOf("package ")
                            if (pkg == -1) {
                                0
                            } else {
                                contents.indexOf("\n", pkg) + 1
                            }
                        } else {
                            firstImport
                        }
                    }
                    file.edits.add(
                        PendingEdit(
                            replaceFix,
                            contents,
                            offset,
                            offset,
                            importStatement
                        )
                    )
                }
            }
        }

        replaceFix.selectPattern?.let {
            file.selection = Selection(Pattern.compile(it), start.offset)
        }

        val edit = PendingEdit(replaceFix, contents, startOffset, endOffset, replacement)
        file.edits.add(edit)
        return true
    }

    /**
     * Given a package [prefix] and a Java/Kotlin source fragment, removes the
     * package prefix from any fully qualified references with that package
     * prefix. The reason we can't just use [String.replace] is that we only
     * want to replace prefixes in the same package, not in any sub packages.
     *
     * For example, given the package prefix `p1.p2`, for the source
     * string `p1.p2.p3.Class1, `p1.p2.Class2`, this method will return
     * `p1.p2.p3.Class1, Class2`.
     */
    private fun removePackage(source: String, prefix: String): String {
        if (prefix.isEmpty()) {
            return source
        }

        // Checks whether the symbol starting at offset [next] references
        // the [prefix] package and not potentially some subpackage of it
        fun isPackageMatchAt(next: Int): Boolean {
            var i = next + prefix.length
            while (i < source.length) {
                val c = source[i++]
                if (c == '.') {
                    return false
                } else if (!c.isJavaIdentifierPart()) {
                    return true
                }
            }
            return true
        }

        val sb = StringBuilder()
        var index = 0
        while (true) {
            val next = source.indexOf(prefix, index)
            sb.append(source.substring(index, if (next == -1) source.length else next))
            if (next == -1) {
                break
            }
            index = next + prefix.length
            if ((index == source.length || !source[index].isUpperCase()) && !isPackageMatchAt(next)) {
                sb.append(source.substring(next, index))
            }
        }

        return sb.toString()
    }

    fun computeEdits(incident: Incident, lintFix: LintFix): List<PendingEditFile> {
        val fileMap = mutableMapOf<File, PendingEditFile>()
        registerFix(fileMap, incident, lintFix)
        return fileMap.values.toList()
    }

    companion object {
        fun getLocation(incident: Incident, fix: LintFix? = incident.fix): Location {
            return fix?.range ?: incident.location
        }

        fun isEditingFix(fix: LintFix): Boolean {
            return fix is ReplaceString || fix is AnnotateFix || fix is SetAttribute || fix is CreateFileFix
        }

        /**
         * Not all fixes are eligible for auto-fix; this function checks
         * whether a given fix is.
         */
        fun canAutoFix(lintFix: LintFix): Boolean {
            return canAutoFix(lintFix, true)
        }

        fun canAutoFix(lintFix: LintFix, requireAutoFixable: Boolean): Boolean {
            if (!requireAutoFixable && !(lintFix is LintFixGroup && lintFix.type == GroupType.ALTERNATIVES)) {
                return true
            }

            if (lintFix is LintFixGroup) {
                when (lintFix.type) {
                    GroupType.ALTERNATIVES ->
                        // More than one type: we don't know which to apply
                        return false

                    GroupType.COMPOSITE -> {
                        // All nested fixes must be auto-fixable
                        for (nested in lintFix.fixes) {
                            if (!canAutoFix(nested, requireAutoFixable)) {
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

        private fun implicitlyImported(pkg: String): Boolean {
            // See Kotlin spec https://kotlinlang.org/spec/packages-and-imports.html
            return when (pkg) {
                "kotlin",
                "kotlin.jvm",
                "kotlin.annotation",
                "kotlin.collections",
                "kotlin.comparisons",
                "kotlin.io",
                "kotlin.ranges",
                "kotlin.sequences",
                "kotlin.text",
                "kotlin.math",
                "java.lang" -> true

                else -> false
            }
        }

        fun createAnnotationFix(
            fix: AnnotateFix,
            location: Location?,
            contents: String?
        ): ReplaceString {
            // Don't use fully qualified names for implicitly imported packages
            val annotation = fix.annotation.let {
                val argStart = it.indexOf('(', 1)
                    .let { index -> if (index == -1) it.length else index }
                val packageEnd = it.lastIndexOf('.', argStart)
                if (packageEnd != -1 && implicitlyImported(it.substring(1, packageEnd))) {
                    "@" + it.substring(packageEnd + 1)
                } else {
                    it
                }
            }

            // Add indent?
            var replacement: String = annotation + "\n"
            if (location != null && contents != null) {
                val start = location.start!!
                val startOffset = start.offset
                var lineBegin = startOffset
                while (lineBegin > 0) {
                    val c = contents[lineBegin - 1]
                    if (!Character.isWhitespace(c)) {
                        break
                    } else if (c == '\n' || lineBegin == 1) {
                        val indent = contents.substring(lineBegin, startOffset)
                        replacement = annotation + "\n" + indent
                        break
                    } else lineBegin--
                }
            }

            val replaceFixBuilder = LintFix.create()
                .replace()
                .beginning()
                .with(replacement)
                .shortenNames()
                .reformat(true)
            val range = fix.range
            if (range != null) {
                replaceFixBuilder.range(range)
            }
            return replaceFixBuilder.build() as ReplaceString
        }
    }

    data class Selection(
        val pattern: Pattern?,
        var dot: Int = Integer.MIN_VALUE,
        var mark: Int = Integer.MIN_VALUE
    )

    class PendingEditFile(val client: LintCliClient, val file: File, val initialText: String) {
        val edits: MutableList<PendingEdit> = mutableListOf()
        var selection: Selection? = null
        var delete: Boolean = false
        var createText: String? = null
        var createBytes: ByteArray? = null

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
            return StringBuilder(contents).apply {
                replace(startOffset, endOffset, replacement)
            }.toString()
        }

        fun adjustOffset(offset: Int, biasLeft: Boolean): Int {
            return if (offset < startOffset || offset == startOffset && biasLeft) {
                offset
            } else {
                offset - abs(startOffset - endOffset) + replacement.length
            }
        }

        private fun isDelete(): Boolean = endOffset > startOffset

        fun fixName(): String {
            return fix.getFamilyName() ?: return fix.getDisplayName() ?: fix.javaClass.simpleName
        }

        override fun toString(): String {
            return when {
                replacement.isEmpty() ->
                    "At $startOffset, delete \"${
                        source.substring(
                            startOffset,
                            endOffset
                        )
                    }\""

                startOffset == endOffset -> "At $startOffset, insert \"$replacement\""
                else ->
                    "At $startOffset, change \"${
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
