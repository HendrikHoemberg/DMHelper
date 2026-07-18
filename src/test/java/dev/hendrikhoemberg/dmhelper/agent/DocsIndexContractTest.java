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

    @Test
    void musicProviderFeasibilityDecisionIsIndexedAndComplete() throws Exception {
        String architectureIndex = Files.readString(Path.of("docs/architecture/README.md"));
        Path decisionPath = Path.of("docs/architecture/music-provider-feasibility.md");

        assertThat(architectureIndex).contains("music-provider-feasibility.md");
        assertThat(decisionPath).exists();

        String decision = Files.readString(decisionPath);
        assertThat(decision)
                .contains("# Music-Provider Feasibility Decision")
                .contains("**Official sources checked:**")
                .containsPattern("\\*\\*Official sources checked:\\*\\* 20\\d{2}-\\d{2}-\\d{2}")
                .contains("## Decision")
                .contains("**Readiness provider:**")
                .contains("**YouTube status:**")
                .contains("**Spotify status:**")
                .contains("## Candidate Matrix")
                .contains("## DM-Device Playback Proof")
                .contains("## Spotify Provider Contract")
                .contains("## Provider Capabilities")
                .contains("## Provider Failure Mapping")
                .contains("## Account and Subscription Prerequisites")
                .contains("## Downstream Implementation Contract")
                .contains("## Roadmap Outcome")
                .contains("https://developers.google.com/youtube/iframe_api_reference")
                .contains("https://developer.spotify.com/policy");
    }

    @Test
    void approvedMusicDesignCoversAuthlessAndProviderHostedPlaybackClients() throws Exception {
        String master = Files.readString(Path.of(
                "docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md"));
        String atmosphere = Files.readString(Path.of(
                "docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md"));

        assertThat(master)
                .contains("general-purpose runtime CDN")
                .contains("official provider-hosted playback client or script");
        assertThat(atmosphere)
                .contains("may require a provider account")
                .contains("An authless public provider path may declare `AudioAuthMode.NONE`")
                .contains("must not collapse or hide that player while audio continues");
    }
}
