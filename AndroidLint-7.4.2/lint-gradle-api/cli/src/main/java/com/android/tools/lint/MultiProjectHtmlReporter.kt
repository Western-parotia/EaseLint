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
package com.android.tools.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.utils.SdkUtils
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * "Multiplexing" reporter which allows output to be split up into a
 * separate report for each separate project. It also adds an overview
 * index.
 */
class MultiProjectHtmlReporter(
    client: LintCliClient,
    private val dir: File,
    private val flags: LintCliFlags
) : Reporter(
    client,
    File(dir, INDEX_NAME)
) {
    @Throws(IOException::class)
    override fun write(
        stats: LintStats,
        issues: List<Incident>,
        registry: IssueRegistry
    ) {
        val projectToIncidents: MutableMap<Project, MutableList<Incident>> = HashMap()
        for (incident in issues) {
            val project = incident.project ?: continue
            val list = projectToIncidents.computeIfAbsent(project) { ArrayList() }
            list.add(incident)
        }

        // Set of unique file names: lowercase names to avoid case conflicts in web environment
        val unique: MutableSet<String> = Sets.newHashSet()
        unique.add(INDEX_NAME.toLowerCase(Locale.US))
        val projects: MutableList<ProjectEntry> =
            Lists.newArrayList()
        for (project in projectToIncidents.keys) {
            // TODO: Can I get the project name from the Android manifest file instead?
            val projectName = project.name

            // Produce file names of the form Project.html, Project1.html, Project2.html, etc
            var number = 1
            var fileName: String
            while (true) {
                val numberString = if (number > 1) number.toString() else ""
                fileName = String.format("%1\$s%2\$s.html", projectName, numberString)
                val lowercase = fileName.toLowerCase(Locale.US)
                if (!unique.contains(lowercase)) {
                    unique.add(lowercase)
                    break
                }
                number++
            }
            val output = File(dir, fileName)
            if (output.exists()) {
                val deleted = output.delete()
                if (!deleted) {
                    client.log(null, "Could not delete old file %1\$s", output)
                    continue
                }
            }
            if (!output.parentFile.canWrite()) {
                client.log(null, "Cannot write output file %1\$s", output)
                continue
            }
            val reporter = createHtmlReporter(client, output, flags)
            reporter.urlMap = urlMap
            val projectIssues = projectToIncidents[project] ?: continue
            var projectErrorCount = 0
            var projectWarningCount = 0
            for (incident in projectIssues) {
                if (incident.severity.isError) {
                    projectErrorCount++
                } else if (incident.severity === Severity.WARNING) {
                    projectWarningCount++
                }
            }
            val prefix = project.referenceDir.path
            val path = project.dir.path
            val relative = if (path.startsWith(prefix) && path.length > prefix.length) {
                var i = prefix.length
                if (path[i] == File.separatorChar) {
                    i++
                }
                path.substring(i)
            } else {
                projectName
            }
            reporter.title = String.format("Lint Report for %1\$s", relative)
            reporter.setStripPrefix(relative)
            reporter.write(stats, projectIssues, registry)
            projects.add(
                ProjectEntry(fileName, projectErrorCount, projectWarningCount, relative)
            )
        }

        // Sort project list in decreasing order of errors, warnings and names
        projects.sort()
        val reporter = createHtmlReporter(client, output!!, flags)
        reporter.writeProjectList(stats, projects)
        if (!client.flags.isQuiet &&
            (stats.errorCount > 0 || stats.warningCount > 0)
        ) {
            val index = File(dir, INDEX_NAME)
            val url = SdkUtils.fileToUrlString(index.absoluteFile)
            println(String.format("Wrote overview index to %1\$s", url))
        }
    }

    class ProjectEntry(
        val fileName: String,
        val errorCount: Int,
        val warningCount: Int,
        val path: String
    ) : Comparable<ProjectEntry> {
        override fun compareTo(other: ProjectEntry): Int {
            var delta = other.errorCount - errorCount
            if (delta != 0) {
                return delta
            }
            delta = other.warningCount - warningCount
            return if (delta != 0) {
                delta
            } else path.compareTo(other.path)
        }
    }

    companion object {
        private const val INDEX_NAME = "index.html"
    }
}
