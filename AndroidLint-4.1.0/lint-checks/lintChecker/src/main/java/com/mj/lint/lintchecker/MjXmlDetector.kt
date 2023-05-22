package com.mj.lint.lintchecker

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.mj.lint.lintchecker.utils.createWithMj
import org.w3c.dom.Attr
import org.w3c.dom.Element

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/8/30 9:47
 *
 */
class MjXmlDetector : ResourceXmlDetector() {
    companion object {
        val ISSUE_VIEW_ID = Issue.createWithMj<MjXmlDetector>(
            id = "ViewIdDetector",
            briefDescription = "控件id命名不规范",
            explanation = "控件id请使用驼峰法命名",
            implementationScope = Scope.RESOURCE_FILE_SCOPE
        )

        val ISSUE_RELATIVE_LAYOUT = Issue.createWithMj<MjXmlDetector>(
            id = "RelativeLayoutDetector",
            briefDescription = "禁止使用RelativeLayout",
            explanation = "禁止使用RelativeLayout，因为RV在布局里测量时始终是2的n次方，建议改为ConstraintLayout或者FrameLayout",
            implementationScope = Scope.RESOURCE_FILE_SCOPE
        )
    }

    //=========================控件id命名不规范======================================

    private val regex = Regex("[a-z]+((\\d)|([A-Z0-9][a-z0-9]+))*([A-Z])?")

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return ResourceFolderType.LAYOUT == folderType
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(VALUE_ID)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.value.substring(attribute.value.indexOf("/") + 1)
        if (!regex.matches(name)) {
            context.report(
                ISSUE_VIEW_ID,
                context.getLocation(attribute.ownerElement),
                ISSUE_VIEW_ID.getExplanation(TextFormat.TEXT),
                null
            )
        }
    }

    //==========================禁止使用RelativeLayout=====================================

    override fun getApplicableElements(): Collection<String> {
        return listOf(RELATIVE_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        context.report(
            ISSUE_RELATIVE_LAYOUT,
            context.getLocation(element),
            ISSUE_RELATIVE_LAYOUT.getExplanation(TextFormat.TEXT),
            null
        )
    }
}