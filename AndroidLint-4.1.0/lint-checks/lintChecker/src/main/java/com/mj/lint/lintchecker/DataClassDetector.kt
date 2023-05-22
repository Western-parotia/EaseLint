package com.mj.lint.lintchecker

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.mj.lint.lintchecker.utils.createWithMj
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import java.io.File


/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/9/20 13:26
 *
 */
class DataClassDetector : Detector(), SourceCodeScanner {
    companion object {
        val ISSUE_DATA_CLASS = Issue.createWithMj<DataClassDetector>(
            briefDescription = "缺少默认值",
            explanation = "以Res结尾的data class类中的非空变量必须有默认值",
            implementationScope = Scope.JAVA_FILE_SCOPE
        )

        @Deprecated("不需要此规则了，逻辑代码已经删除，ISSUE暂时先保留着")
        val ISSUE_RETROFIT_GENERICS = Issue.createWithMj<DataClassDetector>(
            id = "RetrofitGenericsDetector",
            briefDescription = "类型错误",
            explanation = "Retrofit解析返回泛型里应都为data class，类名必须以Res结尾",
            implementationScope = Scope.JAVA_FILE_SCOPE
        )

        private val TARGET_PACKAGE_NAME = "data${File.separator}res"
        private const val TARGET_CLASS_SUFFIX = "Res"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        val filePath = context.file.path
        //判断文件是否来自com.xxx...xxx.data.res包路径下
        if (filePath.contains(TARGET_PACKAGE_NAME))
            return null
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val className = node.name ?: ""
                //判断类名是否为Res结尾
                if (!className.endsWith(TARGET_CLASS_SUFFIX)) return
                /*
                检查该类的构造函数个数和每个构造中的参数个数
                构造函数0个，通过（用来判断java）
                构造函数>0个，有一个构造的为0个参数，通过（用来判断kotlin）
                构造函数>0个，所有构造的参数都>0个，不通过
                 */
                val hasEmptyConstructor = node.constructors.isEmpty() || node.constructors.any {
                    it.parameters.isEmpty()
                }
                //kotlin的data class如果没有无参构造，说明有变量无默认值
                if (!hasEmptyConstructor) {
                    context.report(
                        ISSUE_DATA_CLASS,
                        context.getNameLocation(node),
                        ISSUE_DATA_CLASS.getExplanation(TextFormat.TEXT)
                    )
                }
            }
        }
    }
}