package com.buildsrc.easelint.lint.task

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.variant.ComponentInfo
import com.buildsrc.easelint.lint.helper.LintGradleHelper
import com.google.common.collect.ImmutableList
import org.gradle.api.Project

class LintTaskHelper {
    companion object {
        const val TASK_NAME_LINT_FULL = "easeLintFull"
        const val TASK_NAME_LINT_INCREMENT = "easeLintIncrement"
    }

    fun apply(
        project: Project,
        variantManager: VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
    ) {
        val variants: List<ComponentInfo<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>> =
            variantManager.mainComponents
        val variantPropertiesList = variants.stream()
            .map {
                it.properties
            }.collect(ImmutableList.toImmutableList())

        var variant = variantPropertiesList.find {
            it.name.contains("debug")
        }
        if (variant == null) variant = variantPropertiesList[0]
        if (variant == null) return

        TaskFactoryImpl(project.tasks).apply {
            register(
                MJLintCreationAction(
                    project, TASK_NAME_LINT_FULL, variant, variantPropertiesList
                )
            )
            register(
                MJLintCreationAction(
                    project, TASK_NAME_LINT_INCREMENT, variant, variantPropertiesList
                )
            )
        }
        //为两个任务 配置不一样等文件抓取流程


    }

    class MJLintCreationAction(
        private val project: Project,
        private val taskName: String,
        variantProperties: VariantPropertiesImpl,
        allVariants: List<VariantPropertiesImpl>
    ) : MJLintPerVariantTask.CreationAction(variantProperties, allVariants) {
        override fun configure(task: MJLintPerVariantTask) {
            //加入补丁修复lint的bug同时支持增量扫描功能，需要在super#configure之前调用
            LintGradleHelper.injectLintPatch(project)
            super.configure(task)
        }

        override val name: String
            get() = taskName
    }
}