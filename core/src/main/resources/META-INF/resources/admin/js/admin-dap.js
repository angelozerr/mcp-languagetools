/**
 * Admin UI - DAP (Debug Adapter Protocol) Management
 *
 * Handles DAP session creation, launching, and management
 */

/**
 * Create a new test session for a DAP server.
 * Called from the workspace Debuggers tab.
 */
async function createNewTestSession(dapServerId) {
    try {
        // Get current workspace URI from admin.js
        const workspaceUri = window.selectedWorkspace;
        if (!workspaceUri) {
            window.showAlert('No Workspace Selected', 'Please select a workspace first.');
            return;
        }

        // Create the session
        const response = await fetch('/api/admin/dap/sessions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                workspaceUri: workspaceUri,
                dapServerId: dapServerId,
                sessionName: 'Test Session'
            })
        });

        if (!response.ok) {
            const errorText = await response.text();

            // Try to parse JSON error
            let errorMessage = errorText;
            try {
                const errorJson = JSON.parse(errorText);
                errorMessage = errorJson.error || errorJson.message || errorText;
            } catch (e) {
                // If HTML error page, try to extract the error message
                if (errorText.includes('<html')) {
                    const match = errorText.match(/<h1[^>]*>(.*?)<\/h1>/i) ||
                                  errorText.match(/<title>(.*?)<\/title>/i);
                    if (match) {
                        errorMessage = match[1].replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
                    } else {
                        errorMessage = 'Server error (see console for details)';
                        console.error('Full error HTML:', errorText);
                    }
                }
            }

            throw new Error(errorMessage);
        }

        const session = await response.json();

        // Add session to DOM immediately (don't wait for WebSocket)
        await addSessionToDOM({
            sessionId: session.sessionId,
            workspaceUri: workspaceUri,
            eventType: 'CREATED'
        });

        // Show launch config form in console
        showLaunchConfigForm(session, dapServerId);

    } catch (error) {
        console.error('Error creating test session:', error);

        // Show error in console
        const consoleArea = document.getElementById('console-area');
        consoleArea.innerHTML = `
            <div style="padding: 1rem;">
                <h3 style="color: #f48771;">❌ Failed to Create Session</h3>
                <pre style="background: #1e1e1e; padding: 1rem; border-radius: 3px; color: #f48771; font-family: monospace; white-space: pre-wrap;">${error.message}</pre>
            </div>
        `;
    }
}

/**
 * Show the launch configuration form in the console area.
 */
function showLaunchConfigForm(session, dapServerId) {
    const consoleArea = document.getElementById('console-area');
    const sessionId = session.sessionId;

    // Store session ID for later use
    window.currentDapSessionId = sessionId;

    // Check if session div already exists
    let sessionDiv = document.getElementById(`dap-session-${sessionId}`);
    if (sessionDiv) {
        // Session already exists, just show it
        showSessionDiv(sessionId);
        return;
    }

    // Create new session div
    const defaultConfig = getDefaultLaunchConfig(dapServerId);

    const sessionHTML = `
        <div style="padding: 1rem; height: 100%; display: flex; flex-direction: column; overflow: hidden;">
            <div style="margin-bottom: 1rem;">
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                    <h3 style="margin: 0; color: #cccccc;">${session.sessionName || 'New Debug Session'}</h3>
                    <span id="dap-session-status-${sessionId}" class="status-badge status-stopped">Not Started</span>
                </div>
                <p style="margin: 0; color: #858585; font-size: 0.85rem;">Server: ${session.dapServerId || dapServerId}</p>
            </div>

            <div style="margin-bottom: 1rem;">
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                    <label style="font-weight: 500; color: #cccccc;">Launch Configuration</label>
                    <div style="display: flex; gap: 0;">
                        <button
                            id="dap-debug-btn-${sessionId}"
                            onclick="debugDapSession('${session.sessionId}')"
                            style="padding: 0.1rem 0.2rem; background: transparent; color: #569cd6; border: none; cursor: pointer; font-size: 1rem; display: flex; align-items: center; border-radius: 3px; transition: background 0.2s;"
                            onmouseover="this.style.background='rgba(86, 156, 214, 0.2)'"
                            onmouseout="this.style.background='transparent'"
                            title="Debug (with breakpoints)">
                            🐛
                        </button>
                        <button
                            id="dap-launch-btn-${sessionId}"
                            onclick="launchDapSession('${session.sessionId}')"
                            style="padding: 0.1rem 0.2rem; background: transparent; color: #4ec9b0; border: none; cursor: pointer; font-size: 1rem; display: flex; align-items: center; border-radius: 3px; transition: background 0.2s;"
                            onmouseover="this.style.background='rgba(78, 201, 176, 0.2)'"
                            onmouseout="this.style.background='transparent'"
                            title="Run (without debugging)">
                            ▶
                        </button>
                        <button
                            id="dap-stop-btn-${sessionId}"
                            onclick="stopDapSession('${session.sessionId}')"
                            disabled
                            style="padding: 0.1rem 0.2rem; background: transparent; color: #f48771; border: none; cursor: pointer; font-size: 1rem; display: flex; align-items: center; border-radius: 3px; transition: background 0.2s; opacity: 0.3;"
                            onmouseover="if (!this.disabled) this.style.background='rgba(244, 135, 113, 0.2)'"
                            onmouseout="this.style.background='transparent'"
                            title="Stop debug session">
                            ⏹
                        </button>
                        <button
                            onclick="deleteDapSession('${session.sessionId}')"
                            style="padding: 0.1rem 0.2rem; background: transparent; color: #858585; border: none; cursor: pointer; font-size: 0.9rem; display: flex; align-items: center; border-radius: 3px; transition: background 0.2s;"
                            onmouseover="this.style.background='rgba(133, 133, 133, 0.2)'"
                            onmouseout="this.style.background='transparent'"
                            title="Delete session">
                            🗑️
                        </button>
                    </div>
                    <select
                        id="launch-template-selector-${sessionId}"
                        onchange="applyLaunchTemplate('${session.sessionId}', this.value)"
                        style="padding: 0.2rem 0.4rem; background: #252526; color: #cccccc; border: 1px solid #3a3a3a; border-radius: 3px; font-size: 0.85rem; cursor: pointer;">
                        <option value="">Select template...</option>
                    </select>
                </div>
                <textarea
                    id="launch-config-editor"
                    style="width: 100%; padding: 0.75rem; background: #1e1e1e; color: #d4d4d4; border: 1px solid #3a3a3a; border-radius: 3px; font-family: 'Courier New', monospace; font-size: 0.9rem; resize: vertical; height: 150px;"
                >${JSON.stringify(defaultConfig, null, 2)}</textarea>
            </div>

            <div style="flex: 1; display: flex; flex-direction: column; min-height: 0;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                    <label style="font-weight: 500; color: #cccccc;">Console:</label>
                    <div class="console-controls">
                        <label style="color: #cccccc; font-size: 0.85rem;">
                            Trace Level:
                            <select id="dap-trace-level" onchange="changeDapTraceLevel('${session.sessionId}', this.value)" style="margin-left: 0.5rem; background: #3e3e42; color: #cccccc; border: 1px solid #555; padding: 0.25rem 0.5rem; border-radius: 3px;">
                                <option value="off">Off</option>
                                <option value="messages">Messages</option>
                                <option value="verbose" selected>Verbose</option>
                            </select>
                        </label>
                        <button onclick="toggleAllDapTraces('${session.sessionId}')" id="dap-fold-button">Unfold All</button>
                        <button onclick="clearDapConsole('${session.sessionId}')">Clear</button>
                    </div>
                </div>
                <div id="dap-traces-container-${sessionId}" style="flex: 1; overflow-y: auto; background: #1e1e1e; padding: 0.5rem; border-radius: 3px; font-family: 'Courier New', monospace; font-size: 0.85rem;">
                    <div style="color: #666;">Ready. Click ▶ to launch.</div>
                </div>
            </div>
        </div>
    `;

    // Clear console area completely and create session div
    consoleArea.innerHTML = '';

    sessionDiv = document.createElement('div');
    sessionDiv.id = `dap-session-${sessionId}`;
    sessionDiv.style.display = 'block';
    sessionDiv.style.height = '100%';
    sessionDiv.innerHTML = sessionHTML;
    consoleArea.appendChild(sessionDiv);

    // Load launch configuration templates for this DAP server
    if (dapServerId) {
        loadLaunchConfigurationTemplates(sessionId, dapServerId);
    }

    // Clear DAP server selection
    selectedDapServer = null;

    // Highlight the session in the workspace list
    document.querySelectorAll('.dap-session-item').forEach(el => el.classList.remove('active'));
    const selectedElement = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (selectedElement) {
        selectedElement.classList.add('active');
    }
}

