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

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.analysis.api.standalone.configureApplicationEnvironment
import org.jetbrains.kotlin.analysis.api.standalone.configureProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService
import java.io.File
import kotlin.concurrent.withLock

/** This class is FIR version of [UastEnvironment] */
class FirUastEnvironment private constructor(
    // TODO: we plan to redesign Analysis API facade to not use KotlinCoreEnvironment (which is FE1.0 specific)
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

    /** In FIR UAST, even Kotlin files are analyzed lazily. */
    override fun analyzeFiles(ktFiles: List<File>) {
        // TODO: addKtFilesFromSrcJars ?
    }

    companion object {
        @JvmStatic
        fun create(config: UastEnvironment.Configuration): FirUastEnvironment {
            val parentDisposable = Disposer.newDisposable("FirUastEnvironment.create")
            val kotlinEnv = createKotlinCompilerEnv(parentDisposable, config)
            return FirUastEnvironment(kotlinEnv, parentDisposable)
        }
    }
}

private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
    val config = createCommonKotlinCompilerConfig()

    // TODO: NO_JDK ?

    // TODO: if [enableKotlinScripting], register FIR version of scripting compiler plugin if any

    return config
}

private fun createKotlinCompilerEnv(
    parentDisposable: Disposable,
    config: UastEnvironment.Configuration
): KotlinCoreEnvironment {
    // TODO: will use Analysis API facade instead
    val env = KotlinCoreEnvironment
        .createForProduction(
            parentDisposable,
            config.kotlinCompilerConfig,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    appLock.withLock { configureFirApplicationEnvironment(env.projectEnvironment.environment) }
    configureFirProjectEnvironment(env, config)

    return env
}

private fun configureFirProjectEnvironment(
    env: KotlinCoreEnvironment,
    config: UastEnvironment.Configuration
) {
    val project = env.projectEnvironment.project
    val jarFileSystem = env.projectEnvironment.environment.jarFileSystem as CoreJarFileSystem
    // TODO: will use Analysis API facade instead
    configureProjectEnvironment(
        project,
        config.kotlinCompilerConfig,
        env::createPackagePartProvider,
        jarFileSystem
    )

    project.registerService(
        FirKotlinUastResolveProviderService::class.java,
        FirCliKotlinUastResolveProviderService::class.java
    )

    configureProjectEnvironment(project, config)
}

private fun configureFirApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
    configureApplicationEnvironment(appEnv) {
        // TODO: will use Analysis API facade instead
        configureApplicationEnvironment(it.application)

        it.addExtension(UastLanguagePlugin.extensionPointName, FirKotlinUastLanguagePlugin())

        it.application.registerService(
            BaseKotlinUastResolveProviderService::class.java,
            FirCliKotlinUastResolveProviderService::class.java
        )
    }
}
