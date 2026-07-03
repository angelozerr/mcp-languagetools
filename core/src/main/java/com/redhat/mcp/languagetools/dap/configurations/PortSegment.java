package com.redhat.mcp.languagetools.dap.configurations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic segment for ${port} - extracts port number.
 */
public class PortSegment extends DynamicSegment {

    private static final Pattern PORT_PATTERN = Pattern.compile("^(\\d+)");

    public PortSegment() {
        super("${port}", DynamicSegmentType.PORT);
    }

    @Override
    public String matches(String input) {
        Matcher matcher = PORT_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
