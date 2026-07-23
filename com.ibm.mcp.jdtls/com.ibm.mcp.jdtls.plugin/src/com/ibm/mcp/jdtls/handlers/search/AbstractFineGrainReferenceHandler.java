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
package com.ibm.mcp.jdtls.handlers.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.ibm.mcp.jdtls.ICommandHandler;
import com.ibm.mcp.jdtls.JdtUtils;

/**
 * Base class for fine-grained type reference searches.
 *
 * <p>Subclasses supply the search constant (e.g.
 * {@code IJavaSearchConstants.CAST_TYPE_REFERENCE}), a label for the type key
 * in the result map, and a key for the result list.</p>
 *
 * <p>Copied and adapted from
 * <a href="https://github.com/pzalutski-pixel/javalens-mcp/blob/master/org.javalens.mcp/src/org/javalens/mcp/tools/AbstractFineGrainReferenceTool.java">javalens-mcp AbstractFineGrainReferenceTool</a>
 * for JDT.LS delegate command handler architecture.</p>
 */
public abstract class AbstractFineGrainReferenceHandler implements ICommandHandler {

    private final int searchConstant;
    private final String typeLabel;
    private final String resultKey;

    /**
     * @param searchConstant one of the {@code IJavaSearchConstants.*_TYPE_REFERENCE} constants
     * @param typeLabel      label used as the key for the type name in the result map (e.g. "type")
     * @param resultKey      key used for the list of matches in the result map (e.g. "casts")
     */
    protected AbstractFineGrainReferenceHandler(int searchConstant, String typeLabel, String resultKey) {
        this.searchConstant = searchConstant;
        this.typeLabel = typeLabel;
        this.resultKey = resultKey;
    }

    @Override
    public Object execute(List<Object> arguments, IProgressMonitor monitor) throws Exception {
        IType type = JdtUtils.resolveType(arguments, monitor);
        if (type == null || !type.exists()) {
            return Map.of("error", "Type not found");
        }

        // Use string-based pattern (not IType-based) to avoid JDT internal ClassCastException
        // with IntersectionCastTypeReference when scanning complex cast expressions.
        String typeName = type.getFullyQualifiedName('$');
        SearchPattern pattern = SearchPattern.createPattern(
                typeName,
                IJavaSearchConstants.TYPE,
                searchConstant,
                SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
        if (pattern == null) {
            return Map.of(typeLabel, type.getFullyQualifiedName(), resultKey, List.of());
        }

        // Use source-only scope to avoid scanning JDK/library JARs
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(
                new IJavaElement[]{type.getJavaProject()}, IJavaSearchScope.SOURCES);
        List<Map<String, Object>> usages = new ArrayList<>();

        SearchEngine engine = new SearchEngine();
        engine.search(
                pattern,
                new SearchParticipant[]{SearchEngine.getDefaultSearchParticipant()},
                scope,
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) {
                        if (!(match.getResource() instanceof org.eclipse.core.resources.IFile f)
                                || !"java".equalsIgnoreCase(f.getFileExtension())) {
                            return;
                        }
                        Map<String, Object> usage = new HashMap<>();
                        usage.put("uri", match.getResource().getLocationURI().toString());
                        usage.put("offset", match.getOffset());
                        usage.put("length", match.getLength());
                        if (match.getElement() instanceof IJavaElement element) {
                            usage.put("element", element.getElementName());
                            IType declaringType = (IType) element.getAncestor(IJavaElement.TYPE);
                            if (declaringType != null) {
                                usage.put("declaringType", declaringType.getFullyQualifiedName());
                            }
                        }
                        usages.add(usage);
                    }
                },
                monitor);

        Map<String, Object> result = new HashMap<>();
        result.put(typeLabel, type.getFullyQualifiedName());
        result.put("count", usages.size());
        result.put(resultKey, usages);
        return result;
    }
}
