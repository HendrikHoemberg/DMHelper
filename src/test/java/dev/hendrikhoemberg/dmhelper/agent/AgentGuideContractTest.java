package dev.hendrikhoemberg.dmhelper.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGuideContractTest {

    @Test
    void agentGuideFilesExist() {
        for (String path : List.of(
                "docs/agent/README.md",
                "docs/agent/conversion-playbook.md",
                "docs/agent/mapping-rules.md",
                "docs/agent/verification-checklist.md",
                "docs/agent/dry-run-repair.md")) {
            assertThat(Path.of(path)).exists();
        }
    }

    @Test
    void playbookReferencesCatalogAndSchemas() throws Exception {
        String playbook = Files.readString(Path.of("docs/agent/conversion-playbook.md"));
        assertThat(playbook).contains("/api/v1/catalog");
        assertThat(playbook).contains("/api/v1/schemas/");
        assertThat(playbook).contains("must never invent");
        assertThat(playbook).contains("capability-manifest.json");
    }

    @Test
    void dryRunRepairReferencesExecutableExamples() throws Exception {
        String body = Files.readString(Path.of("docs/agent/dry-run-repair.md"));
        assertThat(body).contains("docs-examples/schema-error.dmcampaign.json");
        assertThat(body).contains("docs-examples/semantic-error.dmcampaign.json");
        assertThat(body).contains("UNRESOLVED_REFERENCE");
    }
}
