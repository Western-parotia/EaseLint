/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.GradleScanner;
import com.android.tools.lint.detector.api.Location;
import com.android.utils.Pair;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link GradleDetector} using a real Groovy AST, which the Gradle plugin has
 * access to.
 */
public class GroovyGradleVisitor extends GradleVisitor {
    @Override
    public void visitBuildScript(
            @NonNull GradleContext context, @NonNull List<? extends GradleScanner> detectors) {
        try {
            visitQuietly(context, detectors);
        } catch (AssertionError e) {
            // Test infrastructure checks
            if (LintClient.isUnitTest()) {
                throw e;
            }
        } catch (Throwable t) {
            // else: ignore
            // Parsing the build script can involve class loading that we sometimes can't
            // handle. This happens for example when running lint in build-system/tests/api/.
            // This is a lint limitation rather than a user error, so don't complain
            // about these. Consider reporting a Issue#LINT_ERROR.
        }
    }

    private static void visitQuietly(
            @NonNull final GradleContext context,
            @NonNull final List<? extends GradleScanner> detectors) {
        CharSequence sequence = context.getContents();
        if (sequence == null) {
            return;
        }

        final String source = sequence.toString();
        List<ASTNode> astNodes = new AstBuilder().buildFromString(source);
        GroovyCodeVisitor visitor =
                new CodeVisitorSupport() {
                    private final List<MethodCallExpression> mMethodCallStack = new ArrayList<>();

                    @Override
                    public void visitMethodCallExpression(MethodCallExpression expression) {
                        mMethodCallStack.add(expression);
                        super.visitMethodCallExpression(expression);
                        assert !mMethodCallStack.isEmpty();
                        assert mMethodCallStack.get(mMethodCallStack.size() - 1) == expression;
                        mMethodCallStack.remove(mMethodCallStack.size() - 1);
                    }

                    @Override
                    public void visitBinaryExpression(BinaryExpression expression) {
                        if (expression.getOperation().getText().equals("=")) {
                            Expression leftExpression = expression.getLeftExpression();
                            List<String> hierarchy = getPropertyHierarchy(leftExpression);
                            String property = null;
                            String parent = null;
                            String parentParent = null;
                            if (hierarchy != null && hierarchy.size() > 0) {
                                property = hierarchy.get(0);
                                if (hierarchy.size() == 2) {
                                    parent = hierarchy.get(1);
                                    if (!mMethodCallStack.isEmpty()) {
                                        parentParent = getParent();
                                    }
                                } else if (hierarchy.size() > 2) {
                                    parent = hierarchy.get(1);
                                    parentParent = hierarchy.get(2);
                                } else if (!mMethodCallStack.isEmpty()) {
                                    parent = getParent();
                                    parentParent = getParentN(2);
                                }
                                maybeCheckDslProperty(property, parent, parentParent, expression);
                            }
                        }
                        super.visitBinaryExpression(expression);
                    }

                    @Override
                    public void visitTupleExpression(TupleExpression tupleExpression) {
                        if (!mMethodCallStack.isEmpty()) {
                            MethodCallExpression call =
                                    mMethodCallStack.get(mMethodCallStack.size() - 1);
                            if (call.getArguments() == tupleExpression) {
                                String parent = getParent();
                                String parentParent = getParentN(2);
                                String parent3 = getParentN(3);
                                Map<String, String> namedArguments = new HashMap<>();
                                List<String> unnamedArguments = new ArrayList<>();
                                extractMethodCallArguments(
                                        tupleExpression, unnamedArguments, namedArguments);
                                if (tupleExpression instanceof ArgumentListExpression) {
                                    ArgumentListExpression ale =
                                            (ArgumentListExpression) tupleExpression;
                                    List<Expression> expressions = ale.getExpressions();
                                    if (expressions.size() == 1
                                            && expressions.get(0) instanceof ClosureExpression) {
                                        // a pure block has its effect recorded in mMethodCallStack
                                        // but may need to be inspected for deprecations
                                        maybeCheckMethodCall(
                                                parent,
                                                parentParent,
                                                parent3,
                                                unnamedArguments,
                                                namedArguments,
                                                call);
                                    } else {
                                        maybeCheckMethodCall(
                                                parent,
                                                parentParent,
                                                parent3,
                                                unnamedArguments,
                                                namedArguments,
                                                call);
                                        maybeCheckDslProperty(
                                                parent,
                                                parentParent,
                                                parent3,
                                                unnamedArguments,
                                                namedArguments,
                                                call);
                                    }
                                } else {
                                    maybeCheckMethodCall(
                                            parent,
                                            parentParent,
                                            parent3,
                                            unnamedArguments,
                                            namedArguments,
                                            call);
                                    maybeCheckDslProperty(
                                            parent,
                                            parentParent,
                                            parent3,
                                            unnamedArguments,
                                            namedArguments,
                                            call);
                                }
                            }
                        }

                        super.visitTupleExpression(tupleExpression);
                    }

                    private void extractMethodCallArguments(
                            TupleExpression tupleExpression,
                            List<String> unnamedArguments,
                            Map<String, String> namedArguments) {
                        for (Expression subExpr : tupleExpression.getExpressions()) {
                            if (subExpr instanceof NamedArgumentListExpression) {
                                NamedArgumentListExpression nale =
                                        (NamedArgumentListExpression) subExpr;
                                for (MapEntryExpression mae : nale.getMapEntryExpressions()) {
                                    namedArguments.put(
                                            mae.getKeyExpression().getText(),
                                            mae.getValueExpression().getText());
                                }
                            } else {
                                unnamedArguments.add(getText(subExpr));
                            }
                        }
                    }

                    private String getParent() {
                        return getParentN(1);
                    }

                    private String getParentN(int n) {
                        int nParent = 0;
                        MethodCallExpression methodCallExpression =
                                mMethodCallStack.get(mMethodCallStack.size() - 1);
                        List<String> hierarchy = getMethodCallHierarchy(methodCallExpression);
                        if (hierarchy == null) {
                            return null;
                        }
                        if (n - nParent - 1 < hierarchy.size()) {
                            return hierarchy.get(n - nParent - 1);
                        }
                        nParent += hierarchy.size();
                        for (int i = mMethodCallStack.size() - 2; i >= 0; i--) {
                            methodCallExpression = mMethodCallStack.get(i);
                            Expression arguments = methodCallExpression.getArguments();
                            if (arguments instanceof ArgumentListExpression) {
                                ArgumentListExpression ale = (ArgumentListExpression) arguments;
                                List<Expression> expressions = ale.getExpressions();
                                if (expressions.size() == 1
                                        && expressions.get(0) instanceof ClosureExpression) {
                                    hierarchy = getMethodCallHierarchy(methodCallExpression);
                                    if (hierarchy == null) {
                                        return null;
                                    }
                                    if (n - nParent - 1 < hierarchy.size()) {
                                        return hierarchy.get(n - nParent - 1);
                                    }
                                    nParent += hierarchy.size();
                                }
                            }
                        }
                        return null;
                    }

                    private List<String> getMethodCallHierarchy(
                            MethodCallExpression methodCallExpression) {
                        ArrayList<String> result = new ArrayList<>();
                        result.add(methodCallExpression.getMethodAsString());
                        Expression expression = methodCallExpression.getObjectExpression();
                        while (true) {
                            if (expression instanceof VariableExpression) {
                                VariableExpression variableExpr = (VariableExpression) expression;
                                if (!variableExpr.isThisExpression()) {
                                    result.add(variableExpr.getName());
                                }
                                break;
                            } else if (expression instanceof PropertyExpression) {
                                PropertyExpression propertyExpression =
                                        (PropertyExpression) expression;
                                result.add(propertyExpression.getPropertyAsString());
                                expression = propertyExpression.getObjectExpression();
                            } else {
                                return null;
                            }
                        }
                        return result;
                    }

                    private List<String> getPropertyHierarchy(
                            Expression propertyOrVariableExpression) {
                        ArrayList<String> result = new ArrayList<>();
                        if (propertyOrVariableExpression instanceof VariableExpression) {
                            VariableExpression variableExpression =
                                    (VariableExpression) propertyOrVariableExpression;
                            result.add(variableExpression.getName());
                            return result;
                        } else if (!(propertyOrVariableExpression instanceof PropertyExpression)) {
                            return null;
                        }
                        PropertyExpression propertyExpression =
                                (PropertyExpression) propertyOrVariableExpression;
                        result.add(propertyExpression.getPropertyAsString());
                        Expression expression = propertyExpression.getObjectExpression();
                        while (true) {
                            if (expression instanceof VariableExpression) {
                                VariableExpression variableExpr = (VariableExpression) expression;
                                if (!variableExpr.isThisExpression()) {
                                    result.add(variableExpr.getName());
                                }
                                break;
                            } else if (expression instanceof PropertyExpression) {
                                propertyExpression = (PropertyExpression) expression;
                                result.add(propertyExpression.getPropertyAsString());
                                expression = propertyExpression.getObjectExpression();
                            } else {
                                return null;
                            }
                        }
                        return result;
                    }

                    private void maybeCheckMethodCall(
                            @Nullable String methodName,
                            @Nullable String parent,
                            @Nullable String parentParent,
                            List<String> unnamedArguments,
                            Map<String, String> namedArguments,
                            MethodCallExpression call) {
                        if (methodName != null) {
                            for (GradleScanner scanner : detectors) {
                                scanner.checkMethodCall(
                                        context,
                                        methodName,
                                        parent,
                                        parentParent,
                                        namedArguments,
                                        unnamedArguments,
                                        call);
                            }
                        }
                    }

                    private void maybeCheckDslProperty(
                            @Nullable String property,
                            @Nullable String parent,
                            @Nullable String parentParent,
                            List<String> unnamedArguments,
                            Map<String, String> namedArguments,
                            MethodCallExpression c) {
                        if (property != null && parent != null) {
                            String value = null;
                            if (unnamedArguments.size() == 1 && namedArguments.size() == 0) {
                                value = unnamedArguments.get(0);
                            } else if (unnamedArguments.size() == 0 && namedArguments.size() > 0) {
                                value = getText(c.getArguments());
                            }
                            if (value != null) {
                                for (GradleScanner scanner : detectors) {
                                    scanner.checkDslPropertyAssignment(
                                            context,
                                            property,
                                            value,
                                            parent,
                                            parentParent,
                                            c.getMethod(),
                                            c.getArguments(),
                                            c);
                                }
                            }
                        }
                    }

                    private void maybeCheckDslProperty(
                            @NonNull String property,
                            @Nullable String parent,
                            @Nullable String parentParent,
                            BinaryExpression b) {
                        Expression rightExpression = b.getRightExpression();
                        if (parent == null) parent = "";
                        if (rightExpression != null) {
                            String value = getText(rightExpression);
                            for (GradleScanner scanner : detectors) {
                                scanner.checkDslPropertyAssignment(
                                        context,
                                        property,
                                        value,
                                        parent,
                                        parentParent,
                                        b.getLeftExpression(),
                                        b,
                                        b);
                            }
                        }
                    }

                    private String getText(ASTNode node) {
                        Pair<Integer, Integer> offsets = getOffsets(node, context);
                        return source.substring(offsets.getFirst(), offsets.getSecond());
                    }
                };

        for (ASTNode node : astNodes) {
            node.visit(visitor);
        }
    }

