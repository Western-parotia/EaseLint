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

import static com.android.utils.PositionXmlParser.CONTENT_KEY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Charsets;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.ParserConfigurationException;

import kotlin.io.FilesKt;
import kotlin.text.StringsKt;

/**
 * A customization of the {@link PositionXmlParser} which creates position objects that directly
 * extend the lint {@link com.android.tools.lint.detector.api.Position} class.
 *
 * <p>It also catches and reports parser errors as lint errors.
 */
public class LintCliXmlParser extends XmlParser {
    private final LintClient client;

    public LintCliXmlParser(@NonNull LintClient client) {
        this.client = client;
    }

    @NonNull
    @Override
    public Document parseXml(@NonNull File file)
            throws IOException, SAXException, ParserConfigurationException {
        CharSequence xml = client.readFile(file);
        if (xml.length() == 0) {
            // client.readFile doesn't throw an exception on I/O error, it returns "".
            // Try reading it again, this time possibly getting the IO exception which we'll
            // pass on to the callers.
            xml = FilesKt.readText(file, Charsets.UTF_8);
            if (StringsKt.isBlank(xml)) {
                // SAX would eventually throw an exception anyway, but it's much less readable
                throw new SAXException("XML file is empty; not a valid document: " + file);
            }
        }

        Document document = PositionXmlParser.parse(xml.toString());
        document.setUserData(File.class.getName(), file, null);
        return document;
    }

