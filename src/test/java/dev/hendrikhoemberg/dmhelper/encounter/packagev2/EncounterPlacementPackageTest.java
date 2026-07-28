package dev.hendrikhoemberg.dmhelper.encounter.packagev2;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class EncounterPlacementPackageTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final CampaignManifestV2SchemaValidator schema = new CampaignManifestV2SchemaValidator();

    @Test
    void placementSurvivesExportImportReExport() throws Exception {
        var placement = new CampaignManifestV2.CombatantPlacementDto(
                2, 3, 1, 1, "#ff0000", "skull");
        var combatant = new CampaignManifestV2.CombatantDto(
                "combatant-placed", "Goblin", 15, 0, 1,
                10, 10, 0, "NPC", null, false,
                null, null, null, false, false,
                null, null, false, 0, 0, 0, 0,
                null, null, null, null, null, null, null, placement);
        var encounter = new CampaignManifestV2.EncounterDto(
                "encounter-placed", "Test Encounter", List.of(combatant), "PLANNED",
                0, -1, "SETUP", 0, null, null,
                null, false, List.of(), null, null, List.of(),
                null, null, null);
        var manifest = minimalManifestWith(List.of(encounter));

        String json = mapper.writeValueAsString(manifest);
        assertThat(schema.validate(json)).isEmpty();

        CampaignManifestV2 deserialized = mapper.readValue(json, CampaignManifestV2.class);
        var reEncounter = deserialized.encounters().get(0);
        var reCombatant = reEncounter.combatants().get(0);
        assertThat(reCombatant.placement()).isNotNull();
        assertThat(reCombatant.placement().positionX()).isEqualTo(2);
        assertThat(reCombatant.placement().positionY()).isEqualTo(3);
        assertThat(reCombatant.placement().sizeCols()).isEqualTo(1);
        assertThat(reCombatant.placement().sizeRows()).isEqualTo(1);
        assertThat(reCombatant.placement().color()).isEqualTo("#ff0000");
        assertThat(reCombatant.placement().icon()).isEqualTo("skull");
        assertThat(reCombatant.tokenRef()).isNull();

        String reJson = mapper.writeValueAsString(deserialized);
        assertThat(schema.validate(reJson)).isEmpty();
    }

    @Test
    void legacyTokenRefAcceptedOnImportButNotEmitted() throws Exception {
        var tokenRef = ContentReference.packageRef(dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.TOKEN, "token-goblin");
        var combatant = new CampaignManifestV2.CombatantDto(
                "combatant-legacy", "Goblin", 15, 0, 1,
                10, 10, 0, "NPC", null, false,
                tokenRef, null, null, false, false,
                null, null, false, 0, 0, 0, 0,
                null, null, null, null, null, null, null, null);
        var encounter = new CampaignManifestV2.EncounterDto(
                "encounter-legacy", "Legacy Encounter", List.of(combatant), "PLANNED",
                0, -1, "SETUP", 0, null, null,
                null, false, List.of(), null, null, List.of(),
                null, null, null);
        var manifest = minimalManifestWith(List.of(encounter));

        String json = mapper.writeValueAsString(manifest);
        assertThat(schema.validate(json)).isEmpty();

        CampaignManifestV2 deserialized = mapper.readValue(json, CampaignManifestV2.class);
        var reCombatant = deserialized.encounters().get(0).combatants().get(0);
        assertThat(reCombatant.tokenRef()).isNotNull();
        assertThat(reCombatant.tokenRef().key()).isEqualTo("token-goblin");
        assertThat(reCombatant.placement()).isNull();

        String reJson = mapper.writeValueAsString(deserialized);
        assertThat(reJson).contains("tokenRef");
        assertThat(schema.validate(reJson)).isEmpty();
    }

    @Test
    void placementWithNullIconIsValid() throws Exception {
        var placement = new CampaignManifestV2.CombatantPlacementDto(
                0, 0, 2, 2, "#000000", null);
        var combatant = new CampaignManifestV2.CombatantDto(
                "c-null-icon", "Test", null, 0, 0,
                10, 10, 0, "NPC", null, false,
                null, null, null, false, false,
                null, null, false, 0, 0, 0, 0,
                null, null, null, null, null, null, null, placement);
        var encounter = new CampaignManifestV2.EncounterDto(
                "enc-null-icon", "Test", List.of(combatant), "PLANNED",
                0, -1, "SETUP", 0, null, null,
                null, false, List.of(), null, null, List.of(),
                null, null, null);
        var manifest = minimalManifestWith(List.of(encounter));

        String json = mapper.writeValueAsString(manifest);
        assertThat(schema.validate(json)).isEmpty();

        CampaignManifestV2 deserialized = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(deserialized.encounters().get(0).combatants().get(0).placement().icon()).isNull();
    }

    private CampaignManifestV2 minimalManifestWith(List<CampaignManifestV2.EncounterDto> encounters) {
        var emptyHandouts = java.util.Collections.<CampaignManifestV2.HandoutDto>emptyList();
        var emptyMaps = java.util.Collections.<CampaignManifestV2.MapDto>emptyList();
        var emptyNotes = java.util.Collections.<CampaignManifestV2.NoteDto>emptyList();
        var emptyQuickNotes = java.util.Collections.<CampaignManifestV2.QuickNoteDto>emptyList();
        var emptyAssignments = java.util.Collections.<CampaignManifestV2.AssignmentDto>emptyList();
        var emptyLedger = java.util.Collections.<CampaignManifestV2.LedgerEntryDto>emptyList();
        var emptyTimeline = java.util.Collections.<CampaignManifestV2.TimelineEventDto>emptyList();
        var emptyAdventures = java.util.Collections.<CampaignManifestV2.AdventureDto>emptyList();
        var emptyDiceRolls = java.util.Collections.<CampaignManifestV2.DiceRollDto>emptyList();
        var emptyQuests = java.util.Collections.<CampaignManifestV2.QuestDto>emptyList();
        var emptyAnnotations = java.util.Collections.<CampaignManifestV2.SourceAnnotationDto>emptyList();
        var emptyWorldNpcs = java.util.Collections.<CampaignManifestV2.WorldNpcDto>emptyList();
        var emptyWorldLocations = java.util.Collections.<CampaignManifestV2.WorldLocationDto>emptyList();
        var emptyFactions = java.util.Collections.<CampaignManifestV2.FactionDto>emptyList();
        var emptyRelationships = java.util.Collections.<CampaignManifestV2.WorldRelationshipDto>emptyList();
        var emptyFactionClocks = java.util.Collections.<CampaignManifestV2.FactionClockDto>emptyList();
        var emptyRollableTables = java.util.Collections.<CampaignManifestV2.RollableTableDto>emptyList();
        var emptyTraps = java.util.Collections.<CampaignManifestV2.TrapDto>emptyList();
        var emptyHazards = java.util.Collections.<CampaignManifestV2.HazardDto>emptyList();
        var emptyAudioCues = java.util.Collections.<CampaignManifestV2.AudioCueDto>emptyList();
        var emptyParty = java.util.Collections.<CampaignManifestV2.PartyMemberDto>emptyList();
        var emptyStatBlocks = java.util.Collections.<CampaignManifestV2.StatBlockDto>emptyList();
        var emptySpells = java.util.Collections.<CampaignManifestV2.CustomSpellDto>emptyList();
        var emptyConditions = java.util.Collections.<CampaignManifestV2.CustomConditionDto>emptyList();
        var emptyRules = java.util.Collections.<CampaignManifestV2.CustomRuleDto>emptyList();
        var emptyEquipment = java.util.Collections.<CampaignManifestV2.CustomEquipmentDto>emptyList();
        var emptyMagicItems = java.util.Collections.<CampaignManifestV2.CustomMagicItemDto>emptyList();
        var emptyClasses = java.util.Collections.<CampaignManifestV2.CustomClassDto>emptyList();
        var emptySpecies = java.util.Collections.<CampaignManifestV2.CustomSpeciesDto>emptyList();
        var emptyBackgrounds = java.util.Collections.<CampaignManifestV2.CustomBackgroundDto>emptyList();
        var emptyFeats = java.util.Collections.<CampaignManifestV2.CustomFeatDto>emptyList();
        var emptyAssets = java.util.Collections.<dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor>emptyList();
        return new CampaignManifestV2(
                3,
                new CampaignManifestV2.Metadata("test-pkg", java.time.Instant.parse("2025-01-01T00:00:00Z"),
                        "DMHelper", "1.0", "abc123", List.of()),
                new CampaignManifestV2.CampaignDto("campaign-test", "Test", null,
                        java.time.Instant.parse("2025-01-01T00:00:00Z"),
                        new CampaignManifestV2.CampaignSettingsDto(
                                CampaignManifestV2.LevelingMode.XP, null, null, null),
                        null, null),
                emptyAssets, emptyParty, emptyStatBlocks, emptySpells, emptyConditions, emptyRules,
                emptyEquipment, emptyMagicItems, emptyClasses, emptySpecies, emptyBackgrounds, emptyFeats,
                emptyHandouts, emptyMaps, encounters,
                emptyNotes, emptyQuickNotes, emptyAssignments, emptyLedger, emptyTimeline, emptyAdventures,
                null, emptyDiceRolls,
                emptyQuests, emptyAnnotations, emptyWorldNpcs, emptyWorldLocations, emptyFactions,
                emptyRelationships, emptyFactionClocks, emptyRollableTables, emptyTraps, emptyHazards, emptyAudioCues);
    }
}