/**
 * Show a specific session div and hide others.
 */
function showSessionDiv(sessionId) {
    window.currentDapSessionId = sessionId;
    selectedDapServer = null; // Clear DAP server selection

    const consoleArea = document.getElementById('console-area');

    // Check if session div exists, if not create it
    let sessionDiv = document.getElementById(`dap-session-${sessionId}`);
    if (!sessionDiv) {
        // Session div doesn't exist, need to create it
        showLaunchConfigForm({ sessionId: sessionId }, null);
        return;
    }

    // Hide all session divs
    const allSessions = consoleArea.querySelectorAll('[id^="dap-session-"]');
    allSessions.forEach(div => div.style.display = 'none');

    // Show the selected session
    sessionDiv.style.display = 'block';

    // Update traces container with current traces
    const traces = window.dapTracesBySession?.[sessionId] || [];
    const tracesContainer = document.getElementById(`dap-traces-container-${sessionId}`);
    if (tracesContainer && traces.length > 0) {
        tracesContainer.innerHTML = renderDapTraces(traces, sessionId);
    }
}

/**
 * Get default launch configuration based on DAP server type.
 */
function getDefaultLaunchConfig(dapServerId) {
    const configs = {
        'debugpy': {
            type: 'python',
            request: 'launch',
            name: 'Python: Current File',
            program: '${workspaceFolder}/main.py',
            console: 'integratedTerminal'
        },
        'vscode-js-debug': {
            // Empty - use templates from selector
        }
    };

    return configs[dapServerId] || {
        type: 'debug',
        request: 'launch',
        name: 'Launch',
        program: '${workspaceFolder}/main'
    };
}

/**
 * Debug a DAP session with the provided configuration (with breakpoints).
 */
async function debugDapSession(sessionId) {
    await launchDapSessionInternal(sessionId, true); // debugMode = true
}

/**
 * Launch a DAP session with the provided configuration (without debugging).
 */
async function launchDapSession(sessionId) {
    await launchDapSessionInternal(sessionId, false); // debugMode = false
}

/**
 * Internal function to launch/debug a DAP session.
 */
