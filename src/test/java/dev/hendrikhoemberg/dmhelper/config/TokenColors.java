package dev.hendrikhoemberg.dmhelper.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a token declared in tokens.css to the hex a browser will actually paint,
 * following var() indirection and color-mix(in srgb, ...).
 *
 * <p>A contract that hard-codes the background beside a colour ends up measuring whichever
 * pairing a human picked, not the one the product ships: --border-strong was asserted
 * against --surface-inset, where it reaches 3.94:1, while the boundary that actually
 * renders sits on --surface-raised, where it reached 2.87:1 and failed the 3:1 rule.
 * Resolving through the token graph keeps the assertion honest when a value, an alias hop,
 * or a mix percentage moves.
 */
final class TokenColors {

    private static final Pattern DECLARATION = Pattern.compile("(--[a-z0-9-]+)\\s*:\\s*([^;]+);");
    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
    private static final int MAX_DEPTH = 16;

    private TokenColors() {
    }

    /** The opaque hex a token paints, resolved through var() and color-mix(). */
    static String resolve(String token) {
        return resolveDeclaration("var(" + token + ")");
    }

    /** The opaque hex a declaration value paints, e.g. {@code var(--surface-raised)}. */
    static String resolveDeclaration(String cssValue) {
        return resolveValue(cssValue, declarations(), 0);
    }

    private static Map<String, String> declarations() {
        Map<String, String> declared = new LinkedHashMap<>();
        Matcher m = DECLARATION.matcher(CssRules.read("tokens.css"));
        while (m.find()) declared.put(m.group(1), m.group(2).trim());
        return declared;
    }

    private static String resolveValue(String rawValue, Map<String, String> declared, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Token indirection did not terminate at: " + rawValue);
        }
        String value = rawValue.trim();
        if (value.startsWith("#")) {
            return expandHex(value);
        }
        if (value.startsWith("var(")) {
            List<String> args = splitTopLevel(inside(value, "var"));
            String name = args.get(0).trim();
            if (declared.containsKey(name)) {
                return resolveValue(declared.get(name), declared, depth + 1);
            }
            if (args.size() > 1) {
                return resolveValue(args.get(1), declared, depth + 1);
            }
            throw new IllegalArgumentException(name + " is not declared in tokens.css");
        }
        if (value.startsWith("color-mix(")) {
            return resolveMix(splitTopLevel(inside(value, "color-mix")), declared, depth);
        }
        throw new IllegalArgumentException(
                "Not an opaque colour this resolver can measure: " + rawValue);
    }

    private static String resolveMix(List<String> args, Map<String, String> declared, int depth) {
        if (args.size() != 3 || !args.get(0).trim().equals("in srgb")) {
            throw new IllegalArgumentException("Only color-mix(in srgb, a p%, b) is supported: " + args);
        }
        Weighted first = weighted(args.get(1));
        Weighted second = weighted(args.get(2));
        double firstShare = first.percent() >= 0 ? first.percent()
                : (second.percent() >= 0 ? 1 - second.percent() : 0.5);
        String a = resolveValue(first.color(), declared, depth + 1);
        String b = resolveValue(second.color(), declared, depth + 1);
        return mixSrgb(a, firstShare, b);
    }

    /** CSS interpolates {@code in srgb} in gamma-encoded space, so channels blend directly. */
    private static String mixSrgb(String firstHex, double firstShare, String secondHex) {
        StringBuilder mixed = new StringBuilder("#");
        for (int channel = 1; channel < 7; channel += 2) {
            int a = Integer.parseInt(firstHex.substring(channel, channel + 2), 16);
            int b = Integer.parseInt(secondHex.substring(channel, channel + 2), 16);
            mixed.append(String.format("%02x", Math.round(a * firstShare + b * (1 - firstShare))));
        }
        return mixed.toString();
    }

    private record Weighted(String color, double percent) {
    }

    /** Splits "var(--x) 14%" into its colour and its share; percent is -1 when unstated. */
    private static Weighted weighted(String argument) {
        String value = argument.trim();
        Matcher percent = Pattern.compile("(\\d+(?:\\.\\d+)?)%\\s*$").matcher(value);
        if (percent.find()) {
            return new Weighted(value.substring(0, percent.start()).trim(),
                    Double.parseDouble(percent.group(1)) / 100.0);
        }
        return new Weighted(value, -1);
    }

    private static String inside(String value, String function) {
        int open = value.indexOf('(');
        int close = matchingParen(value, open);
        if (!value.startsWith(function + "(") || close < 0) {
            throw new IllegalArgumentException("Malformed " + function + "(): " + value);
        }
        return value.substring(open + 1, close);
    }

    private static int matchingParen(String value, int open) {
        int depth = 0;
        for (int i = open; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<String> splitTopLevel(String arguments) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(arguments.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(arguments.substring(start));
        return parts;
    }

    private static String expandHex(String value) {
        Matcher m = HEX.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("Expected a 3- or 6-digit hex colour, got: " + value);
        }
        String digits = m.group(1);
        if (digits.length() == 3) {
            StringBuilder expanded = new StringBuilder("#");
            for (char c : digits.toCharArray()) expanded.append(c).append(c);
            return expanded.toString();
        }
        return "#" + digits.toLowerCase();
    }
}
