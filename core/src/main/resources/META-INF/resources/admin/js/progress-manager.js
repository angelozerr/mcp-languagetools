/**
 * Global Progress Manager for Admin UI
 * Tracks multiple concurrent tasks and displays them in a footer panel
 */

// Map of active tasks: taskId -> task object
const activeTasks = new Map();

/**
 * Add or update a task in the progress manager
 * @param {Object} task - Task object with: { id, serverId, title, percent, message, status }
 */
function updateTask(task) {
    if (!task || !task.id) return;

    activeTasks.set(task.id, {
        ...task,
        lastUpdate: Date.now()
    });

    refreshProgressFooter();
    refreshProgressPanel();
}

/**
 * Remove a task from the progress manager
 * @param {string} taskId - Unique task ID
 */
function removeTask(taskId) {
    activeTasks.delete(taskId);
    refreshProgressFooter();
    refreshProgressPanel();
}

/**
 * Clear all tasks
 */
function clearAllTasks() {
    activeTasks.clear();
    refreshProgressFooter();
    refreshProgressPanel();
}

/**
 * Refresh the footer status
 */
function refreshProgressFooter() {
    const statusEl = document.getElementById('progress-status');
    const countEl = document.getElementById('progress-count');
    const iconEl = document.getElementById('progress-icon');

    const count = activeTasks.size;

    if (count === 0) {
        statusEl.textContent = 'No tasks running';
        countEl.textContent = '0';
        iconEl.textContent = '⏸';
    } else if (count === 1) {
        const task = Array.from(activeTasks.values())[0];
        statusEl.textContent = `${task.title} - ${Math.round(task.percent || 0)}%`;
        countEl.textContent = '1';
        iconEl.textContent = '⏵';
    } else {
        statusEl.textContent = `${count} tasks running`;
        countEl.textContent = String(count);
        iconEl.textContent = '⏵';
    }
}

/**
 * Refresh the progress panel content
 */
function refreshProgressPanel() {
    const content = document.getElementById('progress-panel-content');

    if (activeTasks.size === 0) {
        content.innerHTML = '<div style="text-align: center; padding: 2rem; color: #858585;">No active tasks</div>';
        return;
    }

    const tasksHtml = Array.from(activeTasks.values())
        .sort((a, b) => b.lastUpdate - a.lastUpdate)
        .map(task => {
            const percent = Math.round(task.percent || 0);
            return `
                <div class="progress-task-item">
                    <div class="progress-task-header">
                        <div class="progress-task-title">${escapeHtml(task.title)}</div>
                        <div class="progress-task-percent">${percent}%</div>
                    </div>
                    <div class="progress-task-bar">
                        <div class="progress-task-bar-fill" style="width: ${percent}%"></div>
                    </div>
                    ${task.message ? `<div class="progress-task-message">${escapeHtml(task.message)}</div>` : ''}
                </div>
            `;
        })
        .join('');

    content.innerHTML = tasksHtml;
}

/**
 * Toggle the progress panel visibility
 */
function toggleProgressPanel() {
    const panel = document.getElementById('progress-panel');
    panel.classList.toggle('visible');
}

/**
 * Handle progress update messages from WebSocket
 */
function handleProgressUpdate(msg) {
    if (msg.status === 'completed' || msg.status === 'failed') {
        // Remove task after a short delay
        setTimeout(() => removeTask(msg.taskId), 2000);
    } else {
        // Update or create task
        updateTask({
            id: msg.taskId,
            serverId: msg.serverId,
            title: msg.title,
            percent: (msg.progress || 0) * 100,
            message: msg.message,
            status: msg.status
        });
    }
}

// Auto-clean old tasks (remove tasks that haven't updated in 30 seconds)
setInterval(() => {
    const now = Date.now();
    for (const [taskId, task] of activeTasks.entries()) {
        if (now - task.lastUpdate > 30000) {
            removeTask(taskId);
        }
    }
}, 5000);
