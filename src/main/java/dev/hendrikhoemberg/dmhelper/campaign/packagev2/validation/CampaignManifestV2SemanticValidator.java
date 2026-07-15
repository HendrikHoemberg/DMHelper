package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CampaignManifestV2SemanticValidator {

    private final CampaignCatalogService catalog;

    public CampaignManifestV2SemanticValidator(CampaignCatalogService catalog) {
        this.catalog = catalog;
    }

    public List<CampaignImportProblem> validate(CampaignManifestV2 m) {
        List<CampaignImportProblem> problems = new ArrayList<>();
        Map<CampaignContentType, Set<String>> keys = new EnumMap<>(CampaignContentType.class);
        add(keys, CampaignContentType.CAMPAIGN, m.campaign().key(), "/campaign/key", problems);
        for (int i = 0; i < size(m.party()); i++) {
            var p = m.party().get(i);
            add(keys, CampaignContentType.PARTY_MEMBER, p.key(), "/party/" + i + "/key", problems);
            if (p.sheet() != null) {
                add(keys, CampaignContentType.CHARACTER_SHEET, p.sheet().key(), "/party/" + i + "/sheet/key", problems);
                for (int j = 0; j < size(p.sheet().resources()); j++) {
                    var r = p.sheet().resources().get(j);
                    add(keys, CampaignContentType.SHEET_RESOURCE, r.key(), "/party/" + i + "/sheet/resources/" + j + "/key", problems);
                    if (r.currentUses() > r.maxUses()) error(problems, "INVALID_RESOURCE_STATE",
                            "/party/" + i + "/sheet/resources/" + j, "Current uses exceed maximum uses");
                }
            }
        }
        for (int i = 0; i < size(m.customStatBlocks()); i++) add(keys, CampaignContentType.STATBLOCK,
                m.customStatBlocks().get(i).key(), "/customStatBlocks/" + i + "/key", problems);
        for (int i = 0; i < size(m.handouts()); i++) add(keys, CampaignContentType.HANDOUT,
                m.handouts().get(i).key(), "/handouts/" + i + "/key", problems);
        for (int i = 0; i < size(m.maps()); i++) {
            var map = m.maps().get(i);
            add(keys, CampaignContentType.MAP, map.key(), "/maps/" + i + "/key", problems);
            for (int j = 0; j < size(map.tokens()); j++) add(keys, CampaignContentType.TOKEN,
                    map.tokens().get(j).key(), "/maps/" + i + "/tokens/" + j + "/key", problems);
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var encounter = m.encounters().get(i);
            add(keys, CampaignContentType.ENCOUNTER, encounter.key(), "/encounters/" + i + "/key", problems);
            for (int j = 0; j < size(encounter.combatants()); j++) add(keys, CampaignContentType.COMBATANT,
                    encounter.combatants().get(j).key(), "/encounters/" + i + "/combatants/" + j + "/key", problems);
        }
        for (int i = 0; i < size(m.notes()); i++) add(keys, CampaignContentType.NOTE, m.notes().get(i).key(), "/notes/" + i + "/key", problems);
        for (int i = 0; i < size(m.quickNotes()); i++) add(keys, CampaignContentType.QUICK_NOTE, m.quickNotes().get(i).key(), "/quickNotes/" + i + "/key", problems);
        for (int i = 0; i < size(m.assignments()); i++) add(keys, CampaignContentType.ASSIGNMENT, m.assignments().get(i).key(), "/assignments/" + i + "/key", problems);
        for (int i = 0; i < size(m.ledgerEntries()); i++) add(keys, CampaignContentType.LEDGER_ENTRY, m.ledgerEntries().get(i).key(), "/ledgerEntries/" + i + "/key", problems);
        for (int i = 0; i < size(m.timelineEvents()); i++) add(keys, CampaignContentType.TIMELINE_EVENT, m.timelineEvents().get(i).key(), "/timelineEvents/" + i + "/key", problems);
        for (int ai = 0; ai < size(m.adventures()); ai++) {
            var adventure = m.adventures().get(ai);
            add(keys, CampaignContentType.ADVENTURE, adventure.key(), "/adventures/" + ai + "/key", problems);
            for (int ci = 0; ci < size(adventure.chapters()); ci++) {
                var chapter = adventure.chapters().get(ci);
                add(keys, CampaignContentType.CHAPTER, chapter.key(), "/adventures/" + ai + "/chapters/" + ci + "/key", problems);
                for (int si = 0; si < size(chapter.scenes()); si++) add(keys, CampaignContentType.SCENE,
                        chapter.scenes().get(si).key(), "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si + "/key", problems);
            }
        }

        validateReferences(m, keys, problems);
        validateSpatialAndState(m, problems);
        validateAssets(m, problems);
        return problems;
    }

    private void validateReferences(CampaignManifestV2 m, Map<CampaignContentType, Set<String>> keys,
                                    List<CampaignImportProblem> problems) {
        for (int i = 0; i < size(m.party()); i++) {
            var sheet = m.party().get(i).sheet();
            if (sheet == null) continue;
            check(sheet.speciesRef(), "/party/" + i + "/sheet/speciesRef", keys, problems);
            check(sheet.backgroundRef(), "/party/" + i + "/sheet/backgroundRef", keys, problems);
            for (int j = 0; j < size(sheet.featRefs()); j++) check(sheet.featRefs().get(j), "/party/" + i + "/sheet/featRefs/" + j, keys, problems);
            for (int j = 0; j < size(sheet.classLevels()); j++) check(sheet.classLevels().get(j).classRef(), "/party/" + i + "/sheet/classLevels/" + j + "/classRef", keys, problems);
            for (int j = 0; j < size(sheet.spells()); j++) {
                check(sheet.spells().get(j).spellRef(), "/party/" + i + "/sheet/spells/" + j + "/spellRef", keys, problems);
                check(sheet.spells().get(j).sourceClassRef(), "/party/" + i + "/sheet/spells/" + j + "/sourceClassRef", keys, problems);
            }
        }
        for (int i = 0; i < size(m.maps()); i++) for (int j = 0; j < size(m.maps().get(i).tokens()); j++) {
            var token = m.maps().get(i).tokens().get(j);
            check(token.statBlockRef(), "/maps/" + i + "/tokens/" + j + "/statBlockRef", keys, problems);
            check(token.partyMemberRef(), "/maps/" + i + "/tokens/" + j + "/partyMemberRef", keys, problems);
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var encounter = m.encounters().get(i);
            check(encounter.mapRef(), "/encounters/" + i + "/mapRef", keys, problems);
            for (int j = 0; j < size(encounter.combatants()); j++) {
                var c = encounter.combatants().get(j);
                check(c.tokenRef(), "/encounters/" + i + "/combatants/" + j + "/tokenRef", keys, problems);
                check(c.statBlockRef(), "/encounters/" + i + "/combatants/" + j + "/statBlockRef", keys, problems);
                check(c.partyMemberRef(), "/encounters/" + i + "/combatants/" + j + "/partyMemberRef", keys, problems);
            }
        }
        for (int i = 0; i < size(m.quickNotes()); i++) check(m.quickNotes().get(i).targetRef(), "/quickNotes/" + i + "/targetRef", keys, problems);
        for (int i = 0; i < size(m.assignments()); i++) {
            var a = m.assignments().get(i);
            check(a.holderRef(), "/assignments/" + i + "/holderRef", keys, problems);
            check(a.magicItemRef(), "/assignments/" + i + "/magicItemRef", keys, problems);
            check(a.equipmentItemRef(), "/assignments/" + i + "/equipmentItemRef", keys, problems);
        }
        for (int i = 0; i < size(m.ledgerEntries()); i++) check(m.ledgerEntries().get(i).itemAssignmentRef(), "/ledgerEntries/" + i + "/itemAssignmentRef", keys, problems);
        for (int i = 0; i < size(m.timelineEvents()); i++) check(m.timelineEvents().get(i).noteRef(), "/timelineEvents/" + i + "/noteRef", keys, problems);
        for (int ai = 0; ai < size(m.adventures()); ai++) for (int ci = 0; ci < size(m.adventures().get(ai).chapters()); ci++)
            for (int si = 0; si < size(m.adventures().get(ai).chapters().get(ci).scenes()); si++) {
                var scene = m.adventures().get(ai).chapters().get(ci).scenes().get(si);
                String path = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si;
                check(scene.mapRef(), path + "/mapRef", keys, problems);
                check(scene.encounterRef(), path + "/encounterRef", keys, problems);
                for (int j = 0; j < size(scene.statblockRefs()); j++) check(scene.statblockRefs().get(j), path + "/statblockRefs/" + j, keys, problems);
                for (int j = 0; j < size(scene.handoutRefs()); j++) check(scene.handoutRefs().get(j), path + "/handoutRefs/" + j, keys, problems);
            }
    }

    private void check(ContentReference ref, String path, Map<CampaignContentType, Set<String>> keys,
                       List<CampaignImportProblem> problems) {
        if (ref == null) return;
        if (ref.scope() == ContentReference.Scope.PACKAGE) {
            if (!keys.getOrDefault(ref.type(), Set.of()).contains(ref.key())) {
                error(problems, "UNRESOLVED_REFERENCE", path, "Package key does not resolve");
            }
        } else if (catalog.resolve(ref.type(), ref.ruleset(), ref.sourceKey()).isEmpty()) {
            error(problems, "UNRESOLVED_CATALOG_REFERENCE", path, "Catalog key does not resolve");
        }
    }

    private static void validateSpatialAndState(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        int activeEncounters = 0;
        for (int i = 0; i < size(m.maps()); i++) {
            var map = m.maps().get(i);
            int widthPx = map.grid().w() * map.grid().cellPx();
            int heightPx = map.grid().h() * map.grid().cellPx();
            if (map.document() != null && (map.document().grid().width() != map.grid().w()
                    || map.document().grid().height() != map.grid().h()
                    || map.document().grid().cellSizePx() != map.grid().cellPx())) {
                error(problems, "MAP_GRID_MISMATCH", "/maps/" + i + "/document/grid", "Map document grid differs from containing map");
            }
            for (int j = 0; j < size(map.tokens()); j++) {
                var token = map.tokens().get(j);
                if (token.positionX() < 0 || token.positionY() < 0
                        || token.positionX() + token.sizeCols() * map.grid().cellPx() > widthPx
                        || token.positionY() + token.sizeRows() * map.grid().cellPx() > heightPx) {
                    error(problems, "TOKEN_OUT_OF_BOUNDS", "/maps/" + i + "/tokens/" + j, "Token lies outside map pixel bounds");
                }
            }
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var e = m.encounters().get(i);
            if ("ACTIVE".equals(e.status())) activeEncounters++;
            if (e.activeTurnIndex() < -1 || e.activeTurnIndex() >= size(e.combatants())) {
                error(problems, "INVALID_ACTIVE_TURN", "/encounters/" + i + "/activeTurnIndex", "Active turn index is invalid");
            }
        }
        if (activeEncounters > 1) error(problems, "MULTIPLE_ACTIVE_ENCOUNTERS", "/encounters", "Only one encounter may be active");
    }

    private static void validateAssets(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < size(m.assets()); i++) {
            var asset = m.assets().get(i);
            if (!keys.add(asset.key())) error(problems, "DUPLICATE_KEY", "/assets/" + i + "/key", "Duplicate asset key");
            String path = CampaignPackageReader.normalizePath(asset.path());
            if (path == null || !(path.startsWith("assets/maps/") || path.startsWith("assets/handouts/")
                    || path.startsWith("assets/portraits/"))) {
                error(problems, "ASSET_PATH_INVALID", "/assets/" + i + "/path", "Asset path is outside supported directories");
            }
        }
        for (int i = 0; i < size(m.handouts()); i++) if (m.handouts().get(i).assetRef() != null
                && !keys.contains(m.handouts().get(i).assetRef())) error(problems, "UNRESOLVED_ASSET_REFERENCE",
                "/handouts/" + i + "/assetRef", "Handout asset does not resolve");
        for (int i = 0; i < size(m.maps()); i++) if (m.maps().get(i).document() != null)
            for (int j = 0; j < size(m.maps().get(i).document().layers()); j++) {
                var image = m.maps().get(i).document().layers().get(j).image();
                if (image != null && !keys.contains(image.assetRef())) error(problems, "UNRESOLVED_ASSET_REFERENCE",
                        "/maps/" + i + "/document/layers/" + j + "/image/assetRef", "Map image asset does not resolve");
            }
    }

    private static void add(Map<CampaignContentType, Set<String>> keys, CampaignContentType type, String key,
                            String path, List<CampaignImportProblem> problems) {
        if (!keys.computeIfAbsent(type, ignored -> new HashSet<>()).add(key)) {
            error(problems, "DUPLICATE_KEY", path, "Duplicate key within content type");
        }
    }

    private static void error(List<CampaignImportProblem> problems, String code, String path, String message) {
        problems.add(new CampaignImportProblem(ImportSeverity.ERROR, code, path, message, null));
    }

    private static int size(List<?> values) { return values == null ? 0 : values.size(); }
}
