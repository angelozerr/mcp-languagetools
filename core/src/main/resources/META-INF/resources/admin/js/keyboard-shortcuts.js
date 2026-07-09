/**
 * Shared keyboard shortcuts for LSP, MCP, and DAP consoles.
 * Vanilla JS (no modules) - can be included via <script> tag.
 *
 * Usage:
 *   KeyboardShortcuts.register({
 *     getActiveConsole: () => ({ type: 'lsp', containerId: 'console-output', data: traces }),
 *     onSearch: (consoleInfo) => { ... },
 *     onCloseSearch: () => { ... }
 *   });
 */

const KeyboardShortcuts = (function() {
    let fullTextSelected = false;
    let handlers = null;
    let keydownListener = null;
    let mousedownListener = null;

    function handleKeyDown(e) {
        if (!handlers) return;

        // Escape - Close search (handle FIRST, even if in input)
        if (e.key === 'Escape') {
            if (handlers.onCloseSearch) {
                handlers.onCloseSearch();
            }
            return;
        }

        const activeElement = document.activeElement;
        const isInputFocused = activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA';

        // Don't interfere with Monaco Editor or CodeMirror
        const isInEditor = activeElement.classList?.contains('monaco-editor') ||
                          activeElement.closest('.monaco-editor') ||
                          activeElement.classList?.contains('CodeMirror') ||
                          activeElement.closest('.CodeMirror');

        if (isInputFocused || isInEditor) return;

        const consoleInfo = handlers.getActiveConsole();
        if (!consoleInfo) return;

        // Ctrl+A - Select all console content
        if (e.ctrlKey && e.key === 'a') {
            e.preventDefault();
            selectAllConsoleContent(consoleInfo);
        }

        // Ctrl+C after Ctrl+A - Copy full content (including folded)
        if (e.ctrlKey && e.key === 'c' && fullTextSelected) {
            e.preventDefault();
            copyFullConsoleContent(consoleInfo);
        }

        // Ctrl+F - Open search
        if (e.ctrlKey && e.key === 'f') {
            e.preventDefault();
            if (handlers.onSearch) {
                handlers.onSearch(consoleInfo);
            }
        }
    }

    function handleMouseDown() {
        fullTextSelected = false;
    }

    function selectAllConsoleContent(consoleInfo) {
        const container = document.getElementById(consoleInfo.containerId);
        if (!container) return;

        const range = document.createRange();
        range.selectNodeContents(container);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);

        fullTextSelected = true;
        container.focus();
    }

    function copyFullConsoleContent(consoleInfo) {
        const traces = consoleInfo.data || [];

        let fullText = '';
        traces.forEach(trace => {
            // Handle both trace.jsonContent (LSP/MCP) and trace content formats
            const content = trace.jsonContent || trace.content || trace;
            fullText += content + '\n\n';
        });

        navigator.clipboard.writeText(fullText).then(() => {
            fullTextSelected = false;
            console.log(`[${consoleInfo.type.toUpperCase()}] Copied ${traces.length} traces to clipboard`);
        }).catch(err => {
            console.error(`[${consoleInfo.type.toUpperCase()}] Failed to copy:`, err);
            fullTextSelected = false;
        });
    }

    // Public API
    return {
        /**
         * Register keyboard shortcuts
         * @param {Object} h - Handlers
         * @param {Function} h.getActiveConsole - Returns { type, containerId, data }
         * @param {Function} h.onSearch - Called on Ctrl+F
         * @param {Function} h.onCloseSearch - Called on Escape
         */
        register: function(h) {
            if (handlers) {
                console.warn('[KeyboardShortcuts] Already registered - unregistering first');
                this.unregister();
            }

            handlers = h;
            keydownListener = handleKeyDown;
            mousedownListener = handleMouseDown;

            document.addEventListener('keydown', keydownListener);
            document.addEventListener('mousedown', mousedownListener);
            console.log('[KeyboardShortcuts] Registered');
        },

        unregister: function() {
            if (keydownListener) {
                document.removeEventListener('keydown', keydownListener);
            }
            if (mousedownListener) {
                document.removeEventListener('mousedown', mousedownListener);
            }
            handlers = null;
            keydownListener = null;
            mousedownListener = null;
            fullTextSelected = false;
            console.log('[KeyboardShortcuts] Unregistered');
        }
    };
})();
