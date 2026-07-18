package com.ibm.mcp.languagetools.dap.server;

import com.ibm.mcp.languagetools.dap.configurations.NetworkAddressExtractor;

/**
 * Configuration for server readiness.
 * This class holds the configuration options related to server readiness,
 * such as the pattern for matching the server ready message and the connection timeout.
 */
public class ServerReadyConfig {

    // Used by "launch" request
    private final NetworkAddressExtractor debugServerReadyPattern;
    private final Integer connectTimeout;

    // Used by "attach" request
    private final String address;
    private final Integer port;

    /**
     * Creates a new ServerReadyConfig using the provided pattern.
     *
     * @param pattern the pattern to be used for extracting the server ready message.
     */
    public ServerReadyConfig(String pattern) {
        this(new NetworkAddressExtractor(pattern));
    }

    /**
     * Creates a new ServerReadyConfig using the provided NetworkAddressExtractor.
     *
     * @param debugServerReadyPattern the NetworkAddressExtractor to extract the server ready message pattern.
     */
    public ServerReadyConfig(NetworkAddressExtractor debugServerReadyPattern) {
        this(debugServerReadyPattern, null, null, null);
    }

    /**
     * Creates a new ServerReadyConfig using the provided connection timeout.
     *
     * @param connectTimeout the connection timeout in milliseconds.
     */
    public ServerReadyConfig(int connectTimeout) {
        this(null, connectTimeout, null, null);
    }

    /**
     * Creates a new ServerReadyConfig for attach mode.
     *
     * @param address the address to attach to
     * @param port the port to attach to
     */
    public ServerReadyConfig(String address, int port) {
        this(null, null, address, port);
    }

    /**
     * Creates a new ServerReadyConfig with both the server ready pattern and connection timeout.
     *
     * @param debugServerReadyPattern the NetworkAddressExtractor to extract the server ready message pattern.
     * @param connectTimeout the connection timeout in milliseconds.
     * @param address the address for attach mode
     * @param port the port for attach mode
     */
    private ServerReadyConfig(NetworkAddressExtractor debugServerReadyPattern,
                             Integer connectTimeout,
                             String address,
                             Integer port) {
        this.debugServerReadyPattern = debugServerReadyPattern;
        this.connectTimeout = connectTimeout;
        this.address = address;
        this.port = port;
    }

    /**
     * Gets the NetworkAddressExtractor used to extract the server ready message pattern.
     *
     * @return the NetworkAddressExtractor, or null if not provided.
     */
    public NetworkAddressExtractor getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    /**
     * Gets the connection timeout value.
     *
     * @return the connection timeout in milliseconds, or null if not provided.
     */
    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public String getAddress() {
        return address;
    }

    public Integer getPort() {
        return port;
    }
}
