package com.buildsrc.lint

import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintMode
import com.android.build.gradle.internal.lint.ProjectInputs
import com.android.build.gradle.internal.lint.VariantWithTests
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getLintParallelBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.tools.lint.model.LintModelModuleType
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections

/**
easeLintTask 每次执行都是针对指定文件，且为主动执行，
不启用 @input,@output 等引入增量，这让执行结果永远不受，编译与gradle 缓存影响。
再无异常的情况下，通常也不会产生二次扫描，那么始终保持执行过程与结果是纯净的比象征性的提高
lint扫描速度要恰当
 */

abstract class EaseLintTask : AndroidLintAnalysisTask() {
    companion object {
        const val TASK_NAME = "easeLint"
    }

    override fun doTaskAction() {
        val parent =
            project.tasks.findByName("lintAnalyzeDebug") as AndroidLintAnalysisTask

        parent.lintTool.submit(
            mainClass = "com.android.tools.lint.Main",
            workerExecutor = parent.workerExecutor,
            arguments = generateCommandLineArguments(parent),
            android = parent.android.get(),
            fatalOnly = parent.fatalOnly.get(),
            await = false,
            lintMode = LintMode.ANALYSIS
        )
    }

    class SingleVariantCreationAction(variant: VariantWithTests) :
        ELVariantCreationAction(variant) {
        //        override val name = creationConfig.computeTaskName("lintAnalyze")
        override val name = "easeLint"
        override val fatalOnly = false
        override val description = "Run lint analysis on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<EaseLintTask>) {
            ELRegisterOutputArtifacts.registerOutputArtifacts(
                taskProvider,
                InternalArtifactType.LINT_PARTIAL_RESULTS,
                creationConfig.artifacts
            )
        }
    }

    object ELRegisterOutputArtifacts {
        const val LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE"
        const val ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE = "ANDROID_LINT_JARS"
        const val PARTIAL_RESULTS_DIR_NAME = "out"

        fun registerOutputArtifacts(
            taskProvider: TaskProvider<EaseLintTask>,
            internalArtifactType: InternalArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts
                .setInitialProvider(taskProvider, EaseLintTask::partialResultsDirectory)
                .withName(PARTIAL_RESULTS_DIR_NAME)
                .on(internalArtifactType)
        }

    }

    private fun ELInitializeGlobalInputs(
        services: TaskCreationServices,
        isAndroid: Boolean
    ) {
        val buildServiceRegistry = services.buildServiceRegistry
        this.androidGradlePluginVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        val sdkComponentsBuildService =
            getBuildService(buildServiceRegistry, SdkComponentsBuildService::class.java)

        this.android.setDisallowChanges(isAndroid)
        if (isAndroid) {
            this.androidSdkHome.set(
                sdkComponentsBuildService.flatMap { it.sdkDirectoryProvider }
                    .map { it.asFile.absolutePath }
            )
        }
        this.androidSdkHome.disallowChanges()
        this.offline.setDisallowChanges(project.gradle.startParameter.isOffline)

        // Include Lint jars set via the environment variable ANDROID_LINT_JARS
        val globalLintJarsFromEnvVariable: Provider<List<String>> =
            project.providers.environmentVariable(ELRegisterOutputArtifacts.ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE)
                .orElse("")
                .map { it.split(File.pathSeparator).filter(String::isNotEmpty) }
        this.lintRuleJars.from(globalLintJarsFromEnvVariable)

        if (project.gradle.startParameter.showStacktrace != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            printStackTrace.setDisallowChanges(true)
        } else {
            printStackTrace.setDisallowChanges(
                project.providers
                    .environmentVariable(ELRegisterOutputArtifacts.LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE)
                    .map { it.equals("true", ignoreCase = true) }
                    .orElse(false)
            )
        }
        systemPropertyInputs.initialize(project.providers, LintMode.ANALYSIS)
        environmentVariableInputs.initialize(project.providers, LintMode.ANALYSIS)
        this.usesService(
            services.buildServiceRegistry.getLintParallelBuildService(services.projectOptions)
        )
    }

    abstract class ELVariantCreationAction(val variant: VariantWithTests) :
        VariantTaskCreationAction<EaseLintTask,
                ComponentCreationConfig>(variant.main) {

        final override val type: Class<EaseLintTask>
            get() = EaseLintTask::class.java

        abstract val fatalOnly: Boolean
        abstract val description: String

        final override fun configure(task: EaseLintTask) {
            super.configure(task)

            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description
            // 反射 绕过似有方法

            task.ELInitializeGlobalInputs(
                services = variant.main.services,
                isAndroid = true
            )
            task.lintModelDirectory.set(variant.main.paths.getIncrementalDir(task.name))
            task.lintRuleJars.from(creationConfig.global.localCustomLintChecks)
            task.lintRuleJars.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.disallowChanges()
            task.fatalOnly.setDisallowChanges(fatalOnly)
            task.checkOnly.set(
                creationConfig.services.provider {
                    creationConfig.global.lintOptions.checkOnly
                }
            )
            ProjectInputsProxy.initialize(task.projectInputs, variant, LintMode.ANALYSIS)
//            task.projectInputs.initialize()

            task.variantInputs.initialize(
                variant,
                checkDependencies = false,
                warnIfProjectTreatedAsExternalDependency = false,
                LintMode.ANALYSIS
            )
            task.lintTool.initialize(creationConfig.services)
            task.desugaredMethodsFiles.from(
                getDesugaredMethods(
                    creationConfig.services,
                    creationConfig.global.compileOptions.isCoreLibraryDesugaringEnabled,
                    creationConfig.minSdkVersion,
                    creationConfig.global.compileSdkHashString,
                    creationConfig.global.bootClasspath
                )
            )
            task.desugaredMethodsFiles.disallowChanges()
        }
    }


    private fun Collection<String>.asLintPaths() = joinToString(separator = ";", postfix = ";")

    private fun MutableList<String>.add(arg: String, value: String) {
        add(arg)
        add(value)
    }

    private fun generateCommandLineArguments(parent: AndroidLintAnalysisTask): List<String> {

        val arguments = mutableListOf<String>()

        arguments += "--analyze-only"
        if (parent.fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        arguments += listOf("--jdk-home", parent.systemPropertyInputs.javaHome.get())
        parent.androidSdkHome.orNull?.let { arguments.add("--sdk-home", it) }

        /*
        不设置model，在Main.java 中的逻辑: model 与 file list 不能同时存在，且在model 为null
        时才会扫描特殊指定的 file 集合
        要制定自己的扫描文件，只需要直接在集合末尾 追加 file 绝对路径即可。
        */
//        arguments += "--lint-model"
//        arguments += listOf(parent.lintModelDirectory.get().asFile.absolutePath).asLintPaths()

        for (check in parent.checkOnly.get()) {
            arguments += listOf("--check", check)
        }

        val rules = parent.lintRuleJars.files.filter { it.isFile }.map { it.absolutePath }
        if (rules.isNotEmpty()) {
            arguments += "--lint-rule-jars"
            arguments += rules.asLintPaths()
        }
        if (parent.printStackTrace.get()) {
            arguments += "--stacktrace"
        }
        arguments += parent.lintTool.initializeLintCacheDir()

        // Pass information to lint using the --client-id, --client-name, and --client-version flags
        // so that lint can apply gradle-specific and version-specific behaviors.
        arguments.add("--client-id", "gradle")
        arguments.add("--client-name", "AGP")
        arguments.add("--client-version", Version.ANDROID_GRADLE_PLUGIN_VERSION)

        // Pass --offline flag only if lint version is 30.3.0-beta01 or higher because earlier
        // versions of lint don't accept that flag.
//        if (offline.get()
//            && GradleVersion.tryParse(lintTool.version.get())
//                ?.isAtLeast(30, 3, 0, "beta", 1, false) == true) {
//            arguments += "--offline"
//        }
        return Collections.unmodifiableList(arguments)
    }
}

