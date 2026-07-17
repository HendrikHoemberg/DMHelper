package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:wave-persist;DB_CLOSE_DELAY=-1")
class EncounterWavePersistenceTest {

    @Autowired EncounterRepository encounterRepo;
    @Autowired EncounterWaveRepository waveRepo;
    @Autowired CombatantRepository combatantRepo;
    @Autowired CampaignRepository campaignRepo;

    @Test
    void waveAndPlacementRoundTripThroughJpa() {
        Campaign c = new Campaign();
        c.setName("Waves");
        c = campaignRepo.save(c);

        Encounter e = new Encounter();
        e.setCampaign(c);
        e.setName("Ambush");
        e.setPrepJson("{\"tactics\":\"flank\"}");
        e.setRewardsJson("{\"xpTotal\":100}");
        e = encounterRepo.save(e);

        EncounterWave w = new EncounterWave();
        w.setEncounter(e);
        w.setWaveKey("main");
        w.setName("Main");
        w.setSortOrder(0);
        w.setStatus(WaveStatus.ACTIVE);
        w.setTriggerKind(WaveTriggerKind.MANUAL);
        w = waveRepo.save(w);

        Combatant m = new Combatant();
        m.setEncounter(e);
        m.setName("Goblin");
        m.setInitiative(0);
        m.setSortOrder(0);
        m.setMaxHp(7);
        m.setCurrentHp(7);
        m.setKind("MONSTER");
        m.setWave(w);
        m.setStartX(96);
        m.setStartY(144);
        m.setPlacementRegionKey("tree-line");
        combatantRepo.save(m);

        Combatant reloaded = combatantRepo.findById(m.getId()).orElseThrow();
        assertThat(reloaded.getWave().getWaveKey()).isEqualTo("main");
        assertThat(reloaded.getStartX()).isEqualTo(96);
        assertThat(reloaded.getPlacementRegionKey()).isEqualTo("tree-line");
        assertThat(encounterRepo.findById(e.getId()).orElseThrow().getPrepJson())
                .contains("flank");
    }
}
