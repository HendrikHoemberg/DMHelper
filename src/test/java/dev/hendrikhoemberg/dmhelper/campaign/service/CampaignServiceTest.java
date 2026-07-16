package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({CampaignService.class, GameMapService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class CampaignServiceTest {

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
    private StatBlockService statBlockService;

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

    @MockitoBean
    private CampaignImportValidator importValidator;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

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

    @Test
    void shouldExportEmptyCampaignToJson() throws Exception {
        Campaign created = service.create("Export Test", "Test desc");

        String json = service.exportToJson(created.getId());
        var node = objectMapper.readTree(json);

        assertThat(node.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(node.get("campaign").get("name").asText()).isEqualTo("Export Test");
        assertThat(node.get("campaign").get("description").asText()).isEqualTo("Test desc");
        assertThat(node.get("party").size()).isEqualTo(0);
        assertThat(node.get("statBlocks").size()).isEqualTo(0);
        assertThat(node.get("handouts").size()).isEqualTo(0);
        assertThat(node.get("maps").size()).isEqualTo(0);
        assertThat(node.get("encounters").size()).isEqualTo(0);
        assertThat(node.get("notes").size()).isEqualTo(0);
    }

    @Test
    void shouldImportJsonToNewCampaign() throws Exception {
        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "Imported", "description": "Imported desc" },
                  "party": [],
                  "statBlocks": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [],
                  "notes": []
                }
                """;

        CampaignExportDto dto = objectMapper.readValue(json, CampaignExportDto.class);
        when(importValidator.validate(json)).thenReturn(
                new CampaignValidationResult(Optional.of(dto), List.of()));

        Campaign imported = service.importFromJson(json);

        assertThat(imported.getId()).isNotNull();
        assertThat(imported.getName()).isEqualTo("Imported");
        assertThat(imported.getDescription()).isEqualTo("Imported desc");
    }

    @Test
    void shouldRoundTripCampaign() throws Exception {
        Campaign original = service.create("Round Trip", "Round trip test");
        String exported = service.exportToJson(original.getId());

        CampaignExportDto dto = objectMapper.readValue(exported, CampaignExportDto.class);
        when(importValidator.validate(exported)).thenReturn(
                new CampaignValidationResult(Optional.of(dto), List.of()));

        Campaign reimported = service.importFromJson(exported);

        assertThat(reimported.getName()).isEqualTo("Round Trip");
        assertThat(reimported.getDescription()).isEqualTo("Round trip test");
        assertThat(reimported.getId()).isNotEqualTo(original.getId());
    }

    @Test
    void shouldRejectInvalidFormatVersion() throws Exception {
        String json = """
                { "formatVersion": 99, "campaign": { "name": "Bad" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        CampaignExportDto dto = objectMapper.readValue(json, CampaignExportDto.class);
        when(importValidator.validate(json)).thenReturn(
                new CampaignValidationResult(Optional.of(dto), List.of(
                        new CampaignImportProblem(ImportSeverity.ERROR, "INVALID_FORMAT", "/",
                                "Unsupported formatVersion: 99. Expected: 1",
                                "Use format version 1"))));

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formatVersion");
    }

    @Test
    void shouldRejectMalformedJson() {
        when(importValidator.validate("not json")).thenReturn(
                new CampaignValidationResult(Optional.empty(), List.of(
                        new CampaignImportProblem(ImportSeverity.ERROR, "INVALID_JSON", "/",
                                "Campaign file is not valid JSON.",
                                "Fix the JSON syntax near line 1."))));

        assertThatThrownBy(() -> service.importFromJson("not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMissingCampaignName() throws Exception {
        String json = """
                { "formatVersion": 1, "campaign": { "description": "nope" },
                  "party": [], "statBlocks": [], "handouts": [],
                  "maps": [], "encounters": [], "notes": [] }
                """;

        CampaignExportDto dto = objectMapper.readValue(json, CampaignExportDto.class);
        when(importValidator.validate(json)).thenReturn(
                new CampaignValidationResult(Optional.of(dto), List.of(
                        new CampaignImportProblem(ImportSeverity.ERROR, "MISSING_NAME", "/campaign/name",
                                "Campaign name is required",
                                "Provide a campaign name"))));

        assertThatThrownBy(() -> service.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void importResolvesSrdStatBlock() throws Exception {
        service.create("SRD Import Test", null);

        StatBlock sb = mock(StatBlock.class);
        when(sb.getSourceKey()).thenReturn("goblin");
        when(sb.getName()).thenReturn("Goblin");

        when(statBlockRepository.findByCampaignIdAndSourceKey(any(), eq("goblin")))
                .thenReturn(Optional.empty());
        when(statBlockRepository.findBySourceKey("goblin"))
                .thenReturn(Optional.of(sb));

        when(encounterRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String json = """
                {
                  "formatVersion": 1,
                  "campaign": { "name": "SRD Import" },
                  "party": [],
                  "statBlocks": [],
                  "handouts": [],
                  "maps": [],
                  "encounters": [
                    {
                      "name": "Test Encounter",
                      "combatants": [
                        {
                          "name": "Goblin Scout",
                          "initiative": 10, "tieBreaker": 0, "sortOrder": 0,
                          "maxHp": 7, "currentHp": 7, "tempHp": 0,
                          "kind": "NPC",
                          "groupId": null, "groupLeader": false,
                          "statBlockKey": "goblin",
                          "defeated": false, "hidden": false,
                          "conditionsJson": null, "concentratingOn": null,
                          "concentrationCheckPending": false,
                          "legendaryActionsUsed": 0, "legendaryResistancesUsed": 0,
                          "legendaryActionsMax": 0, "legendaryResistancesMax": 0,
                          "rechargedAbilities": null, "notes": null
                        }
                      ],
                      "status": "ACTIVE", "round": 1, "activeTurnIndex": 0,
                      "logSequence": 0, "lairActionName": null,
                      "lairActionDescription": null
                    }
                  ],
                  "notes": [], "quicknotes": [], "assignments": [],
                  "ledger": [], "timeline": []
                }
                """;

        CampaignExportDto dto = objectMapper.readValue(json, CampaignExportDto.class);
        when(importValidator.validate(json)).thenReturn(
                new CampaignValidationResult(Optional.of(dto), List.of()));

        service.importFromJson(json);

        ArgumentCaptor<Combatant> captor = ArgumentCaptor.forClass(Combatant.class);
        verify(combatantRepo).save(captor.capture());
        Combatant importedCombatant = captor.getValue();
        assertThat(importedCombatant.getStatBlock()).isNotNull();
        assertThat(importedCombatant.getStatBlock().getSourceKey()).isEqualTo("goblin");
    }

    @Test
    void invalidImportDoesNotPersist() {
        when(importValidator.validate(anyString())).thenReturn(
                new CampaignValidationResult(Optional.empty(), List.of(
                        new CampaignImportProblem(ImportSeverity.ERROR, "INVALID_JSON", "/",
                                "Invalid", "Fix"))));

        long countBefore = repository.count();
        assertThatThrownBy(() -> service.importFromJson("bad json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    void invalidDirectImportThrows() {
        when(importValidator.validate(anyString())).thenReturn(
                new CampaignValidationResult(Optional.empty(), List.of(
                        new CampaignImportProblem(ImportSeverity.ERROR, "INVALID_JSON", "/",
                                "Invalid", "Fix"))));

        assertThatThrownBy(() -> service.importFromJson("bad json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
