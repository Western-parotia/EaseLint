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

@file:Suppress("NAME_SHADOWING", "SameParameterValue")

package com.android.tools.lint

import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_DEX
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_SRCJAR
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.repository.Revision
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Position
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat.RAW
import com.android.tools.lint.detector.api.TextFormat.TEXT
import com.android.tools.lint.detector.api.getFileUri
import com.android.tools.lint.detector.api.isParent
import com.android.utils.SdkUtils
import com.android.utils.SdkUtils.isBitmapFile
import com.google.common.hash.Hashing
import com.google.common.io.Files
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.Locale
import kotlin.math.min
import kotlin.text.Charsets.UTF_8

/**
 * A reporter which emits lint results into an SARIF
 * (Static Analysis Results Interchange Format) file; see
 * https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html
 *
 * Initial focus is on the subset used and supported by GitHub:
 * https://docs.github.com/en/github/finding-security-vulnerabilities-and-errors-in-your-code/sarif-support-for-code-scanning#supported-sarif-output-file-properties
 *
 * If changing the output or updating the unit tests, please
 * make sure to run the output through a SARIF validator such as
 * https://sarifweb.azurewebsites.net/Validation (remember that the unit
 * test golden output replaces $ with ＄; change back before validating
 * if copying output verbatim from unit test output)
 */
class SarifReporter

/**
 * Constructs a new [SarifReporter]
 *
 * @param client the client
 * @param output the output file
 * @throws IOException if an error occurs
 */
