/**
 * Admin UI - LSP (Language Server Protocol) Global Management
 *
 * Handles global LSP server listing with Overview/Install tabs
 */

let selectedAllServer = null; // Track selected server in global Servers tab
let currentServerTab = 'overview'; // Track current tab: overview, contributions, install
let allServersLoaded = false;

/**
 * Load all global LSP servers.
 */
async function loadAllLspServers(serverIdToSelect) {
    try {
        // Use cached server configs from admin.js (include both LSP and DAP for contribution detection)
        const lspServers = Object.values(window.lspConfigs || {});
        const dapServers = Object.values(window.dapConfigs || {}).map(s => ({...s, isDap: true}));
        const allServers = [...lspServers, ...dapServers];

        const container = document.getElementById('lsp-servers-list');
        if (!container) {
            console.error('lsp-servers-list container not found');
            return;
        }

        // Calculate contributedBy for all servers (including DAP contributions)
        const contributedByMap = buildContributedByMap(allServers);

        // Render only LSP servers in the list
        container.innerHTML = lspServers.map(server => {
            const isActive = selectedAllServer === server.id ? 'active' : '';
            const extensionClass = server.isExtension ? 'server-extension' : '';
            const extensionBadge = server.isExtension ? ' <span style="color: #999999; font-size: 0.85em;">(Extension)</span>' : '';
            const serverIcon = server.isExtension ? '🧩' : '🚀';
            const contributeInfo = formatContributeInfo(server, contributedByMap);
            return `
                <div class="server-item ${isActive} ${extensionClass}" onclick="showServerDetails('${server.id}')">
                    <div class="server-name">
                        <span class="server-source-icon">${serverIcon}</span>
                        ${server.name}${extensionBadge}
                    </div>
                    <div class="server-id" ${contributeInfo.tooltip ? `title="${contributeInfo.tooltip}"` : ''}>${server.id}${contributeInfo.text}</div>
                </div>
            `;
        }).join('');

        allServersLoaded = true;

        // Auto-select: 1) specified server, 2) previously selected, 3) first server
        if (lspServers.length > 0) {
            let serverToShow;
            if (serverIdToSelect && lspServers.find(s => s.id === serverIdToSelect)) {
                serverToShow = serverIdToSelect;
            } else if (selectedAllServer && lspServers.find(s => s.id === selectedAllServer)) {
                serverToShow = selectedAllServer;
            } else {
                serverToShow = lspServers[0].id;
            }
            showServerDetails(serverToShow);
        }
    } catch (error) {
        console.error('Failed to load all LSP servers:', error);
    }
}

/**
 * Show details for a global LSP server with Overview/Contributions/Install tabs.
 */
