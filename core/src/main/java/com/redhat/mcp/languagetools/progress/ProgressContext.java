package com.redhat.mcp.languagetools.progress;

import java.util.Objects;

/**
 * Context information for a progress monitor.
 * Identifies what operation is being tracked.
 */
public class ProgressContext {

    /**
     * Type of operation being tracked
     */
    public enum OperationType {
        INSTALLATION,   // Installing an LSP/DAP server
        STARTING,       // Starting an LSP/DAP server
        OPERATION       // Running an LSP operation (findReferences, etc.)
    }

    private final String id;                // Unique identifier
    private final OperationType type;
    private final String serverId;          // LSP/DAP server ID (if applicable)
    private final String workspaceUri;      // Workspace URI (if applicable)
    private final String operationName;     // Operation name (if type=OPERATION)

    private ProgressContext(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.serverId = builder.serverId;
        this.workspaceUri = builder.workspaceUri;
        this.operationName = builder.operationName;
    }

    public String getId() {
        return id;
    }

    public OperationType getType() {
        return type;
    }

    public String getServerId() {
        return serverId;
    }

    public String getWorkspaceUri() {
        return workspaceUri;
    }

    public String getOperationName() {
        return operationName;
    }

    /**
     * Get a human-readable description of this context.
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name().toLowerCase());

        if (serverId != null) {
            sb.append(" [").append(serverId).append("]");
        }

        if (operationName != null) {
            sb.append(" - ").append(operationName);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgressContext that = (ProgressContext) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private OperationType type;
        private String serverId;
        private String workspaceUri;
        private String operationName;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(OperationType type) {
            this.type = type;
            return this;
        }

        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public Builder workspaceUri(String workspaceUri) {
            this.workspaceUri = workspaceUri;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public ProgressContext build() {
            // Generate ID if not provided
            if (id == null) {
                id = java.util.UUID.randomUUID().toString();
            }
            Objects.requireNonNull(type, "type is required");
            return new ProgressContext(this);
        }
    }

    // ==================== Convenience factory methods ====================

    /**
     * Create context for server installation.
     */
    public static ProgressContext forInstallation(String serverId) {
        return builder()
                .type(OperationType.INSTALLATION)
                .serverId(serverId)
                .build();
    }

    /**
     * Create context for server starting.
     */
    public static ProgressContext forStarting(String serverId, String workspaceUri) {
        return builder()
                .type(OperationType.STARTING)
                .serverId(serverId)
                .workspaceUri(workspaceUri)
                .build();
    }

    /**
     * Create context for LSP operation.
     */
    public static ProgressContext forOperation(String serverId, String operationName) {
        return builder()
                .type(OperationType.OPERATION)
                .serverId(serverId)
                .operationName(operationName)
                .build();
    }
}
