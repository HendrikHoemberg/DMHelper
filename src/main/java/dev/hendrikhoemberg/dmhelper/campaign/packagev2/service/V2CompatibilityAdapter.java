package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.springframework.stereotype.Component;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class V2CompatibilityAdapter {

    public PreparedV1Import prepare(PendingCampaignImport pending) {
        try {
            return convert(pending);
        } catch (IOException e) {
            throw new IllegalStateException("Validated campaign asset could not be read", e);
        }
    }

    private PreparedV1Import convert(PendingCampaignImport pending) throws IOException {
        CampaignManifestV2 v2 = pending.result().manifest();
        Map<String, PreparedV1Import.ImportedKey> bindings = new LinkedHashMap<>();
        Map<String, HandoutImportSource> externalHandouts = new LinkedHashMap<>();
        Map<CampaignContentType, Map<String, String>> legacy = new EnumMap<>(CampaignContentType.class);
        Map<String, AssetDescriptor> assetDescriptors = new LinkedHashMap<>();
        for (AssetDescriptor asset : list(v2.assets())) assetDescriptors.put(asset.key(), asset);

        bind(bindings, legacy, "/campaign", CampaignContentType.CAMPAIGN, v2.campaign().key(), "CAMPAIGN");

        List<CampaignExportDto.PartyMemberExportDto> party = new ArrayList<>();
        for (int i = 0; i < size(v2.party()); i++) {
            var p = v2.party().get(i);
            bind(bindings, legacy, "/party/" + i, CampaignContentType.PARTY_MEMBER, p.key(), p.characterName());
            CampaignExportDto.SheetExportDto sheet = null;
            if (p.sheet() != null) {
                var s = p.sheet();
                bind(bindings, legacy, "/party/" + i + "/sheet", CampaignContentType.CHARACTER_SHEET, s.key(), s.key());
                List<CampaignExportDto.ResourceExportDto> resources = new ArrayList<>();
                for (int j = 0; j < size(s.resources()); j++) {
                    var r = s.resources().get(j);
                    bind(bindings, legacy, "/party/" + i + "/sheet/resources/" + j,
                            CampaignContentType.SHEET_RESOURCE, r.key(), r.name());
                    resources.add(new CampaignExportDto.ResourceExportDto(r.name(), r.maxUses(), r.currentUses(), r.resetRule()));
                }
                sheet = new CampaignExportDto.SheetExportDto(s.abilityScores(),
                        list(s.classLevels()).stream().map(c -> new CampaignExportDto.ClassLevelExportDto(
                                catalogKey(c.classRef()), c.level(), list(c.hitDieRolls()))).toList(),
                        s.proficiencies(), catalogKey(s.speciesRef()), catalogKey(s.backgroundRef()),
                        list(s.featRefs()).stream().map(V2CompatibilityAdapter::catalogKey).toList(), s.xp(), s.overrides(),
                        s.hitDiceUsed(), resources, list(s.spells()).stream().map(spell ->
                        new CampaignExportDto.SpellRefExportDto(catalogKey(spell.spellRef()), spell.prepared(),
                                catalogKey(spell.sourceClassRef()))).toList(), s.spellSlotsUsed());
            }
            party.add(new CampaignExportDto.PartyMemberExportDto(p.characterName(), p.playerName(), p.classAndLevel(),
                    p.ac(), p.maxHp(), p.initiativeBonus(), p.speed(), p.passivePerception(), p.passiveInsight(),
                    p.passiveInvestigation(), p.notes(), p.active(), sheet));
        }

        List<CampaignExportDto.StatBlockExportDto> stats = new ArrayList<>();
        for (int i = 0; i < size(v2.customStatBlocks()); i++) {
            var s = v2.customStatBlocks().get(i);
            String legacyKey = s.sourceKey() == null ? s.key() : s.sourceKey();
            bind(bindings, legacy, "/statBlocks/" + i, CampaignContentType.STATBLOCK, s.key(), legacyKey);
            stats.add(new CampaignExportDto.StatBlockExportDto(legacyKey, s.name(), s.cr(), s.type(), s.size(),
                    s.alignment(), s.ac(), s.hp(), s.speed(), s.strScore(), s.dexScore(), s.conScore(), s.intScore(),
                    s.wisScore(), s.chaScore(), s.strSave(), s.dexSave(), s.conSave(), s.intSave(), s.wisSave(),
                    s.chaSave(), s.skills(), s.damageVulnerabilities(), s.damageResistances(), s.damageImmunities(),
                    s.conditionImmunities(), s.senses(), s.languages(), s.traits(), s.actions(), s.bonusActions(),
                    s.reactions(), s.legendaryActions(), s.legendaryDescription(), s.lairActions(), s.xp()));
        }

        List<CampaignExportDto.HandoutExportDto> handouts = new ArrayList<>();
        for (int i = 0; i < size(v2.handouts()); i++) {
            var h = v2.handouts().get(i);
            bind(bindings, legacy, "/handouts/" + i, CampaignContentType.HANDOUT, h.key(), h.title());
            AssetDescriptor descriptor = requireAsset(assetDescriptors, h.assetRef());
            var assetPath = requireAssetPath(pending, descriptor.key());
            String pointer = "/handouts/" + i;
            externalHandouts.put(pointer, new HandoutImportSource(descriptor.originalName(), descriptor.mediaType(),
                    new FileSystemResource(assetPath), descriptor.sizeBytes(), descriptor.sha256()));
            handouts.add(new CampaignExportDto.HandoutExportDto(h.title(), list(h.tags()), descriptor.originalName(),
                    h.contentType(), null));
        }

        for (int i = 0; i < size(v2.maps()); i++) {
            var m = v2.maps().get(i);
            bind(bindings, legacy, "/maps/" + i, CampaignContentType.MAP, m.key(), m.key());
            for (int j = 0; j < size(m.tokens()); j++) bind(bindings, legacy, "/maps/" + i + "/tokens/" + j,
                    CampaignContentType.TOKEN, m.tokens().get(j).key(), m.tokens().get(j).key());
        }
        for (int i = 0; i < size(v2.encounters()); i++) {
            var e = v2.encounters().get(i);
            bind(bindings, legacy, "/encounters/" + i, CampaignContentType.ENCOUNTER, e.key(), e.key());
            for (int j = 0; j < size(e.combatants()); j++) bind(bindings, legacy,
                    "/encounters/" + i + "/combatants/" + j, CampaignContentType.COMBATANT,
                    e.combatants().get(j).key(), e.combatants().get(j).key());
        }
        for (int i = 0; i < size(v2.notes()); i++) bind(bindings, legacy, "/notes/" + i,
                CampaignContentType.NOTE, v2.notes().get(i).key(), v2.notes().get(i).title());
        for (int i = 0; i < size(v2.assignments()); i++) bind(bindings, legacy, "/assignments/" + i,
                CampaignContentType.ASSIGNMENT, v2.assignments().get(i).key(), uuid(v2.assignments().get(i).key()).toString());

        for (int ai = 0; ai < size(v2.adventures()); ai++) {
            var a = v2.adventures().get(ai);
            bind(bindings, legacy, "/adventures/" + ai, CampaignContentType.ADVENTURE, a.key(), a.name());
            for (int ci = 0; ci < size(a.chapters()); ci++) {
                var c = a.chapters().get(ci);
                bind(bindings, legacy, "/adventures/" + ai + "/chapters/" + ci, CampaignContentType.CHAPTER, c.key(), c.title());
                for (int si = 0; si < size(c.scenes()); si++) {
                    var s = c.scenes().get(si);
                    String scenePath = a.name() + "/" + c.title() + "/" + s.key();
                    bind(bindings, legacy, "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si,
                            CampaignContentType.SCENE, s.key(), scenePath);
                }
            }
        }

        List<CampaignExportDto.MapExportDto> maps = new ArrayList<>();
        for (int i = 0; i < size(v2.maps()); i++) {
            var m = v2.maps().get(i);
            List<MapLayerDto> layers = new ArrayList<>();
            if (m.document() != null) for (var l : list(m.document().layers())) {
                MapLayerDto.ImageDto image = null;
                if (l.image() != null) {
                    AssetDescriptor descriptor = requireAsset(assetDescriptors, l.image().assetRef());
                    byte[] bytes = Files.readAllBytes(requireAssetPath(pending, descriptor.key()));
                    image = new MapLayerDto.ImageDto("data:" + descriptor.mediaType() + ";base64,"
                            + Base64.getEncoder().encodeToString(bytes), l.image().x(), l.image().y(),
                            l.image().width(), l.image().height());
                }
                layers.add(new MapLayerDto(l.id(), l.name(), l.type(), l.visible(), l.locked(),
                        list(l.cells()), list(l.shapes()), image));
            }
            MapDocumentDto document = m.document() == null ? null : new MapDocumentDto(1, m.document().grid(), layers,
                    list(m.document().primitives()), list(m.document().customTerrain()));
            maps.add(new CampaignExportDto.MapExportDto(m.key(), m.name(),
                    new CampaignExportDto.MapExportDto.GridDto(m.grid().w(), m.grid().h(), m.grid().cellPx(), m.grid().gridType()),
                    m.movementMode(), m.showGrid(), document, list(m.tokens()).stream().map(t ->
                    new CampaignExportDto.MapExportDto.TokenExportDto(t.key(), t.name(), t.kind(), t.color(),
                            t.positionX(), t.positionY(), t.sizeCols(), t.sizeRows(), t.hidden(),
                            legacy(t.statBlockRef(), legacy), legacy(t.partyMemberRef(), legacy), t.currentHp(), t.maxHp(),
                            t.dead(), t.notes())).toList()));
        }

        List<CampaignExportDto.EncounterExportDto> encounters = new ArrayList<>();
        for (var e : list(v2.encounters())) encounters.add(new CampaignExportDto.EncounterExportDto(e.name(),
                list(e.combatants()).stream().map(c -> new CampaignExportDto.CombatantExportDto(c.name(), c.initiative(),
                        c.tieBreaker(), c.sortOrder(), c.maxHp(), c.currentHp(), c.tempHp(), c.kind(), c.groupId(),
                        c.groupLeader(), legacy(c.tokenRef(), legacy), legacy(c.statBlockRef(), legacy),
                        legacy(c.partyMemberRef(), legacy), c.defeated(), c.hidden(), c.conditionsJson(), c.concentratingOn(),
                        c.concentrationCheckPending(), c.legendaryActionsUsed(), c.legendaryResistancesUsed(),
                        c.legendaryActionsMax(), c.legendaryResistancesMax(), c.rechargedAbilities(), c.notes())).toList(),
                e.status(), e.round(), e.activeTurnIndex(), e.logSequence(), e.lairActionName(), e.lairActionDescription(),
                e.key(), legacy(e.mapRef(), legacy)));

        List<CampaignExportDto.NoteExportDto> notes = list(v2.notes()).stream().map(n ->
                new CampaignExportDto.NoteExportDto(n.type(), n.title(), n.body(), n.tags(), n.dmOnly())).toList();
        List<CampaignExportDto.QuickNoteExportDto> quickNotes = new ArrayList<>();
        for (int i = 0; i < size(v2.quickNotes()); i++) {
            var q = v2.quickNotes().get(i);
            bind(bindings, legacy, "/quicknotes/" + i, CampaignContentType.QUICK_NOTE, q.key(), q.key());
            quickNotes.add(new CampaignExportDto.QuickNoteExportDto(q.targetRef().type().name(),
                    legacy(q.targetRef(), legacy), q.body(), q.createdAt().toString()));
        }
        List<CampaignExportDto.AssignmentExportDto> assignments = list(v2.assignments()).stream().map(a ->
                new CampaignExportDto.AssignmentExportDto(UUID.fromString(legacy.get(CampaignContentType.ASSIGNMENT).get(a.key())),
                        legacy(a.holderRef(), legacy), catalogKey(a.magicItemRef()), catalogKey(a.equipmentItemRef()),
                        a.customText(), a.quantity(), a.attuned())).toList();
        List<CampaignExportDto.LedgerExportDto> ledger = new ArrayList<>();
        for (int i = 0; i < size(v2.ledgerEntries()); i++) {
            var l = v2.ledgerEntries().get(i);
            bind(bindings, legacy, "/ledger/" + i, CampaignContentType.LEDGER_ENTRY, l.key(), l.key());
            ledger.add(new CampaignExportDto.LedgerExportDto(uuid(l.key()), l.timestamp(), l.inGameYear(), l.inGameMonth(),
                    l.inGameDay(), l.kind(), l.direction(), l.amount(), l.currency(), l.holder(), l.note(),
                    legacy(l.itemAssignmentRef(), legacy)));
        }
        List<CampaignExportDto.TimelineExportDto> timeline = new ArrayList<>();
        for (int i = 0; i < size(v2.timelineEvents()); i++) {
            var t = v2.timelineEvents().get(i);
            bind(bindings, legacy, "/timeline/" + i, CampaignContentType.TIMELINE_EVENT, t.key(), t.key());
            timeline.add(new CampaignExportDto.TimelineExportDto(uuid(t.key()), t.inGameYear(), t.inGameMonth(),
                    t.inGameDay(), t.title(), t.body(), legacy(t.noteRef(), legacy)));
        }
        List<CampaignExportDto.AdventureExportDto> adventures = list(v2.adventures()).stream().map(a ->
                new CampaignExportDto.AdventureExportDto(a.name(), a.description(), a.sourceAttribution(), a.sortOrder(),
                        list(a.chapters()).stream().map(c -> new CampaignExportDto.ChapterExportDto(c.title(), c.intro(),
                                c.sortOrder(), list(c.scenes()).stream().map(s -> new CampaignExportDto.SceneExportDto(
                                s.title(), s.key(), s.body(), s.status(), s.sortOrder(), legacy(s.mapRef(), legacy), s.pin(),
                                legacy(s.encounterRef(), legacy), list(s.statblockRefs()).stream().map(r -> legacy(r, legacy)).toList(),
                                list(s.handoutRefs()).stream().map(r -> legacy(r, legacy)).toList())).toList())).toList())).toList();

        CampaignExportDto dto = new CampaignExportDto(1,
                new CampaignExportDto.CampaignDto(v2.campaign().name(), v2.campaign().description()), party, stats,
                handouts, maps, encounters, notes, quickNotes, assignments, ledger, timeline, adventures);
        return new PreparedV1Import(dto, Map.copyOf(bindings), Map.copyOf(externalHandouts));
    }

    private static java.nio.file.Path requireAssetPath(PendingCampaignImport pending, String key) {
        var path = pending.result().assetsByKey().get(key);
        if (path == null) throw new IllegalStateException("Validated asset is missing");
        return path;
    }

    private static AssetDescriptor requireAsset(Map<String, AssetDescriptor> descriptors, String key) {
        AssetDescriptor descriptor = descriptors.get(key);
        if (descriptor == null) throw new IllegalStateException("Asset reference is unresolved");
        return descriptor;
    }

    private static void bind(Map<String, PreparedV1Import.ImportedKey> bindings,
                             Map<CampaignContentType, Map<String, String>> legacy, String pointer,
                             CampaignContentType type, String key, String legacyValue) {
        bindings.put(pointer, new PreparedV1Import.ImportedKey(type, key));
        legacy.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).put(key, legacyValue);
    }

    private static String legacy(ContentReference ref, Map<CampaignContentType, Map<String, String>> legacy) {
        if (ref == null) return null;
        if (ref.scope() == ContentReference.Scope.CATALOG) return ref.sourceKey();
        String value = legacy.getOrDefault(ref.type(), Map.of()).get(ref.key());
        if (value == null) throw new IllegalStateException("Validated package reference is unresolved");
        return value;
    }

    private static String catalogKey(ContentReference ref) {
        return ref == null ? null : ref.sourceKey();
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("dmhelper-v2:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static int size(List<?> values) { return values == null ? 0 : values.size(); }
    private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
}
