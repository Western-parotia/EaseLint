/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_COLUMN
import com.android.SdkConstants.ATTR_FILE
import com.android.SdkConstants.ATTR_FORMAT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LINE
import com.android.SdkConstants.ATTR_MESSAGE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ISSUE
import com.android.SdkConstants.TAG_ISSUES
import com.android.SdkConstants.TAG_LOCATION
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.AllOfConstraint
import com.android.tools.lint.detector.api.AnyOfConstraint
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.IsAndroidProject
import com.android.tools.lint.detector.api.IsLibraryProject
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.MinSdkAtLeast
import com.android.tools.lint.detector.api.MinSdkLessThan
import com.android.tools.lint.detector.api.NotAndroidProject
import com.android.tools.lint.detector.api.NotLibraryProject
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TargetSdkAtLeast
import com.android.tools.lint.detector.api.TargetSdkLessThan
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.assertionsEnabled
import com.android.tools.lint.model.PathVariables
import com.android.utils.XmlUtils
import com.google.common.base.Joiner
import com.intellij.psi.PsiMethod
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.Base64
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** A reporter which emits lint results into an XML report. */
open class XmlWriter constructor(
    /** Client handling IO, path normalization and error reporting. */
    private val client: LintCliClient,
    /** The type of report to create. */
    private var type: XmlFileType,
    /** Writer to send output to. */
    private val writer: Writer,
    /** Path variables to use when writing */
    private val pathVariables: PathVariables
) {
    constructor(
        /**
         * Client handling IO, path normalization and error reporting.
         */
        client: LintCliClient,
        /** File to write report to. */
        output: File,
        /** The type of report to create. */
        type: XmlFileType
    ) : this(client, type, output.bufferedWriter(), client.pathVariables)

    /** Flush any buffered changes to the file. */
    fun close() {
        writer.close()
    }

    /** Writes the prolog of an XML file. */
    private fun writeProlog() {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    }

    /** Standard attributes included in the root tag. */
    private fun getDefaultRootAttributes(): List<Pair<String, String?>> {
        // Format 4: added urls= attribute with all more info links, comma separated
        // Unfortunate tag name here; this is really an incident but historically
        // in the file format it was called an issue
        // Format 6: support for storing incidents, lint maps, and configured issues
        return listOfNotNull(
            ATTR_FORMAT to "6",
            client.getClientDisplayRevision()?.let { "by" to "lint $it" },
            "type" to if (type != XmlFileType.REPORT) type.name.toLowerCase(Locale.ROOT) else null,
        )
    }

    /** Writes the given tag with the given set of attributes. */
    private fun writeOpenTag(tag: String, attributes: Map<String, String?>, indent: Int = 0) {
        indent(indent)
        writer.write("<$tag")
        if (attributes.isNotEmpty()) {
            for ((key, value) in attributes) {
                value ?: continue
                writer.write(" ")
                writer.write(key)
                writer.write("=\"")
                writer.write(XmlUtils.toXmlAttributeValue(value))
                writer.write("\"")
            }
        }
        writer.write(">\n")
    }

    private fun writeCloseTag(tag: String, indent: Int = 0) {
        if (indent == 0) {
            writer.write("\n")
        } else {
            indent(indent)
        }
        writer.write("</$tag>\n")
    }

    private fun writeCondition(constraint: Constraint, indent: Int = 1, key: String? = null) {
        indent(indent)
        writer.write("<$TAG_CONDITION")
        key?.let { writeAttribute(writer, -1, ATTR_ID, it) }
        val ind = indent + 1

        when (constraint) {
            is MinSdkAtLeast -> writeAttribute(
                writer, -1, ATTR_MIN_GE,
                constraint.minSdkVersion.toString()
            )

            is TargetSdkAtLeast -> writeAttribute(
                writer, -1, ATTR_TARGET_GE,
                constraint.targetSdkVersion.toString()
            )

            is MinSdkLessThan -> writeAttribute(
                writer, -1, ATTR_MIN_LT,
                constraint.minSdkVersion.toString()
            )

            is TargetSdkLessThan -> writeAttribute(
                writer, -1, ATTR_TARGET_LT,
                constraint.targetSdkVersion.toString()
            )

            is IsLibraryProject -> writeAttribute(writer, -1, ATTR_LIBRARY, VALUE_TRUE)
            is NotLibraryProject -> writeAttribute(writer, -1, ATTR_LIBRARY, VALUE_FALSE)
            is IsAndroidProject -> writeAttribute(writer, -1, ATTR_ANDROID, VALUE_TRUE)
            is NotAndroidProject -> writeAttribute(writer, -1, ATTR_ANDROID, VALUE_FALSE)
            else -> {
                if (constraint is AllOfConstraint) {
                    writeAttribute(writer, ind, ATTR_ALL_OF, VALUE_TRUE)
                    writer.write(">")
                    writeCondition(constraint.left, ind)
                    writeCondition(constraint.right, ind)
                    writer.write("\n")
                    indent(indent)
                    writer.write("</$TAG_CONDITION>")
                    return
                } else if (constraint is AnyOfConstraint) {
                    writeAttribute(writer, ind, ATTR_ANY_OF, VALUE_TRUE)
                    writer.write(">")
                    writeCondition(constraint.left, ind)
                    writeCondition(constraint.right, ind)
                    writer.write("\n")
                    indent(indent)
                    writer.write("</$TAG_CONDITION>")
                    return
                }
                error("Unexpected condition $constraint: needs serialization")
            }
        }
        writer.write("/>\n")
    }

    private fun writeIncident(incident: Incident, indent: Int = 1) {
        writer.write("\n")
        indent(indent)
        val tag = if (type.isPersistenceFile()) TAG_INCIDENT else TAG_ISSUE
        writer.write("<$tag")
        val issue = incident.issue
        writeAttribute(writer, indent + 1, ATTR_ID, issue.id)
        if (type != XmlFileType.BASELINE) {
            writeAttribute(
                writer, indent + 1, ATTR_SEVERITY,
                if (type.isPersistenceFile())
                    incident.severity.toName() else incident.severity.description
            )
        }
        writeAttribute(writer, indent + 1, ATTR_MESSAGE, incident.message)

        if (type.includeIssueMetadata()) {
            writeAttribute(writer, indent + 1, ATTR_CATEGORY, issue.category.fullName)
            writeAttribute(writer, indent + 1, ATTR_PRIORITY, issue.priority.toString())
            // We don't need issue metadata in baselines
            writeAttribute(
                writer, indent + 1,
                ATTR_SUMMARY, issue.getBriefDescription(TextFormat.RAW)
            )
            writeAttribute(
                writer, indent + 1,
                ATTR_EXPLANATION, issue.getExplanation(TextFormat.RAW)
            )

            val moreInfo = issue.moreInfo
            if (moreInfo.isNotEmpty()) {
                // Compatibility with old format: list first URL
                writeAttribute(writer, indent + 1, ATTR_URL, moreInfo[0])
                writeAttribute(writer, indent + 1, ATTR_URLS, Joiner.on(',').join(issue.moreInfo))
            }
        }
        if (client.flags.isShowSourceLines && type.includeSourceLines()) {
            val line = incident.getErrorLines(textProvider = { client.getSourceText(it) })
            if (line != null && line.isNotEmpty()) {
                val index1 = line.indexOf('\n')
                if (index1 != -1) {
                    val index2 = line.indexOf('\n', index1 + 1)
                    if (index2 != -1) {
                        val line1 = line.substring(0, index1)
                        val line2 = line.substring(index1 + 1, index2)
                        writeAttribute(writer, indent + 1, ATTR_ERROR_LINE1, line1)
                        writeAttribute(writer, indent + 1, ATTR_ERROR_LINE2, line2)
                    }
                }
            }
        }

        val applicableVariants = incident.applicableVariants
        if (applicableVariants != null && applicableVariants.variantSpecific) {
            writeAttribute(
                writer,
                indent + 1,
                ATTR_INCLUDED_VARIANTS,
                Joiner.on(',').join(applicableVariants.includedVariantNames)
            )
            writeAttribute(
                writer,
                indent + 1,
                ATTR_EXCLUDED_VARIANTS,
                Joiner.on(',').join(applicableVariants.excludedVariantNames)
            )
        }

        if (type == XmlFileType.REPORT_WITH_FIXES &&
            (incident.fix != null || Reporter.hasAutoFix(issue))
        ) {
            writeAttribute(writer, indent + 1, ATTR_QUICK_FIX, VALUE_STUDIO)
        }

        var hasChildren = false

        val fixData = incident.fix
        if (fixData != null) {
            if (type == XmlFileType.REPORT_WITH_FIXES) {
                writer.write(">\n")
                emitFixEdits(incident, fixData)
                hasChildren = true
            }
            if (type.isPersistenceFile()) {
                if (!hasChildren) {
                    writer.write(">\n")
                    hasChildren = true
                    emitFixDescriptors(incident, fixData)
                }
            }
        }
        var location: Location? = incident.location
        if (location != null) {
            if (!hasChildren) {
                writer.write(">\n")
            }
            while (location != null) {
                writeLocation(incident.project, location)
                location = location.secondary
            }
            hasChildren = true
        }

        if (type.isPersistenceFile()) {
            incident.clientProperties?.let { map ->
                if (!hasChildren) {
                    writer.write(">\n")
                    hasChildren = true
                }
                writeLintMap(map)
            }
        }

        if (hasChildren) {
            indent(1)
            writer.write("</$tag>\n")
        } else {
            writer.write("\n")
            indent(1)
            writer.write("/>\n")
        }
    }

    private fun writeLintMap(map: LintMap, indent: Int = 2, name: String? = null) {
        val entries = LintMap.getInternalMap(map).entries
        if (entries.isEmpty()) {
            return
        }
        indent(indent)
        writer.write("<")
        writer.write(TAG_MAP)
        if (name != null) {
            writer.write(" ")
            writer.write(ATTR_ID)
            writer.write("=\"")
            writer.write(XmlUtils.toXmlAttributeValue(name))
            writer.write("\"")
        }
        writer.write(">\n")
        loop@ for ((key, value) in entries) {
            val valueName = when (value) {
                is String -> ATTR_STRING
                is Int -> ATTR_INT
                is Boolean -> ATTR_BOOLEAN
                is Severity -> ATTR_SEVERITY
                is Location -> {
                    writeLocation(null, value, TAG_LOCATION, indent + 1, key)
                    continue@loop
                }

                is LintMap -> {
                    writeLintMap(value, indent + 2, key)
                    continue@loop
                }

                is Incident -> {
                    writeIncident(value, indent + 2)
                    continue@loop
                }

                is Constraint -> {
                    val id = if (key != LintDriver.Companion.KEY_CONDITION) key else null
                    writeCondition(value, indent + 1, id)
                    continue@loop
                }

                else -> error("Unexpected map value type ${value.javaClass}")
            }
            indent(indent + 1)
            writer.write("<")
            writer.write(TAG_ENTRY)
            writeAttribute(writer, indent + 2, ATTR_NAME, key)
            val valueString = value.toString()
            writeAttribute(writer, indent + 2, valueName, valueString)
            writer.write("/>\n")
        }
        indent(indent)
        writer.write("</$TAG_MAP>\n")
    }

    private fun writeLocation(
        project: Project?,
        location: Location,
        tag: String = TAG_LOCATION,
        indent: Int = 2,
        key: String? = null

    ) {
        indent(indent)
        val indented = indent + 1
        writer.write("<")
        writer.write(tag)
        if (key != null) {
            writer.write(" ")
            writer.write(ATTR_ID)
            writer.write("=\"")
            writer.write(XmlUtils.toXmlAttributeValue(key))
            writer.write("\"")
        }
        val neutralPath = getPath(location.file, project)
        writeAttribute(writer, indent + 1, ATTR_FILE, neutralPath)
        val start = location.start
        if (start != null) {
            val line = start.line
            val column = start.column
            if (line >= 0) {
                // +1: Line numbers internally are 0-based, report should be
                // 1-based.
                writeAttribute(writer, indented, ATTR_LINE, (line + 1).toString())
                if (column >= 0) {
                    writeAttribute(writer, indented, ATTR_COLUMN, (column + 1).toString())
                }
            }
            if (type.includeOffsets()) {
                writeAttribute(writer, indented, ATTR_START_OFFSET, start.offset.toString())
                val end = location.end
                if (end != null) {
                    if (line != -1) {
                        val endLine = end.line + 1
                        val endColumn = end.column + 1
                        writeAttribute(writer, indented, ATTR_END_LINE, endLine.toString())
                        writeAttribute(
                            writer,
                            indented,
                            ATTR_END_COLUMN,
                            endColumn.toString()
                        )
                    }
                    writeAttribute(writer, indented, ATTR_END_OFFSET, end.offset.toString())
                }
            }
        }
        location.message?.let {
            writeAttribute(writer, indented, ATTR_MESSAGE, it)
        }

        writer.write("/>\n")
    }

    /**
     * Applies the quickfixes to a temporary doc and writes out the
     * cumulative set of edits to apply to the doc.
     */
    private fun emitFixEdits(incident: Incident, lintFix: LintFix) {
        val fixes =
            if (lintFix is LintFix.LintFixGroup && lintFix.type == LintFix.GroupType.ALTERNATIVES) {
                lintFix.fixes
            } else {
                listOf(lintFix)
            }
        for (fix in fixes) {
            emitEdit(incident, fix)
        }
    }

    private fun getPath(file: File, project: Project?): String {
        var path: String? = null

        // If we have path variables, use those (but if there's no match, don't use
        // an absolute path; try to make it project relative
        if (path == null && type.relativePaths() && type.variables() && pathVariables.any()) {
            // For baselines, if we have a project, try to make it project relative first.
            // We ignore the absolutePaths flag for baselines.
            if (type == XmlFileType.BASELINE) {
                path = client.getDisplayPath(project, file, false)
                // We normally prefer path variables over ../ relative paths, but in a checkDependencies
                // scenario it's normal for the baselines to point into sibling projects; keep the
                // paths relocatable.
                if (path.isParentDirectoryPath() && !client.flags.isCheckDependencies) {
                    path = null
                }
            }

            if (path == null) {
                if (assertionsEnabled()) assert(file.isAbsolute) { file.path }
                path = pathVariables.toPathStringIfMatched(file, unix = type.unixPaths())

                if (path != null && PathVariables.startsWithVariable(path, "HOME")) {
                    // Don't match $HOME if the location is inside the current project -- that just means
                    // the project is under $HOME, which is pretty likely
                    // (We do want to include HOME such that we pick up a relative location to files
                    // outside of the project, such as (say ~/.android)
                    val relativePath = client.getDisplayPath(project, file, false)
                    if (!relativePath.isParentDirectoryPath()) {
                        path = relativePath
                    }
                }
            }
        }

        if (path == null) {
            val absolute = !type.relativePaths() && client.flags.isFullPath
            path = client.getDisplayPath(project, file, absolute)
        }

        return if (type.unixPaths())
            path.replace('\\', '/')
        else
            path
    }

    private fun String.isParentDirectoryPath() = startsWith("..")

    private fun emitEdit(incident: Incident, lintFix: LintFix) {
        indent(2)
        writer.write("<")
        writer.write(TAG_FIX)
        lintFix.getDisplayName()?.let {
            writeAttribute(writer, 3, ATTR_DESCRIPTION, it)
        }
        writeAttribute(
            writer, 3, ATTR_AUTO,
            LintFixPerformer.canAutoFix(lintFix).toString()
        )

        var haveChildren = false

        val performer = LintFixPerformer(client, false)
        val files = performer.computeEdits(incident, lintFix)
        if (files.isNotEmpty()) {
            haveChildren = true
            writer.write(">\n")

            for (file in files) {
                for (edit in file.edits) {
                    indent(3)
                    writer.write("<$TAG_EDIT")

                    val neutralPath = getPath(file.file, incident.project)
                    writeAttribute(writer, 4, ATTR_FILE, neutralPath)

                    with(edit) {
                        val after = source.substring(max(startOffset - 12, 0), startOffset)
                        val before = source.substring(
                            startOffset,
                            min(max(startOffset + 12, endOffset), source.length)
                        )
                        writeAttribute(writer, 4, ATTR_OFFSET, startOffset.toString())
                        writeAttribute(writer, 4, ATTR_AFTER, after)
                        writeAttribute(writer, 4, ATTR_BEFORE, before)
                        if (endOffset > startOffset) {
                            writeAttribute(
                                writer, 4, ATTR_DELETE,
                                source.substring(startOffset, endOffset)
                            )
                        }
                        if (replacement.isNotEmpty()) {
                            writeAttribute(writer, 4, ATTR_INSERT, replacement)
                        }
                    }

                    writer.write("/>\n")
                }
            }
        }

        if (haveChildren) {
            indent(2)
            writer.write("</$TAG_FIX>\n")
        } else {
            writer.write("/>\n")
        }
    }

    /**
     * Applies the quickfixes to a temporary doc and writes out the
     * cumulative set of edits to apply to the doc.
     */
    private fun emitFixDescriptors(incident: Incident, lintFix: LintFix, indent: Int = 2) {
        val indented = indent + 1
        when (lintFix) {
            is LintFix.ReplaceString -> {
                indent(indent)
                writer.write("<$TAG_FIX_REPLACE")
                emitFixSharedAttributes(lintFix, indented)
                lintFix.oldString?.let {
                    writeAttribute(writer, indented, ATTR_OLD_STRING, it)
                }
                lintFix.oldPattern?.let {
                    writeAttribute(writer, indented, ATTR_OLD_PATTERN, it)
                }
                lintFix.selectPattern?.let {
                    writeAttribute(writer, indented, ATTR_SELECT_PATTERN, it)
                }
                writeAttribute(writer, indented, ATTR_REPLACEMENT, lintFix.replacement)
                if (lintFix.shortenNames) {
                    writeAttribute(writer, indented, ATTR_SHORTEN_NAMES, VALUE_TRUE)
                }
                if (lintFix.reformat) {
                    writeAttribute(writer, indented, ATTR_REFORMAT, ATTR_VALUE)
                }
                if (lintFix.imports.isNotEmpty()) {
                    writeAttribute(
                        writer,
                        indented,
                        ATTR_IMPORTS,
                        lintFix.imports.joinToString(",")
                    )
                }
                val range = lintFix.range
                if (range != null) {
                    writer.write(">\n")
                    writeLocation(incident.project, range, TAG_RANGE, indented)
                    indent(indent)
                    writer.write("</")
                    writer.write(TAG_FIX_REPLACE)
                    writer.write(">\n")
                } else {
                    writer.write("/>\n")
                }
            }

            is LintFix.SetAttribute -> {
                indent(indent)
                writer.write("<")
                writer.write(TAG_FIX_ATTRIBUTE)
                emitFixSharedAttributes(lintFix, indented)
                lintFix.namespace?.let {
                    writeAttribute(writer, indented, ATTR_NAMESPACE, it)
                }
                writeAttribute(writer, indented, ATTR_ATTRIBUTE, lintFix.attribute)
                lintFix.value?.let {
                    writeAttribute(writer, indented, ATTR_VALUE, it)
                }
                if (lintFix.dot != Integer.MIN_VALUE) {
                    writeAttribute(writer, indented, ATTR_DOT, lintFix.dot.toString())
                }
                if (lintFix.mark != Integer.MIN_VALUE) {
                    writeAttribute(writer, indented, ATTR_MARK, lintFix.mark.toString())
                }
                val range = lintFix.range
                if (range != null) {
                    writer.write(">\n")
                    writeLocation(incident.project, range, ATTR_RANGE, indented)
                    indent(indent)
                    writer.write("</")
                    writer.write(TAG_FIX_ATTRIBUTE)
                    writer.write(">\n")
                } else {
                    writer.write("/>\n")
                }
            }

            is LintFix.LintFixGroup -> {
                indent(indent)
                val tag = when (lintFix.type) {
                    LintFix.GroupType.ALTERNATIVES -> TAG_FIX_ALTERNATIVES
                    LintFix.GroupType.COMPOSITE -> TAG_FIX_COMPOSITE
                    else -> error("Unexpected fix type ${lintFix.type}")
                }
                writer.write("<")
                writer.write(tag)
                emitFixSharedAttributes(lintFix, indented)
                writer.write(">\n")
                for (fix in lintFix.fixes) {
                    emitFixDescriptors(incident, fix, indented)
                }
                indent(indent)
                writer.write("</")
                writer.write(tag)
                writer.write(">\n")
            }

            is LintFix.ShowUrl -> {
                indent(indent)
                writer.write("<")
                writer.write(TAG_SHOW_URL)
                emitFixSharedAttributes(lintFix, indented)
                writeAttribute(writer, indented, ATTR_URL, lintFix.url)
                writer.write("/>\n")
            }

            is LintFix.AnnotateFix -> {
                indent(indent)
                writer.write("<")
                writer.write(TAG_ANNOTATE)
                emitFixSharedAttributes(lintFix, indented)
                writeAttribute(writer, indented, ATTR_SOURCE, lintFix.annotation)
                if (lintFix.replace) {
                    writeAttribute(writer, indented, ATTR_REPLACE, lintFix.replace.toString())
                }
                val range = lintFix.range
                if (range != null) {
                    writer.write(">\n")
                    writeLocation(incident.project, range, TAG_RANGE, indented)
                    indent(indent)
                    writer.write("</")
                    writer.write(TAG_ANNOTATE)
                    writer.write(">\n")
                } else {
                    writer.write("/>\n")
                }
            }

            is LintFix.CreateFileFix -> {
                indent(indent)
                writer.write("<")
                writer.write(TAG_CREATE_FILE)
                emitFixSharedAttributes(lintFix, indented)
                val neutralPath = getPath(lintFix.file, incident.project)
                writeAttribute(writer, indent + 1, ATTR_FILE, neutralPath)
                if (lintFix.delete) {
                    writeAttribute(writer, indented, ATTR_DELETE, VALUE_TRUE)
                }
                lintFix.selectPattern?.let {
                    writeAttribute(writer, indented, ATTR_SELECT_PATTERN, it)
                }
                lintFix.text?.let {
                    writeAttribute(writer, indented, ATTR_REPLACEMENT, it)
                }
                lintFix.binary?.let {
                    writeAttribute(
                        writer,
                        indented,
                        ATTR_BINARY,
                        Base64.getEncoder().encodeToString(it)
                    )
                }
                writer.write("/>\n")
            }

            is LintFix.DataMap -> {
                indent(indent)
                writer.write("<")
                writer.write(TAG_FIX_DATA)
                emitFixSharedAttributes(lintFix, indented)
                for (key in lintFix.keys()) {
                    val valueString =
                        // TODO: Encode type here?
                        when (val value = lintFix.get(key)) {
                            is String -> value
                            is Int,
                            is Boolean -> value.toString()

                            is File -> getPath(value, incident.project)
                            is List<*> ->
                                // Strings are not allowed to contain ,
                                value.joinToString {
                                    val s = it as String
                                    assert(!s.contains(","))
                                    s
                                }

                            is PsiMethod, is Throwable ->
                                // Not supported for persistence
                                null

                            else -> error("Unexpected fix map value type ${value?.javaClass}")
                        } ?: continue
                    writeAttribute(writer, -1, key, valueString)
                }
                writer.write("/>\n")
            }

            else -> error("Unsupported quickfix ${lintFix.javaClass}")
        }
    }

    private fun emitFixSharedAttributes(lintFix: LintFix, indent: Int) {
        lintFix.getDisplayName()?.let {
            writeAttribute(writer, indent, ATTR_DESCRIPTION, it)
        }
        lintFix.getFamilyName()?.let {
            writeAttribute(writer, indent, ATTR_FAMILY, it)
        }

        if (lintFix.robot) {
            writeAttribute(writer, indent, ATTR_ROBOT, VALUE_TRUE)
        }
        if (lintFix.independent) {
            writeAttribute(writer, indent, ATTR_INDEPENDENT, VALUE_TRUE)
        }
    }

    @Throws(IOException::class)
    private fun writeAttribute(writer: Writer, indent: Int, name: String, value: String) {
        // Allow indent=-1 to signify single line attributes
        if (indent >= 0) {
            writer.write("\n")
            indent(indent)
        } else {
            writer.write(" ")
        }
        writer.write(name)
        writer.write("=\"")
        writer.write(XmlUtils.toXmlAttributeValue(value))
        writer.write("\"")
    }

    @Throws(IOException::class)
    private fun indent(indent: Int) {
        for (level in 0 until indent) {
            writer.write("    ")
        }
    }

    /** Writes the given list. */
    fun writeIncidents(
        incidents: List<Incident>,
        extraAttributes: List<Pair<String, String?>> = emptyList()
    ) {
        writeProlog()
        val rootTag = if (type.isPersistenceFile()) TAG_INCIDENTS else TAG_ISSUES
        val attributeMap = (getDefaultRootAttributes() + extraAttributes).toMap()
        writeOpenTag(rootTag, attributeMap)
        if (incidents.isNotEmpty()) {
            for (incident in incidents) {
                writeIncident(incident, 1)
            }
        }
        writeCloseTag(rootTag)
        close()
    }

    /** Writes the given list. */
    fun writePartialResults(resultMap: Map<Issue, LintMap>) {
        writeProlog()
        // TODO: Switch root tags
        writeOpenTag(TAG_INCIDENTS, getDefaultRootAttributes().toMap())
        if (resultMap.isNotEmpty()) {
            for ((issue, map) in resultMap.entries) {
                val id = issue.id
                if (map.isNotEmpty()) {
                    writeLintMap(map, 1, id)
                }
            }
        }
        writeCloseTag(TAG_INCIDENTS)
        close()
    }

    /** Writes the given list. */
    fun writeConfiguredIssues(severityMap: Map<String, Severity>) {
        writeProlog()
        // TODO: Switch root tags
        writeOpenTag(TAG_INCIDENTS, getDefaultRootAttributes().toMap())

        if (severityMap.isNotEmpty()) {
            for ((id, severity) in severityMap) {
                indent(1)
                writer.write("<$TAG_CONFIG id=\"$id\" severity=\"${severity.toName()}\"/>\n")
            }
        }

        writeCloseTag(TAG_INCIDENTS)
        close()
    }
}

