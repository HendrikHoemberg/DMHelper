package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class CssInventoryContractTest {

    @Test
    void everyShippedStylesheetIsInspectedByTheContractTests() throws IOException {
        List<String> onDisk;
        try (Stream<java.nio.file.Path> files = Files.list(CssRules.CSS_DIR)) {
            onDisk = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".css"))
                    .sorted()
                    .toList();
        }
        assertThat(CssRules.ALL_FILES).containsExactlyInAnyOrderElementsOf(onDisk);
    }

    @Test
    void discoveryIsAutomaticRatherThanHandMaintained() {
        assertThat(CssRules.discoverCssFiles()).isEqualTo(CssRules.ALL_FILES);
    }
}
