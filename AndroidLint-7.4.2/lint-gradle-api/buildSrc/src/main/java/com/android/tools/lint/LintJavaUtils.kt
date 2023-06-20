package com.android.tools.lint

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtElement

/*
 * Copyright (C) 2018 The Android Open Source Project
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



// Class which contains some code which cannot be expressed in Kotlin;
// not public since the public LintUtils methods will more directly expose them
internal object LintJavaUtils {
    /** Returns true if assertions are enabled  */
    fun assertionsEnabled(): Boolean {
        var assertionsEnabled = false
        assert(true.also {
            assertionsEnabled = it // Intentional side-effect
        })
        return assertionsEnabled
    }

    fun resolveToPsiMethod(
        context: KtElement,
        descriptor: DeclarationDescriptor,
        source: PsiElement?
    ): PsiElement? {
        // TODO(kotlin-uast-cleanup): avoid using "internal" utils
        return resolveToPsiMethod(context, descriptor, source)
    }
}
