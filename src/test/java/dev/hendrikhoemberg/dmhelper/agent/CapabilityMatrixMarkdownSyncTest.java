package dev.hendrikhoemberg.dmhelper.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMatrixMarkdownSyncTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void markdownRowsMatchManifestStatusesForSharedNames() throws Exception {
        var manifest = MAPPER.readValue(
                new File("src/main/resources/agent/capability-manifest.json"),
                CapabilityManifest.class);

        var manifestByName = manifest.capabilities().stream()
                .collect(Collectors.toMap(CapabilityManifest.Capability::name, c -> c));

        var markdownLines = Files.readAllLines(new File("docs/campaign-capabilities.md").toPath());

        var header = true;
        for (var line : markdownLines) {
            if (!line.startsWith("|")) continue;
            if (header) {
                header = false;
                continue;
            }
            if (line.contains("---")) continue;

            var columns = parseMarkdownRow(line);
            if (columns.size() < 2) continue;

            var capabilityName = columns.get(0);
            var markdownStatus = columns.get(1);

            var manifestCap = manifestByName.get(capabilityName);
            if (manifestCap == null) continue;

            assertThat(markdownStatus)
                    .as("Status mismatch for capability '%s' between markdown and manifest", capabilityName)
                    .isEqualTo(manifestCap.status());
        }
    }

    private static List<String> parseMarkdownRow(String line) {
        var parts = line.split("\\|");
        var name = parts[1].trim();
        var status = parts[2].trim().replace("`", "");
        return List.of(name, status);
    }
}
