package com.easelint.detector

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.easelint.utils.createWithMj
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType

class ParseDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE_PARSE_STRING = Issue.createWithMj<ParseDetector>(
            id = "ParseStringDetector",
            briefDescription = "调用parse方法必须捕获异常",
            explanation = "parseInt、parseLong、parseFloat、parseDouble需要加try catch，防止类型转换出错。",
            implementationScope = Scope.JAVA_FILE_SCOPE
        )

        val ISSUE_PARSE_COLOR = Issue.createWithMj<ParseDetector>(
            id = "ParseColorDetector",
            briefDescription = "高风险API使用",
            explanation = "请避免使用android.graphics.Color.parseColor(),使用拓展方法进行替换，" +
                    "\n例如：string.toSafeColor()，或者使用try catch捕获异常(必须是Throwable或Exception)",
            implementationScope = Scope.JAVA_FILE_SCOPE
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf(
            "parseInt",
            "parseLong",
            "parseFloat",
            "parseDouble",
            "toInt",
            "toLong",
            "toFloat",
            "toDouble",
            "valueOf",
            "parseColor"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.graphics.Color")) {
            //==========color===========
            if (!hasTryCatch(context, node))
                context.report(
                    ISSUE_PARSE_COLOR,
                    context.getLocation(node),
                    ISSUE_PARSE_COLOR.getExplanation(TextFormat.TEXT)
                )
        } else {
            //=========string===========
            val isValueOf = "valueOf" == method.name
            val argumentType = node.valueArguments.firstOrNull()?.getExpressionType()
            val isString = context.evaluator.typeMatches(argumentType,"java.lang.String")
            //当前判断的方法是isValueOf()，且入参不是String类型，不需要检查此方法
            if (isValueOf && !isString) return

            val methodName = when (method.containingClass?.qualifiedName) {
                "java.lang.Integer" -> if (isValueOf) "Integer.valueOf()" else "parseInt()"
                "java.lang.Long" -> if (isValueOf) "Long.valueOf()" else "parseLong()"
                "java.lang.Float" -> if (isValueOf) "Float.valueOf()" else "parseFloat()"
                "java.lang.Double" -> if (isValueOf) "Double.valueOf()" else "parseDouble()"
                "kotlin.text.StringsKt__StringNumberConversionsJVMKt" -> {
                    when (method.name) {
                        "toInt" -> "toInt()"
                        "toLong" -> "toLong()"
                        "toFloat" -> "toFloat()"
                        "toDouble" -> "toDouble()"
                        else -> ""
                    }
                }
                else -> ""
            }
            //方法不是来自以上目标类直接返回
            if (methodName.isEmpty()) return
            if (!hasTryCatch(context, node)) {
                val msg =
                    "请避免使用${methodName}方法,使用拓展方法进行替换，" +
                            "\n例如：string.toSafeXX()，或者使用try catch捕获异常(必须是Throwable或Exception)"

                context.report(ISSUE_PARSE_STRING, node, context.getLocation(node), msg)
            }
        }
    }

    private fun hasTryCatch(
        context: JavaContext,
        node: UCallExpression
    ): Boolean {
        //判断方法的父节点是否为try-catch表达式
        val tryExpression: UTryExpression =
            node.getParentOfType(UTryExpression::class.java, true) ?: return false

        //判断catch里是否是Throwable或者Exception
        tryExpression.catchClauses.firstOrNull {
            it.types.firstOrNull { type ->
                when (type.canonicalText) {
                    "java.lang.Throwable",
                    "java.lang.Exception",
                    -> true
                    else -> false
                }
            } != null
        } ?: return false

        //获取到try的行号
        val start: Int = context.getLocation(tryExpression).start?.line ?: 0
        //获取第一个catch的行号
        val catchExpression = tryExpression.catchClauses[0]
        val end: Int = context.getLocation(catchExpression).start?.line ?: 0
        //获取方法的行号
        val target = context.getLocation(node).start?.line
        //方法的位置必须在try和第一个catch之间，在其它位置无效
        return target in start..end
    }
}