internal fun ComponentType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        ComponentTypeImpl.BASE_APK -> LintModelModuleType.APP
        ComponentTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        ComponentTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        ComponentTypeImpl.TEST_APK -> LintModelModuleType.TEST
        else -> throw RuntimeException("Unsupported ComponentTypeImpl value")
    }
}

object ProjectInputsProxy {

    internal fun initialize(
        projectInputs: ProjectInputs,
        variant: VariantWithTests,
        lintMode: LintMode
    ) {
        val creationConfig = variant.main
        val globalConfig = creationConfig.global

        initializeFromProject(projectInputs, creationConfig.services.projectInfo, lintMode)
        projectInputs.projectType.setDisallowChanges(creationConfig.componentType.toLintModelModuleType())

        projectInputs.lintOptions.initialize(globalConfig.lintOptions, lintMode)
        projectInputs.resourcePrefix.setDisallowChanges(globalConfig.resourcePrefix)

        projectInputs.dynamicFeatures.setDisallowChanges(globalConfig.dynamicFeatures)

        projectInputs.bootClasspath.fromDisallowChanges(globalConfig.bootClasspath)
        projectInputs.javaSourceLevel.setDisallowChanges(globalConfig.compileOptions.sourceCompatibility)
        projectInputs.compileTarget.setDisallowChanges(globalConfig.compileSdkHashString)
        // `neverShrinking` is about all variants, so look back to the DSL
        projectInputs.neverShrinking.setDisallowChanges(globalConfig.hasNoBuildTypeMinified)
    }

    fun initializeFromProject(
        projectInputs: ProjectInputs,
        projectInfo: ProjectInfo,
        lintMode: LintMode
    ) {
        projectInputs.projectDirectoryPath.setDisallowChanges(projectInfo.projectDirectory.toString())
        projectInputs.projectGradlePath.setDisallowChanges(projectInfo.path)
        projectInputs.mavenGroupId.setDisallowChanges(projectInfo.group)
        projectInputs.mavenArtifactId.setDisallowChanges(projectInfo.name)
        projectInputs.buildDirectoryPath.setDisallowChanges(projectInfo.buildDirectory.map { it.asFile.absolutePath })
        if (lintMode != LintMode.ANALYSIS) {
            projectInputs.projectDirectoryPathInput.set(projectInputs.projectDirectoryPath)
            projectInputs.buildDirectoryPathInput.set(projectInputs.buildDirectoryPath)
        }
        projectInputs.projectDirectoryPathInput.disallowChanges()
        projectInputs.buildDirectoryPathInput.disallowChanges()
    }
}