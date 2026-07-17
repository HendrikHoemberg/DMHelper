package dev.hendrikhoemberg.dmhelper.treasury.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AssignmentDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import dev.hendrikhoemberg.dmhelper.treasury.packagev2.TreasurySectionAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(TreasuryService.class)
class InventoryStateRoundTripTest {

    @Autowired private TreasuryService service;
    @Autowired private ItemAssignmentRepository repository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private PartyMember pc;
    private EquipmentItem sword;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);

        pc = new PartyMember();
        pc.setCampaign(campaign);
        pc.setCharacterName("Thia");
        pc.setPlayerName("Anna");
        pc.setClassAndLevel("Rogue 5");
        pc.setAc(16);
        pc.setMaxHp(38);
        pc.setInitiativeBonus(4);
        pc.setSpeed(30);
        pc.setPassivePerception(17);
        pc.setPassiveInsight(12);
        pc.setPassiveInvestigation(14);
        em.persist(pc);

        sword = new EquipmentItem();
        sword.setSource(ContentSource.SRD);
        sword.setSourceKey("srd_longsword");
        sword.setName("Longsword");
        sword.setCategory(EquipmentItem.Category.WEAPON);
        sword.setCost("15 gp");
        sword.setWeight("3 lb");
        em.persist(sword);

        em.flush();
    }

    @Test
    void shouldSetState() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 1, false));
        assertThat(result.inventoryState()).isEqualTo(InventoryState.CARRIED);

        var updated = service.setInventoryState(result.id(), InventoryState.EQUIPPED);
        assertThat(updated.inventoryState()).isEqualTo(InventoryState.EQUIPPED);
    }

    @Test
    void shouldDefaultStashToStashed() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Stash item", 1, false));
        assertThat(result.inventoryState()).isEqualTo(InventoryState.STASHED);
    }

    @Test
    void shouldAdjustQuantity() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 3, false));
        assertThat(result.quantity()).isEqualTo(3);

        var adjusted = service.adjustQuantity(result.id(), -1);
        assertThat(adjusted.quantity()).isEqualTo(2);
    }

    @Test
    void shouldMarkConsumedWhenQuantityReachesZero() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 2, false));
        assertThat(result.inventoryState()).isEqualTo(InventoryState.CARRIED);

        var adjusted = service.adjustQuantity(result.id(), -2);
        assertThat(adjusted.quantity()).isEqualTo(0);
        assertThat(adjusted.inventoryState()).isEqualTo(InventoryState.CONSUMED);
    }

    @Test
    void shouldFloorQuantityAtZero() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 1, false));

        var adjusted = service.adjustQuantity(result.id(), -10);
        assertThat(adjusted.quantity()).isEqualTo(0);
        assertThat(adjusted.inventoryState()).isEqualTo(InventoryState.CONSUMED);
    }

    @Test
    void shouldRejectEquipOnStashWithNoPartyMember() {
        var stash = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Stash item", 1, false));

        var updated = service.setInventoryState(stash.id(), InventoryState.EQUIPPED);
        assertThat(updated.inventoryState()).isEqualTo(InventoryState.EQUIPPED);
    }

    @Test
    void packageRoundTripPreservesState() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 1, false));
        service.setInventoryState(result.id(), InventoryState.EQUIPPED);

        var fromDb = repository.findById(result.id()).orElseThrow();
        assertThat(fromDb.getInventoryState()).isEqualTo(InventoryState.EQUIPPED);
    }

    @Test
    void shouldCreateWithExplicitState() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, sword.getId(), null, 1, false,
                InventoryState.LOST));
        assertThat(result.inventoryState()).isEqualTo(InventoryState.LOST);
    }

    @Test
    void partyStashDefaultsToStashed() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, sword.getId(), null, 1, false));
        assertThat(result.inventoryState()).isEqualTo(InventoryState.STASHED);
    }
}
