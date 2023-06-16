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
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link Warning} represents a specific warning that a {@link LintClient} has been told about.
 * The context stores these as they are reported into a list of warnings such that it can sort them
 * all before presenting them all at the end.
 */
public class Warning implements Comparable<Warning> {
    public final Issue issue;
    public final String message;
    public final Severity severity;
    public final Project project;
    public Location location;
    public File file;
    public String path;
    public int line = -1;
    public int offset = -1;
    public int endOffset = -1;
    public String errorLine;
    public CharSequence fileContents;
    public Set<String> allVariants;
    public Set<String> variants;
    public LintFix quickfixData;
    public boolean wasAutoFixed;

    public Warning(Issue issue, String message, Severity severity, Project project) {
        this.issue = issue;
        this.message = message;
        this.severity = severity;
        this.project = project;
    }

    @Override
    public int compareTo(@NonNull Warning o) {
        String fileName1 = file != null ? file.getName() : null;
        String fileName2 = o.file != null ? o.file.getName() : null;

        Position start1 = location != null ? location.getStart() : null;
        Position start2 = o.location != null ? o.location.getStart() : null;
        Integer col1 = start1 != null ? start1.getColumn() : null;
        Integer col2 = start2 != null ? start2.getColumn() : null;

        Location secondary1 = location != null ? location.getSecondary() : null;
        Location secondary2 = o.location != null ? o.location.getSecondary() : null;
        File secondFile1 = secondary1 != null ? secondary1.getFile() : null;
        File secondFile2 = secondary2 != null ? secondary2.getFile() : null;

        return ComparisonChain.start()
                .compare(issue.getCategory(), o.issue.getCategory())
                .compare(issue.getPriority(), o.issue.getPriority(), Comparator.reverseOrder())
                .compare(issue.getId(), o.issue.getId())
                .compare(fileName1, fileName2, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(line, o.line)
                .compare(message, o.message)
                .compare(file, o.file, Comparator.nullsLast(Comparator.naturalOrder()))
                // This handles the case where you have a huge XML document without newlines,
                // such that all the errors end up on the same line.
                .compare(col1, col2, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(secondFile1, secondFile2, Comparator.nullsLast(Comparator.naturalOrder()))
                .result();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return this.compareTo((Warning) other) == 0;
    }

    @Override
    public int hashCode() {
        int result = message.hashCode();
        result = 31 * result + (file != null ? file.hashCode() : 0);
        return result;
    }

    public boolean hasAutoFix() {
        return quickfixData != null && LintFixPerformer.Companion.canAutoFix(quickfixData);
    }

    public boolean isVariantSpecific() {
        return variants != null && variants.size() < allVariants.size();
    }

    public boolean includesMoreThanExcludes() {
        assert isVariantSpecific();
        int variantCount = variants.size();
        int allVariantCount = allVariants.size();
        return variantCount <= allVariantCount - variantCount;
    }

    public List<String> getIncludedVariantNames() {
        assert isVariantSpecific();
        List<String> names = new ArrayList<>();
        if (variants != null) {
            names.addAll(variants);
        }
        Collections.sort(names);
        return names;
    }

    public List<String> getExcludedVariantNames() {
        assert isVariantSpecific();
        Set<String> included = new HashSet<>(getIncludedVariantNames());
        Set<String> excluded = Sets.difference(allVariants, included);
        List<String> sorted = Lists.newArrayList(excluded);
        Collections.sort(sorted);
        return sorted;
    }

    @Override
    public String toString() {
        return "Warning{"
                + "issue="
                + issue
                + ", message='"
                + message
                + '\''
                + ", file="
                + file
                + ", line="
                + line
                + '}';
    }
}
