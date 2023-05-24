package com.easelint.detector

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * 这里使用SourceCodeScanner，基于源码扫码
 */
class LogDetector : Detector(), SourceCodeScanner {
    companion object {
        val ISSUE_LOG = Issue.create(
            LogDetector::class.java.simpleName,
            briefDescription = "错误的日志打印API使用",
            explanation = "请使用LogUtils替代",
//            category = Category.LINT,
            category = Category.create("风险代码检测", 110),
            priority = 5,
            severity = Severity.ERROR,
            Implementation(LogDetector::class.java, Scope.JAVA_FILE_SCOPE)//同时检测java 与kotlin 文件
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("wtf", "v", "d", "i", "w", "e", "println", "print")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (checkIllegalLogInvoke(context, method)) {
            context.report(ISSUE_LOG, context.getLocation(node), "println，Log为风险API，使用LogUtils替换~")
        }
    }

    /**
     * 使用 鉴定器，判断代码是否合规
     */
    private fun checkIllegalLogInvoke(context: JavaContext, method: PsiMethod): Boolean {
        val log = context.evaluator.isMemberInClass(method, "android.util.Log")
        val printStream = context.evaluator.isMemberInClass(method, "java.io.PrintStream")
        val kt = context.evaluator.isMemberInClass(method, "kotlin.io.ConsoleKt")
        println("LogDetector method:${method.name} ${method.containingClass}:${method.containingClass?.qualifiedName} kt:$kt")
        return log || printStream || kt
    }

}