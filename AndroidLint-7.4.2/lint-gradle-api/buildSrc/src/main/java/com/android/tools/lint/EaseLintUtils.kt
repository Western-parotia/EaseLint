/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Class name to help Java access of the package functions
@file:JvmName("EaseLintUtils")

package com.android.tools.lint

import com.android.SdkConstants
import com.android.SdkConstants.AAPT_URI
import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ANDROID_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LOCALE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.BIN_FOLDER
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.MANIFEST_PLACEHOLDER_PREFIX
import com.android.SdkConstants.MANIFEST_PLACEHOLDER_SUFFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.PREFIX_BINDING_EXPR
import com.android.SdkConstants.PREFIX_TWOWAY_BINDING_EXPR
import com.android.SdkConstants.REQUEST_FOCUS
import com.android.SdkConstants.TAG
import com.android.SdkConstants.TAG_ATTR
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_IMPORT
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TAG_VARIABLE
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.FolderConfiguration.QUALIFIER_SPLITTER
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier.BCP_47_PREFIX
import com.android.ide.common.util.PathString
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.sdklib.SdkVersionInfo.camelCaseToUnderlines
import com.android.sdklib.SdkVersionInfo.underlinesToCamelCase
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope.ALL_DEPENDENCIES
import com.android.tools.lint.client.api.TYPE_BOOLEAN
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHAR
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_SHORT
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.UImplicitCallExpression
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.PositionXmlParser
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import com.android.utils.findGradleBuildFile
import com.android.utils.iterator
import com.google.common.base.MoreObjects
import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import com.google.common.io.ByteStreams
import com.intellij.ide.util.JavaAnonymousClassesHelper
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.skipParenthesizedExprUp
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun getInternalName(psiClass: PsiClass): String? {
    if (psiClass is PsiAnonymousClass) {
        val parent = PsiTreeUtil.getParentOfType(psiClass, PsiClass::class.java)
        if (parent != null) {
            val internalName = getInternalName(parent) ?: return null
            return internalName + JavaAnonymousClassesHelper.getName(psiClass)!!
        }
    }
    var sig = ClassUtil.getJVMClassName(psiClass)
    if (sig == null) {
        val qualifiedName = psiClass.qualifiedName
        return if (qualifiedName != null) {
            ClassContext.getInternalName(qualifiedName)
        } else null
    } else if (sig.indexOf('.') != -1) {
        // Workaround -- ClassUtil doesn't treat this correctly!
        // .replace('.', '/');
        sig = ClassContext.getInternalName(sig)
    }
    return sig
}

/** Returns the internal method name. */
fun getInternalMethodName(method: PsiMethod): String {
    return if (method.isConstructor) {
        SdkConstants.CONSTRUCTOR_NAME
    } else {
        method.name
    }
}

/**
 * Format a list of strings, and cut of the list at `maxItems` if the
 * number of items are greater.
 *
 * @param strings the list of strings to print out as a comma separated
 *     list
 * @param maxItems the maximum number of items to print
 * @return a comma separated list-string
 */
fun formatList(strings: List<String>, maxItems: Int = Integer.MAX_VALUE): String {
    return formatList(strings, maxItems, true)
}

/**
 * Format a list of strings, and cut of the list at `maxItems` if the
 * number of items are greater.
 *
 * @param strings the list of strings to print out as a comma separated
 *     list
 * @param maxItems the maximum number of items to print
 * @param sort whether the items should be sorted before printing.
 * @param useConjunction whether the last two items should be joined
 *     with "and" instead of ","
 * @return a comma separated list-string
 */
fun formatList(
    strings: List<String>,
    maxItems: Int = Integer.MAX_VALUE,
    sort: Boolean = true,
    useConjunction: Boolean = false
): String {
    var sortedStrings = strings
    if (sort) {
        val sorted = ArrayList(sortedStrings)
        sorted.sort()
        sortedStrings = sorted
    }

    val sb = StringBuilder(20 * sortedStrings.size)

    var i = 0
    val n = sortedStrings.size
    while (i < n) {
        if (sb.isNotEmpty()) {
            if (useConjunction && i == n - 1) {
                sb.append(" and ")
            } else {
                sb.append(", ")
            }
        }
        sb.append(sortedStrings[i])

        if (maxItems > 0 && i == maxItems - 1 && n > maxItems) {
            sb.append(String.format("... (%1\$d more)", n - i - 1))
            break
        }
        i++
    }

    return sb.toString()
}

/**
 * Determine if the given type corresponds to a resource that has a
 * unique file
 *
 * @param type the resource type to check
 * @return true if the given type corresponds to a file-type resource
 */
fun isFileBasedResourceType(type: ResourceType): Boolean {
    val folderTypes = FolderTypeRelationship.getRelatedFolders(type)
    for (folderType in folderTypes) {
        if (folderType != ResourceFolderType.VALUES) {
            return type != ResourceType.ID
        }
    }
    return false
}

/**
 * Returns true if the given file represents an XML file
 *
 * @param file the file to be checked
 * @return true if the given file is an xml file
 */
fun isXmlFile(file: File): Boolean {
    return SdkUtils.endsWithIgnoreCase(file.path, DOT_XML)
}

/**
 * Case insensitive ends with
 *
 * @param string the string to be tested whether it ends with the given
 *     suffix
 * @param suffix the suffix to check
 * @return true if `string` ends with `suffix`, case-insensitively.
 */
fun endsWith(string: String, suffix: String): Boolean {
    return string.regionMatches(
        string.length - suffix.length,
        suffix,
        0,
        suffix.length, ignoreCase = true /* ignoreCase */
    )
}

/**
 * Case insensitive starts with
 *
 * @param string the string to be tested whether it starts with the
 *     given prefix
 * @param prefix the prefix to check
 * @param offset the offset to start checking with
 * @return true if `string` starts with `prefix`, case-insensitively.
 */
fun startsWith(string: String, prefix: String, offset: Int): Boolean {
    return string.regionMatches(
        offset,
        prefix,
        0,
        prefix.length,
        ignoreCase = true /* ignoreCase */
    )
}

/**
 * Returns the basename of the given filename, unless it's a dot-file
 * such as ".svn".
 *
 * @param fileName the file name to extract the basename from
 * @return the basename (the filename without the file extension)
 */
fun getBaseName(fileName: String): String {
    val extension = fileName.indexOf('.')
    return if (extension > 0) {
        fileName.substring(0, extension)
    } else {
        fileName
    }
}

/**
 * Returns a description of counts for errors and warnings, such as "5
 * errors and 2 warnings" or "3 errors" or "2 warnings"
 *
 * @param errorCount the count of errors
 * @param warningCount the count of warnings
 * @param comma if true, use a comma to separate messages, otherwise
 *     "and"
 * @param capitalize whether we should capitalize sentence
 * @return a description string
 */
fun describeCounts(
    errorCount: Int,
    warningCount: Int,
    comma: Boolean,
    capitalize: Boolean
): String {
    if (errorCount == 0 && warningCount == 0) {
        return if (capitalize) {
            "No errors or warnings"
        } else {
            "no errors or warnings"
        }
    }
    val errors = pluralize(errorCount, "error")
    val warnings = pluralize(warningCount, "warning")
    return when {
        errorCount == 0 -> String.format("%1\$d %2\$s", warningCount, warnings)
        warningCount == 0 -> String.format("%1\$d %2\$s", errorCount, errors)
        else -> {
            val conjunction = if (comma) "," else " and"
            String.format(
                "%1\$d %2\$s%3\$s %4\$d %5\$s",
                errorCount, errors, conjunction, warningCount, warnings
            )
        }
    }
}

// PRIVATE because it only works for limited scenarios
private fun pluralize(count: Int, one: String): String {
    return if (count == 1) {
        one
    } else one + "s"
}

/**
 * Returns the children elements of the given node
 *
 * @param node the parent node
 * @return a list of element children, never null
 */
fun getChildren(node: Node): List<Element> {
    val childNodes = node.childNodes
    val children = ArrayList<Element>(childNodes.length)
    var i = 0
    val n = childNodes.length
    while (i < n) {
        val child = childNodes.item(i)
        if (child.nodeType == Node.ELEMENT_NODE) {
            children.add(child as Element)
        }
        i++
    }

    return children
}

/**
 * Returns the **number** of children of the given node
 *
 * @param node the parent node
 * @return the count of element children
 */
fun getChildCount(node: Node): Int {
    return XmlUtils.getSubTagCount(node)
}

/**
 * Returns true if the given element is the root element of its document
 *
 * @param element the element to test
 * @return true if the element is the root element
 */
fun isRootElement(element: Element): Boolean {
    return element === element.ownerDocument.documentElement
}

/**
 * Returns the given id without an `@id/` or `@+id` prefix
 *
 * @param id the id to strip
 * @return the stripped id, never null
 */
@Deprecated(
    message = "Use ResourceUrl for parsing @id and similar strings.",
    replaceWith = ReplaceWith(
        expression = "ResourceUrl.parse(id)?.name",
        imports = ["com.android.resources.ResourceUrl"]
    )
)
fun stripIdPrefix(id: String?): String {
    return when {
        id == null -> ""
        id.startsWith(NEW_ID_PREFIX) -> id.substring(NEW_ID_PREFIX.length)
        id.startsWith(ID_PREFIX) -> id.substring(ID_PREFIX.length)
        else -> id
    }
}