async function launchDapSessionInternal(sessionId, debugMode) {
    try {
        const configText = document.getElementById('launch-config-editor').value;
        const launchConfig = JSON.parse(configText);

        // Pass debugMode as query parameter (not in config)
        const response = await fetch(`/api/admin/dap/sessions/${sessionId}/launch?debugMode=${debugMode}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(launchConfig)
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error || 'Launch failed');
        }

        const result = await response.json();

        // Traces will appear in the dap-traces-container below
        console.log(`${debugMode ? 'Debug' : 'Run'} result:`, result);

        // Notify workspace list to refresh
        if (typeof window.loadWorkspaces === 'function') {
            window.loadWorkspaces();
        }

    } catch (error) {
        console.error(`Error ${debugMode ? 'debugging' : 'launching'} session:`, error);

        // Parse error response
        let errorData = null;
        try {
            errorData = JSON.parse(error.message);
        } catch (e) {
            // Not JSON, use as-is
            errorData = { message: error.message, type: 'Error', stackTrace: '' };
        }

        // Add error to traces (don't replace existing traces)
        const tracesContainer = document.getElementById(`dap-traces-container-${sessionId}`);
        if (tracesContainer && typeof window.formatErrorWithFolding === 'function') {
            const errorHtml = window.formatErrorWithFolding(`Failed to ${debugMode ? 'Debug' : 'Launch'}`, errorData);
            tracesContainer.insertAdjacentHTML('beforeend', errorHtml);
            // Scroll to bottom to show the error
            tracesContainer.scrollTop = tracesContainer.scrollHeight;
        }
    }
}

/**
 * Stop a running DAP session.
 */
async function stopDapSession(sessionId) {
    try {
        const response = await fetch(`/api/admin/dap/sessions/${sessionId}/stop`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Failed to stop session');
        }

        console.log('Stop request sent for session:', sessionId);

    } catch (error) {
        console.error('Error stopping session:', error);
        alert(`Failed to stop session: ${error.message}`);
    }
}

/**
 * Delete a DAP session.
 */
async function deleteDapSession(sessionId) {
    const confirmed = await window.confirmAction(
        'Delete Debug Session',
        'Delete this test session?\n\nThis action cannot be undone.',
        'Delete',
        true
    );

    if (!confirmed) return;

    try {
        const response = await fetch(`/api/admin/dap/sessions/${sessionId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('Failed to delete session');
        }

        // Remove session from DOM (both workspace list and console area)
        removeSessionFromDOM(sessionId);

        // Show placeholder in console
        const consoleArea = document.getElementById('console-area');
        const remainingSessions = consoleArea.querySelectorAll('[id^="dap-session-"]');
        if (remainingSessions.length === 0) {
            consoleArea.innerHTML = `
                <div class="placeholder">
                    Session deleted
                </div>
            `;
        }

    } catch (error) {
        console.error('Error deleting session:', error);
        window.showAlert('Failed to Delete Session', error.message);
    }
}

/**
 * Select a DAP session (called from workspace view).
 */
async function selectDapSession(sessionId) {
    console.log('Selected DAP session:', sessionId);

    // Remove 'active' class from all sessions
    document.querySelectorAll('.dap-session-item').forEach(el => el.classList.remove('active'));

    // Add 'active' class to selected session
    const selectedElement = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (selectedElement) {
        selectedElement.classList.add('active');
    }

    // Check if session div already exists
    const sessionDiv = document.getElementById(`dap-session-${sessionId}`);
    if (sessionDiv) {
        // Just show it
        showSessionDiv(sessionId);
        return;
    }

    // Session div doesn't exist yet, fetch details and create it
    try {
        const response = await fetch(`/api/admin/workspaces`);
        const workspaces = await response.json();

        // Find the session across all workspaces
        let session = null;
        for (const workspace of workspaces) {
            session = workspace.dapSessions?.find(s => s.sessionId === sessionId);
            if (session) break;
        }

        if (!session) {
            console.error('Session not found:', sessionId);
            return;
        }

        // Create and show launch config form
        showLaunchConfigForm(session, session.dapServerId);

    } catch (error) {
        console.error('Error loading session:', error);
        window.showAlert('Failed to Load Session', error.message);
    }
}

function renderDapTraces(traces, sessionId) {
    const level = currentDapTraceLevel[sessionId] || 'verbose';

    return traces.map((trace, index) => {
        // Filter based on trace level
        if (!shouldShowDapTrace(trace, sessionId)) {
            return ''; // Don't show if level is 'off'
        }

        const content = trace.jsonContent;

        // Parse the trace: first line is header, rest is body (same as LSP)
        const lines = content.split('\n');
        const headerLine = lines[0]; // [Trace - HH:mm:ss] ... or [Starting ...]

        // Assign colors based on messageType (not text content)
        let headerColor = '#cccccc';  // Default gray
        if (trace.messageType === 'ERROR') {
            headerColor = '#ff6b6b';  // Red for errors/stderr
        } else if (trace.messageType === 'INFO') {
            headerColor = '#569cd6';  // Blue for console/stdout output
        } else if (trace.messageType === 'UPDATE') {
            headerColor = '#4ec9b0';  // Cyan for progress updates
        }
        // TRACE type stays gray (default)

        // Body is everything after the first line
        const bodyLines = lines.slice(1);
        const body = bodyLines.join('\n').trim();
        const hasBody = body.length > 0;

        // If no body OR level is 'messages', display header only (no folding)
        if (!hasBody || level === 'messages') {
            return `
                <div class="trace-line">
                    <div style="padding: 0.25rem; font-family: 'Consolas', 'Monaco', monospace; font-size: 0.85rem; color: ${headerColor};">${escapeHtml(headerLine)}</div>
                </div>
            `;
        }

        // With body AND level is 'verbose': header + foldable body (like LSP)
        return `
            <div class="trace-line">
                <div class="trace-header folded" onclick="toggleDapTrace(${index})" style="padding: 0.25rem; font-family: 'Consolas', 'Monaco', monospace; font-size: 0.85rem; cursor: pointer;">
                    <span class="trace-toggle" id="dap-toggle-${index}">▶</span>
                    <span class="trace-header-text" style="color: ${headerColor};">${escapeHtml(headerLine)}</span>
                </div>
                <div class="trace-body collapsed" id="dap-body-${index}" style="font-family: 'Consolas', 'Monaco', monospace; font-size: 0.85rem; color: #cccccc; white-space: pre-wrap;">${escapeHtml(body)}</div>
            </div>
        `;
    }).join('');
}

/**
 * Refresh traces display for the current session (called by handleDapTrace).
 */
function renderDapTracesForSession(sessionId) {
    if (!window.currentDapSessionId || window.currentDapSessionId !== sessionId) {
        return;
    }

    const container = document.getElementById(`dap-traces-container-${sessionId}`);
    if (!container) {
        return; // Not in trace view
    }

    const traces = window.dapTracesBySession?.[sessionId] || [];
    container.innerHTML = traces.length > 0 ? renderDapTraces(traces, sessionId) : '<div style="color: #666;">No traces yet.</div>';

    // Auto-scroll to bottom
    container.scrollTop = container.scrollHeight;
}

/**
 * ============================================
 * GLOBAL DAP SERVERS (Debuggers tab)
 * ============================================
 */

let selectedDapServer = null;
let currentDapServerTab = 'overview'; // overview, install
let dapServerConfigs = {};

/**
 * Load all global DAP servers.
 */
async function loadAllDapServers(serverIdToSelect) {
    try {
        const response = await fetch('/api/admin/dap-servers');
        const dapServers = await response.json();

        // Store in map for easy access
        dapServerConfigs = {};
        dapServers.forEach(server => {
            dapServerConfigs[server.id] = server;
        });

        const container = document.getElementById('dap-servers-list');
        if (!container) {
            console.error('dap-servers-list container not found');
            return;
        }

        if (dapServers.length === 0) {
            container.innerHTML = '<div class="servers-placeholder">No debuggers configured</div>';
            return;
        }

        container.innerHTML = dapServers.map(server => {
            const isActive = selectedDapServer === server.id ? 'active' : '';
            return `
                <div class="server-item ${isActive}" onclick="showDapServerDetails('${server.id}')">
                    <div class="server-name">
                        <span class="server-source-icon">🐛</span>
                        ${server.name}
                    </div>
                    <div class="server-id">${server.id}</div>
                </div>
            `;
        }).join('');

        // Auto-select: 1) specified server, 2) previously selected, 3) first server
        if (dapServers.length > 0) {
            let serverToShow;
            if (serverIdToSelect && dapServers.find(s => s.id === serverIdToSelect)) {
                serverToShow = serverIdToSelect;
            } else if (selectedDapServer && dapServers.find(s => s.id === selectedDapServer)) {
                serverToShow = selectedDapServer;
            } else {
                serverToShow = dapServers[0].id;
            }
            showDapServerDetails(serverToShow);
        }
    } catch (error) {
        console.error('Failed to load DAP servers:', error);
    }
}

/**
 * Show details for a global DAP server with Overview/Install tabs.
 */
async function showDapServerDetails(serverId) {
    selectedDapServer = serverId;

    // Re-render server list to update active state
    const dapServers = Object.values(dapServerConfigs);
    const container = document.getElementById('dap-servers-list');
    container.innerHTML = dapServers.map(server => {
        const isActive = selectedDapServer === server.id ? 'active' : '';
        return `
            <div class="server-item ${isActive}" onclick="showDapServerDetails('${server.id}')">
                <div class="server-name">
                    <span class="server-source-icon">🐛</span>
                    ${server.name}
                </div>
                <div class="server-id">${server.id}</div>
            </div>
        `;
    }).join('');

    const server = dapServerConfigs[serverId];
    if (!server) {
        console.error('DAP server not found:', serverId);
        return;
    }

    // Show console column
    const appContainer = document.querySelector('.app-container');
    const consoleColumn = document.querySelector('.console-container');
    consoleColumn.style.display = 'flex';
    appContainer.style.gridTemplateColumns = '400px 1fr';
    consoleColumn.style.gridColumn = '2';

    // Build document selector info
    let docSelectorHTML = '<p style="color: #999;">None configured</p>';
    if (server.documentSelector && server.documentSelector.length > 0) {
        docSelectorHTML = server.documentSelector.map(selector => {
            const parts = [];
            if (selector.language) parts.push(`Language: <code>${selector.language}</code>`);
            if (selector.pattern) parts.push(`Pattern: <code>${selector.pattern}</code>`);
            if (selector.scheme) parts.push(`Scheme: <code>${selector.scheme}</code>`);
            return `<li>${parts.join(', ')}</li>`;
        }).join('');
        docSelectorHTML = `<ul style="margin: 0.5rem 0; padding-left: 1.5rem;">${docSelectorHTML}</ul>`;
    }

    // Check if server has contributions
    const lspServers = Object.values(window.serverConfigs || {});
    const dapServersWithFlag = dapServers.map(s => ({...s, isDap: true}));
    const allServers = [...lspServers, ...dapServersWithFlag];
    const hasContributions = (server.contributions && Object.keys(server.contributions).length > 0) ||
                            buildContributedByMap(allServers)[server.id]?.length > 0;

    // Prepare contributions HTML and diagram data (only if has contributions)
    const contributionsHTML = hasContributions ? formatContributionsSection(server, allServers) : '';

    // Store for diagram rendering
    if (hasContributions) {
        window.currentDiagramServers = allServers;
        window.currentDiagramServerId = server.id;
    }

    const detailsHTML = `
        <h3 style="margin-top: 0; color: #4ec9b0;">Debug Adapter Information</h3>

        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Server ID:</strong>
            <p style="margin: 0.25rem 0; color: #d4d4d4;"><code>${server.id}</code></p>
        </div>

        ${server.description ? `
        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Description:</strong>
            <p style="margin: 0.25rem 0; color: #d4d4d4;">${server.description}</p>
        </div>
        ` : ''}

        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Supported Languages/Files:</strong>
            ${docSelectorHTML}
        </div>

        <div style="margin-top: 2rem; padding: 1rem; background: #252526; border-left: 3px solid #4ec9b0; border-radius: 4px;">
            <strong>Note:</strong> Debuggers are started on-demand during debug sessions. They are not automatically started with workspaces.
        </div>
    `;

    const html = `
        <div class="console-header">
            <div class="console-title">
                <span class="server-source-icon">🐛</span>
                ${server.name || server.id}
            </div>
            <div class="console-tabs">
                <button class="tab-button ${currentDapServerTab === 'overview' ? 'active' : ''}" onclick="switchDapServerTab('overview')">Overview</button>
                ${hasContributions ? `<button class="tab-button ${currentDapServerTab === 'contributions' ? 'active' : ''}" onclick="switchDapServerTab('contributions')">Contributions</button>` : ''}
                <button class="tab-button ${currentDapServerTab === 'install' ? 'active' : ''}" onclick="switchDapServerTab('install')">Install</button>
            </div>
        </div>
        <div class="tab-content">
            <div id="dap-server-overview-tab" class="tab-panel ${currentDapServerTab === 'overview' ? 'active' : ''}">
                <div class="details-panel" style="padding: 2rem; color: #cccccc; overflow-y: auto;">
                    ${detailsHTML}
                </div>
            </div>
            ${hasContributions ? `
            <div id="dap-server-contributions-tab" class="tab-panel ${currentDapServerTab === 'contributions' ? 'active' : ''}">
                <div id="server-diagram-container" style="width: 100%; height: 400px; background: #1e1e1e; border-bottom: 1px solid #333;"></div>
                <div class="details-panel" id="dap-contributions-content" style="padding: 2rem; color: #cccccc;">
                    ${contributionsHTML}
                </div>
            </div>
            ` : ''}
            <div id="dap-server-install-tab" class="tab-panel ${currentDapServerTab === 'install' ? 'active' : ''}">
                <div class="install-panel">
                    <h3>Installer Configuration</h3>
                    <div class="install-info">
                        <p><strong>Debugger:</strong> ${server.name}</p>
                        <p><strong>ID:</strong> ${server.id}</p>
                    </div>
                    <div class="installer-editor">
                        <div class="editor-header">
                            <span>installer.json</span>
                            <div class="editor-actions">
                                <button class="editor-btn" onclick="saveDapInstallerJson('${server.id}')" title="Save">💾 Save</button>
                                <button class="editor-btn" onclick="resetDapInstallerJson('${server.id}')" title="Reset">↻ Reset</button>
                            </div>
                        </div>
                        <textarea id="dap-installer-json-editor" class="json-editor" spellcheck="false"></textarea>
                    </div>
                    <button class="install-button" onclick="runDapInstaller('${server.id}')">▶ Run Installer</button>
                    <div id="dap-install-output" class="install-output"></div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('console-area').innerHTML = html;

    // Load installer.json for this DAP server if on Install tab
    if (currentDapServerTab === 'install') {
        loadDapInstallerJson(server.id);
    }
}

/**
 * Switch between DAP server tabs (Overview/Install).
 */
function switchDapServerTab(tab) {
    currentDapServerTab = tab;
    if (selectedDapServer) {
        showDapServerDetails(selectedDapServer);
    }
    // Render diagram when switching to contributions tab
    if (tab === 'contributions' && window.currentDiagramServers && window.currentDiagramServerId) {
        setTimeout(() => renderServerDiagram(window.currentDiagramServers, window.currentDiagramServerId), 100);
    }
}

/**
 * Load installer.json for a DAP server.
 */
async function loadDapInstallerJson(serverId) {
    try {
        const response = await fetch(`/api/admin/dap-servers/${serverId}/installer`);
        if (!response.ok) throw new Error('Failed to load installer.json');

        const installerJson = await response.json();
        const editor = document.getElementById('dap-installer-json-editor');
        if (editor) {
            editor.value = JSON.stringify(installerJson, null, 2);
        }
    } catch (error) {
        console.error('Failed to load DAP installer.json:', error);
        const editor = document.getElementById('dap-installer-json-editor');
        if (editor) {
            editor.value = '// No installer.json found for this debugger';
        }
    }
}

/**
 * Save installer.json for a DAP server.
 */
async function saveDapInstallerJson(serverId) {
    const editor = document.getElementById('dap-installer-json-editor');
    if (!editor) return;

    try {
        const installerJson = JSON.parse(editor.value);

        const response = await fetch(`/api/admin/dap-servers/${serverId}/installer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(installerJson)
        });

        if (!response.ok) throw new Error('Failed to save installer.json');

        if (window.showAlert) {
            window.showAlert('Success', 'Installer configuration saved successfully.');
        }
    } catch (error) {
        console.error('Failed to save DAP installer.json:', error);
        if (window.showAlert) {
            window.showAlert('Error', 'Failed to save installer.json: ' + error.message);
        }
    }
}

