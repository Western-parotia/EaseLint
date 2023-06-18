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
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.model.PathVariables
import java.io.File
import java.io.IOException

/** A reporter which emits lint results into an XML report. */
class XmlReporter constructor(
    /** Client handling IO, path normalization and error reporting. */
    client: LintCliClient,
    /** File to write report to. */
    output: File,
    /**
     * The type of XML file to create; this is used to control details
     * like whether locations are annotated with the surrounding source
     * contents.
     */
    var type: XmlFileType,
) : Reporter(client, output) {

    var pathVariables: PathVariables = client.pathVariables

    private var attributes: MutableMap<String, String>? = null

    fun setBaselineAttributes(client: LintClient, variant: String?, includeDependencies: Boolean) {
        setAttribute(ATTR_CLIENT, LintClient.clientName)
        setAttribute(ATTR_CLIENT_NAME, client.getClientDisplayName())
        val revision = client.getClientDisplayRevision()
        if (revision != null) {
            setAttribute(ATTR_VERSION, revision)
        }
        if (variant != null) {
            setAttribute(ATTR_VARIANT, variant)
        }
        setAttribute(ATTR_CHECK_DEPS, includeDependencies.toString())
    }

    /**
     * Sets a custom attribute to be written out on the root element of
     * the report.
     */
    fun setAttribute(name: String, value: String) {
        val attributes = attributes ?: run {
            val newMap = mutableMapOf<String, String>()
            attributes = newMap
            newMap
        }
        attributes[name] = value
    }

    @Throws(IOException::class)
    override fun write(stats: LintStats, incidents: List<Incident>, registry: IssueRegistry) {
        val writer = output?.bufferedWriter() ?: return
        val xmlWriter = XmlWriter(client, type, writer, pathVariables)

        val clientAttributes: List<Pair<String, String?>> =
            attributes?.asSequence()?.sortedBy { it.key }?.map { Pair(it.key, it.value) }?.toList()
                ?: emptyList()
        xmlWriter.writeIncidents(incidents, clientAttributes)
    }
}
