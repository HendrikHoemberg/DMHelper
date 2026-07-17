package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.*;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class EncounterPrepBuilderTest {

    @Autowired EncounterService service;
    @Autowired EncounterWaveRepository waveRepo;
    @Autowired StatBlockRepository statBlockRepo;
    @Autowired CampaignRepository campaignRepo;

    @Test
    void createEncounterCreatesMainWave() {
        Campaign c = seedCampaign();
        var dto = service.create(c.getId(), new EncounterService.CreateRequest("Ambush", null));
        var waves = waveRepo.findByEncounterIdOrderBySortOrderAsc(dto.id());
        assertThat(waves).hasSize(1);
        assertThat(waves.getFirst().getWaveKey()).isEqualTo("main");
        assertThat(waves.getFirst().getStatus()).isEqualTo(WaveStatus.ACTIVE);
    }

    @Test
    void addFromLibraryCreatesGroupedCombatants() {
        Campaign c = seedCampaign();
        StatBlock goblin = seedGoblin();
        var enc = service.create(c.getId(), new EncounterService.CreateRequest("Ambush", null));
        var created = service.addFromLibrary(enc.id(),
                new EncounterService.AddFromLibraryRequest(goblin.getId(), 3, "Goblin squad", null, 48, 96, "tree-line"));
        assertThat(created).hasSize(3);
        assertThat(created).allMatch(comb -> comb.groupId() != null && !comb.groupId().isBlank());
        assertThat(created.getFirst().groupLeader()).isTrue();
        assertThat(created.subList(1, 3)).allMatch(comb -> !comb.groupLeader());
        assertThat(created.getFirst().startX()).isEqualTo(48);
        assertThat(created.getFirst().placementRegionKey()).isEqualTo("tree-line");
    }

    @Test
    void addFromLibraryRejectsQuantityLessThanOne() {
        Campaign c = seedCampaign();
        StatBlock goblin = seedGoblin();
        var enc = service.create(c.getId(), new EncounterService.CreateRequest("Boss", null));
        assertThatThrownBy(() -> service.addFromLibrary(enc.id(),
                new EncounterService.AddFromLibraryRequest(goblin.getId(), 0, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Campaign seedCampaign() {
        Campaign c = new Campaign();
        c.setName("Test");
        return campaignRepo.save(c);
    }

    private StatBlock seedGoblin() {
        StatBlock sb = new StatBlock();
        sb.setName("Goblin");
        sb.setType("humanoid");
        sb.setCr("1/4");
        sb.setHp("7 (2d6)");
        sb.setSource(ContentSource.SRD);
        return statBlockRepo.save(sb);
    }
}