/**
 * Reset installer.json to original.
 */
async function resetDapInstallerJson(serverId) {
    loadDapInstallerJson(serverId);
}

/**
 * Run installer for a DAP server.
 */
async function runDapInstaller(serverId) {
    const outputDiv = document.getElementById('dap-install-output');
    if (!outputDiv) return;

    outputDiv.innerHTML = '<div style="color: #4ec9b0;">Running installer...</div>';

    try {
        const response = await fetch(`/api/admin/dap-servers/${serverId}/install`, {
            method: 'POST'
        });

        if (!response.ok) throw new Error('Installation failed');

        const result = await response.json();
        outputDiv.innerHTML = `
            <div style="color: #4ec9b0;">✓ Installation completed successfully</div>
            <pre style="margin-top: 0.5rem; color: #d4d4d4;">${JSON.stringify(result, null, 2)}</pre>
        `;
    } catch (error) {
        console.error('Failed to run DAP installer:', error);
        outputDiv.innerHTML = `<div style="color: #f48771;">❌ Installation failed: ${error.message}</div>`;
    }
}

/**
 * Toggle individual DAP trace item.
 */
function toggleDapTrace(index) {
    const header = document.getElementById(`dap-toggle-${index}`).parentElement;
    const body = document.getElementById(`dap-body-${index}`);
    const toggle = document.getElementById(`dap-toggle-${index}`);

    if (body.classList.contains('collapsed')) {
        body.classList.remove('collapsed');
        body.classList.add('expanded');
        header.classList.remove('folded');
        toggle.textContent = '▼';
    } else {
        body.classList.remove('expanded');
        body.classList.add('collapsed');
        header.classList.add('folded');
        toggle.textContent = '▶';
    }
}

