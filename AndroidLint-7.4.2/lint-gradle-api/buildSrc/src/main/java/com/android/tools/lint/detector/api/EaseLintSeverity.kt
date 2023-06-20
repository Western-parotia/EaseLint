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

package com.android.tools.lint.detector.api

import com.android.tools.lint.model.LintModelSeverity

/** Severity of an issue found by lint */
enum class Severity constructor(
    /**
     * A description of this severity suitable for display to the user.
     */
    val description: String
) {
    /** Ignore: The user doesn't want to see this issue. */
    IGNORE("Ignore"),

    /**
     * Information only: Might not be a problem, but the check has found
     * something interesting to say about the code.
     */
    INFORMATIONAL("Information"),

    /** Warning: Probably a problem. */
    WARNING("Warning"),

    /**
     * Errors: The issue is known to be a real error that must be
     * addressed.
     */
    ERROR("Error"),

    /**
     * Like [ERROR], but considered so critical that it should be
     * enforced whether the user ran analysis or not.
     *
     * For example, all lint checks are run when you run the "lint"
     * target from the Android Gradle plugin. But, if you run a "build
     * release" target (which creates an APK to be published), the
     * Gradle plugin will *also* invoke lint, analyzing just the subset
     * of issues that have severity [FATAL].
     *
     * The intention behind this is to add a facility which solves the
     * old problem where people have static analysis tools at their
     * disposal, but forget to run them. Lint is expensive, but so are
     * building release binaries, so we choose to automatically run lint
     * at release time. However, we don't want to make it impossible to
     * release your app without addressing all the potential errors,
     * so [FATAL] allows us to configure a set of issues that are
     * "hard enforced"; they're suppressible, but when reported we're
     * confident that they are real and significant issues, so we want
     * to force the developer to look into these.
     */
    FATAL("Fatal");

    /** Returns true if this severity is at least an error. */
    val isError: Boolean
        get() = this == ERROR || this == FATAL

    /**
     * The persistent name of this enum, which can be matched with
     * [fromName].
     */
    fun toName(): String {
        return name
    }

    override fun toString(): String = toName()

    companion object {
        /**
         * Looks up the severity corresponding to a given named
         * severity. The severity string should be one returned by
         * [toString].
         *
         * @param name the name to look up
         * @return the corresponding severity, or null if it is not a
         *     valid severity name
         */
        @JvmStatic
        fun fromName(name: String): Severity? {
            for (severity in values()) {
                if (severity.name.equals(name, ignoreCase = true)) {
                    return severity
                }
            }

            return null
        }

        /**
         * Returns the smallest / least severe of the two given
         * severities
         *
         * @param severity1 the first severity to compare
         * @param severity2 the second severity to compare
         * @return the least severe of the given severities
         */
        @JvmStatic
        fun min(severity1: Severity, severity2: Severity): Severity =
            if (severity1 < severity2) severity1 else severity2

        /**
         * Returns the largest / most severe of the two given severities
         *
         * @param severity1 the first severity to compare
         * @param severity2 the second severity to compare*
         * @return the most severe of the given severities
         */
        @JvmStatic
        fun max(severity1: Severity, severity2: Severity): Severity =
            if (severity1 > severity2) severity1 else severity2
    }
}

fun LintModelSeverity.getSeverity(issue: Issue?): Severity {
    return when (this) {
        LintModelSeverity.FATAL -> Severity.FATAL
        LintModelSeverity.ERROR -> Severity.ERROR
        LintModelSeverity.WARNING -> Severity.WARNING
        LintModelSeverity.INFORMATIONAL -> Severity.INFORMATIONAL
        LintModelSeverity.IGNORE -> Severity.IGNORE
        LintModelSeverity.DEFAULT_ENABLED -> issue?.defaultSeverity ?: Severity.WARNING
    }
}

fun Severity.getModelSeverity(): LintModelSeverity {
    return when (this) {
        Severity.FATAL -> LintModelSeverity.FATAL
        Severity.ERROR -> LintModelSeverity.ERROR
        Severity.WARNING -> LintModelSeverity.WARNING
        Severity.INFORMATIONAL -> LintModelSeverity.INFORMATIONAL
        Severity.IGNORE -> LintModelSeverity.IGNORE
    }
}