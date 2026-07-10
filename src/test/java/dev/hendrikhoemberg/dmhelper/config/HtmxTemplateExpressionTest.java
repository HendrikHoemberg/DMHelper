package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against a whole class of bug: an htmx attribute carrying a Thymeleaf
 * expression ({@code @{...}} or {@code ${...}}) but missing the {@code th:}
 * prefix. Standard Thymeleaf never evaluates such an attribute, so the raw
 * template string leaks into the rendered HTML and htmx issues it verbatim as a
 * URL, producing a 404. Only {@code th:hx-get="@{...}"} is evaluated server-side.
 */
class HtmxTemplateExpressionTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** hx verbs/attrs that take a value we expect to be a server-rendered expression. */
    private static final Pattern UNPREFIXED_HX_EXPRESSION =
            Pattern.compile("(?<!th:)hx-(get|post|put|delete|patch|swap|target|vals|confirm)=\"([^\"]*)\"");

    @Test
    void noHtmxAttributeCarriesAnUnprocessedThymeleafExpression() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(TEMPLATES)) {
            for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".html"))::iterator) {
                String content = Files.readString(file);
                Matcher m = UNPREFIXED_HX_EXPRESSION.matcher(content);
                while (m.find()) {
                    String value = m.group(2);
                    if (value.contains("@{") || value.contains("${")) {
                        violations.add(TEMPLATES.relativize(file) + " -> " + m.group());
                    }
                }
            }
        }

        assertThat(violations)
                .as("htmx attributes with a Thymeleaf expression must use the th: prefix "
                        + "(e.g. th:hx-get=\"@{...}\") so the expression is evaluated server-side")
                .isEmpty();
    }
}