async function showServerDetails(serverId) {
    // Update selected server
    selectedAllServer = serverId;

    // Re-render server list to update active state
    const lspServers = Object.values(window.lspConfigs || {});
    const dapServers = Object.values(window.dapConfigs || {}).map(s => ({...s, isDap: true}));
    const allServers = [...lspServers, ...dapServers];
    const contributedByMap = buildContributedByMap(allServers);
    const container = document.getElementById('lsp-servers-list');

    // Render only LSP servers in the list
    container.innerHTML = lspServers.map(server => {
        const isActive = selectedAllServer === server.id ? 'active' : '';
        const extensionClass = server.isExtension ? 'server-extension' : '';
        const extensionBadge = server.isExtension ? ' <span style="color: #999999; font-size: 0.85em;">(Extension)</span>' : '';
        const serverIcon = server.isExtension ? '🧩' : '🚀';
        const contributeInfo = formatContributeInfo(server, contributedByMap);
        return `
            <div class="server-item ${isActive} ${extensionClass}" onclick="showServerDetails('${server.id}')">
                <div class="server-name">
                    <span class="server-source-icon">${serverIcon}</span>
                    ${server.name}${extensionBadge}
                </div>
                <div class="server-id" ${contributeInfo.tooltip ? `title="${contributeInfo.tooltip}"` : ''}>${server.id}${contributeInfo.text}</div>
            </div>
        `;
    }).join('');

    const details = window.lspConfigs[serverId];
    if (!details) {
        console.error('Server not found:', serverId);
        return;
    }

    try {
        // Show console column
        const appContainer = document.querySelector('.app-container');
        const consoleColumn = document.querySelector('.console-container');
        consoleColumn.style.display = 'flex';
        appContainer.style.gridTemplateColumns = '400px 1fr';
        consoleColumn.style.gridColumn = '2';

        // Build details HTML
        const serverIcon = details.isExtension ? '🧩' : '🚀';

        // Overview tab content (pass allServers for contribution detection)
        const detailsHTML = buildServerDetailsHTML(details, allServers);

        // Contributions tab content (use same function as workspace, pass allServers)
        const contributionsHTML = formatContributionsSection(details, allServers);

        const html = `
            <div class="console-header">
                <div class="console-title">
                    <span class="server-source-icon">${serverIcon}</span>
                    ${details.name || details.id}
                </div>
                <div class="console-tabs">
                    <button class="tab-button ${currentServerTab === 'overview' ? 'active' : ''}" onclick="switchServerTab('overview')">Overview</button>
                    <button class="tab-button ${currentServerTab === 'contributions' ? 'active' : ''}" onclick="switchServerTab('contributions')">Contributions</button>
                    <button class="tab-button ${currentServerTab === 'install' ? 'active' : ''}" onclick="switchServerTab('install')">Install</button>
                </div>
            </div>
            <div class="tab-content">
                <div id="server-overview-tab" class="tab-panel ${currentServerTab === 'overview' ? 'active' : ''}">
                    <div class="details-panel" style="padding: 2rem; color: #cccccc; overflow-y: auto;">
                        ${detailsHTML}
                        <div style="margin-top: 2rem; padding: 1rem; background: #252526; border-left: 3px solid #007acc; border-radius: 4px;">
                            <strong>Note:</strong> To run this server, open a workspace using an MCP client.
                        </div>
                    </div>
                </div>
                <div id="server-contributions-tab" class="tab-panel ${currentServerTab === 'contributions' ? 'active' : ''}" style="overflow-y: auto;">
                    <div id="server-diagram-container" style="width: 100%; height: 400px; background: #1e1e1e; border-bottom: 1px solid #333;"></div>
                    <div class="details-panel" style="padding: 2rem; color: #cccccc;">
                        ${contributionsHTML || '<p class="detail-value">No contributions</p>'}
                    </div>
                </div>
                <div id="server-install-tab" class="tab-panel ${currentServerTab === 'install' ? 'active' : ''}">
                    <div class="install-panel">
                        <h3>Installer Configuration</h3>
                        <div class="install-info">
                            <p><strong>Server:</strong> ${details.name}</p>
                            <p><strong>ID:</strong> ${details.id}</p>
                        </div>
                        <div class="installer-editor">
                            <div class="editor-header">
                                <span>installer.json</span>
                                <div class="editor-actions">
                                    <button class="editor-btn" onclick="saveInstallerJson('${details.id}')" title="Save">💾 Save</button>
                                    <button class="editor-btn" onclick="resetInstallerJson('${details.id}')" title="Reset">↻ Reset</button>
                                </div>
                            </div>
                            <textarea id="installer-json-editor" class="json-editor" spellcheck="false"></textarea>
                        </div>
                        <button class="install-button" onclick="runInstaller('${details.id}')">▶ Run Installer</button>
                        <div id="install-output" class="install-output"></div>
                    </div>
                </div>
            </div>
        `;

        const consoleArea = document.getElementById('console-area');
        consoleArea.innerHTML = html;

        // Load installer.json for this server
        loadInstallerJson(details.id);

        // Render diagram (will be called when switching to diagram tab)
        // Store servers data for diagram rendering (include both LSP and DAP)
        window.currentDiagramServers = allServers;
        window.currentDiagramServerId = details.id;

        // If contributions tab is active, render diagram immediately
        if (currentServerTab === 'contributions') {
            setTimeout(() => renderServerDiagram(allServers, details.id), 100);
        }

    } catch (error) {
        console.error('Failed to load server details:', error);
        document.getElementById('console-area').innerHTML = `
            <div class="placeholder" style="color: #ff6b6b;">
                Failed to load server details
            </div>
        `;
    }
}

