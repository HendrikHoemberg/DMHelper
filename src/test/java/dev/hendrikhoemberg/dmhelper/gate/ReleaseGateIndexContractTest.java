package dev.hendrikhoemberg.dmhelper.gate;

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
 * Spec 2026-07-22 section 11: "No plan may claim the overall premise until section 11 passes."
 * The index is how that claim is checked, so every test class it names must exist.
 */
class ReleaseGateIndexContractTest {

    private static final Path INDEX = Path.of("docs/product/all-in-one-release-gate.md");

    @Test
    void everyRequirementInSection11HasARow() throws IOException {
        String index = Files.readString(INDEX);

        for (String requirement : List.of(
                "11.1.1", "11.1.2", "11.1.3", "11.1.4", "11.1.5", "11.1.6", "11.1.7", "11.1.8",
                "11.2", "11.3")) {
            assertThat(index).as("%s indexed", requirement).contains(requirement);
        }
    }

    @Test
    void everyTestClassTheIndexNamesExists() throws IOException {
        String index = Files.readString(INDEX);
        Matcher m = Pattern.compile("`([A-Z][A-Za-z0-9]+Test)`").matcher(index);

        List<String> named = new ArrayList<>();
        while (m.find()) named.add(m.group(1));
        assertThat(named).as("the index names test classes").isNotEmpty();

        List<String> missing = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/test/java"))) {
            List<String> present = sources
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .toList();
            for (String name : named) {
                if (!present.contains(name)) missing.add(name);
            }
        }

        assertThat(missing).as("indexed tests that no longer exist").isEmpty();
    }

    @Test
    void theIndexIsLinkedFromTheProductDocs() throws IOException {
        assertThat(Files.readString(Path.of("docs/product/README.md")))
                .contains("all-in-one-release-gate.md");
    }
}
