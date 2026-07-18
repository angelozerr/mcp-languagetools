package com.ibm.mcp.languagetools.dap.configurations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic segment for ${address} - extracts IP address or hostname.
 */
public class AddressSegment extends DynamicSegment {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^([a-zA-Z0-9.-]+)");

    public AddressSegment() {
        super("${address}", DynamicSegmentType.ADDRESS);
    }

    @Override
    public String matches(String input) {
        Matcher matcher = ADDRESS_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