/**
 * Toggle folding state for DAP traces.
 */
let dapFoldedState = {};

function toggleAllDapTraces(sessionId) {
    const container = document.getElementById(`dap-traces-container-${sessionId}`);
    const foldButton = document.getElementById('dap-fold-button');
    const isFolded = dapFoldedState[sessionId] || false;

    if (isFolded) {
        // Unfold all
        container.querySelectorAll('.trace-header').forEach(header => {
            header.classList.remove('folded');
        });
        container.querySelectorAll('.trace-body').forEach((body, index) => {
            body.classList.remove('collapsed');
            body.classList.add('expanded');
            const toggle = document.getElementById(`dap-toggle-${index}`);
            if (toggle) toggle.textContent = '▼';
        });
        foldButton.textContent = 'Fold All';
        dapFoldedState[sessionId] = false;
    } else {
        // Fold all
        container.querySelectorAll('.trace-header').forEach(header => {
            header.classList.add('folded');
        });
        container.querySelectorAll('.trace-body').forEach((body, index) => {
            body.classList.remove('expanded');
            body.classList.add('collapsed');
            const toggle = document.getElementById(`dap-toggle-${index}`);
            if (toggle) toggle.textContent = '▶';
        });
        foldButton.textContent = 'Unfold All';
        dapFoldedState[sessionId] = true;
    }
}

