package com.buildsrc.lint;

import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.gradle.internal.scope.InternalArtifactType;

import org.gradle.api.Task;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.tasks.TaskProvider;


import kotlin.jvm.functions.Function1;

public class ArtifactsImplProxy {

    final String LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE";
    final String ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE = "ANDROID_LINT_JARS";
    final String PARTIAL_RESULTS_DIR_NAME = "out";

    public <FILE_TYPE extends FileSystemLocation, TASK extends Task> void proxy(TaskProvider<TASK> taskProvider,
                                                                                Function1<? super TASK, ? extends FileSystemLocationProperty<FILE_TYPE>> property,
                                                                                ArtifactsImpl artifacts,
                                                                                InternalArtifactType<FILE_TYPE> internalArtifactType) {
        artifacts
                .setInitialProvider(taskProvider, property)
                .withName(PARTIAL_RESULTS_DIR_NAME)
                .on(internalArtifactType);
    }

}
