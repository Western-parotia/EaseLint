package com.easelint.detector

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.easelint.utils.createWithMj
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.sourcePsiElement

class SuppressWarningsDetector : Detector(), SourceCodeScanner {
    companion object {
        val ISSUE_SUPPRESS_ANNOTATION = Issue.createWithMj<SuppressWarningsDetector>(
            id = "SuppressWarningsDetector",
            briefDescription = "抑制类注解必须注释原因",
            explanation = "抑制类注解必须注释原因，请在注解上方添加以“抑制原因”开头的单行注释",
            implementationScope = Scope.JAVA_FILE_SCOPE
        )

        private const val KEYWORDS = "reason:"
    }

    override fun applicableAnnotations(): List<String> {
        return listOf(
            "android.annotation.SuppressLint",
            "java.lang.SuppressWarnings",
            "kotlin.Suppress"
        )
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        //判断注解所在文件的路径，若包含".gradle"说明是三方库，直接结束避免误判
        val filePath = context.getLocation(annotation).file.absolutePath
        if (filePath.contains(".gradle")) return

        //获取注解的代码的原文，例：@SuppressLint("ParseStringDetector")
        val targetOriginalCode = annotation.sourcePsiElement?.originalElement?.text ?: ""

        //目前不支持检查@file类型的注解
        if (targetOriginalCode.startsWith("@file")) return

        /*
        判断文件类型，java文件可以直接通过注解父元素的comments获取到注解上方的注释，但是kotlin文件不行，
        所以kotlin采用获取注解父元素的代码，然后截取在注解前面的字符串，此字符串包含了注解上方的注释。
         */
        val hasKeywords: Boolean = if (annotation.lang.displayName == "Java") {//java文件
            val comment = annotation.uastParent?.comments?.firstOrNull {
                it.text.contains(KEYWORDS)
            }
            comment != null
        } else {//kotlin
            //获取注解父元素的代码
            val parentOriginalCode = annotation.uastParent?.sourcePsi?.originalElement?.text ?: ""
            //截取注解之前的字符串，避免父元素的代码中其它位置的注释有关键字导致判断出错
            val commentString = parentOriginalCode.substringBefore(targetOriginalCode, "")
            commentString.contains(KEYWORDS)
        }

        /*
        有坑，此处特作说明。
        1.只有SuppressLint可以在注解位置报错，context.getLocation(annotation)，其余注解类不失效
        2.所以统一改为在调用位置报错
         */
        if (!hasKeywords) {
            val msg = "抑制类注解缺少必要的注释说明，请在${targetOriginalCode}上方添加包含“${KEYWORDS}”的注释，例：\n" +
                    "//${KEYWORDS}这只是一个示例\n${targetOriginalCode}\n" +
                    "***===若注解在方在内部，请移至方法外，便于Lint检查===***\n"
            //在调用位置报错
            context.report(
                ISSUE_SUPPRESS_ANNOTATION,
                context.getLocation(usage),
                msg
            )
        }
    }

}