/**
 * Returns true if the given two id references match. This is similar to
 * String equality, but it also considers "`@+id/foo == @id/foo`.
 *
 * @param id1 the first id to compare
 * @param id2 the second id to compare
 * @return true if the two id references refer to the same id
 */
fun idReferencesMatch(id1: String?, id2: String?): Boolean {
    if (id1 == null || id2 == null || id1.isEmpty() || id2.isEmpty()) {
        return false
    }
    if (id1.startsWith(NEW_ID_PREFIX)) {
        return if (id2.startsWith(NEW_ID_PREFIX)) {
            id1 == id2
        } else {
            assert(id2.startsWith(ID_PREFIX)) { id2 }
            id1.length - id2.length == NEW_ID_PREFIX.length - ID_PREFIX.length && id1.regionMatches(
                NEW_ID_PREFIX.length,
                id2,
                ID_PREFIX.length,
                id2.length - ID_PREFIX.length
            )
        }
    } else {
        assert(id1.startsWith(ID_PREFIX)) { id1 }
        return if (id2.startsWith(ID_PREFIX)) {
            id1 == id2
        } else {
            assert(id2.startsWith(NEW_ID_PREFIX))
            id2.length - id1.length == NEW_ID_PREFIX.length - ID_PREFIX.length && id2.regionMatches(
                NEW_ID_PREFIX.length,
                id1,
                ID_PREFIX.length,
                id1.length - ID_PREFIX.length
            )
        }
    }
}

/**
 * Computes a canonical "display path" for a resource (which typically
 * is the parent name plus a file separator, plus the file name)
 *
 * @param client lint client used for formatting
 * @param file resource file
 * @return the display path
 */
fun getFileNameWithParent(client: LintClient, file: File): String {
    return client.getDisplayPath(File(file.parentFile.name, file.name))
}

/**
 * Computes a canonical "display path" for a resource (which typically
 * is the parent name plus a file separator, plus the file name)
 *
 * @param client lint client used for formatting
 * @param file resource file
 * @return the display path
 */
fun getFileNameWithParent(client: LintClient, file: PathString): String {
    val parent = file.parent ?: throw IllegalArgumentException()
    return client.getDisplayPath(File(parent.fileName, file.fileName))
}

/**
 * Returns true if the first string can be edited (Via insertions,
 * deletions or substitutions) into the second string in at most the
 * given number of editing operations. This computes the edit distance
 * between the two strings and returns true if it is less than or equal
 * to the given threshold.
 *
 * @param s the first string to compare
 * @param t the second string to compare
 * @param max the maximum number of edit operations allowed
 * @return true if the first string is editable to the second string in
 *     at most the given number of steps
 */
fun isEditableTo(s: String, t: String, max: Int): Boolean {
    return editDistance(s, t, max) <= max
}

/**
 * Computes the edit distance (number of insertions, deletions or
 * substitutions to edit one string into the other) between two strings.
 * In particular, this will compute the Levenshtein distance.
 *
 * See http://en.wikipedia.org/wiki/Levenshtein_distance for details.
 *
 * @param s the first string to compare
 * @param t the second string to compare
 * @param max the maximum edit distance that we care about; if for
 *     example the string length delta is greater than this we
 *     don't bother computing the exact edit distance since the
 *     caller has indicated they're not interested in the result
 * @return the edit distance between the two strings, or some other
 *     value greater than that if the edit distance
 *     is at least as big as the `max` parameter
 */
fun editDistance(s: String, t: String, max: Int = Integer.MAX_VALUE): Int {
    if (s == t) {
        return 0
    }

    if (Math.abs(s.length - t.length) > max) {
        // The string lengths differ more than the allowed edit distance;
        // no point in even attempting to compute the edit distance (requires
        // O(n*m) storage and O(n*m) speed, where n and m are the string lengths)
        return Integer.MAX_VALUE
    }

    val m = s.length
    val n = t.length
    val d = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) {
        d[i][0] = i
    }
    for (j in 0..n) {
        d[0][j] = j
    }
    for (j in 1..n) {
        for (i in 1..m) {
            if (s[i - 1] == t[j - 1]) {
                d[i][j] = d[i - 1][j - 1]
            } else {
                val deletion = d[i - 1][j] + 1
                val insertion = d[i][j - 1] + 1
                val substitution = d[i - 1][j - 1] + 1
                d[i][j] = Math.min(deletion, Math.min(insertion, substitution))
            }
        }
    }

    return d[m][n]
}

/**
 * Returns true if assertions are enabled
 *
 * @return true if assertions are enabled
 */
fun assertionsEnabled(): Boolean = LintJavaUtils.assertionsEnabled()

/**
 * Attempts to find the [PsiMethod] for the operator overload of this
 * array access expression.
 *
 * Temporary workaround for
 * https://youtrack.jetbrains.com/issue/KTIJ-18765
 */
// TODO(kotlin-uast-cleanup): remove this when a fix for https://youtrack.jetbrains.com/issue/KTIJ-18765 arrives.
fun UArrayAccessExpression.resolveOperator(skipOverloadedSetter: Boolean = true): PsiMethod? {
    if (skipOverloadedSetter && uastParent is UBinaryExpression) {
        val uastParentCapture = uastParent as UBinaryExpression
        if (uastParentCapture.leftOperand == this && uastParentCapture.operator == UastBinaryOperator.ASSIGN) {
            return null
        }
    }

    val receiver = this.receiver
    // for normal arrays this is typically PsiArrayType; a PsiClassType
    // is a sign that it's an operator overload
    if (receiver.getExpressionType() !is PsiClassType) return null

    // No UAST accessor method to find the corresponding get/set methods; see
    // https://youtrack.jetbrains.com/issue/KTIJ-18765
    // Instead we'll search ourselves.

    // First try Kotlin resolving service (base version, not FE1.0 variant)
    val ktElement = sourcePsi as? KtElement ?: return null
    val baseService = ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
        ?: return null
    return baseService.resolveCall(ktElement)
}

// TODO(kotlin-uast-cleanup): remove this when a fix for https://youtrack.jetbrains.com/issue/KTIJ-17726 arrives.
// Excerpted/modified from IJ 189b439abec02e39db28fbfa12092b6ca014040a
fun UBinaryExpression.resolveOperatorWorkaround(): PsiMethod? {
    // Try the existing logic first to resolve binary operator in general
    resolveOperator()?.let { return it }
    return when (operator) {
        UastBinaryOperator.ASSIGN -> {
            // Try to resolve overloaded indexed setter
            (leftOperand as? UArrayAccessExpression)?.resolveOperator(skipOverloadedSetter = false)
        }
        else -> null
    }
}

/**
 * Returns the layout resource name for the given layout file
 *
 * @param layoutFile the file pointing to the layout
 * @return the layout resource name, not including the `@layout` prefix
 */
fun getLayoutName(layoutFile: File): String {
    var name = layoutFile.name
    val dotIndex = name.indexOf('.')
    if (dotIndex != -1) {
        name = name.substring(0, dotIndex)
    }
    return name
}

/**
 * Splits the given path into its individual parts, attempting to be
 * tolerant about path separators (: or ;). It can handle possibly
 * ambiguous paths, such as `c:\foo\bar:\other`, though of course these
 * are to be avoided if possible.
 *
 * @param path the path variable to split, which can use both : and ; as
 *     path separators.
 * @return the individual path components as an Iterable of strings
 */
fun splitPath(path: String): Iterable<String> {
    if (path.indexOf(';') != -1) {
        return Splitter.on(';').omitEmptyStrings().trimResults().split(path)
    }

    val combined = ArrayList<String>()
    Iterables.addAll(combined, Splitter.on(':').omitEmptyStrings().trimResults().split(path))
    var i = 0
    var n = combined.size
    while (i < n) {
        val p = combined[i]
        if (p.length == 1 &&
            i < n - 1 &&
            Character.isLetter(p[0]) &&
            // Technically, Windows paths do not have to have a \ after the :,
            // which means it would be using the current directory on that drive,
            // but that's unlikely to be the case in a path since it would have
            // unpredictable results
            !combined[i + 1].isEmpty() &&
            combined[i + 1][0] == '\\'
        ) {
            combined[i] = p + ':'.toString() + combined[i + 1]
            combined.removeAt(i + 1)
            n--
        }
        i++
    }

    return combined
}

/**
 * Computes the shared parent among a set of files (which may be null).
 *
 * @param files the set of files to be checked
 * @return the closest common ancestor file, or null if none was found
 */
fun getCommonParent(files: List<File>): File? {
    val fileCount = files.size
    when (fileCount) {
        0 -> return null
        1 -> return files[0]
        2 -> return getCommonParent(files[0], files[1])
        else -> {
            var common: File? = files[0]
            for (i in 1 until fileCount) {
                common = getCommonParent(common!!, files[i])
                if (common == null) {
                    return null
                }
            }

            return common
        }
    }
}