/**
 * Clear DAP traces for a session.
 */
function clearDapConsole(sessionId) {
    if (window.dapTracesBySession) {
        window.dapTracesBySession[sessionId] = [];
    }
    renderDapTracesForSession(sessionId);
}

/**
 * Change trace level for DAP session.
 */
let currentDapTraceLevel = {};

function changeDapTraceLevel(sessionId, level) {
    currentDapTraceLevel[sessionId] = level;
    renderDapTracesForSession(sessionId);
}

function shouldShowDapTrace(trace, sessionId) {
    // Always show program outputs (INFO/ERROR) regardless of trace level
    // These are console/stdout/stderr outputs, not DAP protocol traces
    if (trace.messageType === 'INFO' || trace.messageType === 'ERROR') {
        return true;
    }

    // For TRACE messages, respect the trace level
    const level = currentDapTraceLevel[sessionId] || 'verbose';
    if (level === 'off') {
        return false;
    }
    return true;
}

/**
 * Handle DAP session update from WebSocket.
 */
async function onDapSessionUpdate(message) {
    console.log('[DAP] Session update:', message.eventType, message.sessionId);

    // Update DOM directly based on event type
    switch (message.eventType) {
        case 'CREATED':
            await addSessionToDOM(message);
            break;
        case 'STATE_CHANGED':
            await updateSessionInDOM(message);
            break;
        case 'DELETED':
            removeSessionFromDOM(message.sessionId);
            break;
    }
}

/**
 * Add a new session to the DOM.
 */
async function addSessionToDOM(message) {
    try {
        // Fetch the session details
        const response = await fetch(`/api/admin/workspaces`);
        const workspaces = await response.json();

        // Find the workspace
        const workspace = workspaces.find(w => w.rootUri === message.workspaceUri);
        if (!workspace) {
            console.warn('[DAP] Workspace not found:', message.workspaceUri);
            return;
        }

        // Find the session in the workspace data
        const session = workspace.dapSessions.find(s => s.sessionId === message.sessionId);
        if (!session) {
            console.warn('[DAP] Session not found in workspace:', message.sessionId);
            return;
        }

        // Check if session already exists in DOM
        const existingSession = document.querySelector(`[data-session-id="${message.sessionId}"]`);
        if (existingSession) {
            console.log('[DAP] Session already in DOM, skipping:', message.sessionId);
            return;
        }

        // Find the debugger container in the DOM (under the DAP server)
        const debuggerContainer = document.querySelector(`[data-dap-server="${session.dapServerId}"]`);
        if (!debuggerContainer) {
            console.warn('[DAP] Debugger container not found for:', session.dapServerId);
            return;
        }

        // Create session HTML
        const sessionHTML = createSessionHTML(session);

        // Insert after the debugger item
        debuggerContainer.insertAdjacentHTML('afterend', sessionHTML);

        console.log('[DAP] Session added to DOM:', message.sessionId);
    } catch (error) {
        console.error('[DAP] Error adding session to DOM:', error);
    }
}

/**
 * Update an existing session in the DOM.
 */
