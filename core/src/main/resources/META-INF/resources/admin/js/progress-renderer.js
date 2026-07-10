/**
 * Shared progress rendering utilities for Admin UI.
 * Used by both LSP servers and DAP sessions to display installation/launch progress.
 */

/**
 * Render a progress badge with animated progress bar and message.
 *
 * @param {string} label - The status label (e.g., "Installing", "Launching")
 * @param {string} statusClass - CSS class for the badge (e.g., "status-installing")
 * @param {number|null} progressPercent - Progress percentage (0-100), or null if no progress
 * @param {string|null} message - Progress message to display below the bar, or null
 * @returns {string} HTML string for the progress badge
 */
function renderProgressBadge(label, statusClass, progressPercent, message) {
    // If no progress data, just show the status badge
    if (progressPercent == null) {
        return `<span class="status-badge ${statusClass}">${label}</span>`;
    }

    const percent = Math.round(progressPercent);

    // Progress badge with animated bar
    const progressBar = `
        <div class="progress-badge ${statusClass}">
            <div class="progress-badge-header">
                <span class="progress-badge-label">${label}</span>
                <span class="progress-badge-percent">${percent}%</span>
            </div>
            <div class="progress-bar-container">
                <div class="progress-bar-fill" style="width: ${percent}%"></div>
            </div>
            ${message ? `<div class="progress-badge-message">${escapeHtmlLocal(message)}</div>` : ''}
        </div>
    `;

    return progressBar;
}

/**
 * Escape HTML to prevent XSS in progress messages.
 * Note: escapeHtml is already defined in trace-renderer.js, but we need it here too.
 * To avoid conflicts, we create a local scoped version if the global one doesn't exist.
 */
function escapeHtmlLocal(text) {
    // Use global escapeHtml if available, otherwise create inline
    if (typeof escapeHtml !== 'undefined') {
        return escapeHtml(text);
    }
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Update progress for a server/session element by ID.
 * Replaces the status badge with a progress badge if progress data is available.
 *
 * @param {string} elementId - ID of the status badge element
 * @param {string} label - The status label
 * @param {string} statusClass - CSS class for the badge
 * @param {number|null} progressPercent - Progress percentage (0-100), or null
 * @param {string|null} message - Progress message, or null
 */
function updateProgressBadge(elementId, label, statusClass, progressPercent, message) {
    const element = document.getElementById(elementId);
    if (!element) {
        console.warn(`[Progress] Element not found: ${elementId}`);
        return;
    }

    const html = renderProgressBadge(label, statusClass, progressPercent, message);
    element.outerHTML = html;
}
