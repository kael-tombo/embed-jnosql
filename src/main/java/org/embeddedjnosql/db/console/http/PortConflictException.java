package org.embeddedjnosql.db.console.http;

import java.io.IOException;

/**
 * Thrown when the administration console cannot bind to the configured port or port range.
 */
public class PortConflictException extends IOException {
    private final int requestedPort;
    private final int minPort;
    private final int maxPort;

    public PortConflictException(String message) {
        super(message);
        this.requestedPort = -1;
        this.minPort = -1;
        this.maxPort = -1;
    }

    public PortConflictException(String message, int requestedPort, int minPort, int maxPort) {
        super(message);
        this.requestedPort = requestedPort;
        this.minPort = minPort;
        this.maxPort = maxPort;
    }

    public int getRequestedPort() {
        return requestedPort;
    }

    public int getMinPort() {
        return minPort;
    }

    public int getMaxPort() {
        return maxPort;
    }
}