/**
 * Build server details HTML for Overview tab.
 */
function buildServerDetailsHTML(details, allServers) {
    // Document selector
    let docSelectorHTML = '<p style="color: #999;">None configured</p>';
    if (details.documentSelector && details.documentSelector.length > 0) {
        docSelectorHTML = details.documentSelector.map(selector => {
            const parts = [];
            if (selector.language) parts.push(`Language: <code>${selector.language}</code>`);
            if (selector.pattern) parts.push(`Pattern: <code>${selector.pattern}</code>`);
            if (selector.scheme) parts.push(`Scheme: <code>${selector.scheme}</code>`);
            return `<li>${parts.join(', ')}</li>`;
        }).join('');
        docSelectorHTML = `<ul style="margin: 0.5rem 0; padding-left: 1.5rem;">${docSelectorHTML}</ul>`;
    }

    // Command
    let commandHTML = '<p style="color: #999;">None (contribution-only server)</p>';
    if (details.command) {
        if (typeof details.command === 'string') {
            commandHTML = `<code>${details.command}</code>`;
        } else {
            commandHTML = Object.entries(details.command).map(([os, cmd]) =>
                `<div style="margin-bottom: 0.25rem;"><strong>${os}:</strong> <code>${cmd}</code></div>`
            ).join('');
        }
    }

    return `
        <h3 style="margin-top: 0; color: #569cd6;">Server Information</h3>

        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Server ID:</strong>
            <p style="margin: 0.25rem 0; color: #d4d4d4;"><code>${details.id}</code></p>
        </div>

        ${details.description ? `
        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Description:</strong>
            <p style="margin: 0.25rem 0; color: #d4d4d4;">${details.description}</p>
        </div>
        ` : ''}

        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Command:</strong>
            <p style="margin: 0.25rem 0; color: #d4d4d4;">${commandHTML}</p>
        </div>

        ${details.args && details.args.length > 0 ? `
        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Arguments:</strong>
            <ul style="margin: 0.5rem 0; padding-left: 1.5rem; color: #d4d4d4;">
                ${details.args.map(arg => `<li><code>${arg}</code></li>`).join('')}
            </ul>
        </div>
        ` : ''}

        <div style="margin-bottom: 1.5rem;">
            <strong style="color: #569cd6;">Supported Languages/Files:</strong>
            ${docSelectorHTML}
        </div>
    `;
}

/**
 * Build contributions HTML for Contributions tab.
 */
function buildContributionsHTML(details) {
    if (!details.contributes) return '';

    let html = '<h3 style="margin-top: 0; color: #4ec9b0;">Contributions</h3>';

    if (details.contributes.languages) {
        html += `
            <div style="margin-bottom: 1.5rem;">
                <strong style="color: #569cd6;">Languages:</strong>
                <ul style="margin: 0.5rem 0; padding-left: 1.5rem; color: #d4d4d4;">
                    ${details.contributes.languages.map(lang =>
                        `<li><strong>${lang.id}</strong>${lang.extensions ? ` (${lang.extensions.join(', ')})` : ''}</li>`
                    ).join('')}
                </ul>
            </div>
        `;
    }

    if (details.contributes.snippets) {
        html += `
            <div style="margin-bottom: 1.5rem;">
                <strong style="color: #569cd6;">Snippets:</strong>
                <p style="color: #d4d4d4; margin: 0.25rem 0;">${details.contributes.snippets.length} snippet file(s)</p>
            </div>
        `;
    }

    return html;
}

/**
 * Switch between LSP server tabs (Overview/Contributions/Install).
 */
