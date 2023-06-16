/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;

/**
 * Dummy implementation. Some annotation lookup insists on calling into the
 * InferredAnnotationsManager; prevent that from throwing exceptions.
 *
 * <pre>
 * java.lang.IllegalStateException: @NonNull method com/intellij/openapi/components/ServiceManager$1.fun must not return null
 * at com.intellij.openapi.components.ServiceManager$1.fun(ServiceManager.java:76)
 * at com.intellij.openapi.components.ServiceManager$1.fun(ServiceManager.java:72)
 * at com.intellij.openapi.util.NonNullLazyKey.getValue(NonNullLazyKey.java:39)
 * at com.intellij.codeInsight.InferredAnnotationsManager.getInstance(InferredAnnotationsManager.java:40)
 * at com.intellij.codeInsight.AnnotationUtil.getAllAnnotations(AnnotationUtil.java:463)
 * at com.intellij.codeInsight.AnnotationUtil.getAllAnnotations(AnnotationUtil.java:444)
 * at com.intellij.codeInsight.AnnotationUtil.getAllAnnotations(AnnotationUtil.java:487)
 * </pre>
 */
public class LintInferredAnnotationsManager extends InferredAnnotationsManager {

    public LintInferredAnnotationsManager() {}

    @Nullable
    @Override
    public PsiAnnotation findInferredAnnotation(
            @NonNull PsiModifierListOwner psiModifierListOwner, @NonNull String s) {
        return null;
    }

    @NonNull
    @Override
    public PsiAnnotation[] findInferredAnnotations(
            @NonNull PsiModifierListOwner psiModifierListOwner) {
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    public boolean isInferredAnnotation(@NonNull PsiAnnotation psiAnnotation) {
        return false;
    }
}