async function updateSessionInDOM(message) {
    try {
        // Determine status text, class, and icon
        let statusText = message.newStatus ? message.newStatus.charAt(0) + message.newStatus.slice(1).toLowerCase() : '';
        let statusClass = 'status-stopped';
        let stateIcon = '<span>⏹️</span>';

        if (message.newStatus === 'INSTALLING') {
            statusText = 'Installing';
            statusClass = 'status-installing';
            stateIcon = '<span>⏳</span>';
        } else if (message.newStatus === 'STARTING') {
            statusText = 'Starting';
            statusClass = 'status-starting';
            stateIcon = '<span>⏳</span>';
        } else if (message.newStatus === 'RUNNING') {
            statusText = 'Running';
            statusClass = 'status-running';
            stateIcon = '<span>▶️</span>';
        } else if (message.newStatus === 'PAUSED') {
            statusText = 'Paused';
            statusClass = 'status-running-not-ready';
            stateIcon = '<span>⏸️</span>';
        } else if (message.newStatus === 'ERROR' || message.newStatus === 'START_FAILED') {
            statusText = 'Error';
            statusClass = 'status-error';
            stateIcon = '<span>❌</span>';
        }

        // Update status badge AND icon in workspace list (left sidebar)
        const sessionElement = document.querySelector(`[data-session-id="${message.sessionId}"]`);
        if (sessionElement) {
            const statusBadge = sessionElement.querySelector('.status-badge');
            if (statusBadge && message.newStatus) {
                statusBadge.className = `status-badge ${statusClass}`;
                statusBadge.textContent = statusText;
            }
            // Update icon (first child element in the session div)
            const firstChild = sessionElement.firstElementChild;
            if (firstChild && firstChild.tagName === 'SPAN') {
                // Parse the new icon HTML and replace the old span
                const temp = document.createElement('div');
                temp.innerHTML = stateIcon;
                const newIcon = temp.firstElementChild;
                if (newIcon && firstChild.parentNode) {
                    firstChild.parentNode.replaceChild(newIcon, firstChild);
                }
            }
        }

        // Update status badge in session console (right panel)
        const sessionStatusBadge = document.getElementById(`dap-session-status-${message.sessionId}`);
        if (sessionStatusBadge && message.newStatus) {
            sessionStatusBadge.className = `status-badge ${statusClass}`;
            sessionStatusBadge.textContent = statusText;
        }

        // Update Debug/Launch/Stop buttons state based on status
        const debugBtn = document.getElementById(`dap-debug-btn-${message.sessionId}`);
        const launchBtn = document.getElementById(`dap-launch-btn-${message.sessionId}`);
        const stopBtn = document.getElementById(`dap-stop-btn-${message.sessionId}`);

        if (debugBtn && launchBtn && stopBtn) {
            // Determine button states based on status
            const isRunning = message.newStatus === 'RUNNING';
            const isStarting = message.newStatus === 'STARTING' || message.newStatus === 'INSTALLING';
            const isStopped = message.newStatus === 'STOPPED' || message.newStatus === 'START_FAILED' || message.newStatus === 'ERROR' || message.newStatus === 'CREATED';

            const canLaunch = isStopped || (!isRunning && !isStarting);
            const canStop = isRunning || isStarting;

            // Update Debug button (same state as Launch)
            debugBtn.disabled = !canLaunch;
            debugBtn.style.opacity = canLaunch ? '1' : '0.3';
            debugBtn.style.cursor = canLaunch ? 'pointer' : 'not-allowed';

            // Update Launch button
            launchBtn.disabled = !canLaunch;
            launchBtn.style.opacity = canLaunch ? '1' : '0.3';
            launchBtn.style.cursor = canLaunch ? 'pointer' : 'not-allowed';

            // Update Stop button
            stopBtn.disabled = !canStop;
            stopBtn.style.opacity = canStop ? '1' : '0.3';
            stopBtn.style.cursor = canStop ? 'pointer' : 'not-allowed';
        }

        console.log('[DAP] Session status updated in DOM:', message.sessionId, message.oldStatus, '->', message.newStatus);
    } catch (error) {
        console.error('[DAP] Error updating session in DOM:', error);
    }
}

/**
 * Remove a session from the DOM.
 */
function removeSessionFromDOM(sessionId) {
    // Remove from workspace list
    const sessionElement = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (sessionElement) {
        sessionElement.remove();
        console.log('[DAP] Session removed from workspace list:', sessionId);
    }

    // Remove from console area
    const sessionDiv = document.getElementById(`dap-session-${sessionId}`);
    if (sessionDiv) {
        sessionDiv.remove();
        console.log('[DAP] Session div removed from console:', sessionId);
    }

    // Clear current session if it was the deleted one
    if (window.currentDapSessionId === sessionId) {
        window.currentDapSessionId = null;
    }
}

/**
 * Create HTML for a DAP session item.
 */
function createSessionHTML(session) {
    // Determine icon based on serverStatus first, then session state
    let stateIcon = '<span>⏹️</span>';
    let statusText = session.state ? session.state.charAt(0) + session.state.slice(1).toLowerCase() : '';
    let statusClass = 'status-stopped';

    if (session.serverStatus === 'INSTALLING') {
        stateIcon = '<span>⏳</span>';
        statusText = 'Installing';
        statusClass = 'status-installing';
    } else if (session.serverStatus === 'STARTING') {
        stateIcon = '<span>⏳</span>';
        statusText = 'Starting';
        statusClass = 'status-starting';
    } else if (session.state === 'RUNNING') {
        stateIcon = '<span>▶️</span>';
        statusText = 'Running';
        statusClass = 'status-running';
    } else if (session.state === 'PAUSED') {
        stateIcon = '<span>⏸️</span>';
        statusText = 'Paused';
        statusClass = 'status-running-not-ready';
    } else if (session.state === 'ERROR' || session.serverStatus === 'START_FAILED' || session.serverStatus === 'ERROR') {
        stateIcon = '<span>❌</span>';
        statusText = 'Error';
        statusClass = 'status-error';
    }

    // Determine action buttons
    const isStopped = session.state === 'CREATED' || session.serverStatus === 'STOPPED' || session.serverStatus === 'START_FAILED' || session.serverStatus === 'ERROR';
    const isRunningOrStarting = session.state === 'RUNNING' || session.serverStatus === 'STARTING' || session.serverStatus === 'INSTALLING';

    let actions = '';
    if (isStopped) {
        actions = `
            <button class="server-action-btn" onclick='event.stopPropagation(); selectDapSession("${session.sessionId}"); setTimeout(() => { const btn = document.getElementById("dap-launch-btn-${session.sessionId}"); if(btn) btn.click(); }, 100);' title="Run (without debugging)" style="font-size: 0.7rem; padding: 0.15rem 0.3rem;">▶</button>
            <button class="server-action-btn" onclick='event.stopPropagation(); selectDapSession("${session.sessionId}"); setTimeout(() => { const btn = document.getElementById("dap-debug-btn-${session.sessionId}"); if(btn) btn.click(); }, 100);' title="Debug (with breakpoints)" style="font-size: 0.7rem; padding: 0.15rem 0.3rem;">🐛</button>
        `;
    } else if (isRunningOrStarting) {
        actions = `<button class="server-action-btn" onclick='event.stopPropagation(); stopDapSession("${session.sessionId}")' title="Stop" style="font-size: 0.7rem; padding: 0.15rem 0.3rem;">⏹</button>`;
    }

    return `
        <div data-session-id="${session.sessionId}" class="dap-session-item" style="margin-left: 2rem; padding: 0.25rem 0.5rem; display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.85rem; opacity: 0.9; border-radius: 4px; transition: background-color 0.2s;" onclick="selectDapSession('${session.sessionId}')">
            ${stateIcon}
            <span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${session.sessionName}</span>
            <span class="status-badge ${statusClass}" style="font-size: 0.7rem; padding: 0.1rem 0.4rem;">${statusText}</span>
            ${actions}
        </div>
    `;
}