    @NonNull
    private static Pair<ASTNode, ASTNode> getBoundingNodes(ASTNode node) {
        if (node.getLastLineNumber() == -1 && node instanceof TupleExpression) {
            // This TupleExpression is not real, probably corresponding to a named argument
            // list.  Use its children instead.
            TupleExpression expression = (TupleExpression) node;
            List<Expression> expressions = expression.getExpressions();
            if (!expressions.isEmpty()) {
                return Pair.of(
                        getBoundingNodes(expressions.get(0)).getFirst(),
                        getBoundingNodes(expressions.get(expressions.size() - 1)).getSecond());
            }
        }
        if (node instanceof ArgumentListExpression) {
            // An ArgumentListExpression's bounds can extend beyond the last expression in the
            // argument list, for example into trailing whitespace or comments.  The bounds given
            // by the first and last subexpressions are more appropriate for highlighting issues
            // related to user code.
            ArgumentListExpression expression = (ArgumentListExpression) node;
            List<Expression> expressions = expression.getExpressions();
            if (!expressions.isEmpty()) {
                return Pair.of(
                        getBoundingNodes(expressions.get(0)).getFirst(),
                        getBoundingNodes(expressions.get(expressions.size() - 1)).getSecond());
            }
        }

        return Pair.of(node, node);
    }

