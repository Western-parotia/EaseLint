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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Multiplexing" reporter which allows output to be split up into a separate report for each
 * separate project. It also adds an overview index.
 */
public class MultiProjectHtmlReporter extends Reporter {
    private static final String INDEX_NAME = "index.html";
    private final File dir;
    protected final LintCliFlags flags;

    public MultiProjectHtmlReporter(
            @NonNull LintCliClient client, @NonNull File dir, @NonNull LintCliFlags flags) {
        super(client, new File(dir, INDEX_NAME));
        this.dir = dir;
        this.flags = flags;
    }

    @Override
    public void write(@NonNull LintStats stats, List<Warning> allIssues) throws IOException {
        Map<Project, List<Warning>> projectToWarnings = new HashMap<>();
        for (Warning warning : allIssues) {
            List<Warning> list =
                    projectToWarnings.computeIfAbsent(warning.project, k -> new ArrayList<>());
            list.add(warning);
        }

        // Set of unique file names: lowercase names to avoid case conflicts in web environment
        Set<String> unique = Sets.newHashSet();
        unique.add(INDEX_NAME.toLowerCase(Locale.US));
        List<ProjectEntry> projects = Lists.newArrayList();

        for (Project project : projectToWarnings.keySet()) {
            // TODO: Can I get the project name from the Android manifest file instead?
            String projectName = project.getName();

            // Produce file names of the form Project.html, Project1.html, Project2.html, etc
            int number = 1;
            String fileName;
            while (true) {
                String numberString = number > 1 ? Integer.toString(number) : "";
                fileName = String.format("%1$s%2$s.html", projectName, numberString);
                String lowercase = fileName.toLowerCase(Locale.US);
                if (!unique.contains(lowercase)) {
                    unique.add(lowercase);
                    break;
                }
                number++;
            }

            File output = new File(dir, fileName);
            if (output.exists()) {
                boolean deleted = output.delete();
                if (!deleted) {
                    client.log(null, "Could not delete old file %1$s", output);
                    continue;
                }
            }
            if (!output.getParentFile().canWrite()) {
                client.log(null, "Cannot write output file %1$s", output);
                continue;
            }

            Reporter reporter = Reporter.createHtmlReporter(client, output, flags);
            reporter.setUrlMap(urlMap);

            List<Warning> issues = projectToWarnings.get(project);
            int projectErrorCount = 0;
            int projectWarningCount = 0;
            for (Warning warning : issues) {
                if (warning.severity.isError()) {
                    projectErrorCount++;
                } else if (warning.severity == Severity.WARNING) {
                    projectWarningCount++;
                }
            }

            String prefix = project.getReferenceDir().getPath();
            String path = project.getDir().getPath();
            String relative;
            if (path.startsWith(prefix) && path.length() > prefix.length()) {
                int i = prefix.length();
                if (path.charAt(i) == File.separatorChar) {
                    i++;
                }
                relative = path.substring(i);
            } else {
                relative = projectName;
            }
            reporter.setTitle(String.format("Lint Report for %1$s", relative));
            reporter.setStripPrefix(relative);
            reporter.write(stats, issues);

            projects.add(
                    new ProjectEntry(fileName, projectErrorCount, projectWarningCount, relative));
        }

        // Sort project list in decreasing order of errors, warnings and names
        Collections.sort(projects);

        Reporter reporter = Reporter.createHtmlReporter(client, output, flags);
        reporter.writeProjectList(stats, projects);

        if (!client.getFlags().isQuiet()
                && (stats.getErrorCount() > 0 || stats.getWarningCount() > 0)) {
            File index = new File(dir, INDEX_NAME);
            String url = SdkUtils.fileToUrlString(index.getAbsoluteFile());
            System.out.println(String.format("Wrote overview index to %1$s", url));
        }
    }

    static class ProjectEntry implements Comparable<ProjectEntry> {
        public final int errorCount;
        public final int warningCount;
        public final String fileName;
        public final String path;

        public ProjectEntry(String fileName, int errorCount, int warningCount, String path) {
            this.fileName = fileName;
            this.errorCount = errorCount;
            this.warningCount = warningCount;
            this.path = path;
        }

        @Override
        public int compareTo(@NonNull ProjectEntry other) {
            int delta = other.errorCount - errorCount;
            if (delta != 0) {
                return delta;
            }

            delta = other.warningCount - warningCount;
            if (delta != 0) {
                return delta;
            }

            return path.compareTo(other.path);
        }
    }
}
