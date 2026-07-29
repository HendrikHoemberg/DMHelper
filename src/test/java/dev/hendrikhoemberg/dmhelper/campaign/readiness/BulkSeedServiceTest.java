package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BulkSeedServiceTest {

    @Autowired private BulkSeedService bulkSeed;
    @Autowired private CampaignReadinessFacade readiness;
    @Autowired private BulkSeedFixtures fixtures;

    @Test
    void seedAllClearsEverySeedEncounterAdvisoryInOneCall() {
        UUID campaignId = fixtures.campaignWithThreeSeedableScenes();

        long before = readiness.reportForCampaign(campaignId).items().stream()
                .filter(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .count();
        assertThat(before).isEqualTo(3);

        BulkSeedService.BulkSeedResult result = bulkSeed.seedAll(campaignId);

        assertThat(result.scenesProcessed()).isEqualTo(3);
        assertThat(result.encountersSeeded()).isEqualTo(3);
        assertThat(result.combatantsAdded()).isGreaterThan(0);

        long after = readiness.reportForCampaign(campaignId).items().stream()
                .filter(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .count();
        assertThat(after).isZero();
    }

    @Test
    void seedAllIsSafeToRepeat() {
        UUID campaignId = fixtures.campaignWithThreeSeedableScenes();
        bulkSeed.seedAll(campaignId);

        BulkSeedService.BulkSeedResult second = bulkSeed.seedAll(campaignId);

        assertThat(second.encountersSeeded())
                .as("already-seeded scenes must not produce duplicate encounters")
                .isZero();
    }
}
