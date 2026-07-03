package com.redhat.mcp.languagetools.dap.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract base class for DAP transport streams.
 * Provides input and output streams for DAP communication.
 */
public abstract class TransportStreams implements AutoCloseable {

    protected InputStream in = null;
    protected OutputStream out = null;

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;

        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                exception = e;
            }
        }

        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }

        if (exception != null) {
            throw exception;
        }
    }
}
