package com.buildsrc.easelint.lint.plugin

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.buildsrc.easelint.lint.extensions.LintConfigExtensionHelper
import com.buildsrc.easelint.lint.helper.LintGradleHelper
import com.buildsrc.easelint.lint.task.LintTaskHelper
import org.gradle.api.Plugin
import org.gradle.api.Project

class EaseLintPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        LintConfigExtensionHelper.apply(target)

        target.afterEvaluate {
            // 访问网络，获取lint 配置,lint gradle 版本，lint wrapper，这两个需要在插件任务初始化时完成配置
            // 最好直接从cdn等文件资源读取数据，这样耗时可以忽略不计
            LintGradleHelper.init(true, "0.0.11")
//        LintWrapperHelper.init(true, "0.0.1")
            val libPlugin = target.plugins.findPlugin(LibraryPlugin::class.java)
            val appPlugin = target.plugins.findPlugin(AppPlugin::class.java)
            if (libPlugin == null && appPlugin == null) return@afterEvaluate

            val currentPlugin = libPlugin ?: appPlugin!!
            val variantManager = reflectionVM(currentPlugin)
            //初始化lint task（hook点）
            LintTaskHelper().apply(target, variantManager)
        }
    }
}

/**
 * 读源码后发现可以采用反射获取 VariantManager<VariantT, VariantPropertiesT>
 */
@Suppress("UNCHECKED_CAST")
private fun reflectionVM(
    plugin: Any
): VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl> {
    val vmField = BasePlugin::class.java.getDeclaredField("variantManager")
    vmField.isAccessible = true
    return vmField.get(plugin) as VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
}