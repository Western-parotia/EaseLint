package com.android.build.gradle.internal.lint

import com.android.Version
import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.ide.common.repository.GradleVersion
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModule
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
fun ComponentType.toLintModelModuleType(): LintModelModuleType {
    return when (this) {
        // FIXME add other types
        ComponentTypeImpl.BASE_APK -> LintModelModuleType.APP
        ComponentTypeImpl.LIBRARY -> LintModelModuleType.LIBRARY
        ComponentTypeImpl.OPTIONAL_APK -> LintModelModuleType.DYNAMIC_FEATURE
        ComponentTypeImpl.TEST_APK -> LintModelModuleType.TEST
        else -> throw RuntimeException("Unsupported ComponentTypeImpl value")
    }
}
object VariantInputsProxy {


    fun initializeForStandalone(
        variantInputs: VariantInputs,
        project: Project,
        javaExtension: JavaPluginExtension,
        projectOptions: ProjectOptions,
        fatalOnly: Boolean,
        checkDependencies: Boolean,
        lintMode: LintMode
    ) {
        val mainSourceSet = javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val testSourceSet = javaExtension.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        variantInputs.name.setDisallowChanges(mainSourceSet.name)
        variantInputs.checkDependencies.setDisallowChanges(checkDependencies)
        variantInputs.mainArtifact.initializeForStandalone(
            project,
            projectOptions,
            mainSourceSet,
            checkDependencies
        )
        variantInputs.testArtifact.setDisallowChanges(
            project.objects.newInstance(JavaArtifactInput::class.java).initializeForStandalone(
                project,
                projectOptions,
                testSourceSet,
                checkDependencies,
                // analyzing test bytecode is expensive, without much benefit
                includeClassesOutputDirectories = false
            )
        )
        variantInputs.androidTestArtifact.disallowChanges()
        variantInputs.testFixturesArtifact.disallowChanges()
        variantInputs.namespace.setDisallowChanges("")
//        variantInputs.minSdkVersion.initializeEmpty()
        variantInputs.minSdkVersion.apiLevel.setDisallowChanges(-1)
        variantInputs.minSdkVersion.codeName.setDisallowChanges("")

//        variantInputs.targetSdkVersion.initializeEmpty()
        variantInputs.targetSdkVersion.apiLevel.setDisallowChanges(-1)
        variantInputs.targetSdkVersion.codeName.setDisallowChanges("")

        variantInputs.manifestPlaceholders.disallowChanges()
        variantInputs.resourceConfigurations.disallowChanges()
        variantInputs.debuggable.setDisallowChanges(true)
        variantInputs.mergedManifest.setDisallowChanges(null)
        variantInputs.manifestMergeReport.setDisallowChanges(null)
        variantInputs.minifiedEnabled.setDisallowChanges(false)
        variantInputs.sourceProviders.setDisallowChanges(
            listOf(
                SourceProviderInputProxy.initializeForStandalone(
                    project.objects.newInstance(SourceProviderInput::class.java),
                    project,
                    testSourceSet,
                    lintMode,
                    unitTestOnly = true

                )
//                project.objects.newInstance(SourceProviderInput::class.java)
//                    .initializeForStandalone(
//                        project,
//                        mainSourceSet,
//                        lintMode,
//                        unitTestOnly = false
//                    )
            )
        )
        if (fatalOnly) {
            variantInputs.testSourceProviders.setDisallowChanges(listOf())
        } else {
            variantInputs.testSourceProviders.setDisallowChanges(
                listOf(
                    SourceProviderInputProxy.initializeForStandalone(
                        project.objects.newInstance(SourceProviderInput::class.java),
                        project,
                        testSourceSet,
                        lintMode,
                        unitTestOnly = true

                    )
//                  project.objects.newInstance(SourceProviderInput::class.java)
//                        .initializeForStandalone(
//                            project,
//                            testSourceSet,
//                            lintMode,
//                            unitTestOnly = true
//                        )
                )
            )
        }
        variantInputs.testFixturesSourceProviders.disallowChanges()
        variantInputs.buildFeatures.initializeForStandalone()
        variantInputs.libraryDependencyCacheBuildService
            .setDisallowChanges(
                getBuildService(
                    project.gradle.sharedServices,
                    LibraryDependencyCacheBuildService::class.java
                )
            )
        variantInputs.mavenCoordinatesCache
            .setDisallowChanges(
                getBuildService(
                    project.gradle.sharedServices,
                    MavenCoordinatesCacheBuildService::class.java
                )
            )
        variantInputs.proguardFiles.setDisallowChanges(null)
        variantInputs.extractedProguardFiles.setDisallowChanges(null)
        variantInputs.consumerProguardFiles.setDisallowChanges(null)
        variantInputs.resValues.disallowChanges()
    }


}

object SourceProviderInputProxy {
    fun initializeForStandalone(
        provider: SourceProviderInput,
        project: Project,
        sourceSet: SourceSet,
        lintMode: LintMode,
        unitTestOnly: Boolean
    ): SourceProviderInput {
        val fakeManifestFile =
            project.layout.buildDirectory.file("fakeAndroidManifest/${sourceSet.name}/AndroidManifest.xml")
        provider.manifestFile.setDisallowChanges(fakeManifestFile)
        provider.javaDirectories.fromDisallowChanges(project.provider { sourceSet.allSource.srcDirs })
        provider.resDirectories.disallowChanges()
        provider.assetsDirectories.disallowChanges()
        if (lintMode == LintMode.ANALYSIS) {
            provider.javaDirectoriesClasspath.from(project.provider { sourceSet.allSource.srcDirs })
        } else {
            provider.javaDirectoryPaths.set(sourceSet.allSource.srcDirs.map { it.absolutePath })
        }
        provider.javaDirectoriesClasspath.disallowChanges()
        provider.resDirectoriesClasspath.disallowChanges()
        provider.assetsDirectoriesClasspath.disallowChanges()
        provider.manifestFilePath.disallowChanges()
        provider.javaDirectoryPaths.disallowChanges()
        provider.resDirectoryPaths.disallowChanges()
        provider.assetsDirectoryPaths.disallowChanges()
        provider.debugOnly.setDisallowChanges(false)
        provider.unitTestOnly.setDisallowChanges(unitTestOnly)
        provider.instrumentationTestOnly.setDisallowChanges(false)
        provider.name.setDisallowChanges(sourceSet.name)
        return provider
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

    fun convertToLintModelModule(projectInputs: ProjectInputs): LintModelModule {
        return DefaultLintModelModule(
            loader = null,
            dir = File(projectInputs.projectDirectoryPath.get()),
            modulePath = projectInputs.projectGradlePath.get(),
            type = projectInputs.projectType.get(),
            mavenName = DefaultLintModelMavenName(
                projectInputs.mavenGroupId.get(),
                projectInputs.mavenArtifactId.get()
            ),
            gradleVersion = GradleVersion.tryParse(Version.ANDROID_GRADLE_PLUGIN_VERSION),
            buildFolder = File(projectInputs.buildDirectoryPath.get()),
            lintOptions = projectInputs.lintOptions.toLintModel(),
            lintRuleJars = listOf(),
            resourcePrefix = projectInputs.resourcePrefix.orNull,
            dynamicFeatures = projectInputs.dynamicFeatures.get(),
            bootClassPath = projectInputs.bootClasspath.files.toList(),
            javaSourceLevel = projectInputs.javaSourceLevel.get().toString(),
            compileTarget = projectInputs.compileTarget.get(),
            variants = listOf(),
            neverShrinking = projectInputs.neverShrinking.get()
        )
    }

    fun initializeForStandalone(
        projectInputs: ProjectInputs,
        project: Project,
        javaExtension: JavaPluginExtension,
        dslLintOptions: Lint,
        lintMode: LintMode
    ) {
        initializeFromProject(projectInputs, ProjectInfo(project), lintMode)
        projectInputs.projectType.setDisallowChanges(LintModelModuleType.JAVA_LIBRARY)
        projectInputs.lintOptions.initialize(dslLintOptions, lintMode)
        projectInputs.resourcePrefix.setDisallowChanges("")
        projectInputs.dynamicFeatures.setDisallowChanges(setOf())
        val mainSourceSet = javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val javaCompileTask = project.tasks.named(
            mainSourceSet.compileJavaTaskName,
            JavaCompile::class.java
        )
        projectInputs.bootClasspath.fromDisallowChanges(javaCompileTask.map {
            it.options.bootstrapClasspath ?: project.files()
        })
        projectInputs.javaSourceLevel.setDisallowChanges(javaExtension.sourceCompatibility)
        projectInputs.compileTarget.setDisallowChanges("")
        projectInputs.neverShrinking.setDisallowChanges(true)
    }
}
