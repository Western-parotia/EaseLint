/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.LintFixPerformer.Companion.canAutoFix
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Severity
import kotlin.math.max

/**
 * Value object passed to [Reporter] instances providing statistics to
 * include in the summary.
 */
class LintStats constructor(
    val errorCount: Int,
    val warningCount: Int,
    val baselineErrorCount: Int = 0,
    val baselineWarningCount: Int = 0,
    val baselineFixedCount: Int = 0,
    val autoFixedCount: Int = 0,
    val hasAutoFixCount: Int = 0
    // TODO: Timing stats too?
) {

    fun count(): Int {
        return errorCount + warningCount
    }

    companion object {
        fun create(mergedIncidents: List<Incident>, baseline: LintBaseline?): LintStats {
            return create(
                mergedIncidents,
                if (baseline != null)
                    listOf(baseline)
                else
                    emptyList()
            )
        }

        fun create(errorCount: Int = 0, warningCount: Int = 0): LintStats {
            return LintStats(errorCount, warningCount, 0, 0, 0, 0)
        }

        fun create(
            incidents: List<Incident>,
            baselines: List<LintBaseline>
        ): LintStats {
            var errorCount = 0
            var warningCount = 0
            var autofixed = 0
            var hasAutoFixCount = 0
            for (incident in incidents) {
                if (incident.severity === Severity.ERROR || incident.severity === Severity.FATAL) {
                    errorCount++
                } else if (incident.severity === Severity.WARNING) {
                    warningCount++
                }

                if (incident.wasAutoFixed) {
                    autofixed++
                }
                if (incident.hasAutoFix()) {
                    hasAutoFixCount++
                }
            }

            // Compute baseline counts. This is tricky because an error could appear in
            // multiple variants, and in that case it should only be counted as filtered
            // from the baseline once, but if there are errors that appear only in individual
            // variants, then they shouldn't count as one. To correctly account for this we
            // need to ask the baselines themselves to merge their results. Right now they
            // only contain the remaining (fixed) issues; to address this we'd need to move
            // found issues to a different map such that at the end we can successively
            // merge the baseline instances together to a final one which has the full set
            // of filtered and remaining counts.
            var baselineErrorCount = 0
            var baselineWarningCount = 0
            var baselineFixedCount = 0
            if (baselines.isNotEmpty()) {
                // Figure out the actual overlap; later I could stash these into temporary
                // objects to compare
                // For now just combine them in a simplistic way
                for (baseline in baselines) {
                    baselineErrorCount = max(baselineErrorCount, baseline.foundErrorCount)
                    baselineWarningCount =
                        max(baselineWarningCount, baseline.foundWarningCount)
                    baselineFixedCount = max(baselineFixedCount, baseline.fixedCount)
                }
            }

            return LintStats(
                errorCount,
                warningCount,
                baselineErrorCount,
                baselineWarningCount,
                baselineFixedCount,
                autofixed,
                hasAutoFixCount
            )
        }
    }
}

/**
 * Whether this incident has a fix that can be automatically performed
 * without user intervention.
 */
fun Incident.hasAutoFix(): Boolean {
    val fixData = fix ?: return false
    return canAutoFix(fixData)
}
