package com.buildsrc.easelint.lint.helper

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.variant.ComponentInfo
import com.buildsrc.easelint.lint.task.EaseLintPerVariantTask
import com.buildsrc.easelint.lint.task.PrepareEaseLintTask
import com.buildsrc.easelint.lint.task.TreatEaseLintResultTask
import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.Project

class EaseLintTaskHelper {
    companion object {
        const val TASK_NAME = "easeLint"
    }

    fun apply(
        project: Project,
        variantManager: VariantManager<VariantImpl<VariantPropertiesImpl>, VariantPropertiesImpl>
    ) {
        LintWrapperHelper.apply(project)
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
        if (variant == null) throw GradleException("can not find variant")
        val prepareEaseLintTask = project.tasks.create(
            PrepareEaseLintTask.TASK_NAME,
            PrepareEaseLintTask::class.java
        )
        val treatEaseLintResultTask = project.tasks.create(
            TreatEaseLintResultTask.TASK_NAME,
            TreatEaseLintResultTask::class.java
        )
        val task = TaskFactoryImpl(project.tasks).register(
            EaseLintCreationAction(
                project, TASK_NAME, variant, variantPropertiesList
            )
        ).get()
        task.dependsOn(prepareEaseLintTask)
        task.finalizedBy(treatEaseLintResultTask)
    }

    class EaseLintCreationAction(
        private val project: Project,
        private val taskName: String,
        variantProperties: VariantPropertiesImpl,
        allVariants: List<VariantPropertiesImpl>
    ) : EaseLintPerVariantTask.CreationAction(variantProperties, allVariants) {
        override fun configure(task: EaseLintPerVariantTask) {
            //放在这里最安全，保证一定在super#configure之前调用，覆盖系统的 lint gradle
            LintGradleHelper.injectLintPatch(project)
            super.configure(task)
        }

        override val name: String
            get() = taskName
    }
}