/**
 * Computes the closest common parent path between two files.
 *
 * @param file1 the first file to be compared
 * @param file2 the second file to be compared
 * @return the closest common ancestor file, or null if the two files
 *     have no common parent
 */
fun getCommonParent(file1: File, file2: File): File? {
    when {
        //noinspection FileComparisons
        file1 == file2 -> return file1
        file1.path.startsWith(file2.path) -> return file2
        file2.path.startsWith(file1.path) -> return file1
        else -> {
            // Simple implementation
            var first: File? = file1.parentFile
            while (first != null) {
                var second: File? = file2.parentFile
                while (second != null) {
                    //noinspection FileComparisons
                    if (first == second) {
                        return first
                    }
                    second = second.parentFile
                }

                first = first.parentFile
            }
        }
    }
    return null
}

/**
 * Returns true if the given [parentCandidate] path is a parent of the
 * given [file]
 */
fun isParent(parentCandidate: File?, file: File, strict: Boolean = true): Boolean {
    parentCandidate ?: return false

    val path = file.path
    val parentPath = parentCandidate.path

    val parentLength = parentPath.length
    val pathLength = path.length
    if (pathLength <= parentLength) {
        if (pathLength == parentLength) {
            return if (strict) false else path == parentPath
        }
        return false
    } else if (!path.startsWith(parentPath)) {
        return false
    } else {
        return path[parentLength] == '/' || path[parentLength] == '\\'
    }
}

/** Returns a RFC3986 compliant URI for the given file. */
fun getFileUri(file: File): String {
    return file.toURI().toURL().toString().let {
        // Make URI comply with https://tools.ietf.org/html/rfc3986; the JDK
        // uses file: instead of file:// prefixes
        if (it.startsWith("file:") && !it.startsWith("file://")) {
            "file://" + it.substring(5)
        } else {
            it
        }
    }
}

/**
 * Returns the encoded String for the given file. This is usually the
 * same as `Files.toString(file, Charsets.UTF8`, but if there's a UTF
 * byte order mark (for UTF8, UTF_16 or UTF_16LE), use that instead.
 *
 * @param client the client to use for I/O operations
 * @param file the file to read from
 * @param createString If true, create a [String] instead of a general
 *     [CharSequence]
 * @return the string
 */
@Throws(IOException::class)
fun getEncodedString(
    client: LintClient,
    file: File,
    createString: Boolean
): CharSequence {
    val bytes = client.readBytes(file)
    return if (endsWith(file.name, DOT_XML)) {
        PositionXmlParser.getXmlString(bytes)
    } else com.android.tools.lint.detector.api.getEncodedString(bytes, createString)
}

/**
 * Returns true if the given resource value is a data binding expression
 *
 * @param expression the expression to test
 * @return true if this is a data binding expression
 */
fun isDataBindingExpression(expression: String): Boolean {
    return expression.startsWith(PREFIX_BINDING_EXPR) || expression.startsWith(
        PREFIX_TWOWAY_BINDING_EXPR
    )
}

/**
 * Returns true if the given resource value is a manifest place holder
 * expression.
 */
fun isManifestPlaceHolderExpression(expression: String): Boolean {
    return expression.contains(MANIFEST_PLACEHOLDER_PREFIX)
}

private const val UTF_16 = "UTF_16"
private const val UTF_16LE = "UTF_16LE"


/**
 * Returns true if the given class node represents a static inner class.
 *
 * @param classNode the inner class to be checked
 * @return true if the class node represents an inner class that is
 *     static
 */
fun isStaticInnerClass(classNode: ClassNode): Boolean {
    // Note: We can't just filter out static inner classes like this:
    //     (classNode.access & Opcodes.ACC_STATIC) != 0
    // because the static flag only appears on methods and fields in the class
    // file. Instead, look for the synthetic this pointer.

    val fieldList = classNode.fields // ASM API
    for (f in fieldList) {
        val field = f as FieldNode
        if (field.name.startsWith("this$") && field.access and Opcodes.ACC_SYNTHETIC != 0) {
            return false
        }
    }

    return true
}

/**
 * Returns true if the given class node represents an anonymous inner
 * class
 *
 * @param classNode the class to be checked
 * @return true if the class appears to be an anonymous class
 */
fun isAnonymousClass(classNode: ClassNode): Boolean {
    if (classNode.outerClass == null) {
        return false
    }

    val name = classNode.name
    val index = name.lastIndexOf('$')
    return if (index == -1 || index == name.length - 1) {
        false
    } else Character.isDigit(name[index + 1])
}

/**
 * Returns the previous opcode prior to the given node, ignoring label
 * and line number nodes
 *
 * @param node the node to look up the previous opcode for
 * @return the previous opcode, or [Opcodes.NOP] if no previous node was
 *     found
 */
fun getPrevOpcode(node: AbstractInsnNode): Int {
    val prev = getPrevInstruction(node)
    return prev?.opcode ?: Opcodes.NOP
}

/**
 * Returns the previous instruction prior to the given node, ignoring
 * label and line number nodes.
 *
 * @param node the node to look up the previous instruction for
 * @return the previous instruction, or null if no previous node was
 *     found
 */
fun getPrevInstruction(node: AbstractInsnNode): AbstractInsnNode? {
    var prev: AbstractInsnNode? = node
    while (true) {
        prev = prev!!.previous
        if (prev == null) {
            return null
        } else {
            val type = prev.type
            if (type != AbstractInsnNode.LINE &&
                type != AbstractInsnNode.LABEL &&
                type != AbstractInsnNode.FRAME
            ) {
                return prev
            }
        }
    }
}

/**
 * Returns the next opcode after to the given node, ignoring label and
 * line number nodes
 *
 * @param node the node to look up the next opcode for
 * @return the next opcode, or [Opcodes.NOP] if no next node was found
 */
fun getNextOpcode(node: AbstractInsnNode): Int {
    val next = getNextInstruction(node)
    return next?.opcode ?: Opcodes.NOP
}

/**
 * Returns the next instruction after to the given node, ignoring label
 * and line number nodes.
 *
 * @param node the node to look up the next node for
 * @return the next instruction, or null if no next node was found
 */
fun getNextInstruction(node: AbstractInsnNode): AbstractInsnNode? {
    var next: AbstractInsnNode? = node
    while (true) {
        next = next!!.next
        if (next == null) {
            return null
        } else {
            val type = next.type
            if (type != AbstractInsnNode.LINE &&
                type != AbstractInsnNode.LABEL &&
                type != AbstractInsnNode.FRAME
            ) {
                return next
            }
        }
    }
}

/**
 * Returns true if the given directory is a lint manifest file
 * directory.
 *
 * @param dir the directory to check
 * @return true if the directory contains a manifest file
 */
fun isManifestFolder(dir: File?): Boolean {
    var current = dir ?: return false
    val hasManifest = File(current, ANDROID_MANIFEST_XML).exists()
    if (hasManifest) {
        // Special case: the bin/ folder can also contain a copy of the
        // manifest file, but this is *not* a project directory
        if (current.name == BIN_FOLDER) {
            // ...unless of course it just *happens* to be a project named bin, in
            // which case we peek at its parent to see if this is the case
            current = current.parentFile

            if (current != null && isManifestFolder(current)) {
                // Yes, it's a bin/ directory inside a real project: ignore this dir
                return false
            }
        }
    }

    return hasManifest
}

/**
 * Look up the locale and region from the given parent folder name and
 * return it as a combined string, such as "en", "en-rUS", b+eng-US,
 * etc, or null if no language is specified.
 *
 * @param folderName the folder name
 * @return the locale+region string or null
 */
fun getLocaleAndRegion(folderName: String): String? {
    if (folderName.indexOf('-') == -1) {
        return null
    }

    var locale: String? = null

    for (qualifier in QUALIFIER_SPLITTER.split(folderName)) {
        val qualifierLength = qualifier.length
        if (qualifierLength == 2) {
            val first = qualifier[0]
            val second = qualifier[1]
            if (first in 'a'..'z' && second in 'a'..'z') {
                locale = qualifier
            }
        } else if (qualifierLength == 3 && qualifier[0] == 'r' && locale != null) {
            val first = qualifier[1]
            val second = qualifier[2]
            if (first in 'A'..'Z' && second in 'A'..'Z') {
                return locale + '-'.toString() + qualifier
            }
            break
        } else if (qualifier.startsWith(BCP_47_PREFIX)) {
            return qualifier
        }
    }

    return locale
}

/**
 * Looks up the resource values for the given attribute given a style.
 * Note that this only looks project-level style values, it does not
 * resume into the framework styles.
 *
 * It will return a list of resource values if the styles were found,
 * and false if it failed to resolve some styles, unless [acceptMissing]
 * is true.
 */