// ============================================
// Keyboard shortcuts for DAP console (Ctrl+A, Ctrl+F)
// ============================================

let dapFullTextSelected = false;

document.addEventListener('keydown', (e) => {
    const dapTracesContainer = document.getElementById(`dap-traces-container-${window.currentDapSessionId}`);

    // Ctrl+A to select all DAP console content
    if (e.ctrlKey && e.key === 'a' && dapTracesContainer && window.currentDapSessionId) {
        const activeElement = document.activeElement;
        if (activeElement.tagName !== 'INPUT' && activeElement.tagName !== 'TEXTAREA') {
            e.preventDefault();
            selectAllDapConsoleContent();
        }
    }

    // Ctrl+C after Ctrl+A to copy full content (including folded)
    if (e.ctrlKey && e.key === 'c' && dapFullTextSelected && window.currentDapSessionId) {
        e.preventDefault();
        copyFullDapConsoleContent();
    }

    // Ctrl+F to open search in DAP console
    if (e.ctrlKey && e.key === 'f' && dapTracesContainer && window.currentDapSessionId) {
        e.preventDefault();
        openDapSearch();
    }

    // Escape to close search
    if (e.key === 'Escape') {
        closeDapSearch();
    }
});

// Reset flag when clicking
document.addEventListener('mousedown', () => {
    dapFullTextSelected = false;
});

function selectAllDapConsoleContent() {
    const container = document.getElementById(`dap-traces-container-${window.currentDapSessionId}`);
    if (!container) return;

    const range = document.createRange();
    range.selectNodeContents(container);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);

    dapFullTextSelected = true;
    container.focus();
}

function copyFullDapConsoleContent() {
    const traces = window.dapTracesBySession?.[window.currentDapSessionId] || [];

    let fullText = '';
    traces.forEach(trace => {
        fullText += trace.jsonContent + '\n\n';
    });

    navigator.clipboard.writeText(fullText).then(() => {
        dapFullTextSelected = false;
    }).catch(err => {
        console.error('Failed to copy DAP console:', err);
        dapFullTextSelected = false;
    });
}

function openDapSearch() {
    // TODO: Implement search box for DAP (similar to LSP)
    console.log('DAP search not yet implemented');
}

function closeDapSearch() {
    // TODO: Implement close search for DAP
}

/**
 * Load launch configuration templates for a DAP server.
 * Called when a debug session is displayed.
 */
async function loadLaunchConfigurationTemplates(sessionId, serverId) {
    try {
        const response = await fetch(`/api/admin/dap/sessions/templates/${serverId}`);
        if (!response.ok) {
            console.warn(`No templates found for ${serverId}`);
            return;
        }

        const data = await response.json();
        const templates = data.templates || [];

        // Populate the template selector
        const selector = document.getElementById(`launch-template-selector-${sessionId}`);
        if (!selector) return;

        // Clear existing options (except the first "Select template..." option)
        selector.innerHTML = '<option value="">Select template...</option>';

        // Add template options
        templates.forEach((template, index) => {
            const option = document.createElement('option');
            option.value = index;
            option.textContent = template.label;
            option.dataset.body = template.body;
            selector.appendChild(option);
        });

        // Store templates on the selector for later use
        selector.dataset.templates = JSON.stringify(templates);

    } catch (error) {
        console.error('Failed to load launch configuration templates:', error);
    }
}

/**
 * Apply a selected launch configuration template to the editor.
 */
function applyLaunchTemplate(sessionId, templateIndex) {
    if (!templateIndex) return; // "Select template..." option

    const selector = document.getElementById(`launch-template-selector-${sessionId}`);
    if (!selector) return;

    const templates = JSON.parse(selector.dataset.templates || '[]');
    const template = templates[templateIndex];
    if (!template) return;

    // Parse and pretty-print the template JSON
    try {
        const json = JSON.parse(template.body);
        const editor = document.getElementById('launch-config-editor');
        editor.value = JSON.stringify(json, null, 2);

        // Reset selector to "Select template..."
        selector.value = '';
    } catch (error) {
        console.error('Failed to apply template:', error);
    }
}

// Expose functions globally
window.createNewTestSession = createNewTestSession;
window.launchDapSession = launchDapSession;
window.stopDapSession = stopDapSession;
window.deleteDapSession = deleteDapSession;
window.selectDapSession = selectDapSession;
window.renderDapTracesForSession = renderDapTracesForSession;
window.loadAllDapServers = loadAllDapServers;
window.showDapServerDetails = showDapServerDetails;
window.switchDapServerTab = switchDapServerTab;
window.toggleDapTrace = toggleDapTrace;
window.toggleAllDapTraces = toggleAllDapTraces;
window.clearDapConsole = clearDapConsole;
window.changeDapTraceLevel = changeDapTraceLevel;
window.onDapSessionUpdate = onDapSessionUpdate;
window.loadLaunchConfigurationTemplates = loadLaunchConfigurationTemplates;
window.applyLaunchTemplate = applyLaunchTemplate;
