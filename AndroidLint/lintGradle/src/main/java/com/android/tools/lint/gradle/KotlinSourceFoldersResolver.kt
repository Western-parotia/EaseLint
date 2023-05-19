package com.android.tools.lint.gradle

import org.gradle.api.Project
import java.io.File

class KotlinSourceFoldersResolver(
    private val kotlinSourceFolders: (variantName: String, project: Project?) -> List<File>
) {
    fun getKotlinSourceFolders(variantName: String, project: Project?): List<File> =
        kotlinSourceFolders.invoke(variantName, project)
}