package com.buildsrc.lint

import com.android.build.gradle.internal.lint.AndroidLintWorkAction
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.lint.LintTool
import com.android.utils.JvmWideVariable
import com.google.common.reflect.TypeToken
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import java.lang.ref.SoftReference
import java.net.URI
import java.net.URLClassLoader

object LintHook {

    private val logger = Logging.getLogger(Task::class.java)

    /**
     * Cache the classloaders across the daemon, even if the buildscipt classpath changes
     *
     * Use a soft reference so the cache doesn't end up keeping a classloader that isn't
     * actually reachable.
     *
     * JvmWideVariable must only use built in types to avoid leaking the current classloader.
     */
    private val cachedClassloader: JvmWideVariable<MutableMap<String, SoftReference<URLClassLoader>>> =
        JvmWideVariable(
            AndroidLintWorkAction::class.java,
            "cachedClassloader",
            object : TypeToken<MutableMap<String, SoftReference<URLClassLoader>>>() {}
        ) { HashMap() }

    private fun createClassLoader(key: String, classpath: List<URI>): URLClassLoader {
        logger.info("Android Lint: Creating lint class loader {}", key)
        val classpathUrls = classpath.map { it.toURL() }.toTypedArray()
        return URLClassLoader(classpathUrls, getPlatformClassLoader())
    }

    private fun getPlatformClassLoader(): ClassLoader {
        // AGP is currently compiled against java 8 APIs, so do this by reflection (b/160392650)
        return ClassLoader::class.java.getMethod("getPlatformClassLoader")
            .invoke(null) as ClassLoader
    }

    fun loadHookFile(lintTool: LintTool, project: Project) {
        val group = "com.easelint.snapshot"
        val name = "30.4.2-lint-api"
        val version = "0.0.1-2023-06-26-05-48-55"
        // 创建 classLoader 将自己的 maven包排在最前面 先喂给 cachedClassloader
        val sysLintFiles: List<URI> = lintTool.classpath.files.map { it.toURI() }
        val config = project.configurations.detachedConfiguration(
            project.dependencies.create(
                mapOf(
                    "group" to group,
                    "name" to name,
                    "version" to version,
                )
            )
        ).apply {
            isTransitive = true
            isCanBeResolved = true
        }
        val hookMavenConfig = LintFromMaven(config, version)
        val hookFiles = hookMavenConfig.files.files.map { it.toURI() }
        val summaryFiles = LinkedHashSet<URI>()
        summaryFiles.addAll(hookFiles)
        summaryFiles.addAll(sysLintFiles)

        val key = lintTool.versionKey.get()
        val classLoader: URLClassLoader = cachedClassloader.executeCallableSynchronously {
            val map = cachedClassloader.get()
            val classloader = map[key]?.get()?.also {
                logger.info("Android Lint: Reusing lint classloader {}", key)
            } ?: createClassLoader(key, summaryFiles.toList())
                .also { map[key] = SoftReference(it) }
            classloader
        }
        val x = classLoader
    }
}