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

import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.utils.SdkUtils.isBitmapFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A reporter which emits lint results into an HTML report.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public class HtmlReporter extends Reporter {
    /**
     * Maximum number of warnings allowed for a single issue type before we split up and hide all
     * but the first {@link #SHOWN_COUNT} items.
     */
    private static final int SPLIT_LIMIT;
    /**
     * When a warning has at least {@link #SPLIT_LIMIT} items, then we show the following number of
     * items before the "Show more" button/link.
     */
    private static final int SHOWN_COUNT;

    /** Number of lines to show around code snippets */
    static final int CODE_WINDOW_SIZE;

    private static final String REPORT_PREFERENCE_PROPERTY = "lint.html.prefs";

    private static final boolean USE_WAVY_UNDERLINES_FOR_ERRORS;

    /**
     * Whether we should try to use browser support for wavy underlines. Underlines are not working
     * well; see https://bugs.chromium.org/p/chromium/issues/detail?id=165462 for when to re-enable.
     * If false we're using a CSS trick with repeated images instead. (Only applies if {@link
     * #USE_WAVY_UNDERLINES_FOR_ERRORS} is true.)
     */
    private static final boolean USE_CSS_DECORATION_FOR_WAVY_UNDERLINES = false;

    private static String preferredThemeName = "light";

    static {
        String preferences = System.getProperty(REPORT_PREFERENCE_PROPERTY);
        int codeWindowSize = 3;
        int splitLimit = 8;
        boolean underlineErrors = true;
        if (preferences != null) {
            for (String pref : Splitter.on(',').omitEmptyStrings().split(preferences)) {
                int index = pref.indexOf('=');
                if (index != -1) {
                    String key = pref.substring(0, index).trim();
                    String value = pref.substring(index + 1).trim();
                    if ("theme".equals(key)) {
                        preferredThemeName = value;
                    } else if ("window".equals(key)) {
                        try {
                            int size = Integer.decode(value);
                            if (size >= 1 && size < 3000) {
                                codeWindowSize = size;
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    } else if ("maxPerIssue".equals(key)) {
                        try {
                            int count = Integer.decode(value);
                            if (count >= 1 && count < 3000) {
                                splitLimit = count;
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    } else if ("underlineErrors".equals(key)) {
                        underlineErrors = Boolean.valueOf(value);
                    }
                }
            }
        }

        SPLIT_LIMIT = splitLimit;
        SHOWN_COUNT = Math.max(1, SPLIT_LIMIT - 3);
        CODE_WINDOW_SIZE = codeWindowSize;
        USE_WAVY_UNDERLINES_FOR_ERRORS = underlineErrors;
    }

    /**
     * CSS themes for syntax highlighting. The following classes map to an IntelliJ color theme like
     * this:
     *
     * <ul>
     *   <li>pre.errorlines: General > Text > Default Text
     *   <li>.prefix: XML > Namespace Prefix
     *   <li>.attribute: XML > Attribute name
     *   <li>.value: XML > Attribute value
     *   <li>.tag: XML > Tag name
     *   <li>.comment: XML > Comment
     *   <li>.javado: Comments > JavaDoc > Text
     *   <li>.annotation: Java > Annotations > Annotation name
     *   <li>.string: Java > String > String text
     *   <li>.number: Java > Numbers
     *   <li>.keyword: Java > Keyword
     *   <li>.caretline: General > Editor > Caret row (Background)
     *   <li>.lineno: For color, General > Code > Line number, Foreground, and for background-color,
     *       Editor > Gutter background
     *   <li>.error: General > Errors and Warnings > Error
     *   <li>.warning: General > Errors and Warnings > Warning
     *   <li>text-decoration: none;\n"
     * </ul>
     */
    @SuppressWarnings("ConstantConditions")
    private static final String CSS_SYNTAX_COLORS_LIGHT_THEME =
            ""
                    // Syntax highlighting
                    + "pre.errorlines {\n"
                    + "    background-color: white;\n"
                    + "    font-family: monospace;\n"
                    + "    border: 1px solid #e0e0e0;\n"
                    + "    line-height: 0.9rem;\n" // ensure line number gutter looks contiguous
                    + "    font-size: 0.9rem;"
                    + "    padding: 1px 0px 1px; 1px;\n" // no padding to make gutter look better
                    + "    overflow: scroll;\n"
                    + "}\n"
                    + ".prefix {\n"
                    + "    color: #660e7a;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".attribute {\n"
                    + "    color: #0000ff;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".value {\n"
                    + "    color: #008000;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".tag {\n"
                    + "    color: #000080;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".comment {\n"
                    + "    color: #808080;\n"
                    + "    font-style: italic;\n"
                    + "}\n"
                    + ".javadoc {\n"
                    + "    color: #808080;\n"
                    + "    font-style: italic;\n"
                    + "}\n"
                    + ".annotation {\n"
                    + "    color: #808000;\n"
                    + "}\n"
                    + ".string {\n"
                    + "    color: #008000;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".number {\n"
                    + "    color: #0000ff;\n"
                    + "}\n"
                    + ".keyword {\n"
                    + "    color: #000080;\n"
                    + "    font-weight: bold;\n"
                    + "}\n"
                    + ".caretline {\n"
                    + "    background-color: #fffae3;\n"
                    + "}\n"
                    + ".lineno {\n"
                    + "    color: #999999;\n"
                    + "    background-color: #f0f0f0;\n"
                    + "}\n"
                    + ".error {\n"
                    + (USE_WAVY_UNDERLINES_FOR_ERRORS
                            ? (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES
                                    ? ""
                                            + "    text-decoration: underline wavy #ff0000;\n"
                                            + "    text-decoration-color: #ff0000;\n"
                                            + "    -webkit-text-decoration-color: #ff0000;\n"
                                            + "    -moz-text-decoration-color: #ff0000;\n"
                                            + ""
                                    : ""
                                            + "    display: inline-block;\n"
                                            + "    position:relative;\n"
                                            + "    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFR4T/3uLMgAAADxJREFUCNdNyLERQEAABMCjL4lQwIzcjErpguAL+C9AvgKJDbeD/PRpLdm35Hm+MU+cB+tCKaJW4L4YBy+CAiLJrFs9mgAAAABJRU5ErkJggg==) bottom repeat-x;\n"
                                            + "")
                            : ""
                                    + "    text-decoration: none;\n"
                                    + "    background-color: #f8d8d8;\n")
                    + "}\n"
                    + ".warning {\n"
                    + "    text-decoration: none;\n"
                    + "    background-color: #f6ebbc;\n"
                    + "}\n";

    @SuppressWarnings("ConstantConditions")
    private static final String CSS_SYNTAX_COLORS_DARCULA =
            ""
                    + "pre.errorlines {\n"
                    + "    background-color: #2b2b2b;\n"
                    + "    color: #a9b7c6;\n"
                    + "    font-family: monospace;\n"
                    + "    font-size: 0.9rem;"
                    + "    line-height: 0.9rem;\n" // ensure line number gutter looks contiguous
                    + "    padding: 6px;\n"
                    + "    border: 1px solid #e0e0e0;\n"
                    + "    overflow: scroll;\n"
                    + "}\n"
                    + ".prefix {\n"
                    + "    color: #9876aa;\n"
                    + "}\n"
                    + ".attribute {\n"
                    + "    color: #BABABA;\n"
                    + "}\n"
                    + ".value {\n"
                    + "    color: #6a8759;\n"
                    + "}\n"
                    + ".tag {\n"
                    + "    color: #e8bf6a;\n"
                    + "}\n"
                    + ".comment {\n"
                    + "    color: #808080;\n"
                    + "}\n"
                    + ".javadoc {\n"
                    + "    font-style: italic;\n"
                    + "    color: #629755;\n"
                    + "}\n"
                    + ".annotation {\n"
                    + "    color: #BBB529;\n"
                    + "}\n"
                    + ".string {\n"
                    + "    color: #6a8759;\n"
                    + "}\n"
                    + ".number {\n"
                    + "    color: #6897bb;\n"
                    + "}\n"
                    + ".keyword {\n"
                    + "    color: #cc7832;\n"
                    + "}\n"
                    + ".caretline {\n"
                    + "    background-color: #323232;\n"
                    + "}\n"
                    + ".lineno {\n"
                    + "    color: #606366;\n"
                    + "    background-color: #313335;\n"
                    + "}\n"
                    + ".error {\n"
                    + (USE_WAVY_UNDERLINES_FOR_ERRORS
                            ? (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES
                                    ? ""
                                            + "    text-decoration: underline wavy #ff0000;\n"
                                            + "    text-decoration-color: #ff0000;\n"
                                            + "    -webkit-text-decoration-color: #ff0000;\n"
                                            + "    -moz-text-decoration-color: #ff0000;\n"
                                            + ""
                                    : ""
                                            + "    display: inline-block;\n"
                                            + "    position:relative;\n"
                                            + "    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFR46vckTXgAAAEBJREFUCNdj1NbW/s+ABJj4mJgYork5GNgZGSECYVzsDKd+/WaI5uZgEGVmYmBZ9e0nw6d//xg+/vvJEM7FwQAAPnUOmQBDSmAAAAAASUVORK5CYII=) bottom repeat-x;\n"
                                            + "")
                            : ""
                                    + "    text-decoration: none;\n"
                                    + "    background-color: #52503a;\n")
                    + "}\n"
                    + ".warning {\n"
                    + "    text-decoration: none;\n"
                    + "    background-color: #52503a;\n"
                    + "}\n";

    /** Solarized theme. */
    @SuppressWarnings({"ConstantConditions", "SpellCheckingInspection"})
    private static final String CSS_SYNTAX_COLORS_SOLARIZED =
            ""
                    + "pre.errorlines {\n"
                    + "    background-color: #FDF6E3;\n" // General > Text > Default Text, Background
                    + "    color: #586E75;\n" // General > Text > Default text, Foreground
                    + "    font-family: monospace;\n"
                    + "    font-size: 0.9rem;"
                    + "    line-height: 0.9rem;\n" // ensure line number gutter looks contiguous
                    + "    padding: 0px;\n" // no padding to make gutter look better
                    + "    border: 1px solid #e0e0e0;\n"
                    + "    overflow: scroll;\n"
                    + "}\n"
                    + ".prefix {\n" // XML > Namespace Prefix
                    + "    color: #6C71C4;\n"
                    + "}\n"
                    + ".attribute {\n" // XML > Attribute name
                    + "}\n"
                    + ".value {\n" // XML > Attribute value
                    + "    color: #2AA198;\n"
                    + "}\n"
                    + ".tag {\n" // XML > Tag name
                    + "    color: #268BD2;\n"
                    + "}\n"
                    + ".comment {\n" // XML > Comment
                    + "    color: #DC322F;\n"
                    + "}\n"
                    + ".javadoc {\n" // Comments > JavaDoc > Text
                    + "    font-style: italic;\n"
                    + "    color: #859900;\n"
                    + "}\n"
                    + ".annotation {\n" // Java > Annotations > Annotation name
                    + "    color: #859900;\n"
                    + "}\n"
                    + ".string {\n" // Java > String > String text
                    + "    color: #2AA198;\n"
                    + "}\n"
                    + ".number {\n" // Java > Numbers
                    + "    color: #CB4B16;\n"
                    + "}\n"
                    + ".keyword {\n" // Java > Keyword
                    + "    color: #B58900;\n"
                    + "}\n"
                    + ".caretline {\n" // General > Editor > Caret row, Background
                    + "    background-color: #EEE8D5;\n"
                    + "}\n"
                    + ".lineno {\n"
                    + "    color: #93A1A1;\n" // General > Code > Line number, Foreground
                    + "    background-color: #EEE8D5;\n" // Editor > Gutter background, Background
                    + "}\n"
                    + ".error {\n" // General > Errors and Warnings > Error
                    + (USE_WAVY_UNDERLINES_FOR_ERRORS
                            ? (USE_CSS_DECORATION_FOR_WAVY_UNDERLINES
                                    ? ""
                                            + "    text-decoration: underline wavy #DC322F;\n"
                                            + "    text-decoration-color: #DC322F;\n"
                                            + "    -webkit-text-decoration-color: #DC322F;\n"
                                            + "    -moz-text-decoration-color: #DC322F;\n"
                                            + ""
                                    : ""
                                            + "    display: inline-block;\n"
                                            + "    position:relative;\n"
                                            + "    background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAYAAAC09K7GAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4AwCFRgHs/v4yQAAAD5JREFUCNcBMwDM/wDqe2//++zZ//324v/75NH/AgxKRgDuho8A/OTnAO2KkwAA/fbi//nXxf/mZlz/++TR/4EMI0ZH4MfyAAAAAElFTkSuQmCC) bottom repeat-x;\n"
                                            + "")
                            : ""
                                    + "    text-decoration: none;\n"
                                    + "    color: #073642;\n" // not from theme
                                    + "    background-color: #FFA0A3;\n") // not from theme
                    + "}\n"
                    + ".warning {\n" // General > Errors and Warnings > Warning
                    + "    text-decoration: none;\n"
                    + "    color: #073642;\n"
                    + "    background-color: #FFDF80;\n"
                    + "}\n";

    private static final String CSS_SYNTAX_COLORS;

    static {
        String css;
        switch (preferredThemeName) {
            case "darcula":
                css = CSS_SYNTAX_COLORS_DARCULA;
                break;
            case "solarized":
                css = CSS_SYNTAX_COLORS_SOLARIZED;
                break;
            case "light":
            default:
                css = CSS_SYNTAX_COLORS_LIGHT_THEME;
                break;
        }
        CSS_SYNTAX_COLORS = css;
    }

    /**
     * Stylesheet for the HTML report. Note that the {@link LintSyntaxHighlighter} also depends on
     * these class names.
     */
    static final String CSS_STYLES =
            ""
                    + "section.section--center {\n"
                    + "    max-width: 860px;\n"
                    + "}\n"
                    + ".mdl-card__supporting-text + .mdl-card__actions {\n"
                    + "    border-top: 1px solid rgba(0, 0, 0, 0.12);\n"
                    + "}\n"
                    + "main > .mdl-layout__tab-panel {\n"
                    + "  padding: 8px;\n"
                    + "  padding-top: 48px;\n"
                    + "}\n"
                    + "\n"
                    + ".mdl-card__actions {\n"
                    + "    margin: 0;\n"
                    + "    padding: 4px 40px;\n"
                    + "    color: inherit;\n"
                    + "}\n"
                    + ".mdl-card > * {\n"
                    + "    height: auto;\n"
                    + "}\n"
                    + ".mdl-card__actions a {\n"
                    + "    color: #00BCD4;\n"
                    + "    margin: 0;\n"
                    + "}\n"
                    + ".error-icon {\n"
                    + "    color: #bb7777;\n"
                    + "    vertical-align: bottom;\n"
                    + "}\n"
                    + ".warning-icon {\n"
                    + "    vertical-align: bottom;\n"
                    + "}\n"
                    + ".mdl-layout__content section:not(:last-of-type) {\n"
                    + "  position: relative;\n"
                    + "  margin-bottom: 48px;\n"
                    + "}\n"
                    + "\n"
                    + ".mdl-card .mdl-card__supporting-text {\n"
                    + "  margin: 40px;\n"
                    + "  -webkit-flex-grow: 1;\n"
                    + "      -ms-flex-positive: 1;\n"
                    + "          flex-grow: 1;\n"
                    + "  padding: 0;\n"
                    + "  color: inherit;\n"
                    + "  width: calc(100% - 80px);\n"
                    + "}\n"
                    // Bug workaround - without this the hamburger icon is off center
                    + "div.mdl-layout__drawer-button .material-icons {\n"
                    + "    line-height: 48px;\n"
                    + "}\n"
                    // Make titles look better:
                    + ".mdl-card .mdl-card__supporting-text {\n"
                    + "    margin-top: 0px;\n"
                    + "}\n"
                    + ".chips {\n"
                    + "    float: right;\n"
                    + "    vertical-align: middle;\n"
                    + "}\n"
                    + CSS_SYNTAX_COLORS
                    + ".overview {\n"
                    + "    padding: 10pt;\n"
                    + "    width: 100%;\n"
                    + "    overflow: auto;\n"
                    + "    border-collapse:collapse;\n"
                    + "}\n"
                    + ".overview tr {\n"
                    + "    border-bottom: solid 1px #eeeeee;\n"
                    + "}\n"
                    + ".categoryColumn a {\n"
                    + "     text-decoration: none;\n"
                    + "     color: inherit;\n"
                    + "}\n"
                    + ".countColumn {\n"
                    + "    text-align: right;\n"
                    + "    padding-right: 20px;\n"
                    + "    width: 50px;\n"
                    + "}\n"
                    + ".issueColumn {\n"
                    + "   padding-left: 16px;\n"
                    + "}\n"
                    + ".categoryColumn {\n"
                    + "   position: relative;\n"
                    + "   left: -50px;\n"
                    + "   padding-top: 20px;\n"
                    + "   padding-bottom: 5px;\n"
                    + "}\n";

    protected final Writer writer;
    protected final LintCliFlags flags;
    private HtmlBuilder builder;

    @SuppressWarnings("StringBufferField")
    private StringBuilder sb;

    private String highlightedFile;
    private LintSyntaxHighlighter highlighter;

    /**
     * Creates a new {@link HtmlReporter}
     *
     * @param client the associated client
     * @param output the output file
     * @param flags the command line flags
     * @throws IOException if an error occurs
     */
    public HtmlReporter(
            @NonNull LintCliClient client, @NonNull File output, @NonNull LintCliFlags flags)
            throws IOException {
        super(client, output);
        writer = new BufferedWriter(Files.newWriter(output, Charsets.UTF_8));
        this.flags = flags;
    }

    @Override
    public void write(@NonNull LintStats stats, List<Warning> issues) throws IOException {
        Map<Issue, String> missing = computeMissingIssues(issues);
        List<List<Warning>> related = computeIssueLists(issues);

        startReport(stats);

        writeNavigationHeader(
                stats,
                () -> {
                    append(
                            "      <a class=\"mdl-navigation__link\" href=\"#overview\">"
                                    + "<i class=\"material-icons\">dashboard</i>Overview</a>\n");

                    for (List<Warning> warnings : related) {
                        Warning first = warnings.get(0);
                        String anchor = first.issue.getId();
                        String desc = first.issue.getBriefDescription(TextFormat.HTML);
                        append("      <a class=\"mdl-navigation__link\" href=\"#" + anchor + "\">");
                        if (first.severity.isError()) {
                            append("<i class=\"material-icons error-icon\">error</i>");
                        } else {
                            append("<i class=\"material-icons warning-icon\">warning</i>");
                        }
                        append(desc + " (" + warnings.size() + ")</a>\n");
                    }
                });

        if (!issues.isEmpty()) {
            append("\n<a name=\"overview\"></a>\n");
            writeCard(
                    () -> writeOverview(related, missing.size()), "Overview", true, "OverviewCard");

            Category previousCategory = null;
            for (List<Warning> warnings : related) {
                Category category = warnings.get(0).issue.getCategory();
                if (category != previousCategory) {
                    previousCategory = category;
                    append("\n<a name=\"");
                    append(category.getFullName());
                    append("\"></a>\n");
                }

                writeIssueCard(warnings);
            }

            if (!client.isCheckingSpecificIssues()) {
                writeMissingIssues(missing);
            }

            writeSuppressIssuesCard();
        } else {
            writeCard(() -> append("Congratulations!"), "No Issues Found", "NoIssuesCard");
        }

        finishReport();
        writeReport();

        if (!client.getFlags().isQuiet()
                && (stats.getErrorCount() > 0 || stats.getWarningCount() > 0)) {
            String url = SdkUtils.fileToUrlString(output.getAbsoluteFile());
            System.out.println(String.format("Wrote HTML report to %1$s", url));
        }
    }

    private void append(@NonNull String s) {
        sb.append(s);
    }

    private void append(char s) {
        sb.append(s);
    }

    private void writeSuppressIssuesCard() {
        append("\n<a name=\"SuppressInfo\"></a>\n");
        writeCard(
                () -> {
                    append(TextFormat.RAW.convertTo(Main.getSuppressHelp(), TextFormat.HTML));
                    this.append('\n');
                },
                "Suppressing Warnings and Errors",
                "SuppressCard");
    }

    private void writeIssueCard(List<Warning> warnings) {
        Issue firstIssue = warnings.get(0).issue;
        append("<a name=\"" + firstIssue.getId() + "\"></a>\n");
        writeCard(
                () -> {
                    Warning first = warnings.get(0);
                    Issue issue = first.issue;

                    append("<div class=\"issue\">\n");

                    append("<div class=\"warningslist\">\n");
                    boolean partialHide = warnings.size() > SPLIT_LIMIT;

                    int count = 0;
                    for (Warning warning : warnings) {
                        // Don't show thousands of matches for common errors; this just
                        // makes some reports huge and slow to render and nobody really wants to
                        // inspect 50+ individual reports of errors of the same type
                        if (count >= 50) {
                            if (count == 50) {
                                append(
                                        "<br/><b>NOTE: "
                                                + Integer.toString(warnings.size() - count)
                                                + " results omitted.</b><br/><br/>");
                            }
                            count++;
                            continue;
                        }
                        if (partialHide && count == SHOWN_COUNT) {
                            String id = warning.issue.getId() + "Div";

                            append("<button");
                            append(" class=\"mdl-button mdl-js-button mdl-button--primary\"");
                            append(" id=\"");
                            append(id);
                            append("Link\" onclick=\"reveal('");
                            append(id);
                            append("');\" />");
                            append(
                                    String.format(
                                            "+ %1$d More Occurrences...",
                                            warnings.size() - SHOWN_COUNT));
                            append("</button>\n");
                            append("<div id=\"");
                            append(id);
                            append("\" style=\"display: none\">\n");
                        }
                        count++;
                        String url = null;
                        if (warning.path != null) {
                            url = writeLocation(warning.file, warning.path, warning.line);
                            append(':');
                            append(' ');
                        }

                        // Is the URL for a single image? If so, place it here near the top
                        // of the error floating on the right. If there are multiple images,
                        // they will instead be placed in a horizontal box below the error
                        boolean addedImage = false;
                        if (url != null
                                && warning.location != null
                                && warning.location.getSecondary() == null) {
                            addedImage = addImage(url, warning.file, warning.location);
                        }

                        String rawMessage = warning.message;

                        // Improve formatting of exception stacktraces
                        if (issue == IssueRegistry.LINT_ERROR && rawMessage.contains("\u2190")) {
                            rawMessage = rawMessage.replace("\u2190", "\n\u2190");
                        }

                        append("<span class=\"message\">");
                        append(RAW.convertTo(rawMessage, HTML));
                        append("</span>");
                        if (addedImage) {
                            append("<br clear=\"right\"/>");
                        } else {
                            append("<br />");
                        }

                        if (warning.wasAutoFixed) {
                            append("This issue has been automatically fixed.<br />");
                        }

                        // Insert surrounding code block window
                        if (warning.line >= 0
                                && warning.fileContents != null
                                && warning.offset != -1
                                && warning.endOffset != -1) {
                            appendCodeBlock(
                                    warning.file,
                                    warning.fileContents,
                                    warning.offset,
                                    warning.endOffset,
                                    warning.severity);
                        }
                        append('\n');
                        if (warning.location != null && warning.location.getSecondary() != null) {
                            append("<ul>");
                            Location l = warning.location.getSecondary();
                            int otherLocations = 0;
                            int shownSnippetsCount = 0;
                            while (l != null) {
                                String message = l.getMessage();
                                if (message != null && !message.isEmpty()) {
                                    Position start = l.getStart();
                                    int line = start != null ? start.getLine() : -1;
                                    String path =
                                            client.getDisplayPath(warning.project, l.getFile());
                                    writeLocation(l.getFile(), path, line);
                                    append(':');
                                    append(' ');
                                    append("<span class=\"message\">");
                                    append(RAW.convertTo(message, HTML));
                                    append("</span>");
                                    append("<br />");

                                    // Only display up to 3 inlined views to keep big reports from
                                    // getting massive in rendering cost
                                    if (shownSnippetsCount < 3 && !isBitmapFile(l.getFile())) {
                                        CharSequence s = client.readFile(l.getFile());
                                        if (s.length() > 0) {
                                            int offset = start != null ? start.getOffset() : -1;
                                            appendCodeBlock(
                                                    l.getFile(), s, offset, -1, warning.severity);
                                        }
                                        shownSnippetsCount++;
                                    }
                                } else {
                                    otherLocations++;
                                }

                                l = l.getSecondary();
                            }
                            append("</ul>");
                            if (otherLocations > 0) {
                                String id = "Location" + count + "Div";
                                append("<button id=\"");
                                append(id);
                                append("Link\" onclick=\"reveal('");
                                append(id);
                                append("');\" />");
                                append(
                                        String.format(
                                                "+ %1$d Additional Locations...", otherLocations));
                                append("</button>\n");
                                append("<div id=\"");
                                append(id);
                                append("\" style=\"display: none\">\n");

                                append("Additional locations: ");
                                append("<ul>\n");
                                l = warning.location.getSecondary();
                                while (l != null) {
                                    Position start = l.getStart();
                                    int line = start != null ? start.getLine() : -1;
                                    String path =
                                            client.getDisplayPath(warning.project, l.getFile());
                                    append("<li> ");
                                    writeLocation(l.getFile(), path, line);
                                    append("\n");
                                    l = l.getSecondary();
                                }
                                append("</ul>\n");

                                append("</div><br/><br/>\n");
                            }
                        }

                        // Place a block of images?
                        if (!addedImage
                                && url != null
                                && warning.location != null
                                && warning.location.getSecondary() != null) {
                            addImage(url, warning.file, warning.location);
                        }

                        if (warning.isVariantSpecific()) {
                            append("\n");
                            append("Applies to variants: ");
                            append(Joiner.on(", ").join(warning.getIncludedVariantNames()));
                            append("<br/>\n");
                            append("Does <b>not</b> apply to variants: ");
                            append(Joiner.on(", ").join(warning.getExcludedVariantNames()));
                            append("<br/>\n");
                        }
                    }
                    if (partialHide) { // Close up the extra div
                        append("</div>\n"); // partial hide
                    }

                    append("</div>\n"); // class=warningslist

                    writeIssueMetadata(issue, null, true);

                    append("</div>\n"); // class=issue

                    append("<div class=\"chips\">\n");
                    writeChip(issue.getId());
                    Category category = issue.getCategory();
                    while (category != null && category != Category.LINT) {
                        writeChip(category.getName());
                        category = category.getParent();
                    }
                    writeChip(first.severity.getDescription());
                    writeChip("Priority " + issue.getPriority() + "/10");
                    append("</div>\n"); //class=chips
                },
                XmlUtils.toXmlTextValue(firstIssue.getBriefDescription(TextFormat.TEXT)),
                true,
                firstIssue.getId() + "Card",
                new Action(
                        "Explain",
                        getExplanationId(firstIssue),
                        "reveal")); // HTML style isn't handled right by card widget
    }

    /**
     * Sorts the list of warnings into a list of lists where each list contains warnings for the
     * same base issue type
     */
    @NonNull
    private static List<List<Warning>> computeIssueLists(@NonNull List<Warning> issues) {
        Issue previousIssue = null;
        List<List<Warning>> related = new ArrayList<>();
        if (!issues.isEmpty()) {
            List<Warning> currentList = null;
            for (Warning warning : issues) {
                if (warning.issue != previousIssue) {
                    previousIssue = warning.issue;
                    currentList = new ArrayList<>();
                    related.add(currentList);
                }
                assert currentList != null;
                currentList.add(warning);
            }
        }
        return related;
    }

    private void startReport(@NonNull LintStats stats) {
        sb = new StringBuilder(1800 * stats.count());
        builder = new HtmlBuilder(sb);

        writeOpenHtmlTag();
        writeHeadTag();
        writeOpenBodyTag();
    }

    private void finishReport() {
        writeCloseNavigationHeader();
        writeCloseBodyTag();
        writeCloseHtmlTag();
    }

    private void writeNavigationHeader(@NonNull LintStats stats, @NonNull Runnable appender) {
        append(
                ""
                        + "<div class=\"mdl-layout mdl-js-layout mdl-layout--fixed-header\">\n"
                        + "  <header class=\"mdl-layout__header\">\n"
                        + "    <div class=\"mdl-layout__header-row\">\n"
                        + "      <span class=\"mdl-layout-title\">"
                        + title
                        + ": "
                        + Lint.describeCounts(
                                stats.getErrorCount(), stats.getWarningCount(), false, true)
                        + "</span>\n"
                        + "      <div class=\"mdl-layout-spacer\"></div>\n"
                        + "      <nav class=\"mdl-navigation mdl-layout--large-screen-only\">\n");

        append(String.format("Check performed at %1$s", new Date().toString()));

        append(
                ""
                        + "      </nav>\n"
                        + "    </div>\n"
                        + "  </header>\n"
                        + "  <div class=\"mdl-layout__drawer\">\n"
                        + "    <span class=\"mdl-layout-title\">Issue Types</span>\n"
                        + "    <nav class=\"mdl-navigation\">\n");

        appender.run();

        append(
                ""
                        + "    </nav>\n"
                        + "  </div>\n"
                        + "  <main class=\"mdl-layout__content\">\n"
                        + "    <div class=\"mdl-layout__tab-panel is-active\">");
    }

    private void writeCloseNavigationHeader() {
        append("    </div>\n  </main>\n</div>");
    }

    private void writeOpenBodyTag() {
        append("<body class=\"mdl-color--grey-100 mdl-color-text--grey-700 mdl-base\">\n");
    }

    private void writeCloseBodyTag() {
        append("\n</body>\n");
    }

    private void writeOpenHtmlTag() {
        append(
                ""
                        + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
    }

    private void writeCloseHtmlTag() {
        append("</html>");
    }

    private void writeHeadTag() {
        append(
                ""
                        + "\n"
                        + "<head>\n"
                        + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                        + "<title>"
                        + title
                        + "</title>\n");

        // Material
        append(
                ""
                        + "<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/icon?family=Material+Icons\">\n"
                        // Based on https://getmdl.io/customize/index.html
                        //+ "<link rel=\"stylesheet\" href=\"https://code.getmdl.io/1.2.0/material.indigo-pink.min.css\">\n"
                        //+ " <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.grey-light_blue.min.css" /> \n"
                        + " <link rel=\"stylesheet\" href=\"https://code.getmdl.io/1.2.1/material.blue-indigo.min.css\" />\n"
                        + "<link rel=\"stylesheet\" href=\"http://fonts.googleapis.com/css?family=Roboto:300,400,500,700\" type=\"text/css\">\n"
                        + "<script defer src=\"https://code.getmdl.io/1.2.0/material.min.js\"></script>\n");
        append("<style>\n" + CSS_STYLES + "</style>\n");

        // JavaScript for collapsing/expanding long lists
        append(
                ""
                        + "<script language=\"javascript\" type=\"text/javascript\"> \n"
                        + "<!--\n"
                        + "function reveal(id) {\n"
                        + "if (document.getElementById) {\n"
                        + "document.getElementById(id).style.display = 'block';\n"
                        + "document.getElementById(id+'Link').style.display = 'none';\n"
                        + "}\n"
                        + "}\n"
                        + "function hideid(id) {\n"
                        + "if (document.getElementById) {\n"
                        + "document.getElementById(id).style.display = 'none';\n"
                        //+ "document.getElementById(id).hidden=true;\n" +
                        + "}\n"
                        + "}\n"
                        + "//--> \n"
                        + "</script>\n");

        append("</head>\n");
    }

    private void writeIssueMetadata(Issue issue, String disabledBy, boolean hide) {
        append("<div class=\"metadata\">");

        if (disabledBy != null) {
            append(String.format("Disabled By: %1$s<br/>\n", disabledBy));
        }

        append("<div class=\"explanation\"");
        if (hide) {
            append(" id=\"" + getExplanationId(issue) + "\" style=\"display: none;\"");
        }
        append(">\n");
        String explanationHtml = issue.getExplanation(HTML);
        append(explanationHtml);
        List<String> moreInfo = issue.getMoreInfo();
        append("<br/>");

        // TODO: Skip MoreInfo links already present in the HTML to avoid redundancy.

        int count = moreInfo.size();
        if (count > 0) {
            append("<div class=\"moreinfo\">");
            append("More info: ");
            if (count > 1) {
                append("<ul>");
            }
            for (String uri : moreInfo) {
                if (count > 1) {
                    append("<li>");
                }
                append("<a href=\"");
                append(uri);
                append("\">");
                append(uri);
                append("</a>\n");
            }
            if (count > 1) {
                append("</ul>");
            }
            append("</div>");
        }

        if (client.getRegistry() instanceof BuiltinIssueRegistry) {
            if (Reporter.hasAutoFix(issue)) {
                append(
                        "Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.");
                append("<br>\n");
            }
        }

        append(
                String.format(
                        "To suppress this error, use the issue id \"%1$s\" as explained in the "
                                + "%2$sSuppressing Warnings and Errors%3$s section.",
                        issue.getId(), "<a href=\"#SuppressInfo\">", "</a>"));
        append("<br/>\n");
        append("<br/>");
        append("</div>"); //class=moreinfo
        append("\n</div>\n"); //class=explanation
    }

    protected Map<Issue, String> computeMissingIssues(List<Warning> warnings) {
        Set<Project> projects = new HashSet<>();
        Set<Issue> seen = new HashSet<>();
        for (Warning warning : warnings) {
            projects.add(warning.project);
            seen.add(warning.issue);
        }
        Configuration cliConfiguration = client.getConfiguration();
        Map<Issue, String> map = Maps.newHashMap();
        for (Issue issue : client.getRegistry().getIssues()) {
            if (!seen.contains(issue)) {
                if (client.isSuppressed(issue)) {
                    map.put(issue, "Command line flag");
                    continue;
                }

                if (!issue.isEnabledByDefault() && !client.isAllEnabled()) {
                    map.put(issue, "Default");
                    continue;
                }

                if (cliConfiguration != null && !cliConfiguration.isEnabled(issue)) {
                    map.put(issue, "Command line supplied --config lint.xml file");
                    continue;
                }

                // See if any projects disable this warning
                for (Project project : projects) {
                    if (!project.getConfiguration(null).isEnabled(issue)) {
                        map.put(issue, "Project lint.xml file");
                        break;
                    }
                }
            }
        }

        return map;
    }

    private void writeMissingIssues(@NonNull Map<Issue, String> missing) {
        if (!client.isCheckingSpecificIssues()) {
            append("\n<a name=\"MissingIssues\"></a>\n");

            writeCard(
                    () -> {
                        append(
                                ""
                                        + "One or more issues were not run by lint, either \n"
                                        + "because the check is not enabled by default, or because \n"
                                        + "it was disabled with a command line flag or via one or \n"
                                        + "more <code>lint.xml</code> configuration files in the project "
                                        + "directories.\n");

                        append("<div id=\"SuppressedIssues\" style=\"display: none;\">");
                        List<Issue> list = new ArrayList<>(missing.keySet());
                        Collections.sort(list);
                        append("<br/><br/>");

                        for (Issue issue : list) {
                            append("<div class=\"issue\">\n");

                            // Explain this issue
                            append("<div class=\"id\">");
                            append(issue.getId());
                            append("<div class=\"issueSeparator\"></div>\n");
                            append("</div>\n");
                            String disabledBy = missing.get(issue);
                            writeIssueMetadata(issue, disabledBy, false);
                            append("</div>\n");
                        }

                        append("</div>"); //SuppressedIssues
                    },
                    "Disabled Checks",
                    true,
                    "MissingIssuesCard",
                    new Action("List Missing Issues", "SuppressedIssues", "reveal"));
        }
    }

    private void writeOverview(List<List<Warning>> related, int missingCount) {
        // Write issue id summary
        append("<table class=\"overview\">\n");

        Category previousCategory = null;
        for (List<Warning> warnings : related) {
            Warning first = warnings.get(0);
            Issue issue = first.issue;
            boolean isError = first.severity.isError();

            if (issue.getCategory() != previousCategory) {
                append("<tr><td class=\"countColumn\"></td><td class=\"categoryColumn\">");
                previousCategory = issue.getCategory();
                String categoryName = issue.getCategory().getFullName();
                append("<a href=\"#");
                append(categoryName);
                append("\">");
                append(categoryName);
                append("</a>\n");
                append("</td></tr>");
                append("\n");
            }
            append("<tr>\n");

            // Count column
            append("<td class=\"countColumn\">");
            append(Integer.toString(warnings.size()));
            append("</td>");

            append("<td class=\"issueColumn\">");

            if (isError) {
                append("<i class=\"material-icons error-icon\">error</i>");
            } else {
                append("<i class=\"material-icons warning-icon\">warning</i>");
            }
            append('\n');

            append("<a href=\"#");
            append(issue.getId());
            append("\">");
            append(issue.getId());
            append("</a>");
            append(": ");
            append(issue.getBriefDescription(HTML));

            append("</td></tr>\n");
        }

        if (missingCount > 0 && !client.isCheckingSpecificIssues()) {
            append("<tr><td></td>");
            append("<td class=\"categoryColumn\">");
            append("<a href=\"#MissingIssues\">");
            append(String.format("Disabled Checks (%1$d)", missingCount));

            append("</a>\n");
            append("</td></tr>");
        }

        append("</table>\n");
        append("<br/>");
    }

    private static String getCardId(int cardNumber) {
        return "card" + cardNumber;
    }

    private static String getExplanationId(Issue issue) {
        return "explanation" + issue.getId();
    }

    public void writeCardHeader(@Nullable String title, @NonNull String cardId) {
        append(
                ""
                        + "<section class=\"section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp\" id=\""
                        + cardId
                        + "\" style=\"display: block;\">\n"
                        + "            <div class=\"mdl-card mdl-cell mdl-cell--12-col\">\n");
        if (title != null) {
            append(
                    ""
                            + "  <div class=\"mdl-card__title\">\n"
                            + "    <h2 class=\"mdl-card__title-text\">"
                            + title
                            + "</h2>\n"
                            + "  </div>\n");
        }

        append("              <div class=\"mdl-card__supporting-text\">\n");
    }

    private static class Action {
        public final String title;
        public final String id;
        public final String function;

        public Action(String title, String id, String function) {
            this.title = title;
            this.id = id;
            this.function = function;
        }
    }

    public void writeCardAction(@NonNull Action... actions) {
        append(
                ""
                        + "              </div>\n"
                        + "              <div class=\"mdl-card__actions mdl-card--border\">\n");
        for (Action action : actions) {
            append(
                    ""
                            + "<button class=\"mdl-button mdl-js-button mdl-js-ripple-effect\""
                            + " id=\""
                            + action.id
                            + "Link\""
                            + " onclick=\""
                            + action.function
                            + "('"
                            + action.id
                            + "');"
                            + "\">\n"
                            + action.title
                            + "</button>");
        }
    }

    public void writeCardFooter() {
        append("            </div>\n            </div>\n          </section>");
    }

    public void writeCard(
            @NonNull Runnable appender, @Nullable String title, @Nullable String cardId) {
        writeCard(appender, title, false, cardId);
    }

    public void writeChip(@NonNull String text) {
        append(
                ""
                        + "<span class=\"mdl-chip\">\n"
                        + "    <span class=\"mdl-chip__text\">"
                        + text
                        + "</span>\n"
                        + "</span>\n");
    }

    int cardNumber = 0;

    final Set<String> usedCardIds = Sets.newHashSet();

    public void writeCard(
            @NonNull Runnable appender,
            @Nullable String title,
            boolean dismissible,
            String cardId,
            Action... actions) {
        if (cardId == null) {
            int card = cardNumber++;
            cardId = getCardId(card);
        }

        // Ensure we don't have duplicates (for right now)
        assert !usedCardIds.contains(cardId) : cardId;
        usedCardIds.add(cardId);

        writeCardHeader(title, cardId);
        appender.run();
        if (dismissible) {
            String dismissTitle = "Dismiss";
            if ("New Lint Report Format".equals(title)) {
                dismissTitle = "Got It";
            }
            actions = ObjectArrays.concat(actions, new Action(dismissTitle, cardId, "hideid"));
            writeCardAction(actions);
        }
        writeCardFooter();
    }

    private String writeLocation(File file, String path, int line) {
        String url;
        append("<span class=\"location\">");

        url = getUrl(file);
        if (url != null) {
            append("<a href=\"");
            append(url);
            append("\">");
        }

        String displayPath = stripPath(path);
        if (url != null && url.startsWith("../") && new File(displayPath).isAbsolute()) {
            displayPath = url;
        }

        // Clean up super-long and ugly paths to cache files such as
        //    ../../../../../../.gradle/caches/transforms-1/files-1.1/timber-4.6.0.aar/
        //      8fe9cb22a46026bb3bd0c9d976e2897a/jars/lint.jar
        if (displayPath.contains("transforms-1")
                && displayPath.endsWith("lint.jar")
                && displayPath.contains(".aar")) {
            int aarIndex = displayPath.indexOf(".aar");
            int startWin = displayPath.lastIndexOf('\\', aarIndex) + 1;
            int startUnix = displayPath.lastIndexOf('/', aarIndex) + 1;
            int start = Math.max(startWin, startUnix);
            displayPath =
                    displayPath.substring(start, aarIndex + 4)
                            + File.separator
                            + "..."
                            + File.separator
                            + "lint.jar";
        }

        append(displayPath);
        //noinspection VariableNotUsedInsideIf
        if (url != null) {
            append("</a>");
        }
        if (line >= 0) {
            // 0-based line numbers, but display 1-based
            append(':');
            append(Integer.toString(line + 1));
        }
        append("</span>");
        return url;
    }

    /**
     * Returns the density for the given file, if known (e.g. in a density folder, such as
     * drawable-mdpi
     */
    private static int getDensity(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            String name = parent.getName();
            FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
            if (configuration != null) {
                DensityQualifier qualifier = configuration.getDensityQualifier();
                if (qualifier != null && !qualifier.hasFakeValue()) {
                    Density density = qualifier.getValue();
                    if (density != null) {
                        return density.getDpiValue();
                    }
                }
            }
        }

        return 0;
    }

    /** Compare icons - first in descending density order, then by name */
    static final Comparator<File> ICON_DENSITY_COMPARATOR =
            (file1, file2) -> {
                int density1 = getDensity(file1);
                int density2 = getDensity(file2);
                int densityDelta = density1 - density2;
                if (densityDelta != 0) {
                    return densityDelta;
                }

                return file1.getName().compareToIgnoreCase(file2.getName());
            };

    private boolean addImage(String url, File urlFile, Location location) {
        if (url != null && urlFile != null && isBitmapFile(urlFile)) {
            if (location.getSecondary() != null) {
                // Emit many images
                // Add in linked images as well
                List<File> files = Lists.newArrayList();
                while (location != null) {
                    File file = location.getFile();
                    if (isBitmapFile(file)) {
                        files.add(file);
                    }
                    location = location.getSecondary();
                }

                files.sort(ICON_DENSITY_COMPARATOR);

                List<String> urls = new ArrayList<>();
                for (File file : files) {
                    String imageUrl = getUrl(file);
                    if (imageUrl != null) {
                        urls.add(imageUrl);
                    }
                }

                if (!urls.isEmpty()) {
                    append("<table>\n");
                    append("<tr>");
                    for (String linkedUrl : urls) {
                        // Image series: align top
                        append("<td>");
                        append("<a href=\"");
                        append(linkedUrl);
                        append("\">");
                        append("<img border=\"0\" align=\"top\" src=\"");
                        append(linkedUrl);
                        append("\" /></a>\n");
                        append("</td>");
                    }
                    append("</tr>");

                    append("<tr>");
                    for (String linkedUrl : urls) {
                        append("<th>");
                        int index = linkedUrl.lastIndexOf("drawable-");
                        if (index != -1) {
                            index += "drawable-".length();
                            int end = linkedUrl.indexOf('/', index);
                            if (end != -1) {
                                append(linkedUrl.substring(index, end));
                            }
                        }
                        append("</th>");
                    }
                    append("</tr>\n");

                    append("</table>\n");
                }
            } else {
                // Just this image: float to the right
                append("<img class=\"embedimage\" align=\"right\" src=\"");
                append(url);
                append("\" />");
            }

            return true;
        }

        return false;
    }

    @Override
    public void writeProjectList(
            @NonNull LintStats stats, @NonNull List<MultiProjectHtmlReporter.ProjectEntry> projects)
            throws IOException {
        startReport(stats);

        writeNavigationHeader(
                stats,
                () -> {
                    for (MultiProjectHtmlReporter.ProjectEntry entry : projects) {
                        append(
                                "      <a class=\"mdl-navigation__link\" href=\""
                                        + XmlUtils.toXmlAttributeValue(entry.fileName)
                                        + "\">"
                                        + entry.path
                                        + " ("
                                        + (entry.errorCount + entry.warningCount)
                                        + ")</a>\n");
                    }
                });

        if (stats.getErrorCount() == 0 && stats.getWarningCount() == 0) {
            writeCard(() -> append("Congratulations!"), "No Issues Found", "NoIssuesCard");
            return;
        }

        writeCard(
                () -> {
                    // Write issue id summary
                    append("<table class=\"overview\">\n");

                    append("<tr><th>");
                    append("Project");
                    append("</th><th class=\"countColumn\">");

                    append("Errors");
                    append("</th><th class=\"countColumn\">");

                    append("Warnings");
                    append("</th></tr>\n");
                    for (MultiProjectHtmlReporter.ProjectEntry entry : projects) {

                        append("<tr><td>");
                        append("<a href=\"");
                        append(XmlUtils.toXmlAttributeValue(entry.fileName));
                        append("\">");
                        append(entry.path);
                        append("</a></td><td class=\"countColumn\">");
                        append(Integer.toString(entry.errorCount));
                        append("</td><td class=\"countColumn\">");
                        append(Integer.toString(entry.warningCount));
                        append("</td></tr>\n");

                        append("<tr>\n");
                    }

                    append("</table>\n");
                    append("<br/>");
                },
                "Projects",
                "OverviewCard");

        finishReport();
        writeReport();
    }

    private void writeReport() throws IOException {
        writer.write(sb.toString());
        writer.close();
        sb = null;
        builder = null;
    }

    @NonNull
    private LintSyntaxHighlighter getHighlighter(
            @NonNull File file, @NonNull CharSequence contents) {
        if (highlightedFile == null || !highlightedFile.equals(file.getPath())) {
            highlighter = new LintSyntaxHighlighter(file.getName(), contents.toString());
            highlighter.setPadCaretLine(true);
            highlighter.setDedent(true);
            highlightedFile = file.getPath();
        }

        return highlighter;
    }

    /** Insert syntax highlighted XML */
    private void appendCodeBlock(
            @NonNull File file,
            @NonNull CharSequence contents,
            int startOffset,
            int endOffset,
            @NonNull Severity severity) {
        int start = Math.max(0, startOffset);
        int end = Math.max(start, Math.min(endOffset, contents.length()));
        getHighlighter(file, contents).generateHtml(builder, start, end, severity.isError());
    }
}
