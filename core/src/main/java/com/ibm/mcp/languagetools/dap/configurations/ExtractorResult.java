package com.ibm.mcp.languagetools.dap.configurations;

/**
 * Extractor result for network address extraction.
 *
 * @param matches true if the result matches the input from NetworkAddressExtractor
 * @param address the address value retrieved from ${address} and null otherwise
 * @param port    the port value retrieved from ${port} and null otherwise
 */
public record ExtractorResult(
        boolean matches,
        String address,
        String port) {
}
