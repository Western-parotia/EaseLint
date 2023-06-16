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

import static com.android.tools.lint.detector.api.Lint.describeCounts;
import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.SdkUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * A reporter which emits lint warnings as plain text strings
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public class TextReporter extends Reporter {
    private final Writer writer;
    private final boolean close;
    private final LintCliFlags flags;
    private boolean forwardSlashPaths;
    private boolean writeStats = true;

    /**
     * Constructs a new {@link TextReporter}
     *
     * @param client the client
     * @param flags the flags
     * @param writer the writer to write into
     * @param close whether the writer should be closed when done
     */
    public TextReporter(
            @NonNull LintCliClient client,
            @NonNull LintCliFlags flags,
            @NonNull Writer writer,
            boolean close) {
        this(client, flags, null, writer, close);
    }

    /**
     * Constructs a new {@link TextReporter}
     *
     * @param client the client
     * @param flags the flags
     * @param file the file corresponding to the writer, if any
     * @param writer the writer to write into
     * @param close whether the writer should be closed when done
     */
    public TextReporter(
            @NonNull LintCliClient client,
            @NonNull LintCliFlags flags,
            @Nullable File file,
            @NonNull Writer writer,
            boolean close) {
        super(client, file);
        this.flags = flags;
        this.writer = writer;
        this.close = close;
    }

    @Override
    public void write(@NonNull LintStats stats, List<Warning> issues) throws IOException {
        boolean abbreviate = !flags.isShowEverything();

        StringBuilder output = new StringBuilder(issues.size() * 200);
        if (issues.isEmpty()) {
            if (isDisplayEmpty() && writeStats) {
                writer.write("No issues found");
                if (stats.getBaselineErrorCount() > 0 || stats.getBaselineWarningCount() > 0) {
                    File baselineFile = flags.getBaselineFile();
                    assert baselineFile != null;
                    writer.write(
                            String.format(
                                    " (%1$s filtered by baseline %2$s)",
                                    describeCounts(
                                            stats.getBaselineErrorCount(),
                                            stats.getBaselineWarningCount(),
                                            true,
                                            true),
                                    baselineFile.getName()));
                }
                writer.write('.');
                writer.write('\n');
                writer.flush();
            }
        } else {
            Issue lastIssue = null;
            for (Warning warning : issues) {
                if (warning.issue != lastIssue) {
                    explainIssue(output, lastIssue);
                    lastIssue = warning.issue;
                }
                int startLength = output.length();

                String p = warning.path;
                if (p != null) {
                    appendPath(output, p);
                    output.append(':');

                    if (warning.line >= 0) {
                        output.append(Integer.toString(warning.line + 1));
                        output.append(':');
                    }
                    if (startLength < output.length()) {
                        output.append(' ');
                    }
                }

                Severity severity = warning.severity;
                if (severity == Severity.FATAL) {
                    // Treat the fatal error as an error such that we don't display
                    // both "Fatal:" and "Error:" etc in the error output.
                    severity = Severity.ERROR;
                }
                output.append(severity.getDescription());
                output.append(':');
                output.append(' ');

                output.append(RAW.convertTo(warning.message, TEXT));

                if (warning.issue != null) {
                    output.append(' ').append('[');
                    output.append(warning.issue.getId());
                    output.append(']');
                }

                output.append('\n');

                if (warning.wasAutoFixed) {
                    output.append("This issue has been automatically fixed.\n");
                }

                if (warning.errorLine != null && !warning.errorLine.isEmpty()) {
                    output.append(warning.errorLine);
                }

                if (warning.location != null && warning.location.getSecondary() != null) {
                    Location location = warning.location.getSecondary();
                    boolean omitted = false;
                    while (location != null) {
                        if (location.getMessage() != null && !location.getMessage().isEmpty()) {
                            output.append("    ");
                            String path =
                                    client.getDisplayPath(warning.project, location.getFile());
                            appendPath(output, path);

                            Position start = location.getStart();
                            if (start != null) {
                                int line = start.getLine();
                                if (line >= 0) {
                                    output.append(':');
                                    output.append(Integer.toString(line + 1));
                                }
                            }

                            if (location.getMessage() != null && !location.getMessage().isEmpty()) {
                                output.append(':');
                                output.append(' ');
                                output.append(RAW.convertTo(location.getMessage(), TEXT));
                            }

                            output.append('\n');
                        } else {
                            omitted = true;
                        }

                        location = location.getSecondary();
                    }

                    if (!abbreviate && omitted) {
                        location = warning.location.getSecondary();
                        StringBuilder sb = new StringBuilder(100);
                        sb.append("Also affects: ");
                        int begin = sb.length();
                        while (location != null) {
                            if (location.getMessage() == null || location.getMessage().isEmpty()) {
                                if (sb.length() > begin) {
                                    sb.append(", ");
                                }

                                String path =
                                        client.getDisplayPath(warning.project, location.getFile());
                                appendPath(sb, path);

                                Position start = location.getStart();
                                if (start != null) {
                                    int line = start.getLine();
                                    if (line >= 0) {
                                        sb.append(':');
                                        sb.append(Integer.toString(line + 1));
                                    }
                                }
                            }

                            location = location.getSecondary();
                        }
                        String wrapped = Main.wrap(sb.toString(), Main.MAX_LINE_WIDTH, "     ");
                        output.append(wrapped);
                    }
                }

                if (warning.isVariantSpecific()) {
                    List<String> names;
                    if (warning.includesMoreThanExcludes()) {
                        output.append("Applies to variants: ");
                        names = warning.getIncludedVariantNames();
                    } else {
                        output.append("Does not apply to variants: ");
                        names = warning.getExcludedVariantNames();
                    }
                    output.append(Joiner.on(", ").join(names));
                    output.append('\n');
                }
            }
            explainIssue(output, lastIssue);

            writer.write(output.toString());

            if (writeStats) {
                // TODO: Update to using describeCounts
                writer.write(
                        String.format(
                                "%1$d errors, %2$d warnings",
                                stats.getErrorCount(), stats.getWarningCount()));
                if (stats.getBaselineErrorCount() > 0 || stats.getBaselineWarningCount() > 0) {
                    File baselineFile = flags.getBaselineFile();
                    assert baselineFile != null;
                    writer.write(
                            String.format(
                                    " (%1$s filtered by baseline %2$s)",
                                    describeCounts(
                                            stats.getBaselineErrorCount(),
                                            stats.getBaselineWarningCount(),
                                            true,
                                            true),
                                    baselineFile.getName()));
                }
            }
            writer.write('\n');
            writer.flush();
            if (close) {
                writer.close();

                if (!client.getFlags().isQuiet() && this.output != null) {
                    String path = convertPath(this.output.getAbsolutePath());
                    System.out.println(String.format("Wrote text report to %1$s", path));
                }
            }
        }
    }

    private void appendPath(@NonNull StringBuilder sb, @NonNull String path) {
        sb.append(convertPath(path));
    }

    @NonNull
    private String convertPath(@NonNull String path) {
        if (isForwardSlashPaths()) {
            if (File.separatorChar == '/') {
                return path;
            }
            return path.replace(File.separatorChar, '/');
        }

        return path;
    }

    private void explainIssue(@NonNull StringBuilder output, @Nullable Issue issue) {
        if (issue == null
                || !flags.isExplainIssues()
                || issue == IssueRegistry.LINT_ERROR
                || issue == IssueRegistry.BASELINE) {
            return;
        }

        String explanation = issue.getExplanation(TextFormat.TEXT);
        if (explanation.trim().isEmpty()) {
            return;
        }

        String indent = "   ";
        String formatted = SdkUtils.wrap(explanation, Main.MAX_LINE_WIDTH - indent.length(), null);
        output.append('\n');
        output.append(indent);
        output.append("Explanation for issues of type \"").append(issue.getId()).append("\":\n");
        for (String line : Splitter.on('\n').split(formatted)) {
            if (!line.isEmpty()) {
                output.append(indent);
                output.append(line);
            }
            output.append('\n');
        }
        List<String> moreInfo = issue.getMoreInfo();
        if (!moreInfo.isEmpty()) {
            for (String url : moreInfo) {
                if (formatted.contains(url)) {
                    continue;
                }
                output.append(indent);
                output.append(url);
                output.append('\n');
            }
            output.append('\n');
        }
    }

    boolean isWriteToConsole() {
        return output == null;
    }

    /**
     * Gets whether the reporter should convert paths to forward slashes
     *
     * @return true if forcing paths to forward slashes
     */
    public boolean isForwardSlashPaths() {
        return forwardSlashPaths;
    }

    /**
     * Sets whether the reporter should convert paths to forward slashes
     *
     * @param forwardSlashPaths true to force paths to forward slashes
     */
    public void setForwardSlashPaths(boolean forwardSlashPaths) {
        this.forwardSlashPaths = forwardSlashPaths;
    }

    /** Whether the report should include stats. Default is true. */
    public void setWriteStats(boolean writeStats) {
        this.writeStats = writeStats;
    }
}
