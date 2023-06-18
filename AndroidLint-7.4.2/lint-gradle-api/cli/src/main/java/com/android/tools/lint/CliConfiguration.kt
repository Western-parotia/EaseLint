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

import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.FlagConfiguration
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.getSeverity

/**
 * Consult the lint.xml file, but override with the --enable and
 * --disable flags supplied on the command line (as well as any other
 * applicable [LintCliFlags])
 */
open class CliConfiguration(
    configurations: ConfigurationHierarchy,
    private val flags: LintCliFlags,
    private val fatalOnly: Boolean
) : FlagConfiguration(configurations) {
    override fun fatalOnly(): Boolean = fatalOnly
    override fun isWarningsAsErrors(): Boolean = flags.isWarningsAsErrors
    override fun isIgnoreWarnings(): Boolean = flags.isIgnoreWarnings
    override fun isCheckAllWarnings(): Boolean = flags.isCheckAllWarnings
    override fun enabledIds(): Set<String> = flags.enabledIds
    override fun disabledIds(): Set<String> = flags.suppressedIds
    override fun exactCheckedIds(): Set<String>? = flags.exactCheckedIds
    override fun disabledCategories(): Set<Category>? = flags.disabledCategories
    override fun enabledCategories(): Set<Category>? = flags.enabledCategories
    override fun exactCategories(): Set<Category>? = flags.exactCategories
    override fun severityOverride(issue: Issue): Severity? =
        flags.severityOverrides[issue.id]?.getSeverity(issue)

    override fun allowSuppress(): Boolean = flags.allowSuppress
    override fun severityOverrides(): Set<String> = flags.severityOverrides.keys
}