fun getStyleAttributes(
    project: Project,
    client: LintClient,
    styleUrl: String,
    namespaceUri: String,
    attribute: String,
    acceptMissing: Boolean = false
): List<ResourceValue>? {
    val resources = client.getResources(project, ALL_DEPENDENCIES)
    val style = ResourceUrl.parse(styleUrl)
    if (style == null || style.isFramework) {
        return null
    }

    val result: MutableList<ResourceValue> = ArrayList()

    val queue = ArrayDeque<ResourceValue>()
    queue.add(ResourceValueImpl(ResourceNamespace.RES_AUTO, style.type, style.name, null))
    val seen = HashSet<String>()
    var count = 0
    while (count < 30 && !queue.isEmpty()) {
        val front = queue.remove()
        val name = front.name
        seen.add(name)
        val items = resources.getResources(ResourceNamespace.TODO(), front.resourceType, name)
        if (!acceptMissing && items.isEmpty()) {
            // Failed to resolve the given resource: return null since it's possible that
            // we'll have false positives
            return null
        }
        for (item in items) {
            val rv = item.resourceValue
            if (rv is StyleResourceValue) {
                val srv = rv as StyleResourceValue?
                val namespace = MoreObjects.firstNonNull(
                    ResourceNamespace.fromNamespaceUri(namespaceUri),
                    ResourceNamespace.TODO()
                )
                val frameworkResource: String? = if (!attribute.startsWith(ANDROID_NS_NAME_PREFIX) &&
                    namespace == ResourceNamespace.ANDROID
                ) {
                    ANDROID_NS_NAME_PREFIX + attribute
                } else {
                    null
                }
                for (styleItem in srv!!.definedItems) {
                    if (styleItem.attrName == attribute && styleItem.namespace == namespace ||
                        styleItem.attrName == frameworkResource
                    ) {
                        if (!result.contains(styleItem)) {
                            result.add(styleItem)
                        }
                    }
                }

                // TODO: namespaces
                val parent = srv.parentStyleName
                if (parent != null && !parent.startsWith(ANDROID_PREFIX)) {
                    val p = ResourceUrl.parse(parent)
                    if (p != null) {
                        if (!acceptMissing && p.isFramework) {
                            return null
                        }
                        if (!p.isFramework && !seen.contains(p.name)) {
                            seen.add(p.name)
                            queue.add(
                                ResourceValueImpl(
                                    ResourceNamespace.RES_AUTO,
                                    ResourceType.STYLE,
                                    p.name, null
                                )
                            )
                        }
                    }
                }

                val index = name.lastIndexOf('.')
                if (parent == null && index > 0) {
                    val parentName = name.substring(0, index)
                    if (!seen.contains(parentName)) {
                        seen.add(parentName)
                        queue.add(
                            ResourceValueImpl(
                                ResourceNamespace.RES_AUTO,
                                ResourceType.STYLE,
                                parentName, null
                            )
                        )
                    }
                }
            }
        }

        count++
    }

    return result
}

fun getInheritedStyles(
    project: Project,
    client: LintClient,
    styleUrl: String
): List<StyleResourceValue>? {
    val resources = client.getResources(project, ALL_DEPENDENCIES)
    val style = ResourceUrl.parse(styleUrl)
    if (style == null || style.isFramework) {
        return null
    }

    var result: MutableList<StyleResourceValue>? = null

    val queue = ArrayDeque<ResourceValue>()
    queue.add(ResourceValueImpl(ResourceNamespace.RES_AUTO, style.type, style.name, null))
    val seen = HashSet<String>()
    var count = 0
    while (count < 30 && !queue.isEmpty()) {
        val front = queue.remove()
        val name = front.name
        seen.add(name)
        val items = resources.getResources(ResourceNamespace.TODO(), front.resourceType, name)
        for (item in items) {
            val rv = item.resourceValue
            if (rv is StyleResourceValue) {
                if (result == null) {
                    result = ArrayList()
                }
                result.add(rv)

                // TODO: namespaces
                val parent = rv.parentStyleName
                if (parent != null && !parent.startsWith(ANDROID_PREFIX)) {
                    val p = ResourceUrl.parse(parent)
                    if (p != null && !p.isFramework && !seen.contains(p.name)) {
                        seen.add(p.name)
                        queue.add(
                            ResourceValueImpl(
                                ResourceNamespace.RES_AUTO,
                                ResourceType.STYLE,
                                p.name, null
                            )
                        )
                    }
                }

                val index = name.lastIndexOf('.')
                if (index > 0) {
                    val parentName = name.substring(0, index)
                    if (!seen.contains(parentName)) {
                        seen.add(parentName)
                        queue.add(
                            ResourceValueImpl(
                                ResourceNamespace.RES_AUTO,
                                ResourceType.STYLE,
                                parentName, null
                            )
                        )
                    }
                }
            }
        }

        count++
    }

    return result
}

/**
 * Returns true if the given two paths point to the same logical
 * resource file within a source set. This means that it only checks the
 * parent folder name and individual file name, not the path outside the
 * parent folder.
 *
 * @param file1 the first file to compare
 * @param file2 the second file to compare
 * @return true if the two files have the same parent and file names
 */
fun isSameResourceFile(file1: File?, file2: File?): Boolean {
    if (file1 != null && file2 != null && file1.name == file2.name) {
        val parent1 = file1.parentFile
        val parent2 = file2.parentFile
        if (parent1 != null && parent2 != null && parent1.name == parent2.name) {
            return true
        }
    }

    return false
}

/**
 * Computes a suggested name given a resource prefix and resource name.
 */
fun computeResourceName(
    prefix: String,
    name: String,
    folderType: ResourceFolderType? = null
): String {
    var newPrefix = prefix
    var newName = name
    if (newPrefix.isEmpty()) {
        return newName
    }

    // Regardless of the prefix, if creating a file based resource such as a layout,
    // the name must be lower-case only
    if (folderType != null && folderType != ResourceFolderType.VALUES) {
        newName = newName.toLowerCase(Locale.ROOT)
        var underlined = camelCaseToUnderlines(newPrefix)
        if (newPrefix != underlined) {
            if (!underlined.endsWith("_")) {
                underlined += "_"
            }
            newPrefix = underlined
        }
    }

    // If prefix is "myPrefix" and then name is "MyStyle", we should construct MyPrefixMyStyle
    if (!newName.isEmpty() && Character.isUpperCase(newName[0])) {
        if (newPrefix.indexOf('_') != -1) {
            newPrefix = underlinesToCamelCase(newPrefix)
        }
        newPrefix = Character.toUpperCase(newPrefix[0]) + newPrefix.substring(1)
        // and if the prefix is myPrefix_ we should still construct MyPrefixMyStyle, not
        // MyPrefix_MyStyle
        if (newPrefix.endsWith("_")) {
            newPrefix = newPrefix.substring(0, newPrefix.length - 1)
        }
    }

    return when {
        newName.isEmpty() -> newPrefix
        newPrefix.endsWith("_") -> newPrefix + newName
        else -> newPrefix + Character.toUpperCase(newName[0]) + newName.substring(1)
    }
}

/**
 * Returns true if the given Gradle model is older than the given
 * version number.
 */
fun isModelOlderThan(
    project: Project,
    major: Int,
    minor: Int,
    micro: Int,
    defaultForNonGradleProjects: Boolean = false
): Boolean {
    val version = project.gradleModelVersion ?: return defaultForNonGradleProjects

    if (version.major != major) {
        return version.major < major
    }
    return if (version.minor != minor) {
        version.minor < minor
    } else version.micro < micro
}

/**
 * Returns the Java language level for the given element, or the default
 * level if an applicable the language level is not found (for example
 * if the element is not a Java element.
 */
fun getLanguageLevel(
    element: UElement,
    defaultLevel: LanguageLevel
): LanguageLevel {
    val containingFile = element.getContainingUFile() ?: return defaultLevel

    return getLanguageLevel(containingFile.psi, defaultLevel)
}

/**
 * Returns the Java language level for the given element, or the default
 * level if an applicable the language level is not found (for example
 * if the element is not a Java element.
 */
fun getLanguageLevel(
    element: PsiElement,
    defaultLevel: LanguageLevel
): LanguageLevel {
    val containingFile = element as? PsiFile ?: element.containingFile
    return (containingFile as? PsiJavaFile)?.languageLevel ?: defaultLevel
}

/**
 * Returns the Java language level for the given element, or the default
 * level if an applicable the language level is not found (for example
 * if the element is not a Java element.
 */
fun getLanguageLevel(
    project: Project,
    defaultLevel: LanguageLevel
): LanguageLevel {
    val p = project.ideaProject ?: return defaultLevel
    val extension = LanguageLevelProjectExtension.getInstance(p) ?: return defaultLevel
    return extension.languageLevel
}

/**
 * Looks for a certain string within a larger string, which should
 * immediately follow the given prefix and immediately precede the given
 * suffix.
 *
 * @param string the full string to search
 * @param prefix the optional prefix to follow
 * @param suffix the optional suffix to precede
 * @return the corresponding substring, if present
 */
fun findSubstring(
    string: String,
    prefix: String?,
    suffix: String?
): String? {
    var start = 0
    if (prefix != null) {
        start = string.indexOf(prefix)
        if (start == -1) {
            return null
        }
        start += prefix.length
    }

    if (suffix != null) {
        val end = string.indexOf(suffix, start)
        return if (end == -1) {
            null
        } else string.substring(start, end)
    }

    return string.substring(start)
}

