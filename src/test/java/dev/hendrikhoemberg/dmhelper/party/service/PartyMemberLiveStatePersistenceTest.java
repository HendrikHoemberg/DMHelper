package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService.PartyLiveStateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class PartyMemberLiveStatePersistenceTest {

    @Autowired
    private PartyMemberRepository partyMemberRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private PartyMemberService partyMemberService;

    @Test
    void persistsAndRetrievesLiveStateFields() {
        var campaign = campaignRepository.save(createCampaign("Live State Campaign"));

        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Test Char");
        pm.setPlayerName("Tester");
        pm.setClassAndLevel("Fighter 1");
        pm.setAc(15);
        pm.setMaxHp(50);
        pm.setCurrentHp(50);
        pm.setInitiativeBonus(2);
        pm.setSpeed(30);
        pm.setPassivePerception(10);
        pm.setPassiveInsight(10);
        pm.setPassiveInvestigation(10);
        pm.setActive(true);
        pm.setTempHp(5);
        pm.setInspiration(true);
        pm.setExhaustion(2);
        pm.setDeathSaveSuccesses(1);
        pm.setDeathSaveFailures(2);
        pm.setConcentratingOn("Bless");
        pm.setConditionsJson("[{\"sourceKey\":\"poisoned\",\"name\":\"Poisoned\"}]");

        partyMemberRepository.save(pm);

        var found = partyMemberRepository.findById(pm.getId()).orElseThrow();

        assertThat(found.getTempHp()).isEqualTo(5);
        assertThat(found.isInspiration()).isTrue();
        assertThat(found.getExhaustion()).isEqualTo(2);
        assertThat(found.getDeathSaveSuccesses()).isEqualTo(1);
        assertThat(found.getDeathSaveFailures()).isEqualTo(2);
        assertThat(found.getConcentratingOn()).isEqualTo("Bless");
        assertThat(found.getConditionsJson()).contains("poisoned");
    }

    @Test
    void defaultsAreZeroFalseNull() {
        var campaign = campaignRepository.save(createCampaign("Defaults Campaign"));

        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Defaults");
        pm.setPlayerName("Tester");
        pm.setClassAndLevel("Wizard 1");
        pm.setAc(10);
        pm.setMaxHp(20);
        pm.setCurrentHp(20);
        pm.setInitiativeBonus(2);
        pm.setSpeed(30);
        pm.setPassivePerception(10);
        pm.setPassiveInsight(10);
        pm.setPassiveInvestigation(10);
        pm.setActive(true);

        partyMemberRepository.save(pm);

        var found = partyMemberRepository.findById(pm.getId()).orElseThrow();

        assertThat(found.getTempHp()).isEqualTo(0);
        assertThat(found.isInspiration()).isFalse();
        assertThat(found.getExhaustion()).isEqualTo(0);
        assertThat(found.getDeathSaveSuccesses()).isEqualTo(0);
        assertThat(found.getDeathSaveFailures()).isEqualTo(0);
        assertThat(found.getConcentratingOn()).isNull();
        assertThat(found.getConditionsJson()).isNull();
    }

    @Test
    void updateLiveStateValidatesExhaustion() {
        var campaign = campaignRepository.save(createCampaign("Validation Campaign"));
        var pm = createPartyMember(campaign);

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        0, false, -1, 0, 0, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exhaustion");

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        0, false, 7, 0, 0, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exhaustion");
    }

    @Test
    void updateLiveStateValidatesDeathSaves() {
        var campaign = campaignRepository.save(createCampaign("DeathSave Campaign"));
        var pm = createPartyMember(campaign);

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        0, false, 0, -1, 0, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Death save");

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        0, false, 0, 4, 0, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Death save");

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        0, false, 0, 0, -1, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Death save");
    }

    @Test
    void updateLiveStateValidatesTempHp() {
        var campaign = campaignRepository.save(createCampaign("TempHp Campaign"));
        var pm = createPartyMember(campaign);

        assertThatThrownBy(() ->
                partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                        -1, false, 0, 0, 0, null, null, null, null
                ))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temp HP");
    }

    @Test
    void updateLiveStateClampsCurrentHpToMax() {
        var campaign = campaignRepository.save(createCampaign("Clamp Campaign"));
        var pm = createPartyMember(campaign);

        var updated = partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                0, false, 0, 0, 0, null, null, 999, 50
        ));

        assertThat(updated.getCurrentHp()).isEqualTo(50);
        assertThat(updated.getMaxHp()).isEqualTo(50);
    }

    @Test
    void updateLiveStateUpdatesFields() {
        var campaign = campaignRepository.save(createCampaign("Update Campaign"));
        var pm = createPartyMember(campaign);

        var updated = partyMemberService.updateLiveState(pm.getId(), new PartyLiveStateDto(
                7, true, 1, 2, 0, "Concentrating", "[{\"sourceKey\":\"blinded\"}]", 30, null
        ));

        assertThat(updated.getTempHp()).isEqualTo(7);
        assertThat(updated.isInspiration()).isTrue();
        assertThat(updated.getExhaustion()).isEqualTo(1);
        assertThat(updated.getDeathSaveSuccesses()).isEqualTo(2);
        assertThat(updated.getDeathSaveFailures()).isEqualTo(0);
        assertThat(updated.getConcentratingOn()).isEqualTo("Concentrating");
        assertThat(updated.getConditionsJson()).contains("blinded");
        assertThat(updated.getCurrentHp()).isEqualTo(30);
        assertThat(updated.getMaxHp()).isEqualTo(30);
    }

    private Campaign createCampaign(String name) {
        var c = new Campaign();
        c.setName(name);
        return c;
    }

    private PartyMember createPartyMember(Campaign campaign) {
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Test");
        pm.setPlayerName("Tester");
        pm.setClassAndLevel("Rogue 1");
        pm.setAc(14);
        pm.setMaxHp(30);
        pm.setCurrentHp(30);
        pm.setInitiativeBonus(3);
        pm.setSpeed(30);
        pm.setPassivePerception(12);
        pm.setPassiveInsight(10);
        pm.setPassiveInvestigation(8);
        pm.setActive(true);
        return partyMemberRepository.save(pm);
    }
}
