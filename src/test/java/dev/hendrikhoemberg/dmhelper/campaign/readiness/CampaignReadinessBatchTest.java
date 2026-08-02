package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The campaign index needs readiness per card, and computing it a card at a time is an N+1 on
 * an unbounded list. The batched path exists for that — and the only reason it is safe is that
 * it feeds the same {@link CampaignReadinessService#compute} the single-campaign path uses,
 * batching input assembly rather than restating the rules.
 *
 * <p>This test is what holds that true. If someone later reimplements readiness as an
 * aggregate query "for speed", the two definitions drift, and the card starts disagreeing with
 * the campaign home it links to — silently, because each is internally consistent.
 */
@SpringBootTest
class CampaignReadinessBatchTest {

    @Autowired private CampaignReadinessFacade facade;
    @Autowired private CampaignFixtures campaignFixtures;
    @Autowired private PopulatedCampaignFixture populatedFixture;

    @Test
    void batchedReadinessAgreesWithThePerCampaignPath() {
        List<UUID> campaignIds = List.of(
                populatedFixture.seed().campaignId(),
                campaignFixtures.operationalFixtureNotReady(),
                campaignFixtures.operationalFixtureReadyAfterRepair());

        var batched = facade.reportsForCampaigns(campaignIds);

        assertThat(batched).as("a report per campaign").hasSize(campaignIds.size());
        for (UUID campaignId : campaignIds) {
            var single = facade.reportForCampaign(campaignId);
            var fromBatch = batched.get(campaignId);

            assertThat(fromBatch.sessionReady())
                    .as("session-ready for %s", campaignId)
                    .isEqualTo(single.sessionReady());
            assertThat(fromBatch.blockerCount())
                    .as("blocker count for %s", campaignId)
                    .isEqualTo(single.blockerCount());
            assertThat(fromBatch.items())
                    .as("every readiness item for %s, in order", campaignId)
                    .isEqualTo(single.items());
        }
    }

    /** Both fixtures must land on different sides of the line, or the test above proves little. */
    @Test
    void theFixturesCoverBothReadyAndBlocked() {
        var ready = facade.reportForCampaign(campaignFixtures.operationalFixtureReadyAfterRepair());
        var blocked = facade.reportForCampaign(campaignFixtures.operationalFixtureNotReady());

        assertThat(ready.sessionReady()).isTrue();
        assertThat(blocked.sessionReady()).isFalse();
    }

    @Test
    void anEmptyIndexAsksNothingOfTheDatabase() {
        assertThat(facade.reportsForCampaigns(List.of())).isEmpty();
    }
}