/**
 * Splits up the given message coming from a given string format (where
 * the string format follows the very specific convention of having only
 * strings formatted exactly with the format %n$s where n is between 1
 * and 9 inclusive, and each formatting parameter appears exactly once,
 * and in increasing order.
 *
 * @param format the format string responsible for creating the error
 *     message
 * @param errorMessage an error message formatted with the format string
 * @return the specific values inserted into the format
 */
fun getFormattedParameters(
    format: String,
    errorMessage: String
): List<String> {
    val pattern = StringBuilder(format.length)
    var parameter = 1
    run {
        var i = 0
        val n = format.length
        while (i < n) {
            val c = format[i]
            if (c == '%') {
                // Only support formats of the form %n$s where n is 1 <= n <=9
                assert(i < format.length - 4) { format }
                assert(format[i + 1].toInt() == '0'.toInt() + parameter) { format }
                assert(Character.isDigit(format[i + 1])) { format }
                assert(format[i + 2] == '$') { format }
                assert(format[i + 3] == 's') { format }
                parameter++
                i += 3
                pattern.append("(.*)")
            } else {
                pattern.append(c)
            }
            i++
        }
    }
    try {
        val compile = Pattern.compile(pattern.toString())
        val matcher = compile.matcher(errorMessage)
        if (matcher.find()) {
            val groupCount = matcher.groupCount()
            val parameters = ArrayList<String>(groupCount)
            for (i in 1..groupCount) {
                parameters.add(matcher.group(i))
            }

            return parameters
        }
    } catch (pse: PatternSyntaxException) {
        // Internal error: string format is not valid. Should be caught by unit tests
        // as a failure to return the formatted parameters.
    }

    return emptyList()
}

/**
 * Returns the locale for the given parent folder.
 *
 * @param parent the name of the parent folder
 * @return null if the locale is not known, or a locale qualifier
 *     providing the language and possibly region
 */
fun getLocale(parent: String): LocaleQualifier? {
    if (parent.indexOf('-') != -1) {
        val config = FolderConfiguration.getConfigForFolder(parent)
        if (config != null) {
            return config.localeQualifier
        }
    }
    return null
}

/**
 * Returns the locale for the given context.
 *
 * @param context the context to look up the locale for
 * @return null if the locale is not known, or a locale qualifier
 *     providing the language and possibly region
 */
fun getLocale(context: XmlContext): LocaleQualifier? {
    val root = context.document.documentElement
    if (root != null) {
        val locale = root.getAttributeNS(TOOLS_URI, ATTR_LOCALE)
        if (locale != null && !locale.isEmpty()) {
            if (locale.indexOf('-') == -1) {
                return LocaleQualifier.getQualifier(locale)
            }
            val config = FolderConfiguration.getConfigForQualifierString(locale)
            if (config != null) {
                return config.localeQualifier
            }
        }
    }

    return getLocale(context.file.parentFile.name)
}

/**
 * Check whether the given resource file is in an English locale
 *
 * @param context the XML context for the resource file
 * @param assumeForBase whether the base folder (e.g. no locale
 *     specified) should be treated as English
 */
fun isEnglishResource(context: XmlContext, assumeForBase: Boolean): Boolean {
    val locale = getLocale(context)
    return if (locale == null) {
        assumeForBase
    } else {
        "en" == locale.language
    }
}

/**
 * Create a [Location] for an error in the top level build.gradle file.
 * This is necessary when we're doing an analysis based on the Gradle
 * interpreted model, not from parsing Gradle files - and the model
 * doesn't provide source positions.
 *
 * @param project the project containing the gradle file being analyzed
 * @return location for the top level gradle file if it exists,
 *     otherwise fall back to the project directory.
 */
fun guessGradleLocation(project: Project): Location {
    val dir = project.getDir()
    val location: Location
    val topLevel = findGradleBuildFile(dir)
    location = if (topLevel.exists()) {
        Location.create(topLevel)
    } else {
        Location.create(dir)
    }
    return location
}

/**
 * Attempts to find a string in the build.gradle file for a given
 * project directory. It will skip comments.
 *
 * @param client the client (used to read the file)
 * @param projectDir the project directory
 * @param string the string to locate
 * @return a suitable location (or just the build.gradle file, or the
 *     project directory, if not found)
 */
fun guessGradleLocation(
    client: LintClient,
    projectDir: File,
    string: String?
): Location {
    val gradle = findGradleBuildFile(projectDir)
    return guessGradleLocationForFile(client, gradle, string)
}

/**
 * Like [guessGradleLocation] but where the specific build.gradle file
 * is known.
 */
fun guessGradleLocationForFile(
    client: LintClient,
    gradle: File,
    string: String?
): Location {
    if (gradle.isFile) {
        if (string == null) {
            return Location.create(gradle)
        }
        val contents = client.readFile(gradle).toString()
        val match = contents.indexOf(string)
        if (match != -1) {
            val length = string.length
            if (contents.indexOf(string, match + length) == -1) {
                // Only one match in the file: just use it
                return Location.create(gradle, contents, match, match + length)
            }
            // Tokenize the file such that we can skip comments
            val end = contents.length
            val first = string[0]
            var offset = 0
            while (offset < end - 1) {
                val c = contents[offset]
                if (c == '/') {
                    val next = contents[offset + 1] // safe because we loop to end-1
                    if (next == '/') {
                        // Line comment: jump to end of line
                        offset = contents.indexOf('\n', offset)
                        if (offset == -1) {
                            break
                        }
                        offset++
                        continue
                    } else if (next == '*') {
                        // Comment: jump to end of comment
                        offset = contents.indexOf("*/", offset)
                        if (offset == -1) {
                            break
                        }
                        continue
                    }
                }
                if (c == first && contents.regionMatches(offset, string, 0, length)) {
                    return Location.create(gradle, contents, offset, offset + length)
                }
                offset++
            }
        }
    } else if (gradle.isDirectory) {
        return guessGradleLocation(client, gradle, string)
    }

    return Location.create(gradle)
}

/**
 * Returns true if the given element is the null literal
 *
 * @param element the element to check
 * @return true if the element is "null"
 */
fun isNullLiteral(element: PsiElement?): Boolean {
    return element is PsiLiteral && "null" == element.text
}

fun isTrueLiteral(element: PsiElement?): Boolean {
    return element is PsiLiteral && "true" == element.text
}

fun isFalseLiteral(element: PsiElement?): Boolean {
    return element is PsiLiteral && "false" == element.text
}

@Deprecated(
    message = "This method is ambiguous. To move up/out in the AST, use skipParenthesizedExprUp instead " +
            "(and from Java, import static method in UastUtils). " +
            "To go inside the parentheses, instead use skipParenthesizedExprUp()",
    replaceWith = ReplaceWith(
        "skipParenthesizedExprUp(element)",
        "com.intellij.psi.util.PsiUtil.skipParenthesizedExprUp"
    )
)
fun skipParentheses(element: PsiElement?): PsiElement? {
    return PsiUtil.skipParenthesizedExprUp(element)
}

@Deprecated(
    message = "This method is ambiguous. To move up/out in the AST, use skipParenthesizedExprUp instead " +
            "(and from Java, import static method in UastUtils). If the element is not a UExpression there's no need to call this method. " +
            "To go inside the parentheses, instead use skipParenthesizedExprUp()",
    replaceWith = ReplaceWith("skipParenthesizedExprUp(element)", "org.jetbrains.uast.skipParenthesizedExprUp")
)
fun skipParentheses(element: UElement?): UElement? {
    var current = element
    while (current is UParenthesizedExpression) {
        current = current.uastParent
    }

    return current
}

fun nextNonWhitespace(element: PsiElement?): PsiElement? {
    var current = element
    if (current != null) {
        current = current.nextSibling
        while (current is PsiWhiteSpace) {
            current = current.nextSibling
        }
    }

    return current
}

fun prevNonWhitespace(element: PsiElement?): PsiElement? {
    var current = element
    if (current != null) {
        current = current.prevSibling
        while (current is PsiWhiteSpace) {
            current = current.prevSibling
        }
    }

    return current
}

fun isString(type: PsiType): Boolean {
    if (type is PsiClassType) {
        val shortName = type.className
        if (!Objects.equal(shortName, CommonClassNames.JAVA_LANG_STRING_SHORT)) {
            return false
        }
    }
    return CommonClassNames.JAVA_LANG_STRING == type.canonicalText
}

/**
 * Returns whether the given [field] has a value that will be inlined a
 * compile time. See JLS 15.28 and JLS 13.4.9.
 */
fun isInlined(field: PsiField, evaluator: JavaEvaluator): Boolean {
    val type = field.type
    return if (type is PsiPrimitiveType || isString(type)) {
        evaluator.isStatic(field) && evaluator.isFinal(field)
    } else {
        false
    }
}

fun getAutoBoxedType(primitive: String): String? {
    return when {
        TYPE_INT == primitive -> TYPE_INTEGER_WRAPPER
        TYPE_LONG == primitive -> TYPE_LONG_WRAPPER
        TYPE_CHAR == primitive -> TYPE_CHARACTER_WRAPPER
        TYPE_FLOAT == primitive -> TYPE_FLOAT_WRAPPER
        TYPE_DOUBLE == primitive -> TYPE_DOUBLE_WRAPPER
        TYPE_BOOLEAN == primitive -> TYPE_BOOLEAN_WRAPPER
        TYPE_SHORT == primitive -> TYPE_SHORT_WRAPPER
        TYPE_BYTE == primitive -> TYPE_BYTE_WRAPPER
        else -> null
    }
}

