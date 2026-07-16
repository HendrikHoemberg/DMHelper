package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PartyMemberService.class, dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class PartyMemberServiceTest {

    @Autowired private PartyMemberRepository repository;
    @Autowired private PartyMemberService service;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreatePartyMember() {
        PartyMember pm = service.create(campaign.getId(), "Thia", "Anna",
                "Rogue 5", 16, 38, 4, 30, 17, 12, 14, "darkvision, fey ancestry");
        assertThat(pm.getId()).isNotNull();
        assertThat(pm.getCharacterName()).isEqualTo("Thia");
        assertThat(pm.getPassivePerception()).isEqualTo(17);
        assertThat(pm.isActive()).isTrue();
        assertThat(pm.getCurrentHp()).isEqualTo(pm.getMaxHp());
    }

    @Test
    void shouldFindByCampaign() {
        service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        service.create(campaign.getId(), "Bruenor", "Bob", "Fighter 5", 18, 45, 2, 25, 13, 10, 9, null);
        var members = service.findByCampaignId(campaign.getId());
        assertThat(members).hasSize(2);
        assertThat(members.get(0).getCharacterName()).isEqualTo("Bruenor");
    }

    @Test
    void shouldFindActiveOnly() {
        service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        var pm = service.create(campaign.getId(), "Inactive PC", "Dan", "Wizard 3", 12, 18, 2, 30, 11, 15, 18, null);
        service.setActive(pm.getId(), false);

        var active = service.findActiveByCampaignId(campaign.getId());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCharacterName()).isEqualTo("Thia");
    }

    @Test
    void shouldUpdatePartyMember() {
        var pm = service.create(campaign.getId(), "Thia", "Anna", "Rogue 5", 16, 38, 4, 30, 17, 12, 14, null);
        var updated = service.update(pm.getId(), "Thia", "Anna", "Rogue 6", 17, 45, 4, 30, 18, 12, 14, "expertise in stealth");
        assertThat(updated.getClassAndLevel()).isEqualTo("Rogue 6");
        assertThat(updated.getAc()).isEqualTo(17);
    }

    @Test
    void shouldDeletePartyMember() {
        var pm = service.create(campaign.getId(), "Delete Me", "X", "Wizard 1", 10, 6, 0, 30, 10, 10, 10, null);
        service.delete(pm.getId());
        assertThat(repository.findById(pm.getId())).isEmpty();
    }

    @Test
    void shouldToggleActive() {
        var pm = service.create(campaign.getId(), "Toggle", "T", "Cleric 1", 18, 10, 1, 25, 15, 15, 10, null);
        assertThat(pm.isActive()).isTrue();
        service.setActive(pm.getId(), false);
        var reloaded = repository.findById(pm.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }
}