    @Override
    public Document parseXml(@NonNull CharSequence xml, @Nullable File file) {
        try {
            Document document = PositionXmlParser.parse(xml.toString());
            document.setUserData(File.class.getName(), file, null);
            return document;
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public Document parseXml(@NonNull XmlContext context) {
        String xml = null;
        try {
            // Do we need to provide an input stream for encoding?
            CharSequence contents = context.getContents();
            if (contents != null) {
                xml = contents.toString();
                Document document = PositionXmlParser.parse(xml);
                document.setUserData(File.class.getName(), context.file, null);
                return document;
            }
        } catch (UnsupportedEncodingException e) {
            context.report(
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    IssueRegistry.PARSER_ERROR,
                    Location.create(context.file),
                    e.getCause() != null
                            ? e.getCause().getLocalizedMessage()
                            : e.getLocalizedMessage());
        } catch (SAXException e) {
            Location location = Location.create(context.file);
            String message =
                    e.getCause() != null
                            ? e.getCause().getLocalizedMessage()
                            : e.getLocalizedMessage();
            if (message.startsWith(
                    "The processing instruction target matching "
                            + "\"[xX][mM][lL]\" is not allowed.")) {
                int prologue = xml.indexOf("<?xml ");
                int comment = xml.indexOf("<!--");
                if (prologue != -1 && comment != -1 && comment < prologue) {
                    message =
                            "The XML prologue should appear before, not after, the first XML "
                                    + "header/copyright comment. "
                                    + message;
                }
            }
            context.report(
                    // Must provide an issue since API guarantees that the issue parameter
                    // is valid
                    IssueRegistry.PARSER_ERROR, location, message);
        } catch (Throwable t) {
            context.log(t, null);
        }
        return null;
    }

    @NonNull
    @Override
    public Location getLocation(@NonNull XmlContext context, @NonNull Node node) {
        return getLocation(context.file, node);
    }

    @NonNull
    @Override
    public Location getLocation(@NonNull File file, @NonNull Node node) {
        Pair<File, ? extends Node> mergedSource = findManifestSource(node);
        if (mergedSource != null) {
            file = mergedSource.getFirst();
            node = mergedSource.getSecond();
        }

        return Location.create(file, PositionXmlParser.getPosition(node)).withSource(node);
    }

    @NonNull
    @Override
    public Location getLocation(
            @NonNull XmlContext context, @NonNull Node node, int start, int end) {
        File file = context.file;
        Pair<File, ? extends Node> mergedSource = findManifestSource(node);
        if (mergedSource != null) {
            file = mergedSource.getFirst();
            node = mergedSource.getSecond();
        }

        return Location.create(file, PositionXmlParser.getPosition(node, start, end))
                .withSource(node);
    }

    @Nullable
    private Pair<File, ? extends Node> findManifestSource(@NonNull Node node) {
        if (client.isMergeManifestNode(node)) {
            return client.findManifestSourceNode(node);
        }
        return null;
    }

    @NonNull
    @Override
    public Location getNameLocation(
            @NonNull LintClient client, @NonNull File file, @NonNull Node node) {
        Location location = getLocation(client, file, node);
        Position start = location.getStart();
        Position end = location.getEnd();
        if (start == null || end == null) {
            return location;
        }
        int delta = node instanceof Element ? 1 : 0; // Elements: skip "<"
        int length = node.getNodeName().length();
        int startOffset = start.getOffset() + delta;
        int startColumn = start.getColumn() + delta;
        return Location.create(
                        location.getFile(),
                        new DefaultPosition(start.getLine(), startColumn, startOffset),
                        new DefaultPosition(
                                start.getLine(), startColumn + length, startOffset + length))
                .withSource(node);
    }

    @Override
    @NonNull
    public Location getNameLocation(@NonNull XmlContext context, @NonNull Node node) {
        return getNameLocation(client, context.file, node);
    }

    @Override
    @NonNull
    public Location getValueLocation(@NonNull XmlContext context, @NonNull Attr node) {
        return getValueLocation(client, context.file, node);
    }

    @NonNull
    @Override
    public Location getValueLocation(
            @NonNull LintClient client, @NonNull File file, @NonNull Attr node) {
        Location location = getLocation(client, file, node);
        Position start = location.getStart();
        Position end = location.getEnd();
        if (start == null || end == null) {
            return location;
        }

        String contents = (String) node.getOwnerDocument().getUserData(CONTENT_KEY);
        if (contents == null) {
            return location;
        }

        // With access to the source, look at the source to figure out the true
        // range; we can't just look at the value string and take its length since
        // in source form you can refer to characters by entities or html escapes,
        // so if the value is for example "a > b" we don't know if the source is
        // "a > b" or "a &gt; b", and they have different lengths.
        int valueStartOffset = start.getOffset();
        int documentLength = contents.length();
        while (valueStartOffset < documentLength) {
            char c = contents.charAt(valueStartOffset++);
            if (c == '"' || c == '\'') {
                break;
            }
        }
        char valueQuote = contents.charAt(valueStartOffset - 1); // " or '
        int valueEndOffset = valueStartOffset + 1;
        while (contents.charAt(valueEndOffset) != valueQuote) {
            valueEndOffset++;
        }
        int lineStart = contents.lastIndexOf('\n', valueStartOffset) + 1;
        // We can't have newlines in the value attribute: begins and ends on the same
        // line
        int line = start.getLine();
        int startColumn = valueStartOffset - lineStart;
        int endColumn = valueEndOffset - lineStart;
        return Location.create(
                        location.getFile(),
                        new DefaultPosition(line, startColumn, valueStartOffset),
                        new DefaultPosition(line, endColumn, valueEndOffset))
                .withSource(node);
    }

    @NonNull
    @Override
    public Handle createLocationHandle(@NonNull XmlContext context, @NonNull Node node) {
        return new LocationHandle(this, context.file, node);
    }

    @Override
    public int getNodeStartOffset(@NonNull XmlContext context, @NonNull Node node) {
        Pair<File, ? extends Node> mergedSource = findManifestSource(node);
        if (mergedSource != null) {
            node = mergedSource.getSecond();
        }
        return PositionXmlParser.getPosition(node).getStartOffset();
    }

    @Override
    public int getNodeEndOffset(@NonNull XmlContext context, @NonNull Node node) {
        Pair<File, ? extends Node> mergedSource = findManifestSource(node);
        if (mergedSource != null) {
            node = mergedSource.getSecond();
        }
        return PositionXmlParser.getPosition(node).getEndOffset();
    }

    @Nullable
    @Override
    public Node findNodeAt(@NonNull XmlContext context, int offset) {
        return PositionXmlParser.findNodeAtOffset(context.document, offset);
    }

    @NonNull
    @Override
    public Location getLocation(
            @NonNull LintClient client, @NonNull File file, @NonNull Node node) {
        return getLocation(file, node);
    }

    @Override
    public int getNodeStartOffset(
            @NonNull LintClient client, @NonNull File file, @NonNull Node node) {
        return PositionXmlParser.getPosition(node).getStartOffset();
    }

    @Override
    public int getNodeEndOffset(
            @NonNull LintClient client, @NonNull File file, @NonNull Node node) {
        return PositionXmlParser.getPosition(node).getEndOffset();
    }

    /* Handle for creating DOM positions cheaply and returning full-fledged locations later */
    private static class LocationHandle implements Handle {
        private final LintCliXmlParser parser;
        private final File file;
        private final Node node;
        private Object clientData;

        public LocationHandle(LintCliXmlParser parser, File file, Node node) {
            this.parser = parser;
            this.file = file;
            this.node = node;
        }

        @NonNull
        @Override
        public Location resolve() {
            Node node = this.node;
            File file = this.file;
            Pair<File, ? extends Node> source = parser.findManifestSource(node);
            if (source != null) {
                file = source.getFirst();
                node = source.getSecond();
            }
            return Location.create(file, PositionXmlParser.getPosition(node)).withSource(node);
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
            this.clientData = clientData;
        }

        @Override
        @Nullable
        public Object getClientData() {
            return clientData;
        }

        @Override
        public String toString() {
            return "LocationHandle{" + resolve() + "}";
        }
    }
}
