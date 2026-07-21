package dev.hendrikhoemberg.dmhelper.common;

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

class TemplateFragmentPrecedenceContractTest {

    /**
     * th:replace / th:insert (precedence 100) execute before th:if / th:unless
     * (precedence 300), so a conditional on the same element is silently ignored
     * and the fragment always renders. The conditional must sit on an enclosing
     * element: {@code <th:block th:if="..."><div th:replace="..."></div></th:block>}.
     */
    @Test
    void noConditionalSharesAnElementWithAFragmentInclude() throws IOException {
        Pattern offendingTag = Pattern.compile(
                "<[^>]*th:(?:if|unless)=[^>]*th:(?:replace|insert)=[^>]*>"
                        + "|<[^>]*th:(?:replace|insert)=[^>]*th:(?:if|unless)=[^>]*>");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(file);
                Matcher matcher = offendingTag.matcher(html);
                while (matcher.find()) {
                    long line = html.substring(0, matcher.start()).lines().count() + 1;
                    offenders.add(file + ":" + line);
                }
            }
        }
        assertThat(offenders)
                .as("th:if/th:unless is dead code next to th:replace/th:insert — wrap the include in a conditional th:block")
                .isEmpty();
    }
}
