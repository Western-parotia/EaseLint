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

import com.android.SdkConstants.EXT_JAR
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import java.io.File

const val FIR_UAST_KEY = "lint.use.fir.uast"
private fun useFirUast(): Boolean =
    System.getProperty(FIR_UAST_KEY, "false").toBoolean()

/**
 * This interface provides the setup and configuration needed to use
 * VFS/PSI/UAST on the command line.
 *
 * Basic usage:
 * 1. Create a configuration via [UastEnvironment.Configuration.create]
 *    and mutate it as needed.
 * 2. Create a project environment via [UastEnvironment.create]. You can
 *    create multiple environments in the
 *    same process (one for each "module").
 * 3. Call [analyzeFiles] to initialize PSI machinery and
 *    frontend-specific pre-computation.
 * 4. Analyze PSI/UAST.
 * 5. When finished, call [dispose].
 * 6. Once *all* [UastEnvironment]s are disposed, call
 *    [disposeApplicationEnvironment] to clean up some global
 *    resources, especially if running in a long-living daemon process.
 */
interface UastEnvironment {
    val projectDisposable: Disposable

    val coreAppEnv: CoreApplicationEnvironment

    val ideaProject: MockProject

    val kotlinCompilerConfig: CompilerConfiguration

    /**
     * A configuration is just a container for the classpath, compiler
     * flags, etc.
     */
    interface Configuration {
        companion object {
            /**
             * Creates a new [Configuration] that specifies project
             * structure, classpath, compiler flags, etc.
             */
            @JvmStatic
            @JvmOverloads
            fun create(
                enableKotlinScripting: Boolean = true,
                useFirUast: Boolean = useFirUast(),
            ): Configuration {
                return if (useFirUast)
                    FirUastEnvironment.Configuration.create(enableKotlinScripting)
                else
                    Fe10UastEnvironment.Configuration.create(enableKotlinScripting)
            }
        }

        val kotlinCompilerConfig: CompilerConfiguration

        fun addSourceRoots(sourceRoots: List<File>) {
            // Note: the Kotlin compiler would normally add KotlinSourceRoots to the configuration
            // too, to be used by KotlinCoreEnvironment when computing the set of KtFiles to
            // analyze. However, Lint already computes the list of KtFiles on its own in LintDriver.
            kotlinCompilerConfig.addJavaSourceRoots(sourceRoots)
            if (Configuration::class.java.desiredAssertionStatus()) {
                for (root in sourceRoots) {
                    // The equivalent assertion in JavaCoreProjectEnvironment.addSourcesToClasspath
                    // happens too late to be useful.
                    assert(root.extension != EXT_JAR) {
                        "Jar files should be added as classpath roots, not as source roots: $root"
                    }
                }
            }
        }

        fun addClasspathRoots(classpathRoots: List<File>) {
            kotlinCompilerConfig.addJvmClasspathRoots(classpathRoots)
        }

        // Defaults to LanguageLevel.HIGHEST.
        var javaLanguageLevel: LanguageLevel?

        // Defaults to LanguageVersionSettingsImpl.DEFAULT.
        var kotlinLanguageLevel: LanguageVersionSettings
            get() = kotlinCompilerConfig.languageVersionSettings
            set(value) {
                kotlinCompilerConfig.languageVersionSettings = value
            }
    }

    companion object {
        /**
         * Creates a new [UastEnvironment] suitable for analyzing
         * both Java and Kotlin code. You must still call
         * [UastEnvironment.analyzeFiles] before doing anything
         * with PSI/UAST. When finished using the environment, call
         * [UastEnvironment.dispose].
         */
        @JvmStatic
        fun create(
            config: Configuration,
        ): UastEnvironment {
            return when (config) {
                is FirUastEnvironment.Configuration ->
                    FirUastEnvironment.create(config)

                else ->
                    Fe10UastEnvironment.create(config)
            }
        }

        /**
         * Disposes the global application environment, which is created
         * implicitly by the first [UastEnvironment]. Only call this
         * once *all* [UastEnvironment]s have been disposed.
         */
        @JvmStatic
        fun disposeApplicationEnvironment() {
            // Note: if we later decide to keep the app env alive forever in the Gradle daemon, we
            // should still clear some caches between builds (see CompileServiceImpl.clearJarCache).
            val appEnv = KotlinCoreEnvironment.applicationEnvironment ?: return
            Disposer.dispose(appEnv.parentDisposable)
            checkApplicationEnvironmentDisposed()
            ZipHandler.clearFileAccessorCache()
        }

        @JvmStatic
        fun checkApplicationEnvironmentDisposed() {
            check(KotlinCoreEnvironment.applicationEnvironment == null)
        }
    }

    /**
     * Analyzes the given files so that PSI/UAST resolve works
     * correctly.
     */
    fun analyzeFiles(ktFiles: List<File>)

    fun dispose() {
        Disposer.dispose(projectDisposable)
    }
}
