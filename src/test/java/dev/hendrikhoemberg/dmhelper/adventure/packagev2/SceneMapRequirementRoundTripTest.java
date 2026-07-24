package dev.hendrikhoemberg.dmhelper.adventure.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SceneMapRequirementRoundTripTest {

    @Test
    void legacySceneConstructorDefaultsMapRequirementNull() {
        SceneDto dto = new SceneDto("k", "t", "b", "UNVISITED", 0, null, null, null,
                java.util.List.of(), java.util.List.of(), "s", "loc", java.util.List.of(),
                "region", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null);
        assertThat(dto.mapRequirement()).isNull();
    }

    @Test
    void fullSceneConstructorCarriesMapRequirement() {
        SceneDto dto = new SceneDto("k", "t", "b", "UNVISITED", 0, null, null, null,
                java.util.List.of(), java.util.List.of(), "s", "loc", java.util.List.of(),
                "region", java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null, "REQUIRED");
        assertThat(dto.mapRequirement()).isEqualTo("REQUIRED");
    }
}
