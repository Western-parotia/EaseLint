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
@file:JvmName("SyncOptions")

package com.android.tools.lint

import com.android.tools.lint.detector.api.Category.Companion.getCategory
import com.android.tools.lint.model.LintModelModule

// Operations related to syncing LintOptions to lint's internal state

fun syncTo(project: LintModelModule, flags: LintCliFlags) {
    val options = project.lintOptions
    val disabled = options.disable
    if (disabled.isNotEmpty()) {
        for (id in disabled) {
            val category = getCategory(id)
            if (category != null) {
                // Disabling a whole category
                flags.addDisabledCategory(category)
            } else {
                flags.suppressedIds.add(id)
            }
        }
    }
    val enabled = options.enable
    if (enabled.isNotEmpty()) {
        for (id in enabled) {
            val category = getCategory(id)
            if (category != null) {
                // Enabling a whole category
                flags.addEnabledCategory(category)
            } else {
                flags.enabledIds.add(id)
            }
        }
    }
    val check = options.check
    if (check != null && check.isNotEmpty()) {
        for (id in check) {
            val category = getCategory(id)
            if (category != null) {
                // Checking a whole category
                flags.addExactCategory(category)
            } else {
                flags.addExactId(id)
            }
        }
    }
    flags.isSetExitCode = options.abortOnError
    flags.isFullPath = options.absolutePaths
    flags.isShowSourceLines = !options.noLines
    flags.isQuiet = options.quiet
    flags.isCheckAllWarnings = options.checkAllWarnings
    flags.isIgnoreWarnings = options.ignoreWarnings
    flags.isWarningsAsErrors = options.warningsAsErrors
    flags.isCheckTestSources = options.checkTestSources
    flags.isIgnoreTestSources = options.ignoreTestSources
    flags.isIgnoreTestFixturesSources = options.ignoreTestFixturesSources
    flags.isCheckGeneratedSources = options.checkGeneratedSources
    flags.isCheckDependencies = options.checkDependencies
    flags.isShowEverything = options.showAll
    flags.lintConfig = options.lintConfig
    flags.isExplainIssues = options.explainIssues
    flags.baselineFile = options.baselineFile
    val severityOverrides = options.severityOverrides
    if (severityOverrides != null) {
        flags.severityOverrides = severityOverrides
    } else {
        flags.severityOverrides = emptyMap()
    }
}
