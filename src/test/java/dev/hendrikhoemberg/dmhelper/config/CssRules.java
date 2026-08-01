package dev.hendrikhoemberg.dmhelper.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A deliberately small flat reader for the app stylesheets. The design-system contract
 * tests need to ask "which selector declares this?", which grepping cannot answer and a
 * real CSS parser would be a new dependency for (spec 2026-07-22 section 13).
 */
final class CssRules {

    static final Path CSS_DIR = Path.of("src/main/resources/static/css");

    static final List<String> ALL_FILES = discoverCssFiles();

    private static final Pattern RAW_COLOR = Pattern.compile(
            "(?<!&)#[0-9a-fA-F]{3,8}\\b|\\brgba?\\([^)]*\\)|\\bhsla?\\([^)]*\\)");

    /** Raw color literals in a CSS or markup source, in document order. */
    static List<String> rawColorLiterals(String source) {
        Matcher m = RAW_COLOR.matcher(source);
        List<String> found = new ArrayList<>();
        while (m.find()) found.add(m.group());
        return found;
    }

    static List<String> discoverCssFiles() {
        try (Stream<Path> files = Files.list(CSS_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".css"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every application stylesheet except the token layer, concatenated. */
    static String allApplicationCss() {
        return ALL_FILES.stream()
                .filter(file -> !file.equals("tokens.css"))
                .map(CssRules::read)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Every Thymeleaf template, concatenated, for inline-style and markup rules. */
    static String allTemplateMarkup() {
        Path root = Path.of("src/main/resources/templates");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".html"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stylesheets that paint the live table surfaces (spec section 10.1 "runtime"). */
    static final List<String> RUNTIME_FILES = List.of(
            "cockpit.css", "cockpit-layout.css", "cockpit-modules.css");

    record Rule(String file, String selector, String body) {
        boolean declares(String property) {
            return matcher(property).find();
        }

        /** The last declared value of the property, or null. */
        String value(String property) {
            Matcher m = matcher(property);
            String found = null;
            while (m.find()) found = m.group(1).trim();
            return found;
        }

        List<String> values(String property) {
            Matcher m = matcher(property);
            List<String> found = new ArrayList<>();
            while (m.find()) found.add(m.group(1).trim());
            return found;
        }

        private Matcher matcher(String property) {
            return Pattern.compile("(?:^|[;{\\s])" + Pattern.quote(property) + "\\s*:([^;}]*)")
                    .matcher(body);
        }

        String where() {
            return file + " { " + selector + " }";
        }
    }

    static List<Rule> of(String... files) {
        List<Rule> rules = new ArrayList<>();
        for (String file : files) collect(file, read(file), rules);
        return rules;
    }

    static List<Rule> of(List<String> files) {
        return of(files.toArray(new String[0]));
    }

    static String read(String file) {
        try {
            return stripComments(Files.readString(CSS_DIR.resolve(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Set<String> definedTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(--[a-z0-9-]+)\\s*:").matcher(String.join("\n",
                ALL_FILES.stream().map(CssRules::read).toList()));
        while (m.find()) tokens.add(m.group(1));
        return tokens;
    }

    static Set<String> referencedTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("var\\(\\s*(--[a-z0-9-]+)").matcher(text);
        while (m.find()) tokens.add(m.group(1));
        return tokens;
    }

    /** How many times a token is referenced across app CSS and template markup. */
    static long tokenReferenceCount(String token) {
        String haystack = allApplicationCss() + "\n" + allTemplateMarkup();
        Matcher m = Pattern.compile("var\\(\\s*" + Pattern.quote(token) + "\\s*[,)]")
                .matcher(haystack);
        return m.results().count();
    }

    private static void collect(String file, String css, List<Rule> out) {
        int i = 0;
        while (i < css.length()) {
            int brace = css.indexOf('{', i);
            if (brace < 0) return;
            String prelude = css.substring(i, brace).trim();
            int end = matchingBrace(css, brace);
            if (end < 0) return;
            String body = css.substring(brace + 1, end);
            if (prelude.startsWith("@media") || prelude.startsWith("@supports")
                    || prelude.startsWith("@layer")) {
                collect(file, body, out);
            } else {
                out.add(new Rule(file, prelude.replaceAll("\\s+", " "), body));
            }
            i = end + 1;
        }
    }

    private static int matchingBrace(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private CssRules() {
    }
}
