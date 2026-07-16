package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignSemanticComparatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void identicalSnapshotsPass() throws Exception {
        try (var input = getClass().getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            var manifest = mapper.readValue(input, CampaignManifestV2.class);
            var a = CampaignSemanticSnapshot.from(manifest);
            var b = CampaignSemanticSnapshot.from(manifest);
            CampaignSemanticComparator.assertEquivalent(a, b);
        }
    }

    @Test
    void differingFieldsFail() throws Exception {
        try (var input = getClass().getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            var base = mapper.readValue(input, CampaignManifestV2.class);
            var mod = new CampaignManifestV2(
                    base.formatVersion(), base.metadata(),
                    new CampaignManifestV2.CampaignDto(
                            base.campaign().key(), "Different Name",
                            base.campaign().description(), base.campaign().createdAt(),
                            base.campaign().settings(), base.campaign().currentSceneRef()),
                    base.assets(), base.party(), base.customStatBlocks(),
                    base.handouts(), base.maps(), base.encounters(),
                    base.notes(), base.quickNotes(), base.assignments(),
                    base.ledgerEntries(), base.timelineEvents(), base.adventures(),
                    base.diceRolls());
            var expected = CampaignSemanticSnapshot.from(base);
            var actual = CampaignSemanticSnapshot.from(mod);
            assertThatThrownBy(() -> CampaignSemanticComparator.assertEquivalent(expected, actual))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void ignoresCreatedAtInMetadata() throws Exception {
        try (var input = getClass().getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            var base = mapper.readValue(input, CampaignManifestV2.class);
            var mod = new CampaignManifestV2(
                    base.formatVersion(),
                    new CampaignManifestV2.Metadata(
                            base.metadata().packageKey(),
                            java.time.Instant.parse("2099-01-01T00:00:00Z"),
                            base.metadata().generator(),
                            base.metadata().catalogVersion(),
                            base.metadata().catalogSha256(),
                            base.metadata().exclusions()),
                    base.campaign(), base.assets(), base.party(),
                    base.customStatBlocks(), base.handouts(), base.maps(),
                    base.encounters(), base.notes(), base.quickNotes(),
                    base.assignments(), base.ledgerEntries(), base.timelineEvents(),
                    base.adventures(), base.diceRolls());
            var expected = CampaignSemanticSnapshot.from(base);
            var actual = CampaignSemanticSnapshot.from(mod);
            CampaignSemanticComparator.assertEquivalent(expected, actual);
        }
    }
}
