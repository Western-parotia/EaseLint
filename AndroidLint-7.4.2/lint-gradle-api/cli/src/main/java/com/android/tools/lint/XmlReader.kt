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
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LINE
import com.android.SdkConstants.ATTR_MESSAGE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_ISSUE
import com.android.SdkConstants.TAG_ISSUES
import com.android.SdkConstants.TAG_LOCATION
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.util.PathString
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.isAndroidProject
import com.android.tools.lint.detector.api.isLibraryProject
import com.android.tools.lint.detector.api.minSdkAtLeast
import com.android.tools.lint.detector.api.minSdkLessThan
import com.android.tools.lint.detector.api.notAndroidProject
import com.android.tools.lint.detector.api.notLibraryProject
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.tools.lint.detector.api.targetSdkLessThan
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.Base64

/** The [XmlReader] can restore the state saved by [XmlWriter] */
class XmlReader(
    private val client: LintCliClient,
    private val registry: IssueRegistry,
    private val project: Project?,
    xmlFile: File
) {
    private val incidents = mutableListOf<Incident>()
    private var data: MutableMap<Issue, LintMap>? = null
    private var configs: MutableMap<String, Severity>? = null
    private val parser: XmlPullParser

    /** Issue ID for the incident currently being assembled. */
    private var issueId: String? = null

    /** Message for the incident currently being assembled. */
    private var message: String? = null

    /** Severity for the incident currently being assembled. */
    private var severity: Severity? = null

    /** Location for the incident currently being assembled. */
    private var location: Location? = null

    /**
     * Linked list of locations for the incident currently being
     * assembled.
     */
    private var prevLocation: Location? = null

    /**
     * Additional keys and values for the incident currently being
     * assembled.
     */
    private var lintMap: LintMap? = null

    /** A quickfix for the incident currently being assembled. */
    private var fix: LintFix? = null

    /**
     * A list of fixes for the incident currently being assembled, if
     * composite.
     */
    private val fixLists = ArrayDeque<MutableList<LintFix>>()

    init {
        if (xmlFile.exists()) {
            parser = client.createXmlPullParser(PathString(xmlFile)) ?: KXmlParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parse()
        } else {
            parser = KXmlParser()
        }
    }

    /** Returns the incidents loaded from this XML file. */
    fun getIncidents(): List<Incident> {
        return incidents
    }

    /** Returns any partial results loaded from this XML file. */
    fun getPartialResults(): Map<Issue, LintMap> {
        return data ?: emptyMap()
    }

    /** Returns the configured issues loaded from this XML file. */
    fun getConfiguredIssues(): Map<String, Severity> {
        return configs ?: emptyMap()
    }

    private fun addFix(newFix: LintFix) {
        if (fixLists.isEmpty()) {
            fix = newFix
        } else {
            fixLists.last.add(newFix)
        }
    }

    private fun getParentFix(): LintFix? = if (fixLists.isEmpty()) {
        fix
    } else {
        fixLists.last.last()
    }

    /** Read in and parse the XML file. */
    private fun parse() {
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        TAG_ISSUE, TAG_INCIDENT -> {
                            val issueId = issueId
                            val message = message
                            val location = location
                            if (issueId != null && message != null && location != null) {
                                val issue = registry.getIssue(issueId)
                                if (issue != null) {
                                    val incident = Incident(issue, message, location, fix)
                                    severity?.let { incident.severity = it }
                                    lintMap?.let { incident.clientProperties = it }
                                    project?.let { incident.project = it }
                                    incidents.add(incident)
                                }
                            }
                            this.issueId = null
                            this.message = null
                            this.location = null
                            prevLocation = null
                            lintMap = null
                            severity = null
                            fix = null
                            fixLists.clear()
                        }

                        TAG_FIX_ALTERNATIVES -> fixLists.removeLast()
                        TAG_FIX_COMPOSITE -> fixLists.removeLast()
                    }
                } else if (eventType == XmlPullParser.START_TAG) {
                    when (val tag = parser.name) {
                        TAG_LOCATION -> {
                            val newLocation = readLocation()
                            prevLocation?.let {
                                it.secondary = newLocation
                                this.prevLocation = newLocation
                            } ?: run {
                                location = newLocation
                                this.prevLocation = newLocation
                            }
                        }

                        TAG_RANGE -> {
                            readRange()
                        }

                        TAG_ISSUE, TAG_INCIDENT -> {
                            readIncidentHeader()
                        }

                        TAG_CONFIG -> {
                            readConfigInto()
                        }

                        TAG_MAP -> {
                            val issue = parser.getAttributeValue(null, ATTR_ID)?.let {
                                registry.getIssue(it)
                            }
                            val newNote = readLintMap()
                            if (issue != null) {
                                // Top level issue data; not associated with an incident
                                val map = data ?: HashMap<Issue, LintMap>().also { data = it }
                                if (map[issue] != null) {
                                    error("Should only specify one LintMap for issue $issue")
                                }
                                map[issue] = newNote
                            } else {
                                lintMap = newNote
                            }
                        }

                        TAG_ENTRY -> {
                            error("$TAG_ENTRY should be nested inside $TAG_MAP")
                        }

                        TAG_FIX_DATA -> addFix(readFixData())
                        TAG_SHOW_URL -> addFix(readFixShowUrl())
                        TAG_ANNOTATE -> addFix(readFixAnnotate())
                        TAG_FIX_REPLACE -> addFix(readFixReplace())
                        TAG_FIX_ATTRIBUTE -> addFix(readFixSetAttribute())
                        TAG_CREATE_FILE -> addFix(readCreateFile())
                        TAG_FIX_ALTERNATIVES,
                        TAG_FIX_COMPOSITE -> readFixComposite()

                        TAG_ISSUES, TAG_INCIDENTS -> {
                        }

                        else -> {
                            error("Unexpected tag $tag")
                        }
                    }
                } else if (eventType != XmlPullParser.START_TAG) {
                    continue
                }
            }
        } catch (e: IOException) {
            client.log(e, null)
        } catch (e: XmlPullParserException) {
            client.log(e, null)
        }

        if (project != null) {
            for (incident in incidents) {
                incident.project = project
            }
        }
    }

    private fun readIncidentHeader() {
        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_ID -> issueId = value
                ATTR_MESSAGE -> message = value
                ATTR_SEVERITY -> severity = Severity.fromName(value)
                else -> error("Unexpected issue attribute: $value")
            }
        }
        if (issueId == null || message == null) {
            error("Missing required issue or message")
        }
    }

    private fun readRange() {
        val newLocation = readLocation()
        when (val parentFix = getParentFix()) {
            is LintFix.ReplaceString -> parentFix.range = newLocation
            is LintFix.SetAttribute -> parentFix.range = newLocation
            is LintFix.AnnotateFix -> parentFix.range = newLocation
            else -> error("Unexpected parent of <$TAG_RANGE>")
        }
    }

    private fun readConfigInto() {
        val id = parser.getAttributeValue(null, ATTR_ID)
        val severityString = parser.getAttributeValue(null, ATTR_SEVERITY)
        if (id == null || severityString == null) {
            error("Missing required id or severity")
        }
        val map = configs ?: HashMap<String, Severity>().also { configs = it }
        if (map[id] != null) {
            error("Should only specify one severity for issue $id")
        }
        val s = Severity.fromName(severityString)
            ?: error("Unknown severity $severityString")
        map[id] = s
    }

    private fun readFixComposite() {
        val tag = parser.name

        var displayName: String? = null
        var familyName: String? = null
        var robot = false
        var independent = false
        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                ATTR_ROBOT -> robot = true
                ATTR_INDEPENDENT -> independent = true
                else -> error("Unexpected note attribute: $name")
            }
        }
        // Not using a builder because we want to mutate the
        // list of fixes after construction
        val fixList = ArrayList<LintFix>()
        val type = if (tag == TAG_FIX_ALTERNATIVES)
            LintFix.GroupType.ALTERNATIVES
        else
            LintFix.GroupType.COMPOSITE
        val newFix = LintFix.LintFixGroup(displayName, familyName, type, fixList)
        newFix.autoFix(robot, independent)
        addFix(newFix)
        fixLists.addLast(fixList)
    }

    private fun readCreateFile(): LintFix {
        var text: String? = null
        var binary: ByteArray? = null
        var selectPattern: String? = null
        var reformat = false
        var displayName: String? = null
        var familyName: String? = null
        var robot = false
        var independent = false
        var file: File? = null
        var delete = false

        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_FILE -> file = getFile(value)
                ATTR_DELETE -> delete = value == VALUE_TRUE
                ATTR_REPLACEMENT -> text = value
                ATTR_BINARY -> binary = Base64.getDecoder().decode(value)
                ATTR_SELECT_PATTERN -> selectPattern = value
                ATTR_REFORMAT -> reformat = true
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                ATTR_INDEPENDENT -> independent = true
                ATTR_ROBOT -> robot = true
                else -> error("Unexpected fix attribute: $name")
            }
        }

        val builder = LintFix.create().name(displayName).sharedName(familyName)
        val fix = when {
            delete -> builder.deleteFile(file!!)
            text != null -> builder.newFile(file!!, text)
            else -> builder.newFile(file!!, binary!!)
        }
        return fix
            .select(selectPattern)
            .reformat(reformat)
            .autoFix(robot, independent)
            .build()
    }

    private fun readFixSetAttribute(): LintFix {
        var namespace: String? = null
        var attributeName: String? = null
        var attributeValue: String? = null
        var dot = Integer.MIN_VALUE
        var mark = Integer.MIN_VALUE
        var displayName: String? = null
        var familyName: String? = null
        var robot = false
        var independent = false

        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_NAMESPACE -> namespace = value
                ATTR_ATTRIBUTE -> attributeName = value
                ATTR_VALUE -> attributeValue = value
                ATTR_DOT -> dot = value.toInt()
                ATTR_MARK -> mark = value.toInt()
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                ATTR_INDEPENDENT -> independent = true
                ATTR_ROBOT -> robot = true
                else -> error("Unexpected note attribute: $name")
            }
        }
        attributeName!!
        // Note: not setting a range here; it's a child of this tag
        // and the separate TAG_RANGE handling will add it to the
        // existing fix
        return LintFix.create().set()
            .name(displayName)
            .sharedName(familyName)
            .namespace(namespace)
            .attribute(attributeName)
            .value(attributeValue)
            .range(null)
            .select(dot, mark)
            .autoFix(robot, independent)
            .build()
    }

    private fun readFixAnnotate(): LintFix {
        var source: String? = null
        var replace = true
        var displayName: String? = null
        var familyName: String? = null
        var robot = false
        var independent = false

        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_SOURCE -> source = value
                ATTR_REPLACE -> replace = true
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                ATTR_INDEPENDENT -> independent = true
                ATTR_ROBOT -> robot = true
                else -> error("Unexpected note attribute: $name")
            }
        }
        // Note: not setting a range here; it's a child of this tag
        // and the separate TAG_RANGE handling will add it to the
        // existing fix
        return LintFix.create()
            .name(displayName)
            .sharedName(familyName)
            .annotate(source!!, replace)
            .autoFix(robot, independent)
            .build()
    }

    private fun readFixShowUrl(): LintFix {
        var url: String? = null
        var displayName: String? = null
        var familyName: String? = null

        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_URL -> url = value
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                else -> error("Unexpected note attribute: $name")
            }
        }
        return LintFix.create()
            .name(displayName)
            .sharedName(familyName)
            .url(url!!)
            .build()
    }

    private fun readFixData(): LintFix.DataMap {
        val map = HashMap<String, Any>()
        var desc: String? = null
        var familyName: String? = null
        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)!!
            when (name) {
                ATTR_DESCRIPTION -> desc = value
                ATTR_FAMILY -> familyName = value
                else -> map[name] = value
            }
        }
        return LintFix.DataMap(desc, familyName, map)
    }

    private fun readFixReplace(): LintFix {
        var oldString: String? = null
        var oldPattern: String? = null
        var selectPattern: String? = null
        var replacement: String? = null
        var shortenNames = false
        var reformat = false
        var imports: List<String>? = null
        var displayName: String? = null
        var familyName: String? = null
        var robot = false
        var independent = false

        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_OLD_STRING -> oldString = value
                ATTR_OLD_PATTERN -> oldPattern = value
                ATTR_SELECT_PATTERN -> selectPattern = value
                ATTR_REPLACEMENT -> replacement = value
                ATTR_SHORTEN_NAMES -> shortenNames = true
                ATTR_REFORMAT -> reformat = true
                ATTR_IMPORTS -> imports = value.split(",")
                ATTR_DESCRIPTION -> displayName = value
                ATTR_FAMILY -> familyName = value
                ATTR_INDEPENDENT -> independent = true
                ATTR_ROBOT -> robot = true
                // TODO
                else -> error("Unexpected note attribute: $name")
            }
        }

        // Note: not setting a range here; it's a child of this tag
        // and the separate TAG_RANGE handling will add it to the
        // existing fix
        return LintFix.create().replace()
            .name(displayName)
            .sharedName(familyName)
            .text(oldString)
            .pattern(oldPattern)
            .select(selectPattern)
            .with(replacement ?: "")
            .shortenNames(shortenNames)
            .reformat(reformat)
            .apply { imports?.let { this.imports(*it.toTypedArray()) } }
            .autoFix(robot, independent)
            .build()
    }

    private fun getFile(path: String): File {
        return client.pathVariables.fromPathString(path, project?.dir)
    }

    private fun readLocation(): Location {
        var file: File? = null
        var line: String? = null
        var endLine: String? = null
        var column = "0"
        var endColumn = "0"
        var startOffset: String? = null
        var endOffset: String? = null
        var locationMessage: String? = null
        val n = parser.attributeCount
        for (i in 0 until n) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            when (name) {
                ATTR_ID -> { /* ignore: this is used as attribute key when in a note */
                }

                ATTR_FILE -> file = getFile(value)
                ATTR_LINE -> line = value
                ATTR_END_LINE -> endLine = value
                ATTR_COLUMN -> column = value
                ATTR_END_COLUMN -> endColumn = value
                ATTR_START_OFFSET -> startOffset = value
                ATTR_END_OFFSET -> endOffset = value
                ATTR_MESSAGE -> locationMessage = value
                else -> error("Unexpected location attribute: $value")
            }
        }
        if (file == null) {
            error("Missing $file attribute")
        }
        val newLocation =
            if (line == null && startOffset == null) {
                Location.create(file)
            } else try {
                val start = DefaultPosition(
                    if (line != null) line.toInt() - 1 else -1,
                    column.toInt() - 1,
                    startOffset?.toInt() ?: -1
                )
                val end =
                    DefaultPosition(
                        if (endLine != null) endLine.toInt() - 1 else -1,
                        endColumn.toInt() - 1,
                        endOffset?.toInt() ?: -1
                    )
                Location.create(file, start, end)
            } catch (e: NumberFormatException) {
                error("Invalid number: $e")
            }

        if (locationMessage != null) {
            newLocation.message = locationMessage
        }
        return newLocation
    }

    private fun readCondition(): Constraint {
        val index = if (parser.attributeCount == 2) 1 else 0 // first element is the optional id
        val name = parser.getAttributeName(index)
        val value = parser.getAttributeValue(index)
        return when (name) {
            ATTR_MIN_GE -> minSdkAtLeast(value.toInt())
            ATTR_MIN_LT -> minSdkLessThan(value.toInt())
            ATTR_TARGET_GE -> targetSdkAtLeast(value.toInt())
            ATTR_TARGET_LT -> targetSdkLessThan(value.toInt())
            ATTR_LIBRARY -> if (value == VALUE_TRUE) isLibraryProject() else notLibraryProject()
            ATTR_ANDROID -> if (value == VALUE_TRUE) isAndroidProject() else notAndroidProject()
            ATTR_ALL_OF -> {
                skipToNextTag()
                val left = readCondition()
                skipToNextTag()
                val right = readCondition()
                left and right
            }

            ATTR_ANY_OF -> {
                skipToNextTag()
                val left = readCondition()
                skipToNextTag()
                val right = readCondition()
                left or right
            }

            else -> error("Unexpected condition attribute: $name=$value")
        }
    }

    private fun skipToNextTag() {
        // parser.nextTag() does not work when currently in previous tag
        @Suppress("ControlFlowWithEmptyBody")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == XmlPullParser.START_TAG) {
                break
            }
        }
    }

    private fun readLintMap(): LintMap {
        assert(parser.name == TAG_MAP || parser.name == TAG_DATA)
        val lintMap = LintMap()
        val map = LintMap.getInternalMap(lintMap)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == XmlPullParser.END_TAG) {
                when (val tag = parser.name) {
                    TAG_ENTRY, TAG_LOCATION, TAG_CONDITION -> {
                    }

                    TAG_MAP -> return lintMap
                    else -> {
                        error("Unexpected tag $tag")
                    }
                }
            } else if (eventType == XmlPullParser.START_TAG) {
                when (val tag = parser.name) {
                    TAG_ENTRY -> {
                        val n = parser.attributeCount
                        if (n != 2) {
                            error("Expected key and value")
                        }
                        var noteKey = ""
                        var noteValue: Any? = null
                        for (i in 0 until n) {
                            val name = parser.getAttributeName(i)
                            val value = parser.getAttributeValue(i)
                            when (name) {
                                ATTR_NAME -> noteKey = value
                                ATTR_INT -> noteValue = value.toInt()
                                ATTR_BOOLEAN -> noteValue = value!!.toBoolean()
                                ATTR_SEVERITY -> noteValue = Severity.fromName(value!!)
                                ATTR_STRING -> noteValue = value
                                else -> error("Unexpected note attribute: $name")
                            }
                        }
                        map[noteKey] = noteValue!!
                    }

                    TAG_MAP -> {
                        //  Nested note map
                        val mapName = parser.getAttributeValue(null, ATTR_ID)
                            ?: error("No key on nested map")
                        val nestedNode = readLintMap()
                        map[mapName] = nestedNode
                    }

                    TAG_LOCATION -> {
                        val id = parser.getAttributeValue(null, ATTR_ID)
                        val location = readLocation()
                        map[id] = location
                    }

                    TAG_CONDITION -> {
                        val id = parser.getAttributeValue(null, ATTR_ID) ?: LintDriver.KEY_CONDITION
                        val condition = readCondition()
                        map[id] = condition
                    }

                    else -> {
                        error("Unexpected tag $tag")
                    }
                }
            }
        }
        return lintMap
    }
}
