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

import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.tools.lint.checks.ApiLookup.SDK_DATABASE_MIN_VERSION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiManager;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LintExternalAnnotationsManager extends BaseExternalAnnotationsManager {
    public static final String SDK_ANNOTATIONS_PATH = "annotations.zip";

    private final List<VirtualFile> roots = Lists.newArrayList();

    public LintExternalAnnotationsManager(
            @NonNull final com.intellij.openapi.project.Project project,
            @NonNull PsiManager psiManager) {
        super(psiManager);
    }

    @Override
    protected boolean hasAnyAnnotationsRoots() {
        return !roots.isEmpty();
    }

    @NonNull
    @Override
    protected List<VirtualFile> getExternalAnnotationsRoots(@NonNull VirtualFile virtualFile) {
        return roots;
    }

    public void updateAnnotationRoots(@NonNull LintClient client, @Nullable IAndroidTarget target) {
        Collection<Project> projects = client.getKnownProjects();
        if (Project.isAospBuildEnvironment()) {
            for (Project project : projects) {
                // If we are dealing with the AOSP frameworks project, we explicitly
                // set the external annotations manager to be a no-op.
                if (Project.isAospFrameworksProject(project.getDir())) {
                    return;
                }
            }
        }

        File sdkAnnotations = findSdkAnnotations(client, target);
        List<File> libraryAnnotations = client.getExternalAnnotations(projects);
        updateAnnotationRoots(sdkAnnotations, libraryAnnotations);
    }

    @Nullable
    private static File findSdkAnnotations(
            @NonNull LintClient client, @Nullable IAndroidTarget target) {
        // Until the SDK annotations are bundled in platform tools, provide
        // a fallback for Gradle builds to point to a locally installed version.
        // This is also done first to allow build setups to hardcode exactly where
        // lint looks instead of relying on the SDK (this is used by lint when running
        // in Android platform builds for example).
        String path = System.getenv("SDK_ANNOTATIONS");
        if (path != null) {
            File sdkAnnotations = new File(path);
            if (sdkAnnotations.exists()) {
                return sdkAnnotations;
            }
        }

        if (target != null
                && target.isPlatform()
                && target.getVersion().getFeatureLevel() >= SDK_DATABASE_MIN_VERSION) {
            File file = new File(target.getFile(IAndroidTarget.DATA), SDK_ANNOTATIONS_PATH);
            if (file.isFile()) {
                return file;
            }
        }

        return client.findResource(SDK_ANNOTATIONS_PATH);
    }

    private void updateAnnotationRoots(
            @Nullable File sdkAnnotations, @NonNull List<File> libraryAnnotations) {
        List<File> files;
        if (sdkAnnotations != null) {
            if (libraryAnnotations.isEmpty()) {
                files = Collections.singletonList(sdkAnnotations);
            } else {
                files = new ArrayList<>(libraryAnnotations);
                files.add(sdkAnnotations);
            }
        } else {
            files = libraryAnnotations;
        }

        List<VirtualFile> newRoots = new ArrayList<>(files.size());

        VirtualFileSystem local = StandardFileSystems.local();
        VirtualFileSystem jar = StandardFileSystems.jar();

        for (File file : files) {
            VirtualFile virtualFile;
            boolean isZip = file.getName().equals(FN_ANNOTATIONS_ZIP);
            if (isZip) {
                virtualFile = jar.findFileByPath(file.getPath() + URLUtil.JAR_SEPARATOR);
            } else {
                virtualFile = local.findFileByPath(file.getPath());
            }
            if (virtualFile == null) {
                if (isZip) {
                    virtualFile =
                            jar.findFileByPath(file.getAbsolutePath() + URLUtil.JAR_SEPARATOR);
                } else {
                    virtualFile = local.findFileByPath(file.getAbsolutePath());
                }
            }
            if (virtualFile != null) {
                newRoots.add(virtualFile);
            }
        }

        // We don't need to do equals; we don't worry about having annotations
        // for removed projects, but make sure all the new projects are covered
        //if (this.roots.equals(roots)) {
        if (roots.containsAll(newRoots)) {
            return;
        }

        roots.addAll(newRoots);
        dropCache(); // TODO: Find out if I need to drop cache for pure additions

        // TODO
        //ApplicationManager.getApplication().runWriteAction(
        //    () -> ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incCounter());
    }
}
