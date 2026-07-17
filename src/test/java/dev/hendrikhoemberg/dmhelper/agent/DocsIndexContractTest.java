package dev.hendrikhoemberg.dmhelper.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocsIndexContractTest {

    @Test
    void topLevelIndexLinksMajorAudiences() throws Exception {
        String index = Files.readString(Path.of("docs/README.md"));
        for (String needle : List.of(
                "dm-manual/", "authoring/", "architecture/", "agent/",
                "campaign-capabilities.md", "campaign-format-v2.md")) {
            assertThat(index).contains(needle);
        }
    }

    @Test
    void architectureDocumentsAdapterOrder() throws Exception {
        String body = Files.readString(Path.of("docs/architecture/import-export-flow.md"));
        assertThat(body).contains("CampaignImportCoordinator");
        assertThat(body).contains("CampaignExportCoordinator");
        assertThat(body).contains("CampaignSectionAdapter");
    }

    @Test
    void securityDocStatesPlayerSafetyBoundary() throws Exception {
        String body = Files.readString(Path.of("docs/architecture/security-boundaries.md"));
        assertThat(body).containsIgnoringCase("player");
        assertThat(body).contains("PIN");
        assertThat(body).containsIgnoringCase("untrusted");
    }
}
