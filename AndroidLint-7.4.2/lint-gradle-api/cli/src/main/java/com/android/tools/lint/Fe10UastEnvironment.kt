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

import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_SRCJAR
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.impl.PsiNameHelperImpl
import com.intellij.util.io.URLUtil.JAR_SEPARATOR
import org.jetbrains.kotlin.asJava.classes.FacadeCache
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.CliModuleAnnotationsResolver
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import java.io.File
import kotlin.concurrent.withLock

/** This class is FE1.0 version of [UastEnvironment]. */
class Fe10UastEnvironment private constructor(
    // Luckily, the Kotlin compiler already has the machinery for creating an IntelliJ
    // application environment (because Kotlin uses IntelliJ to parse Java). So most of
    // the work here is delegated to the Kotlin compiler.
    private val kotlinCompilerEnv: KotlinCoreEnvironment,
    override val projectDisposable: Disposable
) : UastEnvironment {
    override val coreAppEnv: CoreApplicationEnvironment
        get() = kotlinCompilerEnv.projectEnvironment.environment

    override val ideaProject: MockProject
        get() = kotlinCompilerEnv.projectEnvironment.project

    override val kotlinCompilerConfig: CompilerConfiguration
        get() = kotlinCompilerEnv.configuration

    class Configuration private constructor(
        override val kotlinCompilerConfig: CompilerConfiguration
    ) : UastEnvironment.Configuration {
        override var javaLanguageLevel: LanguageLevel? = null

        companion object {
            @JvmStatic
            fun create(enableKotlinScripting: Boolean): Configuration =
                Configuration(createKotlinCompilerConfig(enableKotlinScripting))
        }
    }

    /**
     * Analyzes the given files so that PSI/UAST resolve works
     * correctly.
     *
     * For now, only Kotlin files need to be analyzed upfront; Java code
     * is resolved lazily. However, this method must still be called
     * for Java-only projects in order to properly initialize the PSI
     * machinery.
     *
     * Calling this function multiple times clears previous analysis
     * results.
     */
    override fun analyzeFiles(ktFiles: List<File>) {
        val ktPsiFiles = mutableListOf<KtFile>()

        // Convert files to KtFiles.
        val fs = StandardFileSystems.local()
        val psiManager = PsiManager.getInstance(ideaProject)
        for (ktFile in ktFiles) {
            val vFile = fs.findFileByPath(ktFile.absolutePath) ?: continue
            val ktPsiFile = psiManager.findFile(vFile) as? KtFile ?: continue
            ktPsiFiles.add(ktPsiFile)
        }

        // TODO: This is a hack to get resolve working for Kotlin declarations in srcjars,
        //  which has historically been needed in google3. We should investigate whether this is
        //  still needed. In particular, we should ensure we do not add srcjars from dependencies,
        //  because that could lead to a lot of extra work for the compiler.
        //  Note: srcjars are tested by ApiDetectorTest.testSourceJars() and testSourceJarsKotlin().
        addKtFilesFromSrcJars(ktPsiFiles)

        // TODO: This is a hack needed because TopDownAnalyzerFacadeForJVM calls
        //  KotlinCoreEnvironment.createPackagePartProvider(), which permanently adds additional
        //  PackagePartProviders to the environment. This significantly slows down resolve over
        //  time. The root issue is that KotlinCoreEnvironment was not designed to be reused
        //  repeatedly for multiple analyses---which we do when checkDependencies=true. This hack
        //  should be removed when we move to a model where UastEnvironment is used only once.
        resetPackagePartProviders()

        // TODO: This is a temporary hotfix for b/159733104.
        ideaProject.picoContainer.unregisterComponent(FacadeCache::class.java.name)
        ideaProject.registerService(FacadeCache::class.java, FacadeCache(ideaProject))

        val perfManager = kotlinCompilerConfig.get(CLIConfigurationKeys.PERF_MANAGER)
        perfManager?.notifyAnalysisStarted()

        // Run the Kotlin compiler front end.
        // The result is implicitly associated with the IntelliJ project environment.
        // TODO: Consider specifying a sourceModuleSearchScope, which can be used to support
        //  partial compilation by giving the Kotlin compiler access to the compiled output
        //  of the module being analyzed. See KotlinToJVMBytecodeCompiler for an example.
        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            ideaProject,
            ktPsiFiles,
            CliBindingTraceForLint(),
            kotlinCompilerConfig,
            kotlinCompilerEnv::createPackagePartProvider
        )

        perfManager?.notifyAnalysisFinished()
    }

    private fun addKtFilesFromSrcJars(out: MutableList<KtFile>) {
        val jarFs = StandardFileSystems.jar()
        val psiManager = PsiManager.getInstance(ideaProject)
        val roots = kotlinCompilerConfig.getList(CLIConfigurationKeys.CONTENT_ROOTS)

        for (root in roots) {
            // Check if this is a srcjar.
            if (root !is JavaSourceRoot) continue
            if (!root.file.name.endsWith(DOT_SRCJAR)) continue
            val jarRoot = jarFs.findFileByPath(root.file.path + JAR_SEPARATOR) ?: continue

            // Collect Kotlin files.
            VfsUtilCore.iterateChildrenRecursively(jarRoot, null) { file ->
                if (file.name.endsWith(DOT_KT) || file.name.endsWith(DOT_KTS)) {
                    val psiFile = psiManager.findFile(file)
                    if (psiFile is KtFile) {
                        out.add(psiFile)
                    }
                }
                true // Continues the traversal.
            }
        }
    }

    private fun resetPackagePartProviders() {
        run {
            // Clear KotlinCoreEnvironment.packagePartProviders.
            val field = KotlinCoreEnvironment::class.java.getDeclaredField("packagePartProviders")
            field.isAccessible = true
            val list = field.get(kotlinCompilerEnv) as MutableList<*>
            list.clear()
        }
        run {
            // Clear CliModuleAnnotationsResolver.packagePartProviders.
            val field =
                CliModuleAnnotationsResolver::class.java.getDeclaredField("packagePartProviders")
            field.isAccessible = true
            val instance = ModuleAnnotationsResolver.getInstance(ideaProject)
            val list = field.get(instance) as MutableList<*>
            list.clear()
        }
    }

    companion object {
        @JvmStatic
        fun create(config: UastEnvironment.Configuration): Fe10UastEnvironment {
            val parentDisposable = Disposer.newDisposable("Fe10UastEnvironment.create")
            val kotlinEnv = createKotlinCompilerEnv(parentDisposable, config)
            return Fe10UastEnvironment(kotlinEnv, parentDisposable)
        }
    }
}

