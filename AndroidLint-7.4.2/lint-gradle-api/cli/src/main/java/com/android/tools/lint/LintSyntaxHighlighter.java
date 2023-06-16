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

import static com.android.SdkConstants.DOT_AIDL;
import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.lint.HtmlReporter.CODE_WINDOW_SIZE;
import static com.android.tools.lint.detector.api.Lint.isJavaKeyword;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LintSyntaxHighlighter {
    private static final int STYLE_PLAIN_TEXT = 1;
    private static final int STYLE_TAG = 2;
    private static final int STYLE_ATTRIBUTE = 3;
    private static final int STYLE_PREFIX = 4;
    private static final int STYLE_VALUE = 5;
    private static final int STYLE_COMMENT = 6;
    private static final int STYLE_PROLOGUE = 7;
    private static final int STYLE_CDATA = 8;
    private static final int STYLE_STRING = 9;
    private static final int STYLE_NUMBER = 10;
    private static final int STYLE_KEYWORD = 11;
    private static final int STYLE_JAVADOC_COMMENT = 12;
    private static final int STYLE_ANNOTATION = 13;

    private static final int TAB_WIDTH = 4;

    private final String source;

    // Map from offset to STYLE_ constant
    private final Map<Integer, Integer> styles;
    // Map from line start offset to line number
    private final HashBiMap<Integer, Integer> lineNumbers;
    private int lineCount;

    private List<Integer> sortedOffsets;

    private boolean forceSingleLineRange = true;

    private boolean padCaretLine = false;

    private boolean dedent = false;

    public LintSyntaxHighlighter(@NonNull String fileName, @NonNull String source) {
        this.source = source;
        int estimatedLineCount = Math.min(10, source.length() / 50);
        lineNumbers = HashBiMap.create(estimatedLineCount);
        // Estimate at least 5 spans per line
        styles = Maps.newHashMapWithExpectedSize(5 * estimatedLineCount);

        initializeLineNumberMap();

        tokenizeFile(fileName);
    }

    private void tokenizeFile(@NonNull String fileName) {
        styles.put(0, STYLE_PLAIN_TEXT);
        styles.put(source.length(), STYLE_PLAIN_TEXT);

        if (endsWithIgnoreCase(fileName, DOT_XML)) {
            tokenizeXml();
        } else if (endsWithIgnoreCase(fileName, DOT_JAVA)) {
            tokenizeJavaLikeLanguage(Lint::isJavaKeyword);
        } else if (endsWithIgnoreCase(fileName, DOT_GRADLE)) {
            tokenizeJavaLikeLanguage(LintSyntaxHighlighter::isGroovyKeyword);
        } else if (endsWithIgnoreCase(fileName, ".kt")) {
            tokenizeJavaLikeLanguage(LintSyntaxHighlighter::isKotlinKeyword);
        } else if (endsWithIgnoreCase(fileName, DOT_AIDL)) {
            tokenizeJavaLikeLanguage(LintSyntaxHighlighter::isAidlKeyword);
        } // else: plaintext: no need to tokenize

        updateSortedOffsets();
        removeRepeatedStyleSpans();
    }

    @SuppressWarnings("unused")
    public boolean isForceSingleLineRange() {
        return forceSingleLineRange;
    }

    /**
     * Sets whether we force the error range to be limited to a single line in {@link
     * #generateHtml(HtmlBuilder, int, int, boolean)}
     */
    @SuppressWarnings("unused")
    public LintSyntaxHighlighter setForceSingleLineRange(boolean forceSingleLineRange) {
        this.forceSingleLineRange = forceSingleLineRange;
        return this;
    }

    /** Whether we should pad the caret line with spaces to ensure background colors are shown */
    @SuppressWarnings("unused")
    public boolean isPadCaretLine() {
        return padCaretLine;
    }

    /** Whether we should pad the caret line with spaces to ensure background colors are shown */
    public LintSyntaxHighlighter setPadCaretLine(boolean padCaretLine) {
        this.padCaretLine = padCaretLine;
        return this;
    }

    /**
     * Whether the highlighter should dedent the code (removing shared whitespace on the left) to
     * make as much code visible as possible
     */
    public boolean isDedent() {
        return dedent;
    }

    /**
     * Whether the highlighter should dedent the code (removing shared whitespace on the left) to
     * make as much code visible as possible
     */
    public void setDedent(boolean dedent) {
        this.dedent = dedent;
    }

    interface KeywordChecker {
        boolean isKeyword(@NonNull String keyword);
    }

    private void tokenizeJavaLikeLanguage(KeywordChecker keywordLookup) {
        // Simple HTML tokenizer
        int length = source.length();
        final int STATE_INITIAL = 1;
        final int STATE_SLASH = 2;
        final int STATE_LINE_COMMENT = 3;
        final int STATE_BLOCK_COMMENT = 4;
        final int STATE_STRING_DOUBLE_QUOTE = 5;
        final int STATE_STRING_SINGLE_QUOTE = 6; // not java, but Groovy etc
        final int STATE_STRING_TRIPLE_DOUBLE_QUOTE = 7; // Groovy, Kotlin
        final int STATE_STRING_TRIPLE_SINGLE_QUOTE = 8; // Groovy
        final int STATE_NUMBER = 9;
        final int STATE_IDENTIFIER = 10;

        int state = STATE_INITIAL;
        int offset = 0;
        int identifierStart = -1;
        while (offset < length) {
            char c = source.charAt(offset);
            switch (state) {
                case STATE_INITIAL:
                    {
                        if (c == '/') {
                            state = STATE_SLASH;
                            offset++;
                            continue;
                        } else if (c == '"') {
                            state = STATE_STRING_DOUBLE_QUOTE;
                            styles.put(offset, STYLE_STRING);
                            // Look for triple-quoted strings (Groovy and Kotlin)
                            if (source.startsWith("\"\"\"", offset)) {
                                state = STATE_STRING_TRIPLE_DOUBLE_QUOTE;
                                offset += 3;
                                continue;
                            }
                        } else if (c == '\'') {
                            state = STATE_STRING_SINGLE_QUOTE;
                            styles.put(offset, STYLE_STRING);
                            // Look for triple-quoted strings (Groovy and Kotlin)
                            if (source.startsWith("'''", offset)) {
                                state = STATE_STRING_TRIPLE_SINGLE_QUOTE;
                                offset += 3;
                                continue;
                            }
                        } else if (Character.isDigit(c)) {
                            state = STATE_NUMBER;
                            styles.put(offset, STYLE_NUMBER);
                        } else if (Character.isJavaIdentifierStart(c)) {
                            state = STATE_IDENTIFIER;
                            identifierStart = offset;
                        }
                        offset++;
                        continue;
                    }

                case STATE_NUMBER:
                    {
                        if (Character.isDigit(c)
                                || c == '.'
                                || c == '_'
                                || c == 'l'
                                || c == 'L'
                                || c == 'x'
                                || c == 'X'
                                || c >= 'a' && c <= 'f'
                                || c >= 'A' && c <= 'F') {
                            offset++;
                            continue;
                        }
                        styles.put(offset, STYLE_PLAIN_TEXT);
                        state = STATE_INITIAL;
                        continue;
                    }

                case STATE_IDENTIFIER:
                    {
                        if (Character.isJavaIdentifierPart(c)) {
                            offset++;
                            continue;
                        }
                        assert identifierStart != -1 : identifierStart;
                        // See if the identifier was a keyword, and if so highlight it
                        String identifier = source.substring(identifierStart, offset);
                        if (keywordLookup.isKeyword(identifier)) {
                            styles.put(identifierStart, STYLE_KEYWORD);
                            styles.put(offset, STYLE_PLAIN_TEXT);
                        } else if (identifierStart > 0
                                && source.charAt(identifierStart - 1) == '@') {
                            styles.put(identifierStart - 1, STYLE_ANNOTATION);
                            styles.put(offset, STYLE_PLAIN_TEXT);
                        }

                        state = STATE_INITIAL;
                        identifierStart = -1;
                        continue;
                    }

                case STATE_SLASH:
                    {
                        if (c == '/') {
                            state = STATE_LINE_COMMENT;
                            styles.put(offset - 1, STYLE_COMMENT);
                        } else if (c == '*') {
                            state = STATE_BLOCK_COMMENT;
                            if (offset < source.length() - 1 && source.charAt(offset + 1) == '*') {
                                styles.put(offset - 1, STYLE_JAVADOC_COMMENT);
                                offset++;
                            } else {
                                styles.put(offset - 1, STYLE_COMMENT);
                            }
                        } else {
                            state = STATE_INITIAL;
                            continue;
                        }
                        offset++;
                        continue;
                    }

                case STATE_LINE_COMMENT:
                    {
                        if (c == '\n') {
                            state = STATE_INITIAL;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                        }
                        offset++;
                        continue;
                    }

                case STATE_BLOCK_COMMENT:
                    {
                        if (c == '*'
                                && offset < source.length() - 1
                                && source.charAt(offset + 1) == '/') {
                            state = STATE_INITIAL;
                            offset += 2;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            continue;
                        }
                        offset++;
                        continue;
                    }

                case STATE_STRING_DOUBLE_QUOTE:
                    {
                        if (c == '\\') {
                            offset += 2;
                            continue;
                        } else if (c == '"') {
                            state = STATE_INITIAL;
                            offset++;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            continue;
                        }

                        // Worry about Groovy substitution strings?

                        offset++;
                        continue;
                    }

                case STATE_STRING_SINGLE_QUOTE:
                    {
                        if (c == '\\') {
                            offset += 2;
                            continue;
                        } else if (c == '\'') {
                            state = STATE_INITIAL;
                            offset++;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            continue;
                        }

                        offset++;
                        continue;
                    }

                case STATE_STRING_TRIPLE_DOUBLE_QUOTE:
                    {
                        if (c == '"' && source.startsWith("\"\"\"", offset)) {
                            offset += 3;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            state = STATE_INITIAL;
                            continue;
                        }
                        offset++;
                        continue;
                    }

                case STATE_STRING_TRIPLE_SINGLE_QUOTE:
                    {
                        if (c == '\'' && source.startsWith("'''", offset)) {
                            offset += 3;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            state = STATE_INITIAL;
                            continue;
                        }
                        offset++;
                        continue;
                    }

                default:
                    assert false : state;
            }
        }
    }

    private static boolean isGroovyKeyword(@NonNull String keyword) {
        if (isJavaKeyword(keyword)) {
            return true;
        }

        switch (keyword) {
            case "as":
            case "def":
            case "in":
            case "trait":
                return true;
        }
        return false;
    }

    private static boolean isAidlKeyword(@NonNull String keyword) {
        if (isJavaKeyword(keyword)) {
            return true;
        }

        switch (keyword) {
            case "flattenable":
            case "in":
            case "inout":
            case "oneway":
            case "out":
            case "parcelable":
            case "rpc":
                return true;
        }
        return false;
    }

    private static boolean isKotlinKeyword(@NonNull String keyword) {
        // From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
        switch (keyword) {
            case "package":
            case "as":
            case "typealias":
            case "class":
            case "this":
            case "super":
            case "val":
            case "var":
            case "fun":
            case "for":
            case "null":
            case "true":
            case "false":
            case "is":
            case "in":
            case "throw":
            case "return":
            case "break":
            case "continue":
            case "object":
            case "if":
            case "try":
            case "else":
            case "while":
            case "do":
            case "when":
            case "interface":
            case "typeof":
                return true;
        }

        return false;
    }

    private void tokenizeXml() {
        // Simple HTML tokenizer
        int length = source.length();
        final int STATE_TEXT = 1;
        final int STATE_SLASH = 2;
        final int STATE_ATTRIBUTE_NAME = 3;
        final int STATE_IN_TAG = 4;
        final int STATE_BEFORE_ATTRIBUTE = 5;
        final int STATE_ATTRIBUTE_BEFORE_EQUALS = 6;
        final int STATE_ATTRIBUTE_AFTER_EQUALS = 7;
        final int STATE_ATTRIBUTE_VALUE_NONE = 8;
        final int STATE_ATTRIBUTE_VALUE_SINGLE = 9;
        final int STATE_ATTRIBUTE_VALUE_DOUBLE = 10;
        final int STATE_CLOSE_TAG = 11;
        final int STATE_ENDING_TAG = 12;

        int state = STATE_TEXT;
        int offset = 0;
        int attributeStart = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;

            char c = source.charAt(offset);
            switch (state) {
                case STATE_TEXT:
                    {
                        if (c == '<') {
                            state = STATE_SLASH;
                            offset++;
                            continue;
                        }

                        // Other text is just ignored
                        offset++;
                        break;
                    }

                case STATE_SLASH:
                    {
                        if (c == '!') {
                            if (source.startsWith("!--", offset)) {
                                styles.put(offset - 1, STYLE_COMMENT);
                                // Comment
                                int end = source.indexOf("-->", offset + 3);
                                if (end == -1) {
                                    offset = length;
                                    styles.put(offset, STYLE_PLAIN_TEXT);
                                    break;
                                }
                                offset = end + 3;
                                styles.put(offset, STYLE_PLAIN_TEXT);
                                state = STATE_TEXT;
                                continue;
                            } else if (source.startsWith("![CDATA[", offset)) {
                                // TODO: Syntax higlight this better
                                //styles.put(offset - 1, STYLE_COMMENT);

                                // Skip CDATA text content; HTML text is irrelevant to this tokenizer
                                // anyway
                                int end = source.indexOf("]]>", offset + 8);
                                if (end == -1) {
                                    offset = length;
                                    break;
                                }
                                state = STATE_TEXT;
                                offset = end + 3;
                                continue;
                            }
                        } else if (c == '/') {
                            styles.put(offset - 1, STYLE_TAG);
                            state = STATE_CLOSE_TAG;
                            offset++;
                            continue;
                        } else if (c == '?') {
                            styles.put(offset - 1, STYLE_PROLOGUE);
                            // XML Prologue
                            int end = source.indexOf('>', offset + 2);
                            if (end == -1) {
                                offset = length;
                                state = STATE_TEXT;
                                break;
                            }
                            offset = end + 1;
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                            continue;
                        }
                        styles.put(offset - 1, STYLE_TAG);
                        state = STATE_IN_TAG;
                        break;
                    }

                case STATE_CLOSE_TAG:
                    {
                        if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        }
                        offset++;
                        break;
                    }

                case STATE_IN_TAG:
                    {
                        if (Character.isWhitespace(c)) {
                            state = STATE_BEFORE_ATTRIBUTE;
                            styles.put(offset, STYLE_ATTRIBUTE);
                        } else if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else if (c == '/') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_ENDING_TAG;
                        }
                        offset++;
                        break;
                    }

                case STATE_ENDING_TAG:
                    {
                        if (c == '>') {
                            offset++;
                            state = STATE_TEXT;
                        }
                        break;
                    }

                case STATE_BEFORE_ATTRIBUTE:
                    {
                        if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else //noinspection StatementWithEmptyBody
                        if (c == '/') {
                            // we expect an '>' next to close the tag
                        } else if (!Character.isWhitespace(c)) {
                            styles.put(offset, STYLE_ATTRIBUTE);
                            state = STATE_ATTRIBUTE_NAME;
                            attributeStart = offset;
                        }
                        offset++;
                        break;
                    }
                case STATE_ATTRIBUTE_NAME:
                    {
                        if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else if (c == '=') {
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            state = STATE_ATTRIBUTE_AFTER_EQUALS;
                        } else if (Character.isWhitespace(c)) {
                            styles.put(offset, STYLE_PLAIN_TEXT);
                            state = STATE_ATTRIBUTE_BEFORE_EQUALS;
                        } else if (c == ':') {
                            styles.put(attributeStart, STYLE_PREFIX);
                            styles.put(offset + 1, STYLE_ATTRIBUTE);
                        }
                        offset++;
                        break;
                    }
                case STATE_ATTRIBUTE_BEFORE_EQUALS:
                    {
                        if (c == '=') {
                            state = STATE_ATTRIBUTE_AFTER_EQUALS;
                        } else if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else if (!Character.isWhitespace(c)) {
                            // Attribute value not specified (used for some boolean attributes)
                            state = STATE_ATTRIBUTE_NAME;
                            attributeStart = offset;
                        }
                        offset++;
                        break;
                    }

                case STATE_ATTRIBUTE_AFTER_EQUALS:
                    {
                        if (c == '\'') {
                            // a='b'
                            styles.put(offset, STYLE_VALUE);
                            state = STATE_ATTRIBUTE_VALUE_SINGLE;
                        } else if (c == '"') {
                            // a="b"
                            styles.put(offset, STYLE_VALUE);
                            state = STATE_ATTRIBUTE_VALUE_DOUBLE;
                        } else if (!Character.isWhitespace(c)) {
                            // a=b
                            styles.put(offset, STYLE_VALUE);
                            state = STATE_ATTRIBUTE_VALUE_NONE;
                        }
                        offset++;
                        break;
                    }

                case STATE_ATTRIBUTE_VALUE_SINGLE:
                    {
                        if (c == '\'') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_BEFORE_ATTRIBUTE;
                        }
                        offset++;
                        break;
                    }
                case STATE_ATTRIBUTE_VALUE_DOUBLE:
                    {
                        if (c == '"') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_BEFORE_ATTRIBUTE;
                        }
                        offset++;
                        break;
                    }
                case STATE_ATTRIBUTE_VALUE_NONE:
                    {
                        if (c == '>') {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_TEXT;
                        } else if (Character.isWhitespace(c)) {
                            styles.put(offset + 1, STYLE_PLAIN_TEXT);
                            state = STATE_BEFORE_ATTRIBUTE;
                        }
                        offset++;
                        break;
                    }
                default:
                    assert false : state;
            }
        }
    }

    private void removeRepeatedStyleSpans() {
        int length = source.length();
        // Remove repeated entries
        int prevStyle = -1;
        for (Integer o : sortedOffsets) {
            int style = styles.get(o);
            if (prevStyle == style && length != o) {
                styles.remove(o);
            } else {
                prevStyle = style;
            }
        }
        updateSortedOffsets();
    }

    private void updateSortedOffsets() {
        sortedOffsets = Lists.newArrayList(styles.keySet());
        Collections.sort(sortedOffsets);
    }

    public void initializeLineNumberMap() {
        lineNumbers.put(0, 0);

        int lineNumber = 0;
        for (int offset = 0; offset < source.length(); offset++) {
            char c = source.charAt(offset);
            if (c == '\n') {
                lineNumber++;
                lineNumbers.put(offset + 1, lineNumber);
            }
        }
        lineCount = lineNumber + 1;
    }

    private int getLineNumber(int offset) {
        int lineStart = findLineStartOffset(offset);
        Integer line = lineNumbers.get(lineStart);
        assert line != null : lineStart;
        return line;
    }

    private void insertRedundantMarker(int offset) {
        for (int o = 1; o < sortedOffsets.size(); o++) {
            int begin = sortedOffsets.get(o - 1);
            int end = sortedOffsets.get(o);
            if (offset == begin || offset == end) {
                return;
            } else if (offset < end) {
                int style = styles.get(begin);
                styles.put(offset, style);
                return;
            }
        }
    }

    private int findLineStartOffset(int offset) {
        if (offset < 0) {
            return 0;
        }
        if (offset >= source.length()) {
            offset = source.length();
        }
        while (--offset >= 0) {
            char c = source.charAt(offset);
            if (c == '\n') {
                break;
            }
        }

        return offset + 1;
    }

    private int findLineEndOffset(int offset) {
        int length = source.length();
        while (offset < length) {
            char c = source.charAt(offset);
            if (c == '\n') {
                break;
            }
            offset++;
        }

        return Math.min(offset, length);
    }

    public int computeDedent(int fromOffset, int toOffset) {
        toOffset = Math.min(source.length(), toOffset);
        fromOffset = Math.min(toOffset, fromOffset);

        boolean inWhitespace = true;
        int lineBegin = fromOffset;
        int dedent = Integer.MAX_VALUE;
        int column = 0;
        for (int i = fromOffset; i < toOffset; i++, column++) {
            char c = source.charAt(i);
            if (c == '\n') {
                inWhitespace = true;
                column = -1;
            } else if (c == '\t') {
                column += TAB_WIDTH - 1;
            } else if (!Character.isWhitespace(c)) {
                if (inWhitespace) {
                    dedent = Math.min(dedent, column);
                    inWhitespace = false;
                }
            }
        }

        if (dedent == Integer.MAX_VALUE || dedent == 0) {
            return 0;
        } else {
            return dedent - 1; // leave at least one padding character
        }
    }

    public int computeMaxLineLength(int fromOffset, int toOffset) {
        toOffset = Math.min(source.length(), toOffset);
        fromOffset = Math.min(toOffset, fromOffset);

        int maxLineLength = 0;
        int column = 0;
        for (int i = fromOffset; i < toOffset; i++, column++) {
            char c = source.charAt(i);
            if (c == '\t') {
                column += TAB_WIDTH - 1;
            } else if (c == '\n') {
                maxLineLength = Math.max(maxLineLength, column);
                column = -1;
            }
        }

        maxLineLength = Math.max(maxLineLength, column);
        return maxLineLength;
    }

    public void generateHtml(
            @NonNull HtmlBuilder builder,
            int startHighlightOffset,
            int endHighlightOffset,
            boolean error) {
        // From previous highlight runs
        removeRepeatedStyleSpans();

        int caretLineOffset = findLineStartOffset(startHighlightOffset);
        int caretLineEndOffset = findLineEndOffset(startHighlightOffset);
        insertRedundantMarker(caretLineEndOffset);

        if (endHighlightOffset == -1) {
            endHighlightOffset = caretLineEndOffset;
        }

        // Limit error ranges to a single line
        if (forceSingleLineRange && caretLineEndOffset < endHighlightOffset) {
            endHighlightOffset = caretLineEndOffset;
        }

        // Redundant markers around offsets and highlights ensures that we will have
        // span breakpoints where we need them (e.g. to insert a span around the error
        // range, and to have line numbers inserted at each new line
        insertRedundantMarker(startHighlightOffset);
        insertRedundantMarker(endHighlightOffset);

        // Figure out window offsets
        int beginOffset = findLineStartOffset(startHighlightOffset);
        int endOffset = findLineEndOffset(endHighlightOffset);

        insertRedundantMarker(beginOffset);
        insertRedundantMarker(endOffset + 1);

        for (int i = 0; i < CODE_WINDOW_SIZE; i++) {
            beginOffset = findLineStartOffset(beginOffset - 1);
            insertRedundantMarker(beginOffset);
        }
        for (int i = 0; i < CODE_WINDOW_SIZE; i++) {
            endOffset = findLineEndOffset(endOffset + 1);
            insertRedundantMarker(endOffset + 1);
        }

        int currentLine = getLineNumber(beginOffset) + 1; // display as 1-based instead of 0-based
        int lineWidth = (int) (Math.log10(lineCount)) + 1;
        updateSortedOffsets();

        builder.beginPre("errorlines");

        int dedent = this.dedent ? computeDedent(beginOffset, endOffset) : 0;
        int maxWidth = computeMaxLineLength(beginOffset, endOffset);
        int available = 96;
        if (available - maxWidth > 0) {
            dedent = Math.max(0, dedent - (available - maxWidth));
        }
        int cropTo = 0;

        int spanBalance = 0;

        for (int o = 1; o < sortedOffsets.size(); o++) {
            int begin = sortedOffsets.get(o - 1);
            int end = sortedOffsets.get(o);

            if (end <= beginOffset || begin >= endOffset) {
                continue;
            }

            if (begin == caretLineOffset) {
                builder.beginClassSpan("caretline");
                spanBalance++;
            }

            boolean isNewLine = begin == 0 || source.charAt(begin - 1) == '\n';
            if (isNewLine) {
                // new line
                builder.beginClassSpan("lineno");
                String lineString =
                        String.format(Locale.ROOT, " %" + lineWidth + "d ", currentLine++);
                builder.addHtml(lineString);
                builder.endSpan();

                if (dedent > 0) {
                    cropTo = begin;
                    int column = 0;
                    for (int s = begin, n = Math.min(s + dedent, source.length()); s < n; s++) {
                        char c = source.charAt(s);
                        if (c == '\t') {
                            column += TAB_WIDTH;
                        } else if (c == '\n') {
                            cropTo = s;
                        } else if (Character.isWhitespace(c)) {
                            column++;
                            if (column >= dedent) {
                                cropTo = s;
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }

            int style = styles.get(begin);

            if (begin == startHighlightOffset) {
                builder.beginClassSpan(error ? "error" : "warning");
                spanBalance++;
            }

            String cssStyle = getStyleClass(style);
            if (cssStyle != null) {
                builder.beginClassSpan(cssStyle);
                spanBalance++;
            }

            int from = Math.max(cropTo, begin);
            if (from < end) {
                add(builder, from, end);
            }

            //noinspection VariableNotUsedInsideIf
            if (cssStyle != null) {
                builder.endSpan();
                spanBalance--;
            }

            if (end == endHighlightOffset) {
                // Remove the *extra* span for errors
                builder.endSpan();
                spanBalance--;
            }

            if (end == caretLineEndOffset) {
                // Insert spaces to ensure we highlight the whole line
                if (padCaretLine) {
                    builder.addNbsps(100 - (caretLineEndOffset - caretLineOffset));
                }
                builder.endSpan();
                spanBalance--;
            }
        }

        if (spanBalance != 0) {
            // This should not happen. Safety measure.
            for (int i = 0; i < spanBalance; i++) {
                builder.endSpan();
            }
        }

        builder.endPre();

        int lastOffset = sortedOffsets.get(sortedOffsets.size() - 1);
        add(builder, lastOffset, source.length());
    }

    void add(HtmlBuilder builder, int from, int to) {
        String substring = source.substring(from, to);
        if (substring.indexOf('\t') != -1) {
            //noinspection ConstantConditions
            assert TAB_WIDTH == 4; // if not change constant below
            substring = substring.replace("\t", "    ");
        }
        builder.add(substring);
    }

    /**
     * Returns a style class for a given style that matches the styles available in {@link
     * HtmlReporter#CSS_STYLES}
     */
    @Nullable
    private static String getStyleClass(int style) {
        String cssStyle = "";
        switch (style) {
            case STYLE_PLAIN_TEXT:
                cssStyle = null;
                break;
            case STYLE_TAG:
                cssStyle = "tag";
                break;
            case STYLE_PREFIX:
                cssStyle = "prefix";
                break;
            case STYLE_ATTRIBUTE:
                cssStyle = "attribute";
                break;
            case STYLE_VALUE:
                cssStyle = "value";
                break;
            case STYLE_COMMENT:
                cssStyle = "comment";
                break;
            case STYLE_PROLOGUE:
                cssStyle = "prologue";
                break;
            case STYLE_CDATA:
                cssStyle = "cdata";
                break;
            case STYLE_STRING:
                cssStyle = "string";
                break;
            case STYLE_NUMBER:
                cssStyle = "number";
                break;
            case STYLE_KEYWORD:
                cssStyle = "keyword";
                break;
            case STYLE_JAVADOC_COMMENT:
                cssStyle = "javadoc";
                break;
            case STYLE_ANNOTATION:
                cssStyle = "annotation";
                break;
            default:
                assert false : style;
        }
        return cssStyle;
    }
}
