package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({CampaignService.class, GameMapService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class CampaignServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired
    private CampaignRepository repository;

    @Autowired
    private CampaignService service;

    @MockitoBean
    private PartyMemberRepository partyMemberRepository;

    @MockitoBean
    private StatBlockRepository statBlockRepository;

    @MockitoBean
    private PartyMemberService partyMemberService;

    @MockitoBean
    private NoteService noteService;

    @MockitoBean
    private EncounterRepository encounterRepo;

    @MockitoBean
    private CombatantRepository combatantRepo;

    @MockitoBean
    private HandoutService handoutService;

    @MockitoBean
    private HandoutRepository handoutRepo;

    @Test
    void shouldCreateCampaign() {
        Campaign campaign = service.create("Test Campaign", "A test description");

        assertThat(campaign.getId()).isNotNull();
        assertThat(campaign.getName()).isEqualTo("Test Campaign");
        assertThat(campaign.getDescription()).isEqualTo("A test description");
        assertThat(campaign.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindAllOrderedByName() {
        service.create("Zeta Campaign", null);
        service.create("Alpha Campaign", null);

        var campaigns = service.findAll();

        assertThat(campaigns).hasSize(2);
        assertThat(campaigns.get(0).getName()).isEqualTo("Alpha Campaign");
        assertThat(campaigns.get(1).getName()).isEqualTo("Zeta Campaign");
    }

    @Test
    void shouldFindById() {
        Campaign created = service.create("Find Me", null);

        Campaign found = service.findById(created.getId());

        assertThat(found.getName()).isEqualTo("Find Me");
    }

    @Test
    void shouldThrowWhenNotFound() {
        assertThatThrownBy(() -> service.findById(java.util.UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campaign not found");
    }

    @Test
    void shouldUpdateCampaign() {
        Campaign created = service.create("Original", "Old description");

        Campaign updated = service.update(created.getId(), "Updated", "New description");

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldDeleteCampaign() {
        Campaign created = service.create("Delete Me", null);

        service.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

}
