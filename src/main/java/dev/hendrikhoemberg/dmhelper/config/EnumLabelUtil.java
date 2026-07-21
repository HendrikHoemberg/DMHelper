package dev.hendrikhoemberg.dmhelper.config;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns an enum constant into a display label: NOT_STARTED -> "Not Started".
 *
 * <p>Single presentation-layer mapping, applied uniformly. Raw constants must still be used
 * for th:value, th:selected and hx-vals -- only display positions get labels.
 */
@Component
public class EnumLabelUtil {

    /** Display label for any value; null renders as "" so templates need no guard. */
    public String label(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> constant) {
            return labelOf(constant.name());
        }
        return value.toString();
    }

    /** Display label for a raw constant name. */
    public String labelOf(String constantName) {
        if (constantName == null || constantName.isBlank()) {
            return "";
        }
        return Arrays.stream(constantName.split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
