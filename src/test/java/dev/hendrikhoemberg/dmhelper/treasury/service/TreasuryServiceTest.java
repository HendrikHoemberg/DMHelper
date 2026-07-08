package dev.hendrikhoemberg.dmhelper.treasury.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(TreasuryService.class)
class TreasuryServiceTest {

    @Autowired private TreasuryService service;
    @Autowired private ItemAssignmentRepository repository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private PartyMember pc;

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

        EquipmentItem sword = new EquipmentItem();
        sword.setSourceKey("srd_longsword");
        sword.setName("Longsword");
        sword.setCategory(EquipmentItem.Category.WEAPON);
        sword.setCost("15 gp");
        em.persist(sword);

        em.flush();
    }

    @Test
    void shouldCreateAssignmentToPC() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "mysterious amulet", 1, false));
        assertThat(result.id()).isNotNull();
        assertThat(result.holderName()).isEqualTo("Thia");
        assertThat(result.itemName()).isEqualTo("mysterious amulet");
        assertThat(result.quantity()).isEqualTo(1);
    }

    @Test
    void shouldCreateAssignmentToPartyStash() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "bag of gems", 3, false));
        assertThat(result.holderName()).isEqualTo("Party Stash");
    }

    @Test
    void shouldToggleAttunement() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Ring of Protection", 1, false));
        assertThat(result.attuned()).isFalse();

        var toggled = service.toggleAttunement(result.id());
        assertThat(toggled.attuned()).isTrue();
    }

    @Test
    void shouldCountAttunements() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 1", 1, true));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 2", 1, true));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Item 3", 1, false));

        assertThat(service.countAttunements(pc.getId())).isEqualTo(2);
    }

    @Test
    void shouldRejectAssignmentWithoutItemRef() {
        assertThatThrownBy(() -> service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, null, 1, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindByCampaignId() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Dagger", 2, false));
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Party Gold", 100, false));

        var all = service.findByCampaignId(campaign.getId());
        assertThat(all).hasSize(2);
    }

    @Test
    void shouldFindByPartyMember() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Dagger", 1, false));
        var result = service.findByPartyMemberId(pc.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).holderName()).isEqualTo("Thia");
    }

    @Test
    void shouldFindPartyStash() {
        service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), null, null, null, "Stash Item", 1, false));
        var stash = service.findPartyStash(campaign.getId());
        assertThat(stash).hasSize(1);
        assertThat(stash.get(0).holderName()).isEqualTo("Party Stash");
    }

    @Test
    void shouldDeleteAssignment() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Delete Me", 1, false));
        service.delete(result.id());
        assertThat(repository.findById(result.id())).isEmpty();
    }

    @Test
    void shouldUpdateAssignment() {
        var result = service.create(new TreasuryService.CreateAssignmentRequest(
                campaign.getId(), pc.getId(), null, null, "Update Me", 1, false));
        var updated = service.update(result.id(), null, 5, true); // move to stash
        assertThat(updated.holderName()).isEqualTo("Party Stash");
        assertThat(updated.quantity()).isEqualTo(5);
        assertThat(updated.attuned()).isTrue();
    }
}
