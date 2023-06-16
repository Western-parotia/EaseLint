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
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

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
            // I/O error - returns "" instead of null
            throw new IOException();
        }

        return PositionXmlParser.parse(xml.toString());
    }

    @Override
    public Document parseXml(@NonNull CharSequence xml, @Nullable File file) {
        try {
            return PositionXmlParser.parse(xml.toString());
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
                return PositionXmlParser.parse(xml);
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

    @Override
    @NonNull
    public Location getNameLocation(@NonNull XmlContext context, @NonNull Node node) {
        Location location = getLocation(context, node);
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
    public Location getValueLocation(@NonNull XmlContext context, @NonNull Attr node) {
        Location location = getLocation(context, node);
        Position start = location.getStart();
        Position end = location.getEnd();
        if (start == null || end == null) {
            return location;
        }
        int totalLength = end.getOffset() - start.getOffset();
        int length = node.getValue().length();
        int delta = totalLength - 1 - length;
        int startOffset = start.getOffset() + delta;
        int startColumn = start.getColumn() + delta;
        return Location.create(
                        location.getFile(),
                        new DefaultPosition(start.getLine(), startColumn, startOffset),
                        new DefaultPosition(
                                end.getLine(), startColumn + length, startOffset + length))
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

    /* Handle for creating DOM positions cheaply and returning full fledged locations later */
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
    }
}