function switchServerTab(tabName) {
    currentServerTab = tabName;

    // Re-render current server to update tabs
    if (selectedAllServer) {
        showServerDetails(selectedAllServer);
    }

    // Render diagram when switching to contributions tab
    if (tabName === 'contributions' && window.currentDiagramServers && window.currentDiagramServerId) {
        setTimeout(() => renderServerDiagram(window.currentDiagramServers, window.currentDiagramServerId), 100);
    }
}

/**
 * Load installer.json for an LSP server.
 */
async function loadInstallerJson(serverId) {
    try {
        const response = await fetch(`/api/admin/lsp/configs/${serverId}/installer`);
        if (!response.ok) {
            throw new Error('Failed to load installer.json');
        }

        const installerJson = await response.json();
        const editor = document.getElementById('installer-json-editor');
        if (editor) {
            editor.value = JSON.stringify(installerJson, null, 2);
        }
    } catch (error) {
        console.error('Failed to load installer.json:', error);
        const editor = document.getElementById('installer-json-editor');
        if (editor) {
            editor.value = '// No installer.json found for this server';
        }
    }
}

/**
 * Save installer.json for an LSP server.
 */
async function saveInstallerJson(serverId) {
    const editor = document.getElementById('installer-json-editor');
    if (!editor) return;

    try {
        const installerJson = JSON.parse(editor.value);

        const response = await fetch(`/api/admin/lsp/configs/${serverId}/installer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(installerJson)
        });

        if (!response.ok) {
            throw new Error('Failed to save installer.json');
        }

        if (window.showAlert) {
            window.showAlert('Success', 'Installer configuration saved successfully.');
        }
    } catch (error) {
        console.error('Failed to save installer.json:', error);
        if (window.showAlert) {
            window.showAlert('Error', 'Failed to save installer.json: ' + error.message);
        }
    }
}

/**
 * Reset installer.json to original.
 */
function resetInstallerJson(serverId) {
    loadInstallerJson(serverId);
}

/**
 * Run installer for an LSP server.
 */
async function runInstaller(serverId) {
    const outputDiv = document.getElementById('install-output');
    if (!outputDiv) return;

    outputDiv.innerHTML = '<div style="color: #4ec9b0;">Running installer...</div>';

    try {
        const response = await fetch(`/api/admin/lsp/configs/${serverId}/install`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Installation failed');
        }

        const result = await response.json();
        outputDiv.innerHTML = `
            <div style="color: #4ec9b0;">✓ Installation completed successfully</div>
            <pre style="margin-top: 0.5rem; color: #d4d4d4;">${JSON.stringify(result, null, 2)}</pre>
        `;
    } catch (error) {
        console.error('Failed to run installer:', error);
        outputDiv.innerHTML = `<div style="color: #f48771;">❌ Installation failed: ${error.message}</div>`;
    }
}

/**
 * Helper: Build contributedBy map.
 */
function buildContributedByMap(servers) {
    const map = {};
    servers.forEach(server => {
        if (server.contributes && server.contributes.contributeServerConfigurations) {
            server.contributes.contributeServerConfigurations.forEach(targetId => {
                if (!map[targetId]) map[targetId] = [];
                map[targetId].push(server.id);
            });
        }
    });
    return map;
}

/**
 * Helper: Format contribute info for server list.
 */
function formatContributeInfo(server, contributedByMap) {
    const contributors = contributedByMap[server.id] || [];
    if (contributors.length === 0) return { text: '', tooltip: '' };

    const text = ` ← ${contributors.length}`;
    const tooltip = `Contributions from: ${contributors.join(', ')}`;
    return { text, tooltip };
}

// renderServerDiagram() is defined in diagram.js - do not override it here

// Expose functions globally
window.loadAllLspServers = loadAllLspServers;
window.showServerDetails = showServerDetails;
window.switchServerTab = switchServerTab;
window.loadInstallerJson = loadInstallerJson;
window.saveInstallerJson = saveInstallerJson;
window.resetInstallerJson = resetInstallerJson;
window.runInstaller = runInstaller;
window.buildContributedByMap = buildContributedByMap;
window.formatContributeInfo = formatContributeInfo;
window.renderServerDiagram = renderServerDiagram;
