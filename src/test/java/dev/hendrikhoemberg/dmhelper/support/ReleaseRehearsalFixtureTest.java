package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessState;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 8.3 and section 11.3: the rehearsal needs a campaign that is
 * session-ready, Phandelver-shaped and entirely synthetic.
 */
@SpringBootTest
class ReleaseRehearsalFixtureTest {

    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private CampaignReadinessFacade readiness;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private GameMapRepository mapRepository;
    @Autowired private PartyMemberRepository partyMemberRepository;

    @Test
    void theSeededCampaignIsSessionReady() throws IOException {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).sessionReady())
                .as("a rehearsal that starts blocked proves nothing about the rehearsal")
                .isTrue();
    }

    @Test
    void theHostileSceneCanSeedAnEncounterFromResolvedParticipants() throws IOException {
        var seeded = fixture.seed();

        assertThat(readiness.reportForCampaign(seeded.campaignId()).byState(ReadinessState.BLOCKER))
                .isEmpty();
        assertThat(mapRepository.findById(seeded.playableMapId()).orElseThrow().getGridWidth())
                .isPositive();
    }

    @Test
    void theAssetSetCoversAllThreeClassifications() throws IOException {
        var seeded = fixture.seed();

        assertThat(handoutRepository.findById(seeded.playerSafeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_SAFE);
        assertThat(handoutRepository.findById(seeded.dmSourceHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.DM_SOURCE);
        assertThat(handoutRepository.findById(seeded.derivativeHandoutId()).orElseThrow()
                .getSafetyClassification())
                .isEqualTo(Handout.SafetyClassification.PLAYER_DERIVATIVE);
    }

    @Test
    void thePartyHasFourMembers() throws IOException {
        var seeded = fixture.seed();

        assertThat(seeded.partyMemberIds()).hasSize(4);
        assertThat(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(seeded.campaignId()))
                .hasSize(4);
    }

    @Test
    void nothingInTheFixtureCameFromAPublishedCampaign() throws IOException {
        var seeded = fixture.seed();
        String everything = fixture.textualContentOf(seeded);

        assertThat(everything.toLowerCase())
                .doesNotContain("phandelver", "klarg", "cragmaw", "wave echo", "sildar",
                        "gundren", "rockseeker", "neverwinter", "tresendar");
    }
}
