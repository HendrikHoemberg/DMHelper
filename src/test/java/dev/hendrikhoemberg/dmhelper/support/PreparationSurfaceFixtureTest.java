package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PreparationSurfaceFixtureTest {

    @Autowired private PreparationSurfaceFixture fixture;
    @Autowired private PartyMemberService party;
    @Autowired private EncounterService encounters;

    @Test
    void seedsARosterWithLiveStateAndAPlannedEncounter() {
        PreparationSurfaceFixture.Seeded seeded = fixture.seed();

        var members = party.findByCampaignId(seeded.campaignId());
        assertThat(members).hasSize(4);
        assertThat(party.findActiveByCampaignId(seeded.campaignId())).hasSize(3);

        var wounded = party.findById(seeded.woundedMemberId());
        assertThat(wounded.getCurrentHp()).isLessThan(wounded.getMaxHp());
        assertThat(wounded.getConditions()).contains(PreparationSurfaceFixture.CONDITION_NAME);

        var encounter = encounters.getById(seeded.encounterId());
        assertThat(encounter.status()).isEqualTo("PLANNED");
        assertThat(encounters.getCombatants(seeded.encounterId())).hasSize(3);
        assertThat(encounters.getPrep(seeded.encounterId()).tactics())
                .isEqualTo(PreparationSurfaceFixture.PREP_TACTICS);
        assertThat(encounters.getRewards(seeded.encounterId()).xpTotal())
                .isEqualTo(PreparationSurfaceFixture.REWARD_XP_TOTAL);
    }
}
