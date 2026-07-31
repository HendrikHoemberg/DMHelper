package dev.hendrikhoemberg.dmhelper.config;

/** WCAG 2.1 relative-luminance contrast, for design-contract assertions only. */
final class ColorContrast {

    private ColorContrast() {
    }

    static double ratio(String foregroundHex, String backgroundHex) {
        double a = luminance(foregroundHex);
        double b = luminance(backgroundHex);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(String hex) {
        String value = hex.trim();
        if (!value.startsWith("#")) {
            throw new IllegalArgumentException("Expected a hex color, got: " + hex);
        }
        value = value.substring(1);
        if (value.length() == 3) {
            StringBuilder expanded = new StringBuilder();
            for (char c : value.toCharArray()) expanded.append(c).append(c);
            value = expanded.toString();
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected a 3- or 6-digit hex color, got: " + hex);
        }
        double r = channel(Integer.parseInt(value.substring(0, 2), 16));
        double g = channel(Integer.parseInt(value.substring(2, 4), 16));
        double b = channel(Integer.parseInt(value.substring(4, 6), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int raw) {
        double s = raw / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