private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
    val config = createCommonKotlinCompilerConfig()

    config.put(JVMConfigurationKeys.NO_JDK, true)

    // Registers the scripting compiler plugin to support build.gradle.kts files.
    if (enableKotlinScripting) {
        config.add(
            ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS,
            ScriptingCompilerConfigurationComponentRegistrar()
        )
    }

    return config
}

private fun createKotlinCompilerEnv(
    parentDisposable: Disposable,
    config: UastEnvironment.Configuration
): KotlinCoreEnvironment {
    // We don't bundle .dll files in the Gradle plugin for native file system access;
    // prevent warning logs on Windows when it's not found (see b.android.com/260180).
    System.setProperty("idea.use.native.fs.for.win", "false")

    // By default the Kotlin compiler will dispose the application environment when there
    // are no projects left. However, that behavior is poorly tested and occasionally buggy
    // (see KT-45289). So, instead we manage the application lifecycle manually.
    CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"

    val env = KotlinCoreEnvironment
        .createForProduction(parentDisposable, config.kotlinCompilerConfig, JVM_CONFIG_FILES)
    appLock.withLock { configureFe10ApplicationEnvironment(env.projectEnvironment.environment) }
    configureFe10ProjectEnvironment(env.projectEnvironment.project, config)

    return env
}

private fun configureFe10ProjectEnvironment(
    project: MockProject,
    config: UastEnvironment.Configuration
) {
    // UAST support.
    @Suppress("DEPRECATION") // TODO: Migrate to using UastFacade instead.
    project.registerService(UastContext::class.java, UastContext(project))
    AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())
    project.registerService(
        KotlinUastResolveProviderService::class.java,
        CliKotlinUastResolveProviderService::class.java
    )

    // PsiNameHelper is used by Kotlin UAST.
    project.registerService(PsiNameHelper::class.java, PsiNameHelperImpl::class.java)

    configureProjectEnvironment(project, config)
}

private fun configureFe10ApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
    configureApplicationEnvironment(appEnv) {
        it.addExtension(UastLanguagePlugin.extensionPointName, KotlinUastLanguagePlugin())

        it.application.registerService(
            BaseKotlinUastResolveProviderService::class.java,
            CliKotlinUastResolveProviderService::class.java
        )
    }
}

// A Kotlin compiler BindingTrace optimized for Lint.
private class CliBindingTraceForLint : CliBindingTrace() {
    override fun <K, V> record(slice: WritableSlice<K, V>, key: K, value: V) {
        // Copied from NoScopeRecordCliBindingTrace.
        when (slice) {
            BindingContext.LEXICAL_SCOPE,
            BindingContext.DATA_FLOW_INFO_BEFORE -> return
        }
        super.record(slice, key, value)
    }

    // Lint does not need compiler checks, so disable them to improve performance slightly.
    override fun wantsDiagnostics(): Boolean = false

    override fun report(diagnostic: Diagnostic) {
        // Even with wantsDiagnostics=false, some diagnostics still come through. Ignore them.
        // Note: this is a great place to debug errors such as unresolved references.
    }
}
