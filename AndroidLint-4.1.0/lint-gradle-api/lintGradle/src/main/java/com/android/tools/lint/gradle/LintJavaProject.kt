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
package com.android.tools.lint.gradle

import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.detector.api.Project
import com.intellij.pom.java.LanguageLevel
import java.io.File

/** Lint project wrapping a non-AGP Gradle project, such as a plain Kotlin or Java project  */
class LintJavaProject(
    lintClient: LintGradleClient?,
    projectDir: File?,
    dependencies: List<Project>,
    sources: List<File>,
    classes: List<File>,
    libs: List<File>,
    tests: List<File>,
    private val languageLevel: LanguageLevel?
) : Project(lintClient!!, projectDir!!, projectDir) {

    init {
        gradleProject = true
        mergeManifests = true
        directLibraries = dependencies
        javaSourceFolders = sources
        javaClassFolders = classes
        javaLibraries = libs
        testSourceFolders = tests
    }

    override fun initialize() {
        // Deliberately not calling super; that code is for ADT compatibility
    }

    override fun isLibrary(): Boolean = true

    override fun isGradleProject(): Boolean = true

    override fun isAndroidProject(): Boolean = false

    override fun getBuildTarget(): IAndroidTarget? = null

    override fun getJavaLanguageLevel(): LanguageLevel {
        return languageLevel ?: super.getJavaLanguageLevel()
    }
}
