package com.ibm.mcp.languagetools.dap.transport;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Default transport using standard input/output streams (stdio).
 * Typically used when the DAP server process is launched by the client.
 */
public class StdioTransportStreams extends TransportStreams {

    public StdioTransportStreams(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }
}
