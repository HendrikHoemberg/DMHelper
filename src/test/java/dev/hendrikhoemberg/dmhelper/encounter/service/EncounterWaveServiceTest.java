package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({EncounterService.class, CombatDifficultyCalculator.class,
        DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class,
        EncounterWaveServiceTest.MockConfig.class})
class EncounterWaveServiceTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        TablePresentationService tablePresentationService() {
            return Mockito.mock(TablePresentationService.class);
        }
    }

    @Autowired EncounterService service;
    @Autowired EncounterWaveRepository waveRepo;
    @Autowired CampaignRepository campaignRepo;
    @Autowired StatBlockRepository statBlockRepo;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        Campaign c = new Campaign(); c.setName("Waves"); c = campaignRepo.save(c); campaignId = c.getId();
    }

    @Test
    void reserveWaveCombatantsHiddenFromInitiativeUntilSpawn() {
        var enc = service.create(campaignId, new EncounterService.CreateRequest("Boss", null));
        service.activate(enc.id());
        var reserve = service.createWave(enc.id(), new EncounterService.CreateWaveRequest(
                "reinforcements", "Reinforcements", WaveTriggerKind.ROUND, "3", null));
        StatBlock wolf = seedWolf();
        service.addFromLibrary(enc.id(), new EncounterService.AddFromLibraryRequest(
                wolf.getId(), 2, "Wolves", reserve.id(), null, null, null));

        assertThat(service.getCombatants(enc.id())).isEmpty();

        service.spawnWave(enc.id(), reserve.id());
        assertThat(service.getCombatants(enc.id())).hasSize(2);
        List<EncounterService.CombatLogEntryDto> log = service.getLog(enc.id());
        assertThat(log.get(log.size() - 1).type()).isEqualTo("WAVE_SPAWNED");
    }

    @Test
    void spawnWaveRequiresActiveEncounter() {
        var enc = service.create(campaignId, new EncounterService.CreateRequest("Boss", null));
        var reserve = service.createWave(enc.id(), new EncounterService.CreateWaveRequest(
                "r1", "R1", WaveTriggerKind.MANUAL, null, null));
        assertThatThrownBy(() -> service.spawnWave(enc.id(), reserve.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotDeleteMainWave() {
        var enc = service.create(campaignId, new EncounterService.CreateRequest("Boss", null));
        var waves = service.listWaves(enc.id());
        var main = waves.getFirst();
        assertThatThrownBy(() -> service.deleteWave(main.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private StatBlock seedWolf() {
        StatBlock s = new StatBlock(); s.setName("Wolf"); s.setType("beast");
        s.setHp("11 (2d8+2)"); s.setCr("1/4"); s.setSource(ContentSource.SRD); return statBlockRepo.save(s);
    }
}