fun getPrimitiveType(autoBoxedType: String): String? {
    return when {
        TYPE_INTEGER_WRAPPER == autoBoxedType -> TYPE_INT
        TYPE_LONG_WRAPPER == autoBoxedType -> TYPE_LONG
        TYPE_CHARACTER_WRAPPER == autoBoxedType -> TYPE_CHAR
        TYPE_FLOAT_WRAPPER == autoBoxedType -> TYPE_FLOAT
        TYPE_DOUBLE_WRAPPER == autoBoxedType -> TYPE_DOUBLE
        TYPE_BOOLEAN_WRAPPER == autoBoxedType -> TYPE_BOOLEAN
        TYPE_SHORT_WRAPPER == autoBoxedType -> TYPE_SHORT
        TYPE_BYTE_WRAPPER == autoBoxedType -> TYPE_BYTE
        else -> null
    }
}

/**
 * Returns the fully qualified class name for a manifest entry element
 * that specifies a name attribute. Will also replace $ with dots for
 * inner classes.
 *
 * @param element the element
 * @return the fully qualified class name
 */
fun resolveManifestName(element: Element): String {
    var className = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
    className = className.replace('$', '.')
    if (className.startsWith(".")) {
        // If the activity class name starts with a '.', it is shorthand for prepending the
        // package name specified in the manifest.
        val pkg = element.ownerDocument
            .documentElement
            .getAttribute(ATTR_PACKAGE) // required to exist
        return pkg + className
    } else if (className.indexOf('.') == -1) {
        val pkg = element.ownerDocument
            .documentElement
            .getAttribute(ATTR_PACKAGE) // required to exist

        // According to the <activity> manifest element documentation, this is not
        // valid ( http://developer.android.com/guide/topics/manifest/activity-element.html )
        // but it appears in manifest files and appears to be supported by the runtime
        // so handle this in code as well:
        return pkg + '.'.toString() + className
    } // else: the class name is already a fully qualified class name

    return className
}

@Deprecated(
    "Supply defaultValue for missing variables",
    ReplaceWith("resolvePlaceHolders(project, value, substitutions, \"\"")
)
fun resolvePlaceHolders(
    project: Project?,
    value: String,
    substitutions: Map<String, String>?
): String {
    return resolvePlaceHolders(project, value, substitutions, "")!!
}

/**
 * Finds the place holder values for the current string and replaces
 * them with the current variant version, or values from the default map
 * if supplied.
 */
@Contract("_, !null, _, !null -> !null")
fun resolvePlaceHolders(
    project: Project?,
    value: String,
    substitutions: Map<String, String>? = null,
    defaultValue: String? = null
): String? {
    var s = value
    while (true) {
        val start = s.indexOf(MANIFEST_PLACEHOLDER_PREFIX)
        if (start == -1) {
            return s
        }
        val end = s.indexOf(MANIFEST_PLACEHOLDER_SUFFIX, start + 1)
        if (end == -1) {
            return s // not terminated
        }
        val name = s.substring(
            start + MANIFEST_PLACEHOLDER_PREFIX.length,
            end
        )
        val replacement = resolvePlaceHolder(project, name) ?: substitutions?.get(name) ?: defaultValue ?: return null
        s = s.substring(0, start) + replacement +
                s.substring(end + MANIFEST_PLACEHOLDER_SUFFIX.length)
    }
}

/**
 * Looks up the value of a given place holder for the current variant of
 * the given project.
 */
fun resolvePlaceHolder(
    project: Project?,
    name: String
): String? {
    val variant = project?.buildVariant ?: return null
    val placeHolders = variant.manifestPlaceholders
    return placeHolders[name]
}

/** Returns true if the given string is a reserved Java keyword. */
fun isJavaKeyword(keyword: String): Boolean {
    // TODO when we built on top of IDEA core replace this with
    //   JavaLexer.isKeyword(candidate, LanguageLevel.JDK_1_5)
    when (keyword) {
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "false",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "null",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "true",
        "try",
        "void",
        "volatile",
        "while" -> return true
    }

    return false
}

/**
 * Returns true if the given string is a reserved Kotlin **hard**
 * keyword.
 */
fun isKotlinHardKeyword(keyword: String): Boolean {
    // From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
    when (keyword) {
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while"
        -> return true
    }

    return false
}

/**
 * Reads the data from the given URL, with an optional timeout (in
 * milliseconds)
 */
fun readUrlData(client: LintClient, query: String, timeout: Int): ByteArray? {
    val url = URL(query)

    val connection = client.openConnection(url, timeout) ?: return null
    return try {
        val `is` = connection.getInputStream() ?: return null
        ByteStreams.toByteArray(`is`)
    } finally {
        client.closeConnection(connection)
    }
}

/**
 * Reads the data from the given URL, with an optional timeout (in
 * milliseconds), and returns it as a UTF-8 encoded String.
 */
fun readUrlDataAsString(client: LintClient, query: String, timeout: Int): String? {
    val bytes = readUrlData(client, query, timeout)
    return if (bytes != null) {
        String(bytes, Charsets.UTF_8)
    } else {
        null
    }
}

@SafeVarargs
fun <T> coalesce(vararg ts: T): T? {
    for (t in ts) {
        if (t != null) {
            return t
        }
    }
    return null
}

/**
 * Looks up the method name of a given call. You should be able to
 * just call [UCallExpression.methodName] but due to bugs in UAST a
 * workaround is currently necessary in some cases.
 *
 * @param call the call to look up
 * @return the call name, if any
 */
fun getMethodName(call: UCallExpression): String? {
    val methodIdentifier = call.methodIdentifier
    return methodIdentifier?.name ?: call.methodName
}

/** Returns true if the given element is written in Kotlin. */
fun isKotlin(element: PsiElement?): Boolean {
    return element != null && isKotlin(element.language)
}

/**
 * Checks whether the given [element] is just a marker tag in a layout,
 * not a real view. Note that merge and include tags are not considered
 * markers since they are replaced with real views.
 */
fun isLayoutMarkerTag(element: Element): Boolean {
    val tagName = element.localName
    if (isLayoutMarkerTag(tagName)) {
        return true
    }
    return tagName == TAG_ATTR && element.namespaceURI == AAPT_URI
}

/**
 * Checks whether the given [tagName] is just a marker tag in a layout,
 * not a real view. Note that merge and include tags are not considered
 * markers since they are replaced with real views.
 */
fun isLayoutMarkerTag(tagName: String): Boolean = when (tagName) {
    REQUEST_FOCUS,
    TAG,
    "aapt:attr", // checked with namespace in #isLayoutMarkerTag
    TAG_LAYOUT,
    TAG_VARIABLE,
    TAG_DATA,
    TAG_IMPORT -> true
    else -> false
}

/** Returns true if the given element is written in Java. */
fun isJava(element: PsiElement?): Boolean {
    return element != null && isJava(element.language)
}

/** Returns true if the given language is Kotlin. */
fun isKotlin(language: Language?): Boolean {
    return language == KotlinLanguage.INSTANCE
}

/** Returns true if the given language is Java. */
fun isJava(language: Language?): Boolean {
    return language == JavaLanguage.INSTANCE
}

/** Returns true if the given string contains only digits. */
fun isNumberString(s: String?): Boolean {
    if (s == null || s.isEmpty()) {
        return false
    }
    var i = 0
    val n = s.length
    while (i < n) {
        if (!Character.isDigit(s[i])) {
            return false
        }
        i++
    }

    return true
}

/**
 * Returns true if the given [UElement] is a polyadic form from
 * [KtStringTemplateExpression]
 */
@OptIn(ExperimentalContracts::class)
fun isPolyadicFromStringTemplate(element: UElement?): Boolean {
    contract {
        returns(true) implies (element is UPolyadicExpression)
    }
    return element is UPolyadicExpression && element.sourcePsi is KtStringTemplateExpression
}

/**
 * Computes argument mapping from arguments to parameters (or returns
 * null if the mapping is 1-1, e.g. in Java), or if the mapping is
 * trivial (Kotlin 0 or 1 args), or if there's some kind of error.
 */
fun computeKotlinArgumentMapping(call: UCallExpression, method: PsiMethod):
        Map<UExpression, PsiParameter>? {
    if (call is UImplicitCallExpression) {
        return call.getArgumentMapping()
    }

    if (method.parameterList.parametersCount <= 1) {
        // When there is at most one parameter the mapping is easy to figure out!
        return null
    }

    // Kotlin? If not, mapping is trivial
    if (call.psi !is KtElement) return null

    val parameters = method.parameterList.parameters
    if (parameters.isNotEmpty()) {
        val mapping = mutableMapOf<UExpression, PsiParameter>()

        parameters.forEachIndexed { index, psiParameter ->
            call.getArgumentForParameter(index)?.let { argument ->
                if (argument is UExpressionList) { // vararg
                    argument.expressions.forEach {
                        mapping[it] = psiParameter
                    }
                } else {
                    mapping[argument] = psiParameter
                }
            }
        }

        if (mapping.isNotEmpty()) {
            return mapping
        }
    }

    return null
}

