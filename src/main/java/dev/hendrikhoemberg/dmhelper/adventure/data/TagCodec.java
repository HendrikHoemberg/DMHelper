package dev.hendrikhoemberg.dmhelper.adventure.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class TagCodec {

    private TagCodec() {}

    public static List<String> parse(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var part : tags.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static String format(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }
}
