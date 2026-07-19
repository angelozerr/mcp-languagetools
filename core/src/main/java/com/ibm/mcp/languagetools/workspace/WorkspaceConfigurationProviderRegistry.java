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
package com.ibm.mcp.languagetools.workspace;

import org.jboss.logging.Logger;

import java.util.*;

/**
 * Registry for workspace configuration providers discovered via Java SPI (ServiceLoader).
 */
public class WorkspaceConfigurationProviderRegistry {

    private static final Logger LOG = Logger.getLogger(WorkspaceConfigurationProviderRegistry.class);
    private static final WorkspaceConfigurationProviderRegistry INSTANCE = new WorkspaceConfigurationProviderRegistry();

    private final Map<String, WorkspaceConfigurationProvider> providers = new LinkedHashMap<>();

    private WorkspaceConfigurationProviderRegistry() {
        ServiceLoader<WorkspaceConfigurationProvider> loader = ServiceLoader.load(WorkspaceConfigurationProvider.class);
        for (WorkspaceConfigurationProvider provider : loader) {
            providers.put(provider.getId(), provider);
            LOG.infof("Registered workspace configuration provider: %s", provider.getId());
        }
    }

    public static WorkspaceConfigurationProviderRegistry getInstance() {
        return INSTANCE;
    }

    public WorkspaceConfigurationProvider getProvider(String id) {
        return providers.get(id);
    }

    public Collection<WorkspaceConfigurationProvider> getProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }

    /**
     * Returns provider ids in SPI discovery order.
     */
    public List<String> getProviderIds() {
        return List.copyOf(providers.keySet());
    }
}
