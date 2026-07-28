package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignSemanticComparatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void reportsTheExactChangedSemanticPath() throws Exception {
        CampaignManifestV2 base = minimal();
        var changed = new CampaignManifestV2(
                base.formatVersion(), base.metadata(),
                new CampaignManifestV2.CampaignDto(
                        base.campaign().key(), "Changed", base.campaign().description(),
                        base.campaign().createdAt(), base.campaign().settings(), base.campaign().currentSceneRef(), null),
                base.assets(), base.party(), base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(),
                base.encounters(), base.notes(), base.quickNotes(), base.assignments(), base.ledgerEntries(),
                base.timelineEvents(), base.adventures(), base.session(), base.diceRolls(), base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(base), CampaignSemanticSnapshot.from(changed)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/campaign/name");
    }

    @Test
    void ignoresOnlyPackageCreationTime() throws Exception {
        CampaignManifestV2 base = minimal();
        var metadata = new CampaignManifestV2.Metadata(
                base.metadata().packageKey(), Instant.parse("2099-01-01T00:00:00Z"),
                base.metadata().generator(), base.metadata().catalogVersion(),
                base.metadata().catalogSha256(), base.metadata().exclusions());
        var changed = new CampaignManifestV2(
                base.formatVersion(), metadata, base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(), base.notes(),
                base.quickNotes(), base.assignments(), base.ledgerEntries(), base.timelineEvents(),
                base.adventures(), base.session(), base.diceRolls(), base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatCode(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(base), CampaignSemanticSnapshot.from(changed)))
                .doesNotThrowAnyException();
    }

    @Test
    void treatsNumericallyEqualDecimalScalesAsEquivalent() throws Exception {
        var expected = withLedgerAmount(minimal(), new java.math.BigDecimal("250.0"));
        var actual = withLedgerAmount(minimal(), new java.math.BigDecimal("250.00"));

        assertThatCode(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(expected), CampaignSemanticSnapshot.from(actual)))
                .doesNotThrowAnyException();
    }

    @Test
    void comparesTheIndependentPersistenceProjectionWhenBothSnapshotsHaveOne() throws Exception {
        var expectedProjection = mapper.readTree("{\"party\":[{\"currentHp\":12}]}");
        var actualProjection = mapper.readTree("{\"party\":[{\"currentHp\":11}]}");

        assertThatThrownBy(() -> CampaignSemanticComparator.assertEquivalent(
                new CampaignSemanticSnapshot(minimal(), expectedProjection),
                new CampaignSemanticSnapshot(minimal(), actualProjection)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/persistence/party/0/currentHp");
    }

    @Test
    void preservesTheOrderOfSceneHandoutReferences() throws Exception {
        var first = dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference.packageRef(
                dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.HANDOUT, "first");
        var second = dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference.packageRef(
                dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.HANDOUT, "second");
        var expected = withSceneHandouts(minimal(), java.util.List.of(first, second));
        var actual = withSceneHandouts(minimal(), java.util.List.of(second, first));

        assertThatThrownBy(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(expected), CampaignSemanticSnapshot.from(actual)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/adventures/0/chapters/0/scenes/0/handoutRefs/0/key");
    }

    @Test
    void treatsNullInitiativeAsEquivalentToOmittedInitiative() throws Exception {
        var base = minimal();
        var a = withCombatantInitiative(base, null);
        var b = withCombatantInitiative(base, null);
        assertThatCode(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(a), CampaignSemanticSnapshot.from(b)))
                .doesNotThrowAnyException();
    }

    @Test
    void distinguishesNullFromZeroInitiative() throws Exception {
        var base = minimal();
        var withNull = withCombatantInitiative(base, null);
        var withZero = withCombatantInitiative(base, 0);
        assertThatThrownBy(() -> CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(withNull), CampaignSemanticSnapshot.from(withZero)))
                .isInstanceOf(AssertionError.class);
    }

    private CampaignManifestV2 withCombatantInitiative(CampaignManifestV2 base, Integer initiative) {
        var combatant = new CampaignManifestV2.CombatantDto(
                "c1", "Goblin", initiative, 0, 0, 7, 7, 0,
                "MONSTER", null, false, null, null, null,
                false, false, null, null, false,
                0, 0, 0, 0, null, null,
                null, null, null, null, null, null);
        var encounter = new CampaignManifestV2.EncounterDto(
                "enc1", "Test", List.of(combatant), "PLANNED",
                0, -1, null, 0, null, null, null, false, List.of(),
                null, null, null, null, null, null);
        return new CampaignManifestV2(
                base.formatVersion(), base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), List.of(encounter), base.notes(),
                base.quickNotes(), base.assignments(), base.ledgerEntries(), base.timelineEvents(),
                base.adventures(), base.session(), base.diceRolls(), base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private CampaignManifestV2 withSceneHandouts(
            CampaignManifestV2 base,
            java.util.List<dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference> refs) {
        var scene = new CampaignManifestV2.SceneDto(
                "scene", "Scene", null, "UNVISITED", 0,
                null, null, null, java.util.List.of(), refs,
                null, null, null, null, null, null, null, null, null, null);
        var chapter = new CampaignManifestV2.ChapterDto(
                "chapter", "Chapter", null, 0, java.util.List.of(scene));
        var adventure = new CampaignManifestV2.AdventureDto(
                "adventure", "Adventure", null, null, 0,
                java.util.List.of(chapter), Instant.parse("2025-01-01T00:00:00Z"));
        return new CampaignManifestV2(
                base.formatVersion(), base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(), base.notes(),
                base.quickNotes(), base.assignments(), base.ledgerEntries(), base.timelineEvents(),
                java.util.List.of(adventure), base.session(), base.diceRolls(), base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private CampaignManifestV2 withLedgerAmount(CampaignManifestV2 base, java.math.BigDecimal amount) {
        var entry = new CampaignManifestV2.LedgerEntryDto(
                "ledger", Instant.parse("2025-01-01T00:00:00Z"),
                null, null, null, "GOLD", "GAIN", amount, "gp", null, null, null);
        return new CampaignManifestV2(
                base.formatVersion(), base.metadata(), base.campaign(), base.assets(), base.party(),
                base.customStatBlocks(), base.customSpells(), base.customConditions(), base.customRules(),
                base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                base.customBackgrounds(), base.customFeats(),
                base.handouts(), base.maps(), base.encounters(), base.notes(),
                base.quickNotes(), base.assignments(), java.util.List.of(entry), base.timelineEvents(),
                base.adventures(), base.session(), base.diceRolls(), base.quests(), base.annotations(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private CampaignManifestV2 minimal() throws Exception {
        try (var input = getClass().getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            return mapper.readValue(input, CampaignManifestV2.class);
        }
    }
}
