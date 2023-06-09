package com.buildsrc.easelint.lint.plugin

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.buildsrc.easelint.lint.helper.LintSlot
import com.buildsrc.easelint.lint.helper.LintConfigExtensionHelper
import com.buildsrc.easelint.lint.helper.LintGradleHelper
import com.buildsrc.easelint.lint.helper.LintWrapperHelper
import com.buildsrc.easelint.lint.helper.EaseLintTaskHelper
import com.buildsrc.easelint.lint.utils.log
import org.gradle.api.Plugin
import org.gradle.api.Project

class EaseLintPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        "EaseLintPlugin:apply".log("lifeTrack____1")
        val libPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
        val appPlugin = project.plugins.findPlugin(AppPlugin::class.java)
        if (libPlugin == null && appPlugin == null) return
        val currentPlugin = libPlugin ?: appPlugin!!

        LintConfigExtensionHelper.apply(project)
        // 访问网络，获取lint 配置,lint gradle 版本，lint wrapper，这两个需要在插件任务初始化时完成配置
        // 最好直接从cdn等文件资源读取数据，这样耗时可以忽略不计
        LintGradleHelper.init(false, "0.0.4-2023-05-24-11-31-56")
        LintWrapperHelper.init(true, "0.0.1-2023-05-24-10-18-01")

        project.afterEvaluate {
            "EaseLintPlugin:afterEvaluate".log("lifeTrack____1")
            val lcg = LintConfigExtensionHelper.findLintConfigExtension(project)
            LintSlot.setExtensionParams(lcg)
            //放在afterEvaluate内才能保证在变种配置完成后进行hook
            val variantManager = reflectionVM(currentPlugin)
            EaseLintTaskHelper().apply(project, variantManager)
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