/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.GradleStyleMessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import java.util.concurrent.locks.ReentrantLock

internal fun createCommonKotlinCompilerConfig(): CompilerConfiguration {
    val config = CompilerConfiguration()

    config.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")

    // We're not running compiler checks, but we still want to register a logger
    // in order to see warnings related to misconfiguration.
    val logger = PrintingMessageCollector(System.err, GradleStyleMessageRenderer(), false)
    config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, logger)

    // The Kotlin compiler uses a fast, ASM-based class file reader.
    // However, Lint still relies on representing class files with PSI.
    config.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

    return config
}

internal fun configureProjectEnvironment(
    project: MockProject,
    config: UastEnvironment.Configuration
) {
    // Annotation support.
    project.registerService(
        ExternalAnnotationsManager::class.java,
        LintExternalAnnotationsManager::class.java
    )
    project.registerService(
        InferredAnnotationsManager::class.java,
        LintInferredAnnotationsManager::class.java
    )

    // Java language level.
    val javaLanguageLevel = config.javaLanguageLevel
    if (javaLanguageLevel != null) {
        LanguageLevelProjectExtension.getInstance(project).languageLevel = javaLanguageLevel
    }
}

// In parallel builds the Kotlin compiler will reuse the application environment
// (see KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction).
// So we need a lock to ensure that we only configure the application environment once.
internal val appLock = ReentrantLock()
private var appConfigured = false

internal fun configureApplicationEnvironment(
    appEnv: CoreApplicationEnvironment,
    configurator: (CoreApplicationEnvironment) -> Unit
) {
    check(appLock.isHeldByCurrentThread)

    if (appConfigured) return

    if (!Logger.isInitialized()) {
        Logger.setFactory(::IdeaLoggerForLint)
    }

    // Mark the registry as loaded, otherwise there are warnings upon registry value lookup.
    Registry.markAsLoaded()

    // The Kotlin compiler does not use UAST, so we must configure it ourselves.
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        UastLanguagePlugin.extensionPointName,
        UastLanguagePlugin::class.java
    )
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        UEvaluatorExtension.EXTENSION_POINT_NAME,
        UEvaluatorExtension::class.java
    )
    appEnv.addExtension(UastLanguagePlugin.extensionPointName, JavaUastLanguagePlugin())

    appEnv.addExtension(UEvaluatorExtension.EXTENSION_POINT_NAME, KotlinEvaluatorExtension())

    configurator(appEnv)

    // These extensions points seem to be needed too, probably because Lint
    // triggers different IntelliJ code paths than the Kotlin compiler does.
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        CustomExceptionHandler.KEY,
        CustomExceptionHandler::class.java
    )
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        DiagnosticSuppressor.EP_NAME,
        DiagnosticSuppressor::class.java
    )

    appConfigured = true
    Disposer.register(
        appEnv.parentDisposable,
        Disposable {
            appConfigured = false
        }
    )
}

// Most Logger.error() calls exist to trigger bug reports but are
// otherwise recoverable. E.g. see commit 3260e41111 in the Kotlin compiler.
// Thus we want to log errors to stderr but not throw exceptions (similar to the IDE).
private class IdeaLoggerForLint(category: String) : DefaultLogger(category) {
    override fun error(message: String?, t: Throwable?, vararg details: String?) {
        if (IdeaLoggerForLint::class.java.desiredAssertionStatus()) {
            throw AssertionError(message, t)
        } else {
            dumpExceptionsToStderr(message + attachmentsToString(t), t, *details)
        }
    }
}
