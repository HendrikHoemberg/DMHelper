package dev.hendrikhoemberg.dmhelper.threat.service;

import java.util.regex.Pattern;

public final class ThreatValidationRules {

    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");
    public static final int MIN_DC = 0;
    public static final int MAX_DC = 40;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 20;

    private ThreatValidationRules() {}
}
