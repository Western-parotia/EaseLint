package com.mj.lint.lintchecker

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.mj.lint.lintchecker.utils.log
import org.jetbrains.uast.*

private val BASIC_TYPE_LIST = listOf(
    TYPE_STRING,
    TYPE_INT,
    TYPE_LONG,
    TYPE_CHAR,
    TYPE_FLOAT,
    TYPE_DOUBLE,
    TYPE_BOOLEAN,
    TYPE_SHORT,
    TYPE_BYTE,
    TYPE_NULL,
    TYPE_INTEGER_WRAPPER,
    TYPE_BOOLEAN_WRAPPER,
    TYPE_BYTE_WRAPPER,
    TYPE_SHORT_WRAPPER,
    TYPE_LONG_WRAPPER,
    TYPE_DOUBLE_WRAPPER,
    TYPE_FLOAT_WRAPPER,
    TYPE_CHARACTER_WRAPPER,
)

/**
 * 序列化探测器
 * 扫描实现了 Parcelable 或 Serializable 的类
 * 1. field 是否实现 Parcelable 或 Serializable
 */
class SerializationDetector : Detector(), SourceCodeScanner {
    private val TAG = "ParcelableDetector"

    companion object {
        val ISSUE_SERIALIZATION_MEMBERS = Issue.create(
            SerializationDetector::class.java.simpleName,
            briefDescription = "有field成员未实现序列化接口",
            explanation = "explanation",
            category = Category.create("风险代码检测", 110),
            priority = 5,
            severity = Severity.ERROR,
            Implementation(
                SerializationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        val ISSUE_KOTLIN_CLASS = Issue.create(
            SerializationDetector::class.java.simpleName,
            briefDescription = "实现 Parcelable 的 kotlin 类，需添加注解{kotlinx.parcelize.Parcelize}",
            explanation = "explanation",
            category = Category.create("风险代码检测", 110),
            priority = 5,
            severity = Severity.ERROR,
            Implementation(
                SerializationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        private const val PARCELABLE = "android.os.Parcelable"
        private const val SERIALIZABLE = "java.io.Serializable"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(PARCELABLE, SERIALIZABLE)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val eva = context.evaluator
        //区分parcelable 与 serializable 的处理
        val inSerializable = eva.implementsInterface(declaration, SERIALIZABLE, true)
        val iName = if (inSerializable) SERIALIZABLE else PARCELABLE
//        "${declaration.name},inSerializable:${inSerializable}:
//        ${declaration.language.displayName}".log(TAG)
        if (!inSerializable && declaration.language.displayName == "Kotlin") {
            //检查是否使用Parcelize
            val parcelize = declaration.findAnnotation("kotlinx.parcelize.Parcelize")
            if (parcelize == null) {
                context.report(
                    ISSUE_KOTLIN_CLASS, context.getNameLocation(declaration),
                    "field：${declaration.name} 需要补充注解：@Parcelize," +
                            "删除describeContents 与" +
                            "writeToParcel 函数"
                )
            }
        }

        val fields = declaration.fields.filter {
            val name = it.type.canonicalText
//            "clz:$name,field:$it".log(TAG)
            !it.modifierList!!.hasModifierProperty("transient")
                    && !it.isStatic
                    && !matchBasicTypeName(name)
        }
        checkFieldsImplement(declaration.name ?: "", context, eva, fields, iName)
    }


    private fun checkFieldsImplement(
        clzName: String,
        context: JavaContext,
        eva: JavaEvaluator,
        fields: List<UField>,
        interfaceName: String,
    ) {
        fields.forEach { uField ->
            val fullName = uField.type.canonicalText
//            "$clzName:fullName:$fullName".log(TAG)
            try {
                val symbol = "<"
                val symbolIndex = fullName.indexOf(symbol)
                if (symbolIndex != -1 && fullName.contains('.')) {

                    val allTypeNameStr = fullName.replace(",", "<").run {
                        replace("? extends ", "")
                    }.run {
                        replace("? super ", "")
                    }
                    val firstRight = allTypeNameStr.indexOfFirst {
                        it.toString() == ">"
                    }
                    val typeList = allTypeNameStr.substring(0, firstRight).split("<")

                    typeList.forEach { typeName ->
                        if (typeName.contains(".")) {
                            val clz = eva.findClass(typeName)
                            clz?.let { it ->
                                filterAndReport(context, eva, it, uField, interfaceName)
                            }
                        }
                    }
                } else {
                    eva.findClass(fullName)?.let {
                        filterAndReport(context, eva, it, uField, interfaceName)
                    }
                }
            } catch (e: Throwable) {
                "=======error==== :$e,clzName:$clzName,fieldName:${uField.name},fullName:$fullName".log(
                    TAG
                )
                e.printStackTrace()
            }
        }
    }

    private fun filterAndReport(
        context: JavaContext, eva: JavaEvaluator, clazz: PsiClass, uField: UField,
        interfaceName: String
    ) {
        val type = eva.getClassType(clazz)
        var strictCheck = matchBasicType(eva, type)
        if (!strictCheck) {
            val isSerializable =
                eva.extendsClass(clazz, "java.io.Serializable")
            val isParcelable = eva.extendsClass(clazz, "android.os.Parcelable")
            val isCollection = eva.extendsClass(clazz, "java.util.Collection")
            val isMap = eva.extendsClass(clazz, "java.util.Map")
            strictCheck = isSerializable || isParcelable || isCollection || isMap
        }
        if (!strictCheck) {
            context.report(
                ISSUE_SERIALIZATION_MEMBERS, context.getLocation(uField),
                "field：${uField.name} 中的${clazz.name}的类型需要实现 $interfaceName"
            )
        }

    }


    private fun matchBasicType(eva: JavaEvaluator, psiType: PsiType?): Boolean {
        BASIC_TYPE_LIST.forEach {
            if (eva.typeMatches(psiType, it)) {
                return true
            }
        }
        return false
    }

    private fun matchBasicTypeName(name: String): Boolean {
        return when {
            BASIC_TYPE_LIST.contains(name) -> true
            else -> false
        }
    }


}