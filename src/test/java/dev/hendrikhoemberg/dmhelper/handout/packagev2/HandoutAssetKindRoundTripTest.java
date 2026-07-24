package dev.hendrikhoemberg.dmhelper.handout.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HandoutAssetKindRoundTripTest {

    @Test
    void legacyConstructorDefaultsKindToNull() {
        HandoutDto dto = new HandoutDto("k", "Map", java.util.List.of(), "a", "image/png",
                true, false, "DM_SOURCE", null, null);
        assertThat(dto.assetKind()).isNull();
    }

    @Test
    void fullConstructorCarriesKind() {
        HandoutDto dto = new HandoutDto("k", "Map", java.util.List.of(), "a", "image/png",
                true, false, "DM_SOURCE", null, null, "TACTICAL_MAP");
        assertThat(dto.assetKind()).isEqualTo("TACTICAL_MAP");
    }
}
