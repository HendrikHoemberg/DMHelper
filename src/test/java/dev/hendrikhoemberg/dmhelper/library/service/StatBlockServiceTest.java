package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({StatBlockService.class, SceneRefCleaner.class})
class StatBlockServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired
    private StatBlockRepository repository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private StatBlockService service;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        campaignRepository.deleteAll();
        Campaign campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();
    }

    private StatBlock createCustom(String name, String cr, String type, int ac, String hp) {
        return service.createCustom(campaignId, name, cr, type, ac, hp, "30 ft.",
                10, 10, 10, 10, 10, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", null,
                null, null);
    }

    @Test
    void shouldCreateCustomStatBlock() {
        StatBlock sb = createCustom("Amber Knight", "5", "Humanoid", 18, "75 (10d8 + 30)");
        assertThat(sb.getId()).isNotNull();
        assertThat(sb.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
        assertThat(sb.getCampaignId()).isEqualTo(campaignId);
        assertThat(sb.getName()).isEqualTo("Amber Knight");
    }

    @Test
    void shouldFindByCampaign() {
        createCustom("Custom A", "1", "Beast", 12, "10");
        createCustomForOtherCampaign();
        assertThat(service.findByCampaignId(campaignId)).hasSize(1);
    }

    private void createCustomForOtherCampaign() {
        service.createCustom(UUID.randomUUID(), "Custom B", "2", "Giant", 14, "30", "30 ft.",
                10, 10, 10, 10, 10, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 10", "Giant",
                null, null);
    }

    @Test
    void shouldSearchByName() {
        createCustom("Fire Elemental Adept", "2", "Elemental", 14, "30");
        createCustom("Ice Knight", "3", "Humanoid", 16, "45");
        var results = service.search(null, null, null, "Fire");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Fire Elemental Adept");
    }

    @Test
    void shouldFilterByCr() {
        createCustom("CR One", "1", "Beast", 12, "20");
        createCustom("CR Three", "3", "Beast", 14, "40");
        var results = service.search(null, "1", null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCr()).isEqualTo("1");
    }

    @Test
    void shouldFilterByType() {
        createCustom("Beast Monster", "1/2", "Beast", 12, "15");
        createCustom("Undead Monster", "1/2", "Undead", 12, "18");
        var results = service.search(null, null, "Undead", null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo("Undead");
    }

    @Test
    void shouldUpdateCustomStatBlock() {
        StatBlock created = createCustom("Original", "1", "Beast", 12, "10");
        StatBlock updated = service.updateCustom(created.getId(), "Renamed", "3", "Beast",
                16, "45", "40 ft.",
                16, 10, 16, 12, 14, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "darkvision 60 ft.", "Common, Giant");
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getCr()).isEqualTo("3");
    }

    @Test
    void shouldDeleteCustomStatBlock() {
        StatBlock created = createCustom("Delete Me", "1/8", "Beast", 10, "5");
        service.delete(created.getId());
        assertThat(repository.findById(created.getId())).isEmpty();
    }

    @Test
    void shouldNotDeleteSrdStatBlock() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("Protected Goblin");
        srd.setCr("1/4");
        srd.setType("Humanoid");
        srd.setHp("7");
        srd.setAc(15);
        StatBlock saved = repository.save(srd);
        assertThatThrownBy(() -> service.delete(saved.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete SRD");
    }

    @Test
    void shouldPromoteToGlobal() {
        StatBlock custom = createCustom("Campaign Monster", "2", "Giant", 14, "50");
        assertThat(custom.getCampaignId()).isNotNull();
        StatBlock promoted = service.promoteToGlobal(custom.getId());
        assertThat(promoted.getCampaignId()).isNull();
        assertThat(promoted.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
    }

    @Test
    void shouldNotPromoteSrdToGlobal() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("SRD Monster");
        srd.setCr("1");
        srd.setType("Beast");
        srd.setHp("10");
        srd.setAc(12);
        StatBlock saved = repository.save(srd);
        assertThatThrownBy(() -> service.promoteToGlobal(saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCloneSrdAsCustom() {
        StatBlock srd = new StatBlock();
        srd.setSource(StatBlock.Source.SRD);
        srd.setName("Goblin");
        srd.setCr("1/4");
        srd.setType("Humanoid");
        srd.setHp("7 (2d6)");
        srd.setAc(15);
        srd.setSpeed("30 ft.");
        srd.setStrScore(8);
        srd.setDexScore(14);
        srd.setConScore(10);
        srd.setIntScore(10);
        srd.setWisScore(8);
        srd.setChaScore(8);
        srd.setTraits("[{\"name\":\"Nimble Escape\",\"description\":\"Test\"}]");
        srd.setActions("[{\"name\":\"Scimitar\",\"description\":\"Test\"}]");
        srd = repository.save(srd);

        StatBlock cloned = service.cloneAsCustom(srd.getId(), campaignId, "Goblin Boss");
        assertThat(cloned.getId()).isNotEqualTo(srd.getId());
        assertThat(cloned.getSource()).isEqualTo(StatBlock.Source.CUSTOM);
        assertThat(cloned.getCampaignId()).isEqualTo(campaignId);
        assertThat(cloned.getName()).isEqualTo("Goblin Boss");
        assertThat(cloned.getTraits()).isEqualTo(srd.getTraits());
    }
}
