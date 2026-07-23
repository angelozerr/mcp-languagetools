/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package com.ibm.mcp.jdtls.handlers.refactoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditVisitor;

/**
 * Base class for refactoring handlers that delegate to the JDT LTK refactoring engine.
 *
 * <p>Subclasses configure and create a {@link Refactoring}, then call
 * {@link #executeRefactoring(Refactoring, IProgressMonitor)} which checks conditions,
 * creates the change, and converts it to the MCP edit format.</p>
 */
public abstract class AbstractLTKRefactoringHandler extends AbstractRefactoringHandler {

    /**
     * Execute an LTK refactoring in preview mode (changes are NOT applied to disk).
     */
    protected Map<String, Object> executeRefactoring(Refactoring refactoring, IProgressMonitor monitor)
            throws CoreException {
        return executeRefactoring(refactoring, false, monitor);
    }

    /**
     * Execute an LTK refactoring, reading the "apply" flag from the params map.
     */
    protected Map<String, Object> executeRefactoring(Refactoring refactoring, Map<String, Object> params,
            IProgressMonitor monitor) throws CoreException {
        return executeRefactoring(refactoring, isApply(params), monitor);
    }

    /**
     * Execute an LTK refactoring and return the result in MCP edit format.
     *
     * @param refactoring the LTK refactoring to execute
     * @param apply       if true, apply changes to disk; if false, return preview only
     * @param monitor     the progress monitor
     * @return the result map with edits and applied status
     */
    protected Map<String, Object> executeRefactoring(Refactoring refactoring, boolean apply,
            IProgressMonitor monitor) throws CoreException {
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        RefactoringStatus initialStatus = refactoring.checkInitialConditions(monitor);
        if (initialStatus.hasFatalError()) {
            return createErrorResult("Refactoring precondition failed: " + initialStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }

        RefactoringStatus finalStatus = refactoring.checkFinalConditions(monitor);
        if (finalStatus.hasFatalError()) {
            return createErrorResult("Refactoring validation failed: " + finalStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL));
        }

        Change change = refactoring.createChange(monitor);
        if (change == null) {
            return createErrorResult("Refactoring produced no changes");
        }

        List<Map<String, Object>> edits = convertChangeToEdits(change);

        if (apply && !edits.isEmpty()) {
            change.perform(monitor);
        }

        return createSuccessResult(edits, apply);
    }

    /**
     * Convert an LTK {@link Change} tree into a flat list of MCP edit maps.
     */
    protected List<Map<String, Object>> convertChangeToEdits(Change change) {
        List<Map<String, Object>> edits = new ArrayList<>();
        collectEdits(change, edits);
        return edits;
    }

    private void collectEdits(Change change, List<Map<String, Object>> edits) {
        if (change instanceof CompositeChange) {
            for (Change child : ((CompositeChange) change).getChildren()) {
                collectEdits(child, edits);
            }
        } else if (change instanceof TextChange) {
            TextChange textChange = (TextChange) change;
            TextEdit edit = textChange.getEdit();
            if (edit == null) {
                return;
            }

            String uri = getChangeUri(change);
            if (uri == null) {
                return;
            }

            try {
                String currentContent = textChange.getCurrentContent(edit.getRegion(), true, 0, new NullProgressMonitor());
                collectTextEdits(edit, uri, currentContent, edits);
            } catch (CoreException e) {
                collectTextEdits(edit, uri, null, edits);
            }
        }
    }

    private void collectTextEdits(TextEdit edit, String uri, String source, List<Map<String, Object>> edits) {
        if (edit instanceof MultiTextEdit) {
            for (TextEdit child : edit.getChildren()) {
                collectTextEdits(child, uri, source, edits);
            }
        } else if (edit instanceof ReplaceEdit) {
            ReplaceEdit replace = (ReplaceEdit) edit;
            Map<String, Object> editMap = new HashMap<>();
            editMap.put("uri", uri);
            editMap.put("offset", replace.getOffset());
            editMap.put("length", replace.getLength());
            editMap.put("newText", replace.getText());
            edits.add(editMap);
        } else if (edit instanceof InsertEdit) {
            InsertEdit insert = (InsertEdit) edit;
            Map<String, Object> editMap = new HashMap<>();
            editMap.put("uri", uri);
            editMap.put("offset", insert.getOffset());
            editMap.put("length", 0);
            editMap.put("newText", insert.getText());
            edits.add(editMap);
        }
    }

    private String getChangeUri(Change change) {
        if (change instanceof TextFileChange) {
            TextFileChange fileChange = (TextFileChange) change;
            if (fileChange.getFile() != null) {
                return fileChange.getFile().getLocationURI().toString();
            }
        }
        Object modifiedElement = change.getModifiedElement();
        if (modifiedElement instanceof ICompilationUnit) {
            ICompilationUnit cu = (ICompilationUnit) modifiedElement;
            if (cu.getResource() != null) {
                return cu.getResource().getLocationURI().toString();
            }
        }
        return null;
    }

    /**
     * Convert an LTK Change to a whole-file replacement edit.
     * This is a simpler approach that applies the change to get the new content.
     */
    protected List<Map<String, Object>> convertChangeToWholeFileEdits(Change change) throws CoreException {
        List<Map<String, Object>> edits = new ArrayList<>();
        collectWholeFileEdits(change, edits);
        return edits;
    }

    private void collectWholeFileEdits(Change change, List<Map<String, Object>> edits) throws CoreException {
        if (change instanceof CompositeChange) {
            for (Change child : ((CompositeChange) change).getChildren()) {
                collectWholeFileEdits(child, edits);
            }
        } else if (change instanceof TextChange) {
            TextChange textChange = (TextChange) change;
            String uri = getChangeUri(change);
            if (uri == null) {
                return;
            }

            String preview = textChange.getPreviewContent(new NullProgressMonitor());
            String current = textChange.getCurrentContent(new NullProgressMonitor());

            if (preview != null && current != null && !preview.equals(current)) {
                edits.addAll(createWholeFileEdit(uri, current, preview));
            }
        }
    }
}