    @NonNull
    private static Pair<Integer, Integer> getOffsets(ASTNode node, Context context) {
        Pair<ASTNode, ASTNode> boundingNodes = getBoundingNodes(node);
        ASTNode startNode = boundingNodes.getFirst();
        ASTNode endNode = boundingNodes.getSecond();

        CharSequence source = context.getContents();
        assert source != null; // because we successfully parsed
        int start = 0;
        int end = source.length();
        int line = 1;
        int startLine = startNode.getLineNumber();
        int startColumn = startNode.getColumnNumber();
        int endLine = endNode.getLastLineNumber();
        int endColumn = endNode.getLastColumnNumber();
        int column = 1;
        for (int index = 0, len = end; index < len; index++) {
            if (line == startLine && column == startColumn) {
                start = index;
            }
            if (line == endLine && column == endColumn) {
                end = index;
                break;
            }

            char c = source.charAt(index);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        return Pair.of(start, end);
    }

    @Override
    public int getStartOffset(@NonNull GradleContext context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<Integer, Integer> offsets = getOffsets(node, context);
        return offsets.getFirst();
    }

    @NonNull
    @Override
    public Location createLocation(@NonNull GradleContext context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<ASTNode, ASTNode> boundingNodes = getBoundingNodes(node);
        ASTNode startNode = boundingNodes.getFirst();
        ASTNode endNode = boundingNodes.getSecond();

        Pair<Integer, Integer> offsets = getOffsets(node, context);
        int fromLine = startNode.getLineNumber() - 1;
        int fromColumn = startNode.getColumnNumber() - 1;
        int toLine = endNode.getLastLineNumber() - 1;
        int toColumn = endNode.getLastColumnNumber() - 1;
        return Location.create(
                context.file,
                new DefaultPosition(fromLine, fromColumn, offsets.getFirst()),
                new DefaultPosition(toLine, toColumn, offsets.getSecond()));
    }
}