fun PsiMethod.getUMethod(): UMethod? {
    return UastFacade.convertElementWithParent(this, UMethod::class.java) as? UMethod
}

fun isJreFolder(homePath: File): Boolean {
    return File(homePath, "bin/java").isFile || File(homePath, "bin/java.exe").isFile
}

fun isJdkFolder(homePath: File): Boolean {
    return File(homePath, "bin/javac").isFile || File(homePath, "bin/javac.exe").isFile
}

/**
 * Given an [element] somewhere in a document, find and return the
 * equivalent element within the given [targetDocument] (a similar but
 * different instance of the document). Typically the element will be
 * something from a merged manifest, and the target we're seeking is the
 * corresponding source element in one of the manifest files that were
 * merged.
 *
 * Note that the documents will typically not be equivalent, so the
 * algorithm is to match up elements by tag path and name, and then some
 * heuristics for matching siblings (such as matching by attributes like
 * id and name and as a last resort, their positions).
 */
fun matchXmlElement(element: Element, targetDocument: Document): Element? {
    val target = targetDocument.documentElement
    val path = mutableListOf<Element>()
    var curr = element
    while (true) {
        path.add(curr)
        curr = curr.parentNode as? Element ?: break
    }
    if (path.size == 1 && element.tagName == target.tagName) {
        return target
    }
    path.reverse() // NO, can just reverse indices instead!

    // Search forwards (there could be multiple subtrees to search so we can't
    // just use a recursive solution where we recurse to find parent matching
    // element and then choose among siblings).
    val start = target ?: return null
    if (start.tagName != path[0].tagName) {
        return null
    }

    return search(path, 1, start)
}

private fun search(path: List<Element>, index: Int, parent: Element): Element? {
    if (index == path.size) { // done searching
        return parent
    }
    val source = path[index]

    // If the source element has a name attribute, match it with the target
    when {
        source.hasAttribute(ATTR_NAME) -> {
            val name = source.getAttribute(ATTR_NAME)
            for (child in parent) {
                if (child.getAttribute(ATTR_NAME) == name) {
                    return search(path, index + 1, child)
                }
            }
        }
        source.hasAttribute(ATTR_ID) -> {
            val id = source.getAttribute(ATTR_ID)
            for (child in parent) {
                if (child.getAttribute(ATTR_ID) == id) {
                    return search(path, index + 1, child)
                }
            }
        }
        else -> {
            // Search by ordinal number.
            val tag = source.nodeName
            var ordinal = 0
            var curr: Node? = source
            while (curr != null) {
                if (curr.nodeType == Node.ELEMENT_NODE && curr.nodeName == tag) {
                    ordinal++
                }
                curr = curr.previousSibling
            }

            curr = parent.firstChild
            while (curr != null) {
                curr = curr.nextSibling
                if (curr.nodeType == Node.ELEMENT_NODE && curr.nodeName == tag) {
                    ordinal--
                    if (ordinal == 0) {
                        val child = curr as Element
                        search(path, index + 1, child)?.let { return it }
                            ?: break
                    }
                }
            }

            // No match by ordinal search; instead search all children tags that match.
            // This can happen if the parents are reordered; we'll also allow a match
            // by path.
            for (child in parent) {
                if (child.tagName == tag) {
                    search(path, index + 1, child)?.let { return it }
                }
            }
        }
    }

    return null
}

fun hasImplicitDefaultConstructor(uClass: UClass?): Boolean {
    val sourcePsi = uClass?.sourcePsi ?: return false
    return if (sourcePsi is KtClass) {
        sourcePsi.declarations.none { it is KtConstructor<*> }
    } else {
        hasImplicitDefaultConstructor(sourcePsi as? PsiClass)
    }
}

fun hasImplicitDefaultConstructor(psiClass: PsiClass?): Boolean {
    psiClass ?: return false

    if (psiClass is KtLightClassForFacade || psiClass is UClass && psiClass.sourcePsi == null) {
        // Top level kt classes (FooKt for Foo.kt) do not have implicit default constructor
        return false
    }

    val constructors = psiClass.constructors
    if (constructors.isEmpty() && !psiClass.isInterface && !psiClass.isAnnotationType && !psiClass.isEnum) {
        if (PsiUtil.hasDefaultConstructor(psiClass)) {
            return true
        }

        // The above method isn't always right; for example, for the ContactsContract.Presence class
        // in the framework, which looks like this:
        //    @Deprecated
        //    public static final class Presence extends StatusUpdates {
        //    }
        // javac makes a default constructor:
        //    public final class android.provider.ContactsContract$Presence extends android.provider.ContactsContract$StatusUpdates {
        //        public android.provider.ContactsContract$Presence();
        //    }
        // but the above method returns false. So add some of our own heuristics:
        if (psiClass.hasModifierProperty(PsiModifier.FINAL) &&
            !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
            psiClass.hasModifierProperty(PsiModifier.PUBLIC)
        ) {
            return true
        }
    }

    return false
}

