package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.*;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;

import java.util.List;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public final class CampaignSemanticComparator {

    private CampaignSemanticComparator() {}

    public static void assertEquivalent(CampaignSemanticSnapshot expected, CampaignSemanticSnapshot actual) {
        CampaignManifestV2 normExpected = normalize(expected.manifest());
        CampaignManifestV2 normActual = normalize(actual.manifest());

        assertThat(normActual)
                .usingRecursiveComparison()
                .isEqualTo(normExpected);
    }

    private static CampaignManifestV2 normalize(CampaignManifestV2 m) {
        if (m == null) return null;
        return new CampaignManifestV2(
                m.formatVersion(),
                CampaignSemanticSnapshot.withoutCreatedAt(m.metadata()),
                normalizeCampaign(m.campaign()),
                normalizeAssets(m.assets()),
                normalizeParty(CampaignSemanticSnapshot.sortByKey(m.party(), p -> p.key())),
                normalizeStatBlocks(m.customStatBlocks()),
                normalizeHandouts(m.handouts()),
                normalizeMaps(m.maps()),
                normalizeEncounters(m.encounters()),
                normalizeNotes(m.notes()),
                normalizeQuickNotes(m.quickNotes()),
                CampaignSemanticSnapshot.sortByKey(m.assignments(), a -> a.key()),
                CampaignSemanticSnapshot.sortByKey(m.ledgerEntries(), l -> l.key()),
                CampaignSemanticSnapshot.sortByKey(m.timelineEvents(), t -> t.key()),
                normalizeAdventures(m.adventures()),
                normalizeDiceRolls(m.diceRolls())
        );
    }

    private static CampaignDto normalizeCampaign(CampaignDto c) {
        if (c == null) return null;
        return new CampaignDto(c.key(), c.name(), c.description(), null, c.settings(), c.currentSceneRef());
    }

    private static List<AssetDescriptor> normalizeAssets(List<AssetDescriptor> assets) {
        if (assets == null) return null;
        return CampaignSemanticSnapshot.sortByKey(assets, a -> a.key() != null ? a.key() : "")
                .stream()
                .map(a -> new AssetDescriptor("asset", "asset", "asset", 0, "asset", "asset"))
                .toList();
    }

    private static List<HandoutDto> normalizeHandouts(List<HandoutDto> handouts) {
        if (handouts == null) return null;
        return CampaignSemanticSnapshot.sortByKey(handouts, h -> h.key())
                .stream()
                .map(h -> new HandoutDto(h.key(), h.title(), h.tags(), "asset", h.contentType(), h.dmOnly(), h.presented()))
                .toList();
    }

    private static List<MapDto> normalizeMaps(List<MapDto> maps) {
        if (maps == null) return null;
        return CampaignSemanticSnapshot.sortByKey(maps, m0 -> m0.key())
                .stream()
                .map(m0 -> new MapDto(m0.key(), m0.name(), m0.grid(), m0.movementMode(), m0.showGrid(), normalizeDoc(m0.document()), m0.tokens(), m0.sortOrder()))
                .toList();
    }

    private static MapDocumentV2 normalizeDoc(MapDocumentV2 doc) {
        if (doc == null) return null;
        var layers = doc.layers() == null ? null : doc.layers().stream()
                .map(l -> l.image() == null ? l : new LayerDto(l.id(), l.name(), l.type(), l.visible(), l.locked(), l.cells(), l.shapes(), new ImageDto("asset", l.image().x(), l.image().y(), l.image().width(), l.image().height())))
                .toList();
        return new MapDocumentV2(doc.schemaVersion(), doc.grid(), layers, doc.primitives(), doc.customTerrain());
    }

    private static List<EncounterDto> normalizeEncounters(List<EncounterDto> encounters) {
        if (encounters == null) return null;
        return CampaignSemanticSnapshot.sortByKey(encounters, e -> e.key())
                .stream()
                .map(e -> {
                    var log = e.combatLog() == null ? null : CampaignSemanticSnapshot.sortByKey(e.combatLog(), c -> c.sequence() + "-" + c.type())
                            .stream()
                            .map(cl -> new CombatLogEntryDto("key", cl.round(), cl.sequence(), cl.type(), cl.combatantRef(), cl.payload(), null))
                            .toList();
                    return new EncounterDto(e.key(), e.name(), e.combatants(), e.status(), e.round(), e.activeTurnIndex(), e.logSequence(), e.lairActionName(), e.lairActionDescription(), e.mapRef(), e.lairActionTriggered(), log);
                })
                .toList();
    }

    private static List<NoteDto> normalizeNotes(List<NoteDto> notes) {
        if (notes == null) return null;
        return CampaignSemanticSnapshot.sortByKey(notes, n -> n.key() != null ? n.key() : "")
                .stream()
                .map(n -> new NoteDto(n.key(), n.type(), n.title(), n.body(), n.tags(), n.dmOnly(), null, n.links()))
                .toList();
    }

    private static List<QuickNoteDto> normalizeQuickNotes(List<QuickNoteDto> notes) {
        if (notes == null) return null;
        return CampaignSemanticSnapshot.sortByKey(notes, q -> q.key())
                .stream()
                .map(q -> new QuickNoteDto(q.key(), q.targetRef(), q.body(), null))
                .toList();
    }

    private static List<DiceRollDto> normalizeDiceRolls(List<DiceRollDto> rolls) {
        if (rolls == null) return null;
        return CampaignSemanticSnapshot.sortByKey(rolls, d -> d.createdAt().toString() + "-" + d.expression() + "-" + d.total())
                .stream()
                .map(d -> new DiceRollDto("key", d.expression(), d.rolls(), d.modifier(), d.total(), d.advantage(), d.disadvantage(), d.encounterRef(), null))
                .toList();
    }

    private static List<StatBlockDto> normalizeStatBlocks(List<StatBlockDto> blocks) {
        if (blocks == null) return null;
        return CampaignSemanticSnapshot.sortByKey(blocks, s -> s.key() != null ? s.key() : "")
                .stream()
                .map(s -> new StatBlockDto(s.key(), s.sourceKey(), s.name(), s.cr(), s.type(),
                        s.size(), s.alignment(), s.ac(), s.hp(), s.speed(),
                        s.strScore(), s.dexScore(), s.conScore(), s.intScore(), s.wisScore(), s.chaScore(),
                        s.strSave(), s.dexSave(), s.conSave(), s.intSave(), s.wisSave(), s.chaSave(),
                        s.skills(), s.damageVulnerabilities(), s.damageResistances(), s.damageImmunities(),
                        s.conditionImmunities(), s.senses(), s.languages(),
                        s.traits(), s.actions(), s.bonusActions(), s.reactions(),
                        s.legendaryActions(), s.legendaryDescription(), s.lairActions(), s.xp(),
                        null))
                .toList();
    }

    private static List<AdventureDto> normalizeAdventures(List<AdventureDto> adventures) {
        if (adventures == null) return null;
        return CampaignSemanticSnapshot.sortByKey(adventures, a -> a.key() != null ? a.key() : "")
                .stream()
                .map(a -> new AdventureDto(a.key(), a.name(), a.description(), a.sourceAttribution(),
                        a.sortOrder(), a.chapters(), null))
                .toList();
    }

    private static List<PartyMemberDto> normalizeParty(List<PartyMemberDto> party) {
        if (party == null) return null;
        return party.stream()
                .map(CampaignSemanticComparator::normalizePartyMember)
                .toList();
    }

    private static PartyMemberDto normalizePartyMember(PartyMemberDto p) {
        SheetDto sheet = p.sheet();
        if (sheet == null) return p;
        SheetDto normSheet = new SheetDto(
                sheet.key(), sheet.abilityScores(), sheet.classLevels(),
                sheet.proficiencies(), sheet.speciesRef(), sheet.backgroundRef(),
                sheet.featRefs(), sheet.xp(), sheet.overrides(), sheet.hitDiceUsed(),
                CampaignSemanticSnapshot.sortByKey(sheet.resources(), r -> r.key() != null ? r.key() : ""),
                sortSpells(sheet.spells()),
                sheet.spellSlotsUsed()
        );
        return new PartyMemberDto(
                p.key(), p.characterName(), p.playerName(), p.classAndLevel(),
                p.ac(), p.maxHp(), p.currentHp(), p.initiativeBonus(),
                p.speed(), p.passivePerception(), p.passiveInsight(), p.passiveInvestigation(),
                p.notes(), p.active(), normSheet
        );
    }

    private static List<SpellRefDto> sortSpells(List<SpellRefDto> spells) {
        if (spells == null) return null;
        return spells.stream()
                .sorted(Comparator.comparing(
                        s -> s.spellRef() != null ? s.spellRef().sourceKey() != null
                                ? s.spellRef().sourceKey() : s.spellRef().key() != null
                                ? s.spellRef().key() : "" : "",
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
