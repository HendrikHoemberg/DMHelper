package dev.hendrikhoemberg.dmhelper.common.web;

import java.util.Locale;

public final class DisplayLabels {
    private DisplayLabels() {}

    public static String humanizeEnum(Enum<?> value) {
        if (value == null) return "\u2014";
        return humanize(value.name());
    }

    public static String humanize(String constant) {
        if (constant == null || constant.isBlank()) return "\u2014";
        String lower = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
