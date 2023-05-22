package com.mj.lint.lintchecker

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.mj.lint.lintchecker.utils.createWithMj
import org.w3c.dom.Element

/**
 * manifest校验
 */
@Suppress("UnstableApiUsage")
class MjManifestDetector : ResourceXmlDetector() {

    companion object {
        const val SCREEN_ORIENTATION = "screenOrientation"

        val ISSUE_MANIFEST_ORIENTATION = Issue.createWithMj<MjManifestDetector>(
            briefDescription = "必须指定$SCREEN_ORIENTATION",
            explanation = "必须显示指定横竖屏" +
                    "\n如果没有设置横竖屏，当跳转页面时会出现比较突兀的横竖屏切换，并且如果不做处理很可能会引发崩溃" +
                    "\n如果设置支持旋转，则应正确处理对应的生命周期",
            implementationScope = Scope.MANIFEST_SCOPE
        )
    }

    override fun getApplicableElements() = listOf(SdkConstants.TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == SdkConstants.TAG_ACTIVITY) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SCREEN_ORIENTATION)) {
                context.report(
                    ISSUE_MANIFEST_ORIENTATION,
                    element,
                    context.getNameLocation(element),
                    "Activity必须添加${SCREEN_ORIENTATION}属性",
                    fix().set(SdkConstants.ANDROID_URI, SCREEN_ORIENTATION, "portrait")
                        .caretEnd().build()
                )
            }
        }
    }
}