const val TAG_INCIDENTS = "incidents"
const val TAG_INCIDENT = "incident"
const val TAG_CONFIG = "config"
const val TAG_FIX = "fix"
const val TAG_FIX_REPLACE = "fix-replace"
const val TAG_FIX_DATA = "fix-data"
const val TAG_FIX_ATTRIBUTE = "fix-attribute"
const val TAG_FIX_ALTERNATIVES = "fix-alternatives"
const val TAG_FIX_COMPOSITE = "fix-composite"
const val TAG_RANGE = "range"
const val TAG_EDIT = "edit"
const val TAG_CONDITION = "condition"
const val TAG_MAP = "map"
const val TAG_ENTRY = "entry"
const val TAG_SHOW_URL = "show-url"
const val TAG_ANNOTATE = "annotate"
const val TAG_CREATE_FILE = "create-file"
const val ATTR_SEVERITY = "severity"
const val ATTR_INT = "int"
const val ATTR_BOOLEAN = "boolean"
const val ATTR_STRING = "string"
const val ATTR_START_OFFSET = "startOffset"
const val ATTR_END_OFFSET = "endOffset"
const val ATTR_END_LINE = "endLine"
const val ATTR_END_COLUMN = "endColumn"
const val ATTR_URL = "url"
const val ATTR_URLS = "urls"
const val ATTR_ANDROID = "android"
const val ATTR_LIBRARY = "library"
const val ATTR_CLIENT = "client"
const val ATTR_CLIENT_NAME = "name"
const val ATTR_VERSION = "version"
const val ATTR_VARIANT = "variant"
const val ATTR_CHECK_DEPS = "dependencies"
const val ATTR_ATTRIBUTE = "attribute"
const val ATTR_NAMESPACE = "namespace"
const val ATTR_RANGE = "range"
const val ATTR_DOT = "dot"
const val ATTR_MARK = "mark"
const val ATTR_SOURCE = "source"
const val ATTR_REPLACE = "replace"
const val ATTR_DESCRIPTION = "description"
const val ATTR_FAMILY = "family"
const val ATTR_INDEPENDENT = "independent"
const val ATTR_ROBOT = "robot"
const val ATTR_OLD_STRING = "oldString"
const val ATTR_OLD_PATTERN = "oldPattern"
const val ATTR_SELECT_PATTERN = "selectPattern"
const val ATTR_REPLACEMENT = "replacement"
const val ATTR_BINARY = "binary"
const val ATTR_SHORTEN_NAMES = "shortenNames"
const val ATTR_REFORMAT = "reformat"
const val ATTR_IMPORTS = "imports"
const val ATTR_OFFSET = "offset"
const val ATTR_AFTER = "after"
const val ATTR_BEFORE = "before"
const val ATTR_DELETE = "delete"
const val ATTR_INSERT = "insert"
const val ATTR_CATEGORY = "category"
const val ATTR_PRIORITY = "priority"
const val ATTR_SUMMARY = "summary"
const val ATTR_EXPLANATION = "explanation"
const val ATTR_ERROR_LINE1 = "errorLine1"
const val ATTR_ERROR_LINE2 = "errorLine2"
const val ATTR_INCLUDED_VARIANTS = "includedVariants"
const val ATTR_EXCLUDED_VARIANTS = "excludedVariants"
const val ATTR_TARGET_GE = "targetGE"
const val ATTR_TARGET_LT = "targetLT"
const val ATTR_MIN_GE = "minGE"
const val ATTR_MIN_LT = "minLT"
const val ATTR_ALL_OF = "allOf"
const val ATTR_ANY_OF = "anyOf"
const val ATTR_QUICK_FIX = "quickfix"
const val VALUE_STUDIO = "studio"
const val ATTR_AUTO = "auto"
