package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CampaignManifestV2SemanticValidatorTest {

    @Mock CampaignCatalogService catalog;
    private CampaignManifestV2SemanticValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CampaignManifestV2SemanticValidator(catalog);
    }

    private CampaignManifestV2 minimal() {
        return new CampaignManifestV2(
                2,
                new CampaignManifestV2.Metadata("pkg", Instant.parse("2025-01-01T00:00:00Z"), "test", "1", "abc", List.of()),
                new CampaignManifestV2.CampaignDto("camp", "Test", null, Instant.parse("2025-01-01T00:00:00Z"),
                        new CampaignManifestV2.CampaignSettingsDto(CampaignManifestV2.LevelingMode.XP, null, null), null),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void minimalValidManifestPasses() {
        assertThat(validator.validate(minimal())).isEmpty();
    }

    @Test
    void duplicateKeysAreDetected() {
        var manifest = minimal();
        var adv = new CampaignManifestV2.AdventureDto("dup", "Dup", null, null, 1, List.of(), null);
        var adv2 = new CampaignManifestV2.AdventureDto("dup", "Dup2", null, null, 2, List.of(), null);
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), List.of(adv, adv2), manifest.session(), manifest.diceRolls(),
                manifest.quests(), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("DUPLICATE_KEY");
    }

    @Test
    void crossCampaignTargetIsDetected() {
        var manifest = minimal();
        var handout = new CampaignManifestV2.HandoutDto("h1", "Cross", List.of(), "missing-asset", "image/png", false, false);
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), List.of(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                List.of(handout), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), manifest.adventures(), manifest.session(), manifest.diceRolls(),
                manifest.quests(), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_ASSET_REFERENCE");
    }

    @Test
    void transitionWithUnresolvableTargetRefDetected() {
        var transition = new CampaignManifestV2.SceneTransitionDto(
                "t-bad", "CHOICE", "Bad",
                ContentReference.packageRef(CampaignContentType.SCENE, "missing-scene"),
                null, null, null, null, 1);
        var scene = new CampaignManifestV2.SceneDto(
                "sc-test", "Test", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(transition), null);
        var chapter = new CampaignManifestV2.ChapterDto("ch-test", "Ch", null, 1, List.of(scene));
        var adv = new CampaignManifestV2.AdventureDto("adv-test", "Adv", null, null, 1, List.of(chapter), null);
        var manifest = minimal();
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), List.of(adv), manifest.session(), manifest.diceRolls(),
                manifest.quests(), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var problems = validator.validate(manifest2);
        assertThat(problems).extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_REFERENCE");
    }

    @Test
    void duplicateEdgesDetected() {
        var manifest = minimal();
        var scene = new CampaignManifestV2.SceneDto("dup-scene", "Test", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        var chapter = new CampaignManifestV2.ChapterDto("dup-ch", "Ch", null, 1, List.of(scene, scene));
        var adv = new CampaignManifestV2.AdventureDto("dup-adv", "Adv", null, null, 1, List.of(chapter), null);
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), List.of(adv), manifest.session(), manifest.diceRolls(),
                manifest.quests(), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("DUPLICATE_KEY");
    }

    @Test
    void invalidOwnerRefDetected() {
        var annotation = new CampaignManifestV2.SourceAnnotationDto(
                "ann-bad", ContentReference.packageRef(CampaignContentType.SCENE, "missing-scene"),
                "/field", "msg", "HIGH", null, "OPEN", null, Instant.parse("2025-01-01T00:00:00Z"));
        var manifest = minimal();
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), manifest.adventures(), manifest.session(), manifest.diceRolls(),
                manifest.quests(), List.of(annotation), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_REFERENCE");
    }

    @Test
    void absentCatalogEntryDetected() {
        when(catalog.resolve(any(), any(), any())).thenReturn(Optional.empty());
        var manifest = minimal();
        var sb = ContentReference.catalogRef(CampaignContentType.STATBLOCK, "SRD_5_2", "nonexistent");
        var combatant = new CampaignManifestV2.CombatantDto(
                "c1", "Test", 0, 0, 0, 10, 10, 0,
                "MONSTER", null, false, null, sb, null,
                false, false, null, null, false, 0, 0, 0, 0, null, null,
                null, null, null, null);
        var encounter = new CampaignManifestV2.EncounterDto(
                "enc1", "Test", List.of(combatant), "PLANNED",
                0, -1, 0, null, null, null, false, List.of(),
                null, null, null);
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), List.of(encounter),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), manifest.adventures(), manifest.session(), manifest.diceRolls(),
                manifest.quests(), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_CATALOG_REFERENCE");
    }

    @Test
    void unresolvablePrerequisiteRefDetected() {
        var manifest = minimal();
        var objA = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1,
                null, null);
        var objB = new CampaignManifestV2.QuestObjectiveDto("obj-b", "B", null, "NOT_STARTED", "ALL", 2,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "nonexistent")), null);
        var quest = new CampaignManifestV2.QuestDto("quest-cycle", "Cycle", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objA, objB), Instant.parse("2025-01-01T00:00:00Z"));
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), manifest.adventures(), manifest.session(), manifest.diceRolls(),
                List.of(quest), manifest.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var problems = validator.validate(manifest2);
        assertThat(problems).extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_REFERENCE");
    }

    @Test
    void missingAnnotationsDetectedAsUnresolvedOwnerRef() {
        var manifest = minimal();
        var annotation = new CampaignManifestV2.SourceAnnotationDto(
                "ann-missing", ContentReference.packageRef(CampaignContentType.SCENE, "no-such-scene"),
                "/field", "msg", "MEDIUM", null, "OPEN", null, Instant.parse("2025-01-01T00:00:00Z"));
        var manifest2 = new CampaignManifestV2(
                2, manifest.metadata(), manifest.campaign(), manifest.assets(), manifest.party(),
                manifest.customStatBlocks(), manifest.customSpells(), manifest.customConditions(), manifest.customRules(),
                manifest.customEquipment(), manifest.customMagicItems(), manifest.customClasses(), manifest.customSpecies(),
                manifest.customBackgrounds(), manifest.customFeats(),
                manifest.handouts(), manifest.maps(), manifest.encounters(),
                manifest.notes(), manifest.quickNotes(), manifest.assignments(), manifest.ledgerEntries(),
                manifest.timelineEvents(), manifest.adventures(), manifest.session(), manifest.diceRolls(),
                manifest.quests(), List.of(annotation), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest2))
                .extracting(CampaignImportProblem::code)
                .contains("UNRESOLVED_REFERENCE");
    }

    @Test
    void selfDependencyIsRejected() {
        var obj = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-a")), null);
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, null, List.of(obj), Instant.parse("2025-01-01T00:00:00Z"));
        var manifest = withQuests(List.of(quest));
        assertThat(validator.validate(manifest))
                .extracting(CampaignImportProblem::code)
                .contains("SELF_DEPENDENCY");
    }

    @Test
    void duplicateDependencyEdgeIsRejected() {
        var ref = ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-a");
        var objA = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1, null, null);
        var objB = new CampaignManifestV2.QuestObjectiveDto("obj-b", "B", null, "NOT_STARTED", "ALL", 2,
                List.of(ref, ref), null);
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objA, objB), Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(validator.validate(withQuests(List.of(quest))))
                .extracting(CampaignImportProblem::code)
                .contains("DUPLICATE_DEPENDENCY");
    }

    @Test
    void cyclicDependencyIsRejected() {
        var objA = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-b")), null);
        var objB = new CampaignManifestV2.QuestObjectiveDto("obj-b", "B", null, "NOT_STARTED", "ALL", 2,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-a")), null);
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objA, objB), Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(validator.validate(withQuests(List.of(quest))))
                .extracting(CampaignImportProblem::code)
                .contains("DEPENDENCY_CYCLE");
    }

    @Test
    void crossQuestDependencyIsRejected() {
        var objA = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1, null, null);
        var objB = new CampaignManifestV2.QuestObjectiveDto("obj-b", "B", null, "NOT_STARTED", "ALL", 1,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-a")), null);
        var q1 = new CampaignManifestV2.QuestDto("q1", "Q1", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objA), Instant.parse("2025-01-01T00:00:00Z"));
        var q2 = new CampaignManifestV2.QuestDto("q2", "Q2", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objB), Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(validator.validate(withQuests(List.of(q1, q2))))
                .extracting(CampaignImportProblem::code)
                .contains("CROSS_QUEST_DEPENDENCY");
    }

    @Test
    void giverMustTargetNoteOrStatblock() {
        var link = new CampaignManifestV2.QuestLinkDto("GIVER",
                ContentReference.packageRef(CampaignContentType.PARTY_MEMBER, "pm-1"),
                "Bad", null, 1);
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, List.of(link), List.of(), Instant.parse("2025-01-01T00:00:00Z"));
        var party = new CampaignManifestV2.PartyMemberDto(
                "pm-1", "Aria", null, "Fighter 1", 16, 10, 10, 2, 30, 12, 10, 10, null, true, null,
                null, null, null, null, null, null, null);
        var base = minimal();
        var manifest = new CampaignManifestV2(
                2, base.metadata(), base.campaign(), base.assets(), List.of(party),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(),
                base.notes(), base.quickNotes(), base.assignments(), base.ledgerEntries(),
                base.timelineEvents(), base.adventures(), base.session(), base.diceRolls(),
                List.of(quest), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest))
                .extracting(CampaignImportProblem::code)
                .contains("INVALID_GIVER");
    }

    @Test
    void multipleGiversRejected() {
        var noteRef = ContentReference.packageRef(CampaignContentType.NOTE, "n1");
        var link1 = new CampaignManifestV2.QuestLinkDto("GIVER", noteRef, "G1", null, 1);
        var link2 = new CampaignManifestV2.QuestLinkDto("GIVER", noteRef, "G2", null, 2);
        var note = new CampaignManifestV2.NoteDto(
                "n1", "NPC", "Giver", null, null, false, Instant.parse("2025-01-01T00:00:00Z"), List.of());
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, List.of(link1, link2), List.of(), Instant.parse("2025-01-01T00:00:00Z"));
        var base = minimal();
        var manifest = new CampaignManifestV2(
                2, base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(),
                List.of(note), base.quickNotes(), base.assignments(), base.ledgerEntries(),
                base.timelineEvents(), base.adventures(), base.session(), base.diceRolls(),
                List.of(quest), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest))
                .extracting(CampaignImportProblem::code)
                .contains("MULTIPLE_GIVERS");
    }

    @Test
    void checkWithoutDcRequiresSourceAnnotation() {
        var check = new CampaignManifestV2.SceneCheckDto(
                "Unknown check", "wis", "perception", null, "PLAYER_FACING",
                null, null, null, null, null, 1);
        var scene = new CampaignManifestV2.SceneDto(
                "sc-1", "S", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, List.of(check), null, null, null);
        var chapter = new CampaignManifestV2.ChapterDto("ch-1", "C", null, 1, List.of(scene));
        var adv = new CampaignManifestV2.AdventureDto("adv-1", "A", null, null, 1, List.of(chapter), null);
        var base = minimal();
        var manifest = new CampaignManifestV2(
                2, base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(),
                base.notes(), base.quickNotes(), base.assignments(), base.ledgerEntries(),
                base.timelineEvents(), List.of(adv), base.session(), base.diceRolls(),
                base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest))
                .extracting(CampaignImportProblem::code)
                .contains("MISSING_SOURCE_ANNOTATION");
    }

    @Test
    void invalidSceneLinkRoleTypeRejected() {
        var link = new CampaignManifestV2.SceneLinkDto("HANDOUT",
                ContentReference.packageRef(CampaignContentType.NOTE, "n1"),
                "wrong", null, 1);
        var note = new CampaignManifestV2.NoteDto(
                "n1", "PERSON", "N", null, null, false, Instant.parse("2025-01-01T00:00:00Z"), List.of());
        var scene = new CampaignManifestV2.SceneDto(
                "sc-1", "S", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(link));
        var chapter = new CampaignManifestV2.ChapterDto("ch-1", "C", null, 1, List.of(scene));
        var adv = new CampaignManifestV2.AdventureDto("adv-1", "A", null, null, 1, List.of(chapter), null);
        var base = minimal();
        var manifest = new CampaignManifestV2(
                2, base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(),
                List.of(note), base.quickNotes(), base.assignments(), base.ledgerEntries(),
                base.timelineEvents(), List.of(adv), base.session(), base.diceRolls(),
                base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(manifest))
                .extracting(CampaignImportProblem::code)
                .contains("INVALID_REFERENCE_TYPE");
    }

    @Test
    void prerequisitesRequireCompletionMode() {
        var objA = new CampaignManifestV2.QuestObjectiveDto("obj-a", "A", null, "NOT_STARTED", "ALL", 1, null, null);
        var objB = new CampaignManifestV2.QuestObjectiveDto("obj-b", "B", null, "NOT_STARTED", null, 2,
                List.of(ContentReference.packageRef(CampaignContentType.OBJECTIVE, "obj-a")), null);
        var quest = new CampaignManifestV2.QuestDto("q1", "Q", "ACTIVE", null, null,
                null, null, null, null, null, List.of(objA, objB), Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(validator.validate(withQuests(List.of(quest))))
                .extracting(CampaignImportProblem::code)
                .contains("INVALID_COMPLETION_MODE");
    }

    @Test
    void validRollableTablePasses() {
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "NPC Reactions", null,
                "RANGE", "1d6", "GENERIC", null,
                List.of(new CampaignManifestV2.RollableTableEntryDto(
                        "e1", 1, 2, null, "Friendly", null, null)),
                null, null);
        assertThat(validator.validate(withRollableTables(List.of(table)))).isEmpty();
    }

    @Test
    void invalidTableExpressionDetected() {
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Bad", null,
                "RANGE", "not-a-dice", "GENERIC", null, null, null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("INVALID_TABLE_EXPRESSION");
    }

    @Test
    void invalidQuantityExpressionDetected() {
        var entry = new CampaignManifestV2.RollableTableEntryDto(
                "e1", 1, 6, null, "test", "not-a-qty", null);
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Test", null,
                "RANGE", "1d6", "GENERIC", null, List.of(entry), null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("INVALID_QUANTITY_EXPRESSION");
    }

    @Test
    void tableRangeGapDetected() {
        var e1 = new CampaignManifestV2.RollableTableEntryDto("e1", 1, 2, null, "A", null, null);
        var e2 = new CampaignManifestV2.RollableTableEntryDto("e2", 4, 6, null, "B", null, null);
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Gap", null,
                "RANGE", "1d6", "GENERIC", null, List.of(e1, e2), null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("TABLE_RANGE_GAP");
    }

    @Test
    void tableRangeOverlapDetected() {
        var e1 = new CampaignManifestV2.RollableTableEntryDto("e1", 1, 4, null, "A", null, null);
        var e2 = new CampaignManifestV2.RollableTableEntryDto("e2", 3, 6, null, "B", null, null);
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Overlap", null,
                "RANGE", "1d6", "GENERIC", null, List.of(e1, e2), null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("TABLE_RANGE_OVERLAP");
    }

    @Test
    void tableRangeBoundsDetected() {
        var e1 = new CampaignManifestV2.RollableTableEntryDto("e1", 0, 2, null, "A", null, null);
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Bounds", null,
                "RANGE", "1d6", "GENERIC", null, List.of(e1), null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("TABLE_RANGE_BOUNDS");
    }

    @Test
    void tableWeightInvalidDetected() {
        var e1 = new CampaignManifestV2.RollableTableEntryDto("e1", null, null, 0, "A", null, null);
        var table = new CampaignManifestV2.RollableTableDto(
                "rt-1", null, "Weight", null,
                "WEIGHTED", null, "GENERIC", null, List.of(e1), null, null);
        assertThat(validator.validate(withRollableTables(List.of(table))))
                .extracting(CampaignImportProblem::code)
                .contains("TABLE_WEIGHT_INVALID");
    }

    @Test
    void tableReferenceCycleDetected() {
        var refA = ContentReference.packageRef(CampaignContentType.ROLLABLE_TABLE, "rt-b");
        var refB = ContentReference.packageRef(CampaignContentType.ROLLABLE_TABLE, "rt-a");
        var e1 = new CampaignManifestV2.RollableTableEntryDto("e1", null, null, null, "A", null, List.of(refA));
        var e2 = new CampaignManifestV2.RollableTableEntryDto("e2", null, null, null, "B", null, List.of(refB));
        var tA = new CampaignManifestV2.RollableTableDto(
                "rt-a", null, "A", null, "RANGE", "1d6", "GENERIC", null, List.of(e1), null, null);
        var tB = new CampaignManifestV2.RollableTableDto(
                "rt-b", null, "B", null, "RANGE", "1d6", "GENERIC", null, List.of(e2), null, null);
        assertThat(validator.validate(withRollableTables(List.of(tA, tB))))
                .extracting(CampaignImportProblem::code)
                .contains("TABLE_REFERENCE_CYCLE");
    }

    private CampaignManifestV2 withRollableTables(List<CampaignManifestV2.RollableTableDto> tables) {
        var m = minimal();
        return new CampaignManifestV2(
                2, m.metadata(), m.campaign(), m.assets(), m.party(),
                m.customStatBlocks(), m.customSpells(), m.customConditions(), m.customRules(),
                m.customEquipment(), m.customMagicItems(), m.customClasses(), m.customSpecies(),
                m.customBackgrounds(), m.customFeats(),
                m.handouts(), m.maps(), m.encounters(),
                m.notes(), m.quickNotes(), m.assignments(), m.ledgerEntries(),
                m.timelineEvents(), m.adventures(), m.session(), m.diceRolls(),
                m.quests(), m.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), tables);
    }

    private CampaignManifestV2 withQuests(List<CampaignManifestV2.QuestDto> quests) {
        var m = minimal();
        return new CampaignManifestV2(
                2, m.metadata(), m.campaign(), m.assets(), m.party(),
                m.customStatBlocks(), m.customSpells(), m.customConditions(), m.customRules(),
                m.customEquipment(), m.customMagicItems(), m.customClasses(), m.customSpecies(),
                m.customBackgrounds(), m.customFeats(),
                m.handouts(), m.maps(), m.encounters(),
                m.notes(), m.quickNotes(), m.assignments(), m.ledgerEntries(),
                m.timelineEvents(), m.adventures(), m.session(), m.diceRolls(),
                quests, m.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
