package com.android.tools.lint.client.api

/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import java.io.File
import java.util.*

/** Information about a request to run lint. */
open class LintRequest(
    /**
     * The lint client requesting the lint check
     *
     * @return the client, never null
     */
    val client: LintClient,

    /**
     * The set of files to check with lint. This can reference Android
     * projects, or directories containing Android projects, or
     * individual XML or Java files (typically for incremental IDE
     * analysis).
     */
    val files: List<File>
) {

    /** The root directory containing all the projects. */
    var srcRoot: File? = null

    @JvmField
    protected var scope: EnumSet<Scope>? = null

    @JvmField
    protected var platform: EnumSet<Platform>? = null

    @JvmField
    protected var releaseMode: Boolean? = null

    /**
     * The projects for the lint requests. This is optional; if not
     * provided lint will search the [files] directories and look for
     * projects via [LintClient.isProjectDirectory]. However, this
     * method allows a lint client to set up all the projects ahead of
     * time, and associate those projects with native resources (in an
     * IDE for example, each lint project can be associated with the
     * corresponding IDE project).
     */
    @JvmField
    protected var projects: Collection<Project>? = null

    /**
     * Gets the scope to use; lint checks which require a wider scope
     * set will be ignored
     *
     * @return the scope to use, or null to use the default
     */
    open fun getScope(): EnumSet<Scope>? = scope

    /**
     * Sets the scope to use; lint checks which require a wider scope
     * set will be ignored
     *
     * @param scope the scope
     * @return this, for constructor chaining
     */
    fun setScope(scope: EnumSet<Scope>?): LintRequest {
        this.scope = scope
        return this
    }

    /**
     * Gets the platforms that apply to this lint analysis
     *
     * @return the platforms to use, or null to use the default
     */
    open fun getPlatform(): EnumSet<Platform>? = platform

    /**
     * Sets the platforms that apply to this lint analysis
     *
     * @return this, for constructor chaining
     */
    fun setPlatform(platform: EnumSet<Platform>?): LintRequest {
        this.platform = platform
        return this
    }

    /**
     * Returns `true` if lint is invoked as part of a release mode
     * build, `false` if it is part of a debug mode build, and `null` if
     * the release mode is not known
     *
     * @return true if this lint is running in release mode, null if not
     *     known
     */
    fun isReleaseMode(): Boolean? = releaseMode

    /**
     * Sets the release mode. Use `true` if lint is invoked as part of a
     * release mode build, `false` if it is part of a debug mode build,
     * and `null` if the release mode is not known
     *
     * @param releaseMode true if this lint is running in release mode,
     *     null if not known
     * @return this, for constructor chaining
     */
    fun setReleaseMode(releaseMode: Boolean?): LintRequest {
        this.releaseMode = releaseMode
        return this
    }

    /**
     * Returns the project to be used as the main project during
     * analysis. This is usually the project itself, but when you are
     * for example analyzing a library project, it can be the app
     * project using the library.
     *
     * @param project the project to look up the main project for
     * @return the main project
     */
    open fun getMainProject(project: Project): Project = project

    open fun getProjects(): Collection<Project>? = projects

    fun setProjects(projects: Collection<Project>?): LintRequest {
        val project = projects?.first()
        project?.addFile(
            File(
                "/Volumes/D/CodeProject/AndroidProject/EaseLint-7.0/" +
                        "AndroidLint-7.4.2/lint-gradle-api/app/src/main/java/" +
                        "com/easelint/gradle/SubModuleKotlinPrint.kt"
            )
        )
        project?.addFile(
            File(
                "/Volumes/D/CodeProject/AndroidProject/EaseLint-7.0/" +
                        "AndroidLint-7.4.2/lint-gradle-api/app/src/main/java/" +
                        "com/easelint/gradle/JavaParse.java"
            )
        )

        this.projects = projects
        println("========= easeLint cover LintRequest ==========")
        return this
    }
}
