/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintClient
import com.google.common.collect.Sets
import com.intellij.codeInsight.ContainerProvider
import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint
import com.intellij.core.CoreJavaFileManager
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.psi.FileContextProvider
import com.intellij.psi.JavaModuleSystem
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.augment.TypeAnnotationModifier
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.meta.MetaDataContributor
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.cli.common.CliModuleVisibilityManagerImpl
import org.jetbrains.kotlin.cli.common.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.extensions.CompilerConfigurationExtension
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import java.io.File
import java.util.ArrayList

/**
 * Modeled after KotlinCoreEnvironment, but written to reuse as much of it as possible, this
 * class is responsible for configuring the PSI and UAST environment for command line usage
 * in lint (and various other command line tools that need access to PSI, such as the
 * annotation extractions code in the Android Gradle plugin, as well as in Metalava.
 * <p>
 * This class tries to delegate as much as possible to KotlinCoreEnvironment for registration
 * of extension points and services, since those change regularly and KotlinCoreEnvironment
 * is updated along with the compiler; if lint had inlined its own copy this would easily
 * get stale and we'd miss some important initialization.
 */
class UastEnvironment private constructor(
    val disposable: Disposable,
    val projectEnvironment: ProjectEnvironment,
    initialConfiguration: CompilerConfiguration
) {
    class ProjectEnvironment(
        disposable: Disposable,
        applicationEnvironment: KotlinCoreApplicationEnvironment
    ) : KotlinCoreProjectEnvironment(disposable, applicationEnvironment) {

        private var extensionRegistered = false

        override fun preregisterServices() {
            KotlinCoreEnvironment.registerProjectExtensionPoints(project.extensionArea)
        }

        fun registerExtensionsFromPlugins(configuration: CompilerConfiguration) {
            if (!extensionRegistered) {
                KotlinCoreEnvironment.registerPluginExtensionPoints(project)
                KotlinCoreEnvironment.registerExtensionsFromPlugins(project, configuration)
                AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())

                extensionRegistered = true
            }
        }

        override fun registerJavaPsiFacade() {
            with(project) {
                registerService(
                    CoreJavaFileManager::class.java,
                    ServiceManager.getService(
                        this,
                        JavaFileManager::class.java
                    ) as CoreJavaFileManager
                )

                KotlinCoreEnvironment.registerKotlinLightClassSupport(project)
            }

            val area = Extensions.getArea(project)
            registerProjectExtensionPoints(area)
            registerProjectServices(this)

            super.registerJavaPsiFacade()
        }

        private fun registerProjectExtensionPoints(area: ExtensionsArea) {
            registerExtensionPoint(
                area,
                UastLanguagePlugin.extensionPointName,
                UastLanguagePlugin::class.java
            )
        }

        private fun registerProjectServices(projectEnvironment: KotlinCoreProjectEnvironment) {
            val project = projectEnvironment.project
            with(project) {
                @Suppress("DEPRECATION") // client code may still look for it
                registerService(
                    UastContext::class.java,
                    UastContext(project)
                )
                registerService(
                    ExternalAnnotationsManager::class.java,
                    LintExternalAnnotationsManager::class.java
                )
                registerService(
                    InferredAnnotationsManager::class.java,
                    LintInferredAnnotationsManager::class.java
                )

                registerService(
                    KotlinUastResolveProviderService::class.java,
                    CliKotlinUastResolveProviderService() // or CliKotlinUastResolveProviderService::class.java
                )
            }
        }

        private val myPaths: MutableList<File> = ArrayList()
        val paths: List<File> get() = myPaths

        fun registerPaths(classpath: List<File>) {
            myPaths.addAll(classpath)
            val expectedSize = classpath.size
            val files: MutableSet<File> = Sets.newHashSetWithExpectedSize(expectedSize)
            val local = StandardFileSystems.local()
            for (path in classpath) {
                @Suppress("NAME_SHADOWING")
                var path = path
                if (files.contains(path)) {
                    continue
                }
                files.add(path)
                if (path.exists()) {
                    if (path.isFile) {
                        // Make sure these paths are absolute - nested jar file systems
                        //
                        // do not work correctly with relative paths (for example
                        // JavaPsiFacade.findClass will not find classes in these jar
                        // file systems.)
                        if (!path.isAbsolute) {
                            path = path.absoluteFile
                        }
                        val pathString = path.path
                        if (pathString.endsWith(SdkConstants.DOT_SRCJAR)) {
                            // Mount as source files
                            val root = StandardFileSystems.jar()
                                .findFileByPath(pathString + URLUtil.JAR_SEPARATOR)
                            if (root != null) {
                                addSourcesToClasspath(root)
                                continue
                            }
                        }
                        addJarToClassPath(path)
                    } else if (path.isDirectory) {
                        val virtualFile = local.findFileByPath(path.path)
                        virtualFile?.let { addSourcesToClasspath(it) }
                    }
                }
            }
        }
    }

    val configuration: CompilerConfiguration = initialConfiguration.copy()

    init {
        val project = projectEnvironment.project
        projectEnvironment.registerExtensionsFromPlugins(configuration)
        KotlinCoreEnvironment.registerProjectServicesForCLI(projectEnvironment)
        KotlinCoreEnvironment.registerProjectServices(projectEnvironment.project)

        project.registerService(
            ModuleVisibilityManager::class.java,
            CliModuleVisibilityManagerImpl(false)
        )

        for (extension in CompilerConfigurationExtension.getInstances(project)) {
            extension.updateConfiguration(configuration)
        }
    }

    val project: Project
        get() = projectEnvironment.project

    companion object {
        private val DEBUGGING = LintClient.isUnitTest
        private val APPLICATION_LOCK = Object()
        private var ourApplicationEnvironment: KotlinCoreApplicationEnvironment? = null
        private var ourCreator: Throwable? = null
        private var ourProjectCount = 0

        @JvmStatic
        fun create(
            parentDisposable: Disposable
        ): UastEnvironment {
            val configuration = CompilerConfiguration()
            configuration.put(JVMConfigurationKeys.NO_JDK, true)
            configuration.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")
            configuration.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
            return createForProduction(parentDisposable, configuration)
        }

        @JvmStatic
        fun createForProduction(
            parentDisposable: Disposable,
            configuration: CompilerConfiguration
        ): UastEnvironment {
            val appEnv =
                getOrCreateApplicationEnvironmentForProduction(parentDisposable, configuration)
            val projectEnv = ProjectEnvironment(parentDisposable, appEnv)
            val environment =
                UastEnvironment(parentDisposable, projectEnv, configuration)

            synchronized(APPLICATION_LOCK) {
                ourProjectCount++
            }
            return environment
        }

        @TestOnly
        @JvmStatic
        fun createForTests(
            parentDisposable: Disposable,
            initialConfiguration: CompilerConfiguration
        ): UastEnvironment {
            val configuration = initialConfiguration.copy()
            // Tests are supposed to create a single project and dispose it right after use
            val appEnv =
                KotlinCoreEnvironment.createApplicationEnvironment(
                    parentDisposable,
                    configuration,
                    unitTestMode = true
                )
            val projectEnv = ProjectEnvironment(parentDisposable, appEnv)
            return UastEnvironment(parentDisposable, projectEnv, configuration)
        }

        val applicationEnvironment: KotlinCoreApplicationEnvironment?
            get() = ourApplicationEnvironment

        private fun getOrCreateApplicationEnvironmentForProduction(
            parentDisposable: Disposable,
            configuration: CompilerConfiguration
        ): KotlinCoreApplicationEnvironment {
            // We don't bundle .dll files in the Gradle plugin for native file system access;
            // prevent warning logs on Windows when it's not found (see b.android.com/260180)
            System.setProperty("idea.use.native.fs.for.win", "false")
            synchronized(APPLICATION_LOCK) {
                if (ourApplicationEnvironment == null) {
                    if (!Logger.isInitialized()) {
                        Logger.setFactory(::IdeaLoggerForLint)
                    }
                    Registry.getInstance().markAsLoaded()
                    System.setProperty("idea.plugins.compatible.build", "201.7223.91")
                    val disposable = Disposer.newDisposable()
                    ourApplicationEnvironment = KotlinCoreEnvironment.createApplicationEnvironment(
                        disposable,
                        configuration,
                        unitTestMode = false
                    )
                    registerAppExtensionPoints()
                    registerAppExtensions(disposable)
                    if (DEBUGGING && ourCreator == null) {
                        ourCreator = Throwable()
                    }
                    ourProjectCount = 0
                    Disposer.register(disposable, Disposable {
                        synchronized(APPLICATION_LOCK) {
                            ourApplicationEnvironment = null
                            ourCreator = null
                        }
                    })
                }

                // NOTE -- keep-alive may be enabled in Gradle, but in ReflectiveLintRunner
                // we dispose it anyway!
                // ----
                // From KotlinCoreEnvironment:
                // Disposing of the environment is unsafe in production when parallel
                // builds are enabled, but turning it off universally
                // breaks a lot of tests, therefore it is disabled for production and
                // enabled for tests
                val keepAlive = System.getProperty(KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY)
                if (keepAlive.toBooleanLenient() != true) {
                    // JPS may run many instances of the compiler in parallel (there's an option
                    // for compiling independent modules in parallel in IntelliJ)
                    // All projects share the same ApplicationEnvironment, and when the
                    // last project is disposed, the ApplicationEnvironment is disposed
                    // as well
                    Disposer.register(parentDisposable, Disposable {
                        synchronized(APPLICATION_LOCK) {
                            if (--ourProjectCount <= 0) {
                                disposeApplicationEnvironment()
                            }
                        }
                    })
                }

                return ourApplicationEnvironment!!
            }
        }

        @JvmStatic
        fun disposeApplicationEnvironment() {
            synchronized(APPLICATION_LOCK) {
                val environment = ourApplicationEnvironment ?: return
                ourApplicationEnvironment = null
                ourCreator = null
                Disposer.dispose(environment.parentDisposable)
                ZipHandler.clearFileAccessorCache()
            }
        }

        @JvmStatic
        fun ensureDisposed() {
            if (ourApplicationEnvironment != null) {
                System.err.println("Lint UAST environment not disposed")
                val creator = ourCreator
                disposeApplicationEnvironment()
                if (creator != null) {
                    creator.printStackTrace()
                    error("Application environment not disposed")
                }
            }
        }

        private fun registerAppExtensionPoints() {
            val rootArea = Extensions.getRootArea()
            registerExtensionPoint(
                rootArea,
                "com.intellij.filetype.stubBuilder",
                FileTypeExtensionPoint::class.java
            )
            registerExtensionPoint(
                rootArea,
                FileContextProvider.EP_NAME,
                FileContextProvider::class.java
            )
            registerExtensionPoint(
                rootArea,
                MetaDataContributor.EP_NAME,
                MetaDataContributor::class.java
            )
            registerExtensionPoint(
                rootArea,
                PsiAugmentProvider.EP_NAME,
                PsiAugmentProvider::class.java
            )
            registerExtensionPoint(
                rootArea,
                JavaMainMethodProvider.EP_NAME,
                JavaMainMethodProvider::class.java
            )
            registerExtensionPoint(
                rootArea,
                ContainerProvider.EP_NAME,
                ContainerProvider::class.java
            )
            registerExtensionPoint(
                rootArea,
                ClsCustomNavigationPolicy.EP_NAME,
                ClsCustomNavigationPolicy::class.java
            )
            registerExtensionPoint(
                rootArea,
                ClassFileDecompilers.EP_NAME,
                ClassFileDecompilers.Decompiler::class.java
            )
            registerExtensionPoint(
                rootArea,
                TypeAnnotationModifier.EP_NAME,
                TypeAnnotationModifier::class.java
            )
            registerExtensionPoint(rootArea, MetaLanguage.EP_NAME, MetaLanguage::class.java)
            registerExtensionPoint(
                rootArea,
                UastLanguagePlugin.extensionPointName,
                UastLanguagePlugin::class.java
            )
            registerExtensionPoint(
                rootArea,
                CustomExceptionHandler.KEY,
                CustomExceptionHandler::class.java
            )
            registerExtensionPoint(rootArea, JavaModuleSystem.EP_NAME, JavaModuleSystem::class.java)
            registerExtensionPoint(
                rootArea,
                DiagnosticSuppressor.EP_NAME,
                DiagnosticSuppressor::class.java
            )
            registerExtensionPoint(
                rootArea,
                UEvaluatorExtension.EXTENSION_POINT_NAME,
                UEvaluatorExtension::class.java
            )
        }

        private fun registerAppExtensions(disposable: Disposable) {
            val rootArea = Extensions.getRootArea()
            with(rootArea) {
                getExtensionPoint(UastLanguagePlugin.extensionPointName)
                    .registerExtension(JavaUastLanguagePlugin(), disposable)
                getExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME)
                    .registerExtension(KotlinEvaluatorExtension(), disposable)
                getExtensionPoint(UastLanguagePlugin.extensionPointName)
                    .registerExtension(KotlinUastLanguagePlugin(), disposable)
            }
        }

        // Most Logger.error() calls exist to trigger bug reports but are
        // otherwise recoverable. E.g. see commit 3260e41111 in the Kotlin compiler.
        // Thus we want to log errors to stderr but not throw exceptions (similar to the IDE).
        class IdeaLoggerForLint(category: String) : DefaultLogger(category) {
            override fun error(message: String?, t: Throwable?, vararg details: String?) {
                if (DEBUGGING) {
                    throw AssertionError(message, t)
                } else {
                    dumpExceptionsToStderr(message + attachmentsToString(t), t, *details)
                }
            }
        }
    }
}
