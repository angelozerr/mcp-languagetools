package com.ibm.mcp.languagetools.dap.transport;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.Socket;

/**
 * Socket-based transport for DAP communication.
 * Connects to a DAP server via TCP socket.
 */
public class SocketTransportStreams extends TransportStreams {

    private static final Logger LOG = Logger.getLogger(SocketTransportStreams.class);

    private final Socket socket;

    /**
     * Create a socket transport to the given host and port.
     *
     * @param host the host address (e.g., "127.0.0.1" or "localhost")
     * @param port the port number
     * @throws IOException if connection fails
     */
    public SocketTransportStreams(String host, int port) throws IOException {
        LOG.infof("Connecting to DAP server via socket: %s:%d", host, port);
        this.socket = new Socket(host, port);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        LOG.infof("Connected to DAP server via socket: %s:%d", host, port);
    }

    @Override
    public void close() throws IOException {
        LOG.infof("Closing socket transport");
        try {
            super.close();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public Socket getSocket() {
        return socket;
    }
}