// For compatibility reasons
@Suppress("unused")
object LintUtils {
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getInternalName(psiClass)")
    )
    @JvmStatic
    fun getInternalName(psiClass: PsiClass): String? {
        return com.android.tools.lint.getInternalName(psiClass)
    }

    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getInternalMethodName(method)")
    )
    @JvmStatic
    fun getInternalMethodName(method: PsiMethod): String {
        return com.android.tools.lint.getInternalMethodName(method)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.formatList(strings, maxItems)")
    )
    fun formatList(strings: List<String>, maxItems: Int): String {
        return com.android.tools.lint.formatList(strings, maxItems)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.formatList(strings, maxItems, sort)")
    )
    fun formatList(strings: List<String>, maxItems: Int, sort: Boolean): String {
        return com.android.tools.lint.formatList(strings, maxItems, sort)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isFileBasedResourceType(type)")
    )
    fun isFileBasedResourceType(type: ResourceType): Boolean {
        return com.android.tools.lint.isFileBasedResourceType(type)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isXmlFile(file)")
    )
    fun isXmlFile(file: File): Boolean {
        return com.android.tools.lint.isXmlFile(file)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.endsWith(string, suffix)")
    )
    fun endsWith(string: String, suffix: String): Boolean {
        return com.android.tools.lint.endsWith(string, suffix)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.startsWith(string, prefix, offset)")
    )
    fun startsWith(string: String, prefix: String, offset: Int): Boolean {
        return com.android.tools.lint.startsWith(string, prefix, offset)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getBaseName(fileName)")
    )
    fun getBaseName(fileName: String): String {
        return com.android.tools.lint.getBaseName(fileName)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.describeCounts(errorCount, warningCount, comma, capitalize)")
    )
    fun describeCounts(
        errorCount: Int,
        warningCount: Int,
        comma: Boolean,
        capitalize: Boolean
    ): String {
        return com.android.tools.lint.describeCounts(
            errorCount,
            warningCount,
            comma,
            capitalize
        )
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getChildren(node)")
    )
    fun getChildren(node: Node): List<Element> {
        return com.android.tools.lint.getChildren(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getChildCount(node)")
    )
    fun getChildCount(node: Node): Int {
        return com.android.tools.lint.getChildCount(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isRootElement(element)")
    )
    fun isRootElement(element: Element): Boolean {
        return com.android.tools.lint.isRootElement(element)
    }

    @JvmStatic
    @Deprecated(
        message = "Use ResourceUrl for parsing @id and similar strings and consider the namespace used.",
        replaceWith = ReplaceWith(
            expression = "ResourceUrl.parse(id)",
            imports = ["com.android.resources.ResourceUrl"]
        )
    )
    fun stripIdPrefix(id: String?): String {
        return com.android.tools.lint.stripIdPrefix(id)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.idReferencesMatch(id1, id2)")
    )
    fun idReferencesMatch(id1: String?, id2: String?): Boolean {
        return com.android.tools.lint.idReferencesMatch(id1, id2)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getFileNameWithParent(client, file)")
    )
    fun getFileNameWithParent(client: LintClient, file: File): String {
        return com.android.tools.lint.getFileNameWithParent(client, file)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getFileNameWithParent(client, file)")
    )
    fun getFileNameWithParent(client: LintClient, file: PathString): String {
        return com.android.tools.lint.getFileNameWithParent(client, file)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isEditableTo(s, t, max)")
    )
    fun isEditableTo(s: String, t: String, max: Int): Boolean {
        return com.android.tools.lint.isEditableTo(s, t, max)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.editDistance(s, t, max)")
    )
    @JvmOverloads
    fun editDistance(s: String, t: String, max: Int = Integer.MAX_VALUE): Int {
        return com.android.tools.lint.editDistance(s, t, max)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.assertionsEnabled()")
    )
    fun assertionsEnabled(): Boolean = com.android.tools.lint.assertionsEnabled()

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLayoutName(layoutFile)")
    )
    fun getLayoutName(layoutFile: File): String {
        return com.android.tools.lint.getLayoutName(layoutFile)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.splitPath(path)")
    )
    fun splitPath(path: String): Iterable<String> {
        return com.android.tools.lint.splitPath(path)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getCommonParent(files)")
    )
    fun getCommonParent(files: List<File>): File? {
        return com.android.tools.lint.getCommonParent(files)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getCommonParent(file1, file2)")
    )
    fun getCommonParent(file1: File, file2: File): File? {
        return com.android.tools.lint.getCommonParent(file1, file2)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getEncodedString(client, file, createString)")
    )
    @Throws(IOException::class)
    fun getEncodedString(
        client: LintClient,
        file: File,
        createString: Boolean
    ): CharSequence {
        return com.android.tools.lint.getEncodedString(client, file, createString)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isDataBindingExpression(expression)")
    )
    fun isDataBindingExpression(expression: String): Boolean {
        return com.android.tools.lint.isDataBindingExpression(expression)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isManifestPlaceHolderExpression(expression)")
    )
    fun isManifestPlaceHolderExpression(expression: String): Boolean {
        return com.android.tools.lint.isManifestPlaceHolderExpression(expression)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getEncodedString(data, createString)")
    )
    fun getEncodedString(data: ByteArray?, createString: Boolean): CharSequence {
        return com.android.tools.lint.detector.api.getEncodedString(data, createString)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isStaticInnerClass(classNode)")
    )
    fun isStaticInnerClass(classNode: ClassNode): Boolean {
        return com.android.tools.lint.isStaticInnerClass(classNode)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isAnonymousClass(classNode)")
    )
    fun isAnonymousClass(classNode: ClassNode): Boolean {
        return com.android.tools.lint.isAnonymousClass(classNode)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getPrevOpcode(node)")
    )
    fun getPrevOpcode(node: AbstractInsnNode): Int {
        return com.android.tools.lint.getPrevOpcode(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getPrevInstruction(node)")
    )
    fun getPrevInstruction(node: AbstractInsnNode): AbstractInsnNode? {
        return com.android.tools.lint.getPrevInstruction(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getNextOpcode(node)")
    )
    fun getNextOpcode(node: AbstractInsnNode): Int {
        return com.android.tools.lint.getNextOpcode(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getNextInstruction(node)")
    )
    fun getNextInstruction(node: AbstractInsnNode): AbstractInsnNode? {
        return com.android.tools.lint.getNextInstruction(node)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isManifestFolder(dir)")
    )
    fun isManifestFolder(dir: File?): Boolean {
        return com.android.tools.lint.isManifestFolder(dir)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLocaleAndRegion(folderName)")
    )
    fun getLocaleAndRegion(folderName: String): String? {
        return com.android.tools.lint.getLocaleAndRegion(folderName)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getStyleAttributes(")
    )
    fun getStyleAttributes(
        project: Project,
        client: LintClient,
        styleUrl: String,
        namespaceUri: String,
        attribute: String
    ): List<ResourceValue>? {
        return com.android.tools.lint.getStyleAttributes(
            project,
            client,
            styleUrl,
            namespaceUri,
            attribute
        )
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getInheritedStyles(project, client, styleUrl)")
    )
    fun getInheritedStyles(
        project: Project,
        client: LintClient,
        styleUrl: String
    ): List<StyleResourceValue>? {
        return com.android.tools.lint.getInheritedStyles(project, client, styleUrl)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isSameResourceFile(file1, file2)")
    )
    fun isSameResourceFile(file1: File?, file2: File?): Boolean {
        return com.android.tools.lint.isSameResourceFile(file1, file2)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.computeResourceName(prefix, name, folderType)")
    )
    @JvmOverloads
    fun computeResourceName(
        prefix: String,
        name: String,
        folderType: ResourceFolderType? = null
    ): String {
        return com.android.tools.lint.computeResourceName(prefix, name, folderType)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isModelOlderThan(project, major, minor, micro, defaultForNonGradleProjects")
    )
    @JvmOverloads
    fun isModelOlderThan(
        project: Project,
        major: Int,
        minor: Int,
        micro: Int,
        defaultForNonGradleProjects: Boolean = false
    ): Boolean {
        return com.android.tools.lint.isModelOlderThan(
            project,
            major,
            minor,
            micro,
            defaultForNonGradleProjects
        )
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLanguageLevel(element, defaultLevel)")
    )
    fun getLanguageLevel(
        element: UElement,
        defaultLevel: LanguageLevel
    ): LanguageLevel {
        return com.android.tools.lint.getLanguageLevel(element, defaultLevel)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLanguageLevel(element, defaultLevel)")
    )
    fun getLanguageLevel(
        element: PsiElement,
        defaultLevel: LanguageLevel
    ): LanguageLevel {
        return com.android.tools.lint.getLanguageLevel(element, defaultLevel)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.findSubstring(string, prefix, suffix)")
    )
    fun findSubstring(
        string: String,
        prefix: String?,
        suffix: String?
    ): String? {
        return com.android.tools.lint.findSubstring(string, prefix, suffix)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getFormattedParameters(format, errorMessage)")
    )
    fun getFormattedParameters(
        format: String,
        errorMessage: String
    ): List<String> {
        return com.android.tools.lint.getFormattedParameters(format, errorMessage)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLocale(parent)")
    )
    fun getLocale(parent: String): LocaleQualifier? {
        return com.android.tools.lint.getLocale(parent)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getLocale(context)")
    )
    fun getLocale(context: XmlContext): LocaleQualifier? {
        return com.android.tools.lint.getLocale(context)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isEnglishResource(context, assumeForBase)")
    )
    fun isEnglishResource(context: XmlContext, assumeForBase: Boolean): Boolean {
        return com.android.tools.lint.isEnglishResource(context, assumeForBase)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.guessGradleLocation(project)")
    )
    fun guessGradleLocation(project: Project): Location {
        return com.android.tools.lint.guessGradleLocation(project)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.guessGradleLocation(client, projectDir, string)")
    )
    fun guessGradleLocation(
        client: LintClient,
        projectDir: File,
        string: String?
    ): Location {
        return com.android.tools.lint.guessGradleLocation(client, projectDir, string)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isNullLiteral(element)")
    )
    fun isNullLiteral(element: PsiElement?): Boolean {
        return com.android.tools.lint.isNullLiteral(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isTrueLiteral(element)")
    )
    fun isTrueLiteral(element: PsiElement?): Boolean {
        return com.android.tools.lint.isTrueLiteral(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isFalseLiteral(element)")
    )
    fun isFalseLiteral(element: PsiElement?): Boolean {
        return com.android.tools.lint.isFalseLiteral(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.skipParentheses(element)")
    )
    fun skipParentheses(element: PsiElement?): PsiElement? {
        return com.android.tools.lint.skipParentheses(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.skipParentheses(element)")
    )
    fun skipParentheses(element: UElement?): UElement? {
        return skipParenthesizedExprUp(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.nextNonWhitespace(element)")
    )
    fun nextNonWhitespace(element: PsiElement?): PsiElement? {
        return com.android.tools.lint.nextNonWhitespace(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.prevNonWhitespace(element)")
    )
    fun prevNonWhitespace(element: PsiElement?): PsiElement? {
        return com.android.tools.lint.prevNonWhitespace(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isString(type)")
    )
    fun isString(type: PsiType): Boolean {
        return com.android.tools.lint.isString(type)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getAutoBoxedType(primitive)")
    )
    fun getAutoBoxedType(primitive: String): String? {
        return com.android.tools.lint.getAutoBoxedType(primitive)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getPrimitiveType(autoBoxedType)")
    )
    fun getPrimitiveType(autoBoxedType: String): String? {
        return com.android.tools.lint.getPrimitiveType(autoBoxedType)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.resolveManifestName(element)")
    )
    fun resolveManifestName(element: Element): String {
        return com.android.tools.lint.resolveManifestName(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isJavaKeyword(keyword)")
    )
    fun isJavaKeyword(keyword: String): Boolean {
        return com.android.tools.lint.isJavaKeyword(keyword)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.readUrlData(client, query, timeout)")
    )
    @Throws(IOException::class)
    fun readUrlData(client: LintClient, query: String, timeout: Int): ByteArray? {
        return com.android.tools.lint.readUrlData(client, query, timeout)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.readUrlDataAsString(client, query, timeout)")
    )
    @Throws(IOException::class)
    fun readUrlDataAsString(
        client: LintClient,
        query: String,
        timeout: Int
    ): String? {
        return com.android.tools.lint.readUrlDataAsString(client, query, timeout)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.coalesce(*ts)")
    )
    @SafeVarargs
    fun <T> coalesce(vararg ts: T): T? {
        return com.android.tools.lint.coalesce(*ts)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.getMethodName(call)")
    )
    fun getMethodName(call: UCallExpression): String? {
        return com.android.tools.lint.getMethodName(call)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isKotlin(element)")
    )
    fun isKotlin(element: PsiElement?): Boolean {
        return com.android.tools.lint.isKotlin(element)
    }

    @JvmStatic
    @Deprecated(
        "Use package function instead",
        replaceWith = ReplaceWith("com.android.tools.lint.isKotlin(language)")
    )
    fun isKotlin(language: Language?): Boolean {
        return com.android.tools.lint.isKotlin(language)
    }
}
