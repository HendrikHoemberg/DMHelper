package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({HazardService.class, TrapService.class, ThreatValidator.class, ThreatReferenceResolver.class,
        ThreatDependencyService.class, CustomContentSupport.class, LibraryReferenceCleaner.class})
class HazardServiceTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired private HazardService service;
    @Autowired private HazardRepository repository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private EntityManager em;

    private UUID campaignId;
    private UUID srdHazardId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        campaignRepository.deleteAll();

        Campaign campaign = new Campaign();
        campaign.setName("Hazard Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        Hazard srd = new Hazard();
        srd.setSourceKey("srd-slime");
        srd.setSource(ContentSource.SRD);
        srd.setName("SRD Slime");
        srd.setDescription("Classic slime.");
        srd.setSeverity(ThreatSeverity.SETBACK);
        srd.setExposureMode(HazardExposureMode.CONTINUOUS);
        srd = repository.save(srd);
        srdHazardId = srd.getId();
        em.flush();
    }

    @Test
    void createsUpdatesClonesAndPromotes() {
        Hazard created = service.create(campaignId, validWrite("green-slime", "Green Slime"), null);
        assertThat(created.getExposureMode()).isEqualTo(HazardExposureMode.ON_ENTER);

        HazardWrite update = new HazardWrite(
                "green-slime", "Green Slime+", "Updated slime description text.",
                ThreatSeverity.DANGEROUS, 2, 6, HazardExposureMode.PER_ROUND,
                "Each round", "15-ft",
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "CON", null, 14),
                "2d6", List.of(DamageType.ACID), "Worse", "Sunlight", List.of());
        Hazard updated = service.updateCustom(created.getId(), update, null);
        assertThat(updated.getSeverity()).isEqualTo(ThreatSeverity.DANGEROUS);
        assertThat(updated.getDamageExpression()).isEqualTo("2d6");

        Hazard cloned = service.cloneAsCustom(srdHazardId, campaignId, "Slime Copy");
        assertThat(cloned.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(cloned.getName()).isEqualTo("Slime Copy");

        Hazard promoted = service.promoteToGlobal(created.getId());
        assertThat(promoted.getCampaign()).isNull();
    }

    @Test
    void rejectsSrdMutationAndMissingCampaign() {
        assertThatThrownBy(() -> service.updateCustom(srdHazardId, validWrite("x", "X"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom");
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), validWrite("y", "Y"), null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsSourceKeyCollisionAndInvalidWrite() {
        service.create(campaignId, validWrite("same", "First"), null);
        assertThatThrownBy(() -> service.create(campaignId, validWrite("same", "Second"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceKey");

        HazardWrite invalid = new HazardWrite(
                "bad", "Bad", " ", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, null, List.of(), null, null, List.of());
        assertThatThrownBy(() -> service.create(campaignId, invalid, null))
                .isInstanceOf(ThreatValidationException.class);
    }

    @Test
    void deletesWithoutDependents() {
        Hazard hazard = service.create(null, validWrite("del", "Delete Me"), null);
        UUID id = hazard.getId();
        assertThatCode(() -> service.deleteCustom(id, false)).doesNotThrowAnyException();
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void requireVisibleWorksForGlobalScope() {
        Hazard global = service.create(null, validWrite("g", "Global"), null);
        assertThat(service.requireVisible(global.getId(), null).getId()).isEqualTo(global.getId());
        assertThat(service.requireVisible(global.getId(), campaignId).getId()).isEqualTo(global.getId());
    }

    private HazardWrite validWrite(String key, String name) {
        return new HazardWrite(
                key, name, "A solid hazard description.",
                ThreatSeverity.SETBACK, null, null, HazardExposureMode.ON_ENTER,
                "When entered", null, null, null, List.of(), null, null, List.of());
    }
}
