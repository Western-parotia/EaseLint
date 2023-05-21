package com.buildsrc.easelint.lint.plugin

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.buildsrc.easelint.lint.extensions.ExtensionHelper
import com.buildsrc.easelint.lint.helper.LintWrapperHelper
import com.buildsrc.easelint.lint.task.LintTaskHelper
import org.gradle.api.Plugin
import org.gradle.api.Project

class EaseLintPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        ExtensionHelper().apply(target)//得放在afterEvaluate之外，否则无法读取到配置
        val libPlugin = target.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = target.plugins.findPlugin(AppPlugin::class.java)
        if (libPlugin == null && appPlugin == null) return

        val currentPlugin = libPlugin ?: appPlugin!!
        val variantManager = reflectionVM(currentPlugin)
        LintTaskHelper().apply(target, variantManager)
        LintWrapperHelper.apply(target)

    }
}

/**
 * 获取 VariantManager<VariantT, VariantPropertiesT> variantManager;
 *
 */
@Suppress("UNCHECKED_CAST")
private fun reflectionVM(
    plugin: Any
): VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl> {
    val vmField = BasePlugin::class.java.getDeclaredField("variantManager")
    vmField.isAccessible = true
    return vmField.get(plugin) as VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
}