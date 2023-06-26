package com.buildsrc.lint

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.lint.AndroidLintWorkAction
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.services.ProjectServices
import com.android.utils.JvmWideVariable
import com.google.common.reflect.TypeToken
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.lang.ref.SoftReference
import java.net.URI
import java.net.URLClassLoader

open class HookLintPlugin : Plugin<Project> {
    private lateinit var project: Project

    private val cachedClassloader: JvmWideVariable<MutableMap<String, SoftReference<URLClassLoader>>> =
        JvmWideVariable(
            AndroidLintWorkAction::class.java,
            "cachedClassloader",
            object : TypeToken<MutableMap<String, SoftReference<URLClassLoader>>>() {}
        ) { HashMap() }

    override fun apply(project: Project) {
        this.project = project
        println("IncrementLintPlugin ======== started")
        project.afterEvaluate {
            startHook(this)
        }
    }

    private fun startHook(project: Project) {
        val classpath = constructClasspath()
        println("first classpath: ${classpath.firstOrNull()}")
        getClassloader(project, PROPERTY_KEY, classpath)
    }

    private fun getClassloader(project: Project, key: String, classpath: List<File>): ClassLoader {
        val uris = classpath.map { it.toURI() }
        return cachedClassloader.executeCallableSynchronously {
            val map = cachedClassloader.get()
            val classloader = map[key]?.get()?.also {
                project.logger.info("Hook Android Lint: Reusing lint classloader {}", key)
            } ?: createClassLoader(project, key, uris).also { map[key] = SoftReference(it) }
            classloader
        }
    }

    private fun createClassLoader(
        project: Project,
        key: String,
        classpath: List<URI>
    ): URLClassLoader {
        project.logger.info("Android Lint: Creating lint class loader {}", key)
        val classpathUrls = classpath.map { it.toURL() }.toTypedArray()
        return URLClassLoader(classpathUrls, getPlatformClassLoader())
    }

    private fun getPlatformClassLoader(): ClassLoader {
        // AGP is currently compiled against java 8 APIs, so do this by reflection (b/160392650)
        return ClassLoader::class.java.getMethod("getPlatformClassLoader")
            .invoke(null) as ClassLoader
    }

    private fun constructClasspath(): List<File> {
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        val files = getLintMavenByReflection(appPlugin)?.files ?: emptyList<File>()
        val dependenciesFile = createConfig(project).files
        val hookFile = dependenciesFile.firstOrNull {
            it.absolutePath.contains(GROUP_NAME)
        }
        if (hookFile == null) {
            project.logger.info("not found $GROUP_NAME absolutePath")
        }
        val linkSet = LinkedHashSet<File>().apply {
            if (hookFile != null) {
                add(hookFile)
            }
            addAll(files)
        }
        return linkSet.toList()
    }

    @Suppress("PrivateApi")
    private fun getLintMavenByReflection(appPlugin: AppPlugin?): LintFromMaven? {
        if (appPlugin == null) {
            return null
        }
        return try {
            val fields =
                com.android.build.gradle.internal.plugins.BasePlugin::class.java.getDeclaredField("projectServices")
            fields.isAccessible = true

            val projectServices = fields.get(appPlugin) as? ProjectServices
            projectServices?.lintFromMaven
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createConfig(project: Project): Configuration {
        val config = project.configurations.detachedConfiguration(
            project.dependencies.create(
                mapOf(
                    "group" to GROUP_NAME,
                    "name" to "lint",
                    "version" to VERSION_NAME,
                )
            )
        )
        config.isTransitive = true
        config.isCanBeResolved = true
        return config
    }

    companion object {
        private const val GROUP_NAME = "你的包名"
        private const val VERSION_NAME = "你的版本"
        private const val PROPERTY_KEY = "30.2.2"
    }
}