@Throws(IOException::class)
constructor(client: LintCliClient, output: File) : Reporter(client, output) {
    private val writer: Writer = BufferedWriter(Files.newWriter(output, UTF_8))
    private val incidentSnippets = mutableMapOf<Incident, String>()
    private var root: File? = null
    private val home: File = File(System.getProperty("user.home"))

    private fun getRoot(incident: Incident): File? {
        return root ?: client.getRootDir() ?: run {
            val project = incident.project
            if (project != null) {
                // Workaround: we need the root project; and the client couldn't compute it.
                // We don't just want the project directory, we want the root, which often
                // is not the same; as a temporary workaround before this shows up in the
                // model (see LintCliClient#getRootDir) we find the highest directory that
                // supports build.grade/kts.
                var dir = project.dir
                while (true) {
                    val parent = dir.parentFile ?: break
                    if (File(parent, FN_BUILD_GRADLE).exists() ||
                        File(parent, FN_BUILD_GRADLE_KTS).exists()
                    ) {
                        dir = parent
                    } else {
                        break
                    }
                }
                dir
            } else {
                null
            }
        }.also { root = it }
    }

    @Throws(IOException::class)
    override fun write(stats: LintStats, incidents: List<Incident>, registry: IssueRegistry) {
        var indent = 0
        writer.indent(indent++).write("{\n")
        writer.indent(indent)
            .write("\"\$schema\" : \"https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json\",\n")

        writer.indent(indent).write("\"version\" : \"2.1.0\",\n")
        writer.indent(indent++).write("\"runs\" : [\n")
        writer.indent(indent++).write("{\n")

        val issues = getIssues(incidents)

        writeTools(issues, indent)
        writeBaseUris(incidents, indent)
        writeResults(incidents, issues, indent)

        writer.indent(--indent).write("}\n")
        writer.indent(--indent).write("]\n")
        writer.indent(--indent).write("}\n")
        writer.close()

        if (!client.flags.isQuiet && output != null && (stats.errorCount > 0 || stats.warningCount > 0)) {
            val url = SdkUtils.fileToUrlString(output.absoluteFile)
            println(String.format("Wrote SARIF report to %1\$s", url))
        }
    }

    private fun getIssues(incidents: List<Incident>): List<Issue> {
        var previousIssue: Issue? = null
        val issues = mutableListOf<Issue>()
        for (incident in incidents) {
            val issue = incident.issue
            // Note: The incident list is always sorted by lint before reporting,
            // and the sort ranks by issue first, so we're guaranteed to not see
            // issues again once we've left them. (This fact is used in other reporters too,
            // such as the HtmlReporter.)
            if (issue !== previousIssue) {
                previousIssue = issue
                issues.add(issue)
            }
        }
        issues.sortBy { it.id.toLowerCase(Locale.US) }
        return issues
    }

    /**
     * Returns true if at least one location references paths under the
     * home directory (*and* that directory is not inside the source
     * root)
     */
    private fun locationsContainHome(incidents: List<Incident>): Boolean {
        val first = incidents.firstOrNull() ?: return false
        val root = getRoot(first) ?: return false
        for (incident in incidents) {
            if (locationContainsHome(incident.location, root)) {
                return true
            }
        }

        return false
    }

    /**
     * Returns true when the given location references a path under the
     * home directory that is not also under the source root.
     */
    private fun locationContainsHome(location: Location, root: File): Boolean {
        val file = location.file
        if (!isParent(root, file, strict = false) &&
            isParent(home, file, strict = false)
        ) {
            return true
        }

        val next = location.secondary
        return next != null && locationContainsHome(next, root)
    }

    private fun writeTools(issues: List<Issue>, indent: Int) {
        var indent = indent
        val revision = client.getClientRevision()
        val displayRevision = client.getClientDisplayRevision()?.let {
            if (!it[0].isDigit())
                "1.0"
            else
                it
        }
        writer.indent(indent++).write("\"tool\": {\n")
        writer.indent(indent++).write("\"driver\": {\n")
        writer.indent(indent).write("\"name\": \"Android Lint\",\n")
        writer.indent(indent)
            .write("\"fullName\": \"Android Lint (in ${LintClient.clientName})\",\n")
        writer.indent(indent).write("\"version\": \"$displayRevision\",\n")
        if (Revision.safeParseRevision(revision) != Revision.NOT_SPECIFIED) {
            writer.indent(indent).write("\"semanticVersion\": \"$revision\",\n")
        }
        writer.indent(indent).write("\"organization\": \"Google\",\n")
        writer.indent(indent)
            .write("\"informationUri\": \"https://developer.android.com/studio/write/lint\",\n")
        writer.writeDescription(
            indent,
            "fullDescription",
            "Static analysis originally for Android source code but now performing general analysis",
            comma = true
        )
        writer.indent(indent).write("\"language\": \"en-US\",\n")
        writeRules(issues, indent)
        writer.indent(--indent).write("}\n")
        writer.indent(--indent).write("},\n")
    }

    private fun Severity.level(): String {
        return when (this) {
            Severity.FATAL, Severity.ERROR -> "error"
            Severity.WARNING -> "warning"
            Severity.INFORMATIONAL -> "note"
            Severity.IGNORE -> "none"
        }
    }

    private fun Issue.rank(): Int {
        // Priority 1 is highest, corresponds to rank 100; priority 10 is lowest,
        // corresponds to rank 10
        return if (priority in 1..10) {
            (11 - priority) * 10
        } else {
            50
        }
    }

    private fun writeRules(issues: List<Issue>, indent: Int) {
        writer.indent(indent).write("\"rules\": [\n")

        issues.forEachIndexed { index, issue ->
            // https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317836
            val categories = generateSequence(issue.category) { it.parent }
            val separator = ",\n                                    "
            val tags = categories.joinToString(separator = separator) {
                "\"${it.name.escapeJson()}\""
            }
            val short = issue.getBriefDescription(RAW)
            val full = issue.getExplanation(RAW)
            val level = issue.defaultSeverity.level()
            val rank = issue.rank()
            var indent = indent
            indent++
            writer.indent(indent++).write("{\n")
            writer.indent(indent).write("\"id\": \"${issue.id}\",\n")
            writer.writeDescription(indent, "shortDescription", short, comma = true)
            writer.writeDescription(indent, "fullDescription", full, comma = true)
            writer.indent(indent++).write("\"defaultConfiguration\": {\n")
            writer.indent(indent).write("\"level\": \"$level\",\n")
            // TODO: parameters (detector options):
            //  https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html#_Toc10541295
            writer.indent(indent).write("\"rank\": $rank\n")
            writer.indent(--indent).write("},\n")
            writer.indent(indent++).write("\"properties\": {\n")
            writer.indent(indent++).write("\"tags\": [\n")
            writer.indent(indent).write("$tags\n")
            writer.indent(--indent).write("]\n")
            writer.indent(--indent).write("}\n")
            writer.indent(--indent).write("}")
            if (index < issues.size - 1) {
                writer.write(",")
            }
            writer.write("\n")
        }

        writer.indent(indent).write("]\n")
    }

    private fun writeBaseUris(incidents: List<Incident>, indent: Int) {
        if (incidents.isEmpty()) {
            return
        }
        val rootDir = getRoot(incidents.first()) ?: return
        val sourceRoot = getFileUri(rootDir).escapeJson()

        var indent = indent
        writer.indent(indent++).write("\"originalUriBaseIds\": {\n")
        writer.indent(indent++).write("\"$SRC_DIR_VAR\": {\n")
        writer.indent(indent).write("\"uri\": \"$sourceRoot\"\n")
        writer.indent(--indent).write("}")
        if (locationsContainHome(incidents)) {
            writer.write(",\n")
            writer.indent(indent++).write("\"$USER_HOME_VAR\": {\n")
            writer.indent(indent).write("\"uri\": \"$sourceRoot\"\n")
            writer.indent(--indent).write("}\n")
        } else {
            writer.write("\n")
        }
        writer.indent(--indent).write("},\n")
    }

    private fun writeResults(incidents: List<Incident>, issues: List<Issue>, indent: Int) {
        // https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317507
        writer.indent(indent).write("\"results\": [\n")

        val ruleIndices = mutableMapOf<Issue, Int>()
        for (i in issues.indices) {
            val issue = issues[i]
            ruleIndices[issue] = i
        }

        incidents.forEachIndexed { index, incident ->
            val ruleId = incident.issue.id.escapeJson()
            val ruleIndex = ruleIndices[incident.issue]
            val message = incident.message
            var indent = indent + 1
            writer.indent(indent++).write("{\n")
            writer.indent(indent).write("\"ruleId\": \"$ruleId\",\n")
            writer.indent(indent).write("\"ruleIndex\": $ruleIndex,\n")
            writer.writeDescription(indent, "message", message, comma = true)
            if (incident.severity != incident.issue.defaultSeverity) {
                val level = incident.severity.level()
                writer.indent(indent).write("\"level\": \"$level\",\n")
            }

            writeLocations(incident, indent)
            writeQuickFixes(incident, indent)
            writeFingerprint(incident, indent)

            writer.indent(--indent).write("}${if (index < incidents.size - 1) "," else ""}\n")
        }

        writer.indent(indent).write("]\n")
    }

    private fun writeFingerprint(incident: Incident, indent: Int) {
        writer.indent(indent).write("\"partialFingerprints\": {\n")

        val hashFunction = Hashing.farmHashFingerprint64()

        // We could include a hash for the message and id here, but the SARIF
        // spec discourages including fingerprints for things that can easily
        // be derived from the SARIF file

        // Hash the source context -- not just the highlighted range for the
        // error, but a few lines above and below
        incidentSnippets[incident]?.let { context ->
            val fingerprint = hashFunction.hashString(context, UTF_8).toString()
            writer.indent(indent + 1).write("\"sourceContext/v1\": \"$fingerprint\"\n")
        }

        // GitHub code scanning (currently) documents that the only fingerprint they
        // support is primaryLocationLineHash. Initially we computed a fingerprint for
        // this, but it turns out code scanning *also* computes this hash on its own
        // and complains if they don't match; see locationUpdateCallback in
        // https://github.com/Faten-Org/codeql-action/blob/master/src/fingerprints.ts
        // Attempting to match their exact keys doesn't seem like a good idea, so
        // we'll leave that partialFingerprint out for now.

        writer.indent(indent).write("}\n")
    }

    private fun writeRelatedLocations(incident: Incident, location: Location, indent: Int) {
        var indent = indent
        writer.indent(indent++).write("\"relatedLocations\": [\n")

        var curr = location
        var id = 1
        while (true) {
            val next = curr.secondary
            writeSingleLocation(incident, curr, next == null, indent, id++, curr.message)
            if (next != null) {
                curr = next
            } else {
                break
            }
        }

        writer.indent(--indent).write("],\n")
    }

    private fun writeLocations(incident: Incident, indent: Int) {
        var indent = indent
        val location = incident.location
        writer.indent(indent++).write("\"locations\": [\n")
        writeSingleLocation(
            incident, location,
            last = true, indent = indent, message = location.message
        )
        writer.indent(--indent).write("],\n")
        location.secondary?.let { writeRelatedLocations(incident, it, indent) }
    }

    private fun writeSingleLocation(
        incident: Incident,
        location: Location,
        last: Boolean,
        indent: Int,
        id: Int = -1,
        message: String? = null
    ) {
        var indent = indent
        writer.indent(indent++).write("{\n")
        if (id != -1) {
            writer.indent(indent).write("\"id\": $id,\n")
        }
        if (message != null) {
            writer.writeDescription(indent, "message", message, comma = true)
        }
        writer.indent(indent++).write("\"physicalLocation\": {\n")

        val file = location.file
        writeArtifactLocation(incident, file, 8, false)
        val start = location.start
        val end = location.end
        if (start != null && end != null) {
            val fileText = client.getSourceText(file)
            val segment =
                if (fileText.isNotEmpty()) {
                    fileText.substring(start.offset, end.offset)
                } else {
                    null
                }

            val context = computeContext(fileText, start, end)

            writer.indent(indent++).write("\"region\": {\n")
            writer.indent(indent).write("\"startLine\": ${start.line + 1},\n")
            writer.indent(indent).write("\"startColumn\": ${start.column + 1},\n")
            writer.indent(indent).write("\"endLine\": ${end.line + 1},\n")
            writer.indent(indent).write("\"endColumn\": ${end.column + 1},\n")
            writer.indent(indent).write("\"charOffset\": ${start.offset},\n")
            writer.indent(indent).write("\"charLength\": ${end.offset - start.offset}")
            if (segment != null) {
                writer.write(",\n")
                writer.indent(indent++).write("\"snippet\": {\n")
                writer.indent(indent).write("\"text\": \"${segment.escapeJson()}\"\n")
                writer.indent(--indent).write("}\n")
            } else {
                writer.write("\n")
            }
            writer.indent(--indent).write("}")
            if (context != null) {
                val contextStart = context.first
                val contextEnd = context.second
                val snippet = fileText.substring(contextStart.offset, contextEnd.offset)
                writer.write(",\n")
                writer.indent(indent++).write("\"contextRegion\": {\n")
                writer.indent(indent).write("\"startLine\": ${contextStart.line + 1},\n")
                writer.indent(indent).write("\"endLine\": ${contextEnd.line + 1},\n")
                writer.indent(indent++).write("\"snippet\": {\n")
                writer.indent(indent).write("\"text\": \"${snippet.escapeJson()}\"\n")
                writer.indent(--indent).write("}\n")
                writer.indent(--indent).write("}\n")

                if (!incidentSnippets.containsKey(incident)) {
                    incidentSnippets[incident] = snippet
                }
            } else {
                writer.write("\n")

                if (segment != null && !incidentSnippets.containsKey(incident)) {
                    incidentSnippets[incident] = segment
                }
            }
        } else {
            // No locations: usually used for binary files, but in some cases also
            // used for matches in Gradle files where we don't have exact source information
            // Some implementations (such as GitHub DSP) insists on a region to always be
            // specified
            writer.indent(indent++).write("\"region\": {\n")
            if (isBinary(file)) {
                writer.indent(indent).write("\"byteOffset\": 0\n")
            } else {
                writer.indent(indent).write("\"startLine\": 1\n")
            }
            writer.indent(--indent).write("}")
        }
        writer.indent(--indent).write("}\n")
        writer.indent(--indent).write("}${if (!last) "," else ""}\n")
    }

    private fun isBinary(file: File): Boolean {
        val path = file.path
        return isBitmapFile(file) ||
                path.endsWith(DOT_JAR) ||
                path.endsWith(DOT_CLASS) ||
                path.endsWith(DOT_SRCJAR) ||
                path.endsWith(DOT_AAR) ||
                path.endsWith(DOT_DEX) ||
                path.endsWith(".apk") ||
                path.endsWith(".ser") ||
                path.endsWith(".flat") ||
                path.endsWith(".bin")
    }

    private fun writeArtifactLocation(
        incident: Incident,
        file: File,
        indent: Int,
        last: Boolean
    ) {
        val uriBaseId: String?
        val root = getRoot(incident)
        val uri = if (root != null && isParent(root, file)) {
            uriBaseId = SRC_DIR_VAR
            client.getDisplayPath(root, file, false).replace('\\', '/').escapeJson()
        } else if (isParent(home, file)) {
            uriBaseId = USER_HOME_VAR
            client.getDisplayPath(home, file, false).replace('\\', '/').escapeJson()
        } else {
            uriBaseId = null
            getFileUri(file).escapeJson()
        }
        writer.indent(indent).write("\"artifactLocation\": {\n")
        if (uriBaseId != null) {
            writer.indent(indent + 1).write("\"uriBaseId\": \"$uriBaseId\",\n")
        }
        writer.indent(indent + 1).write("\"uri\": \"$uri\"\n")
        writer.indent(indent).write("}${if (!last) "," else ""}\n")
    }

    /**
     * The SARIF format has a way to indicate the "context" around a
     * line error: this is a few lines above and a few lines below the
     * error segment.
     *
     * This CL computes two locations: the location of the start of this
     * snippet, a few lines above, and the location of the end of this
     * snippet, at the end of the line a few lines below. (When the
     * error message is near the beginning of the file or the end of the
     * file the locations are of course clamped to the beginning or end
     * of the file).
     */
    private fun computeContext(
        fileText: CharSequence,
        lineStart: Position,
        lineEnd: Position
    ): Pair<Position, Position>? {
        if (fileText.isEmpty()) {
            return null
        }
        val size = 2 // size of window: number of additional lines on each side
        val start: Position
        if (lineStart.offset > 0) {
            var beginLine = lineStart.line
            val beginOffset: Int
            var offset = lineStart.offset - 1
            for (i in 0 until size) {
                while (true) {
                    if (offset == 0) {
                        break
                    }
                    val ch = fileText[offset--]
                    if (ch == '\n') {
                        beginLine--
                        break
                    }
                }
            }
            while (true) {
                if (offset == 0) {
                    beginOffset = 0
                    break
                } else if (fileText[offset] == '\n') {
                    beginOffset = offset + 1
                    break
                }
                offset--
            }
            start = DefaultPosition(beginLine, 1, beginOffset)
        } else {
            start = lineStart
        }

        var endOffset = lineEnd.offset
        var endLine = lineEnd.line
        var endColumn = lineEnd.column
        var offset = endOffset
        while (offset < fileText.length) {
            if (fileText[offset++] == '\n') {
                endLine++
                endColumn = 1
                endOffset = offset
                break
            }
            if (offset == fileText.length) {
                endOffset = offset
            }
            endColumn++
        }
        for (i in 0 until size) {
            while (offset < fileText.length) {
                val ch = fileText[offset]
                if (ch == '\n' || offset == fileText.length - 1) {
                    endOffset = offset
                    endLine++
                    if (ch == '\n') {
                        endColumn = 1
                    }
                    break
                }
                offset++
                endColumn++
            }
        }

        val end = DefaultPosition(endLine, endColumn, endOffset)

        return Pair(start, end)
    }

    private fun writeQuickFixes(incident: Incident, indent: Int) {
        // See https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317881
        val lintFix = incident.fix ?: return
        val fixes =
            if (lintFix is LintFix.LintFixGroup && lintFix.type == LintFix.GroupType.ALTERNATIVES) {
                lintFix.fixes
            } else {
                listOf(lintFix)
            }

        val performer = LintFixPerformer(client, false)
        val edits = try {
            fixes.map { fix -> Pair(fix, performer.computeEdits(incident, fix)) }
        } catch (exception: Throwable) {
            // Computing fixes can result in errors, e.g. with overlapping
            // edits or invalid regular expressions etc; in this case,
            // omit all the fixes
            client.log(exception, "Couldn't compute fix edits for ${lintFix.getDisplayName()}")
            return
        }

        var indent = indent
        writer.indent(indent++).write("\"fixes\": [\n")
        edits.forEachIndexed { index, (fix, files) ->
            writeQuickFix(incident, fix, files, index == fixes.size - 1, indent)
        }
        writer.indent(--indent).write("],\n")
    }

    private fun writeQuickFix(
        incident: Incident,
        fix: LintFix,
        files: List<LintFixPerformer.PendingEditFile>,
        last: Boolean,
        indent: Int
    ) {
        // Only write fixes that have corresponding edits, since there are quickfixes
        // in lint that just communicate data to the IDE to act on or for example to
        // perform a navigation/selection to take you to the right place but cannot
        // actually figure out what to change
        if (files.isNotEmpty() && files.any { it.edits.isNotEmpty() }) {
            var indent = indent
            val description = fix.getDisplayName() ?: "Fix"
            writer.indent(indent++).write("{\n")
            writer.writeDescription(indent, "description", description, comma = true)
            writer.indent(indent++).write("\"artifactChanges\": [\n")

            files.forEachIndexed { index, file ->
                if (file.edits.isNotEmpty()) {
                    writeArtifactChange(incident, file, index == files.size - 1, indent)
                }
            }

            writer.indent(--indent).write("]\n")
            writer.indent(--indent).write("}${if (last) "\n" else ",\n"}")
        }
    }

    /**
     * Returns a (by default 1-based line number, or 0-based if you pass
     * 0 into [startLineNumber]) line number.
     */
    private fun getLineNumber(
        source: String,
        offset: Int,
        startOffset: Int = 0,
        startLineNumber: Int = 1
    ): Int {
        var lineNumber = startLineNumber
        for (i in startOffset until min(offset, source.length)) {
            if (source[i] == '\n') {
                lineNumber++
            }
        }
        return lineNumber
    }

    /**
     * Returns a 1-based column number for the character [offset] in the
     * given [source]
     */
    private fun getColumn(source: String, offset: Int): Int {
        assert(offset in source.indices)
        val prevNewline = source.subSequence(0, offset).lastIndexOf('\n')
        return offset - prevNewline
    }

    private fun writeArtifactChange(
        incident: Incident,
        file: LintFixPerformer.PendingEditFile,
        last: Boolean,
        indent: Int
    ) {
        var indent = indent
        writer.indent(indent++).write("{\n")
        writeArtifactLocation(incident, file.file, indent, false)
        writer.indent(indent++).write("\"replacements\": [\n")

        val edits = file.edits
        edits.forEachIndexed { index, edit ->
            with(edit) {
                val startLine = getLineNumber(source, startOffset)
                val startColumn = getColumn(source, startOffset)

                var indent = indent
                writer.indent(indent++).write("{\n")
                writer.indent(indent++).write("\"deletedRegion\": {\n")
                writer.indent(indent).write("\"startLine\": $startLine,\n")
                writer.indent(indent).write("\"startColumn\": $startColumn,\n")
                writer.indent(indent).write("\"charOffset\": $startOffset")
                if (endOffset > startOffset) {
                    writer.write(",\n")
                    val endLine = getLineNumber(source, endOffset, startOffset, startLine)
                    val endColumn = getColumn(source, endOffset - 1) + 1
                    writer.indent(indent).write("\"endLine\": $endLine,\n")
                    writer.indent(indent).write("\"endColumn\": $endColumn,\n")
                    writer.indent(indent).write("\"charLength\": ${endOffset - startOffset}\n")
                } else {
                    // else: not deleting anything, just setting offset for insert
                    writer.write("\n")
                }

                writer.indent(--indent).write("},\n")
                writer.indent(indent++).write("\"insertedContent\": {\n")
                writer.indent(indent).write("\"text\": \"${replacement.escapeJson()}\\n\"\n")
                writer.indent(--indent).write("}\n")
                writer.indent(--indent).write("}${if (index < edits.size - 1) "," else ""}\n")
            }
        }

        writer.indent(--indent).write("]\n")
        writer.indent(--indent).write("}${if (last) "\n" else ",\n"}")
    }

    private fun Writer.writeDescription(
        indent: Int,
        name: String,
        raw: String,
        comma: Boolean = false,
        newline: Boolean = false
    ): Writer {
        writer.indent(indent).write("\"$name\": {\n")
        val text = RAW.convertTo(raw, TEXT)
        writer.indent(indent + 1).write("\"text\": \"${text.escapeJson()}\"")
        if (text != raw) {
            writer.write(",\n")
            writer.indent(indent + 1).write("\"markdown\": \"${raw.escapeJson()}\"\n")
        } else {
            writer.write("\n")
        }
        writer.indent(indent).write("}")
        if (comma) {
            writer.write(",")
        }
        if (newline || comma) {
            writer.write("\n")
        }
        return this
    }

    private fun Writer.indent(indent: Int): Writer {
        for (level in 0 until indent) {
            write("    ")
        }
        return this
    }

    private fun String.escapeJson(): String {
        for (ch in this) {
            when (ch) {
                '\\', '"', '\n', '\t', '\r' -> {
                    val sb = StringBuilder(this.length + 5)
                    for (c in this) {
                        when (c) {
                            '\\', '"' -> sb.append("\\").append(c)
                            '\n' -> sb.append("\\n")
                            '\t' -> sb.append("\\t")
                            '\r' -> sb.append("\\r")
                            else -> sb.append(c)
                        }
                    }
                    return sb.toString()
                }
            }
        }
        return this
    }
}

/**
 * Name of uriBaseId for paths under the user directory (typically
 * pointing to maven artifacts)
 */
private const val USER_HOME_VAR = "USER_HOME"

/** Name of uriBaseId for paths under the source root. */
private const val SRC_DIR_VAR = "%SRCROOT%"

/** Returns true if the given [file] points to a SARIF file. */
fun isSarifFile(file: File): Boolean {
// https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317421
// "The file name of a SARIF log file SHOULD end with the extension ".sarif".
// The file name MAY end with the additional extension ".json"."
    return file.path.endsWith(".sarif") || file.path.endsWith(".sarif.json")
}
