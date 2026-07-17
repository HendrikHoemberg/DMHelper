package dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.PackageKeyGenerator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignFormatMigration;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegacyV1ToV2Migration implements CampaignFormatMigration {

    @FunctionalInterface
    public interface PackageKeyResolver {
        String resolve(CampaignContentType type, String pointer, String displayName, String generatedKey);
    }

    private static final Logger log = LoggerFactory.getLogger(LegacyV1ToV2Migration.class);

    private final CampaignCatalogService catalogService;

    public LegacyV1ToV2Migration(CampaignCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override public int sourceVersion() { return 1; }

    @Override
    public CampaignPackageValidationResult migrate(StagedCampaignPackage source, CampaignImportValidator validator) {
        try {
            String json = Files.readString(source.manifestPath(), StandardCharsets.UTF_8);
            var validated = validator.validate(json);
            if (!validated.valid()) {
                return new CampaignPackageValidationResult(source, null, 1, Map.of(), validated.problems(), List.of());
            }
            return convert(source, validated.requireImportable(), json);
        } catch (Exception e) {
            log.warn("Legacy campaign migration failed", e);
            return new CampaignPackageValidationResult(source, null, 1, Map.of(),
                    List.of(problem(ImportSeverity.ERROR, "MIGRATION_ERROR", "", "Legacy package migration failed")),
                    List.of());
        }
    }

    public CampaignPackageValidationResult convert(StagedCampaignPackage source, CampaignExportDto v1,
                                                     String stableSource) throws IOException {
        return convert(source, v1, stableSource, (type, pointer, displayName, generatedKey) -> generatedKey);
    }

    public CampaignPackageValidationResult convert(StagedCampaignPackage source, CampaignExportDto v1,
                                                     String stableSource, PackageKeyResolver keyResolver) throws IOException {
        var catalog = catalogService.snapshot();
        List<CampaignImportProblem> warnings = new ArrayList<>();
        Map<String, Path> assetsByKey = new LinkedHashMap<>();
        List<AssetDescriptor> assets = new ArrayList<>();

        String sourceHash = sha256(stableSource.getBytes(StandardCharsets.UTF_8));
        var metadata = new CampaignManifestV2.Metadata("migrated-" + sourceHash.substring(0, 12), Instant.EPOCH,
                "DMHelper/0.0.1-SNAPSHOT", catalog.version(), catalog.sha256(), List.of());
        String campaignKey = key(keyResolver, CampaignContentType.CAMPAIGN, "/campaign", v1.campaign().name(), sourceHash);
        var defaultSettings = new CampaignManifestV2.CampaignSettingsDto(
                CampaignManifestV2.LevelingMode.XP, null, null);
        var campaign = new CampaignManifestV2.CampaignDto(campaignKey, v1.campaign().name(), v1.campaign().description(),
                Instant.EPOCH, defaultSettings, null);
        warning(warnings, "LEGACY_STATE_DEFAULTED", "/campaign/settings", "Campaign settings defaulted to XP leveling");
        warning(warnings, "LEGACY_STATE_DEFAULTED", "/campaign/createdAt", "Campaign createdAt defaulted to epoch");
        warning(warnings, "LEGACY_STATE_DEFAULTED", "/campaign/currentSceneRef", "Current scene ref defaulted to null");

        Map<String, String> partyKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.PartyMemberDto> party = new ArrayList<>();
        for (int i = 0; i < size(v1.party()); i++) {
            var value = v1.party().get(i);
            String memberKey = key(keyResolver, CampaignContentType.PARTY_MEMBER, "/party/" + i,
                    value.characterName(), "/party/" + i);
            partyKeys.put(value.characterName(), memberKey);
            CampaignManifestV2.SheetDto sheet = null;
            if (value.sheet() != null) {
                var s = value.sheet();
                String sheetKey = key(keyResolver, CampaignContentType.CHARACTER_SHEET, "/party/" + i + "/sheet",
                        value.characterName(), "/party/" + i + "/sheet");
                List<CampaignManifestV2.ClassLevelDto> classes = new ArrayList<>();
                for (int j = 0; j < size(s.classLevels()); j++) {
                    var c = s.classLevels().get(j);
                    classes.add(new CampaignManifestV2.ClassLevelDto(catalog(CampaignContentType.CLASS, c.classSourceKey()),
                            c.level(), list(c.hitDieRolls())));
                }
                List<CampaignManifestV2.ResourceDto> resources = new ArrayList<>();
                for (int j = 0; j < size(s.resources()); j++) {
                    var r = s.resources().get(j);
                    resources.add(new CampaignManifestV2.ResourceDto(
                            key(keyResolver, CampaignContentType.SHEET_RESOURCE,
                                    "/party/" + i + "/sheet/resources/" + j, r.name(),
                                    "/party/" + i + "/sheet/resources/" + j),
                            r.name(), r.maxUses(), r.currentUses(), r.resetRule()));
                }
                List<CampaignManifestV2.SpellRefDto> spells = list(s.spells()).stream()
                        .map(spell -> new CampaignManifestV2.SpellRefDto(catalog(CampaignContentType.SPELL, spell.spellKey()),
                                spell.prepared(), spell.sourceClass() == null ? null
                                : catalog(CampaignContentType.CLASS, spell.sourceClass()))).toList();
                sheet = new CampaignManifestV2.SheetDto(sheetKey, map(s.abilityScores()), classes, map(s.proficiencies()),
                        nullableCatalog(CampaignContentType.SPECIES, s.speciesKey()),
                        nullableCatalog(CampaignContentType.BACKGROUND, s.backgroundKey()),
                        list(s.featRefs()).stream().map(ref -> catalog(CampaignContentType.FEAT, ref)).toList(),
                        s.xp(), map(s.overrides()), s.hitDiceUsed(), resources, spells, map(s.spellSlotsUsed()),
                        List.of(), List.of());
            }
            int currentHp = value.maxHp();
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/party/" + i + "/currentHp",
                    "Party member currentHp defaulted to maxHp");
            party.add(new CampaignManifestV2.PartyMemberDto(memberKey, value.characterName(), value.playerName(),
                    value.classAndLevel(), value.ac(), value.maxHp(), currentHp, value.initiativeBonus(), value.speed(),
                    value.passivePerception(), value.passiveInsight(), value.passiveInvestigation(), value.notes(),
                    value.active(), sheet, null, null, null, null, null, null, null));
        }

        Map<String, String> statKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.StatBlockDto> statBlocks = new ArrayList<>();
        for (int i = 0; i < size(v1.statBlocks()); i++) {
            var s = v1.statBlocks().get(i);
            String statKey = key(keyResolver, CampaignContentType.STATBLOCK, "/statBlocks/" + i, s.name(),
                    s.sourceKey() != null ? s.sourceKey() : "/statBlocks/" + i);
            if (s.sourceKey() != null) statKeys.put(s.sourceKey(), statKey);
            statKeys.put(s.name(), statKey);
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/statBlocks/" + i + "/createdAt",
                    "StatBlock createdAt defaulted to epoch");
            statBlocks.add(new CampaignManifestV2.StatBlockDto(statKey, s.sourceKey(), s.name(), s.cr(), s.type(),
                    s.size(), s.alignment(), s.ac(), s.hp(), s.speed(), s.strScore(), s.dexScore(), s.conScore(),
                    s.intScore(), s.wisScore(), s.chaScore(), s.strSave(), s.dexSave(), s.conSave(), s.intSave(),
                    s.wisSave(), s.chaSave(), s.skills(), s.damageVulnerabilities(), s.damageResistances(),
                    s.damageImmunities(), s.conditionImmunities(), s.senses(), s.languages(), s.traits(), s.actions(),
                    s.bonusActions(), s.reactions(), s.legendaryActions(), s.legendaryDescription(), s.lairActions(), s.xp(),
                    Instant.EPOCH, null));
        }

        Map<String, String> handoutKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.HandoutDto> handouts = new ArrayList<>();
        for (int i = 0; i < size(v1.handouts()); i++) {
            var h = v1.handouts().get(i);
            String handoutKey = key(keyResolver, CampaignContentType.HANDOUT, "/handouts/" + i,
                    h.title(), "/handouts/" + i);
            handoutKeys.put(h.title(), handoutKey);
            String assetKey = null;
            if (h.imageData() != null && !h.imageData().isBlank()) {
                assetKey = addAsset(source, assets, assetsByKey, "handout-" + i,
                        "assets/handouts/handout-" + i + "." + extension(h.contentType()), h.contentType(),
                        h.fileName(), h.imageData());
            }
            handouts.add(new CampaignManifestV2.HandoutDto(handoutKey, h.title(), list(h.tags()), assetKey, h.contentType(),
                    true, false));
        }

        Map<String, String> mapKeys = new LinkedHashMap<>();
        Map<String, String> tokenKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.MapDto> maps = new ArrayList<>();
        for (int i = 0; i < size(v1.maps()); i++) {
            var m = v1.maps().get(i);
            String mapKey = key(keyResolver, CampaignContentType.MAP, "/maps/" + i, m.name(),
                    m.key() != null ? m.key() : "/maps/" + i);
            mapKeys.put(m.name(), mapKey);
            if (m.key() != null) mapKeys.put(m.key(), mapKey);
            List<CampaignManifestV2.MapDto.TokenDto> tokens = new ArrayList<>();
            for (int j = 0; j < size(m.tokens()); j++) {
                var t = m.tokens().get(j);
                String tokenKey = key(keyResolver, CampaignContentType.TOKEN, "/maps/" + i + "/tokens/" + j,
                        t.name(), t.id() != null ? t.id() : "/maps/" + i + "/tokens/" + j);
                if (t.id() != null) tokenKeys.put(t.id(), tokenKey);
                String kind = normalizeKind(t.kind());
                tokens.add(new CampaignManifestV2.MapDto.TokenDto(tokenKey, t.name(), kind, t.color(),
                        t.positionX(), t.positionY(), t.sizeCols(), t.sizeRows(), t.hidden(),
                        ref(CampaignContentType.STATBLOCK, statKeys, t.statBlockKey()),
                        ref(CampaignContentType.PARTY_MEMBER, partyKeys, t.partyMemberName()),
                        t.currentHp(), t.maxHp(), t.dead(), t.notes(), null));
            }
            if (!tokens.isEmpty()) {
                warning(warnings, "LEGACY_STATE_DEFAULTED", "/maps/" + i + "/tokens/kind",
                        "Token kinds normalized to runtime vocabulary");
            }
            List<CampaignManifestV2.MapDto.LayerDto> layers = new ArrayList<>();
            if (m.document() != null) {
                for (int j = 0; j < size(m.document().layers()); j++) {
                    MapLayerDto layer = m.document().layers().get(j);
                    CampaignManifestV2.MapDto.ImageDto image = null;
                    if (layer.image() != null) {
                        String imageType = mediaType(layer.image().dataUrl());
                        String imageName = "map-" + i + "-image-" + j + "." + extension(imageType);
                        String assetKey = addAsset(source, assets, assetsByKey, "map-" + i + "-image-" + j,
                                "assets/maps/" + imageName, imageType, imageName, layer.image().dataUrl());
                        image = new CampaignManifestV2.MapDto.ImageDto(assetKey, layer.image().x(), layer.image().y(),
                                layer.image().width(), layer.image().height());
                    }
                    layers.add(new CampaignManifestV2.MapDto.LayerDto(layer.id(), layer.name(), layer.type(), layer.visible(),
                            layer.locked(), list(layer.cells()), list(layer.shapes()), image));
                }
            }
            var document = m.document() == null ? null : new CampaignManifestV2.MapDto.MapDocumentV2(2,
                    m.document().grid(), layers, list(m.document().primitives()), list(m.document().customTerrain()));
            maps.add(new CampaignManifestV2.MapDto(mapKey, m.name(),
                    new CampaignManifestV2.MapDto.GridDto(m.grid().w(), m.grid().h(), m.grid().cellPx(), m.grid().gridType()),
                    m.movementMode(), m.showGrid(), document, tokens, i));
        }

        Map<String, String> encounterKeys = new LinkedHashMap<>();
        for (int i = 0; i < size(v1.encounters()); i++) {
            var e = v1.encounters().get(i);
            String value = key(keyResolver, CampaignContentType.ENCOUNTER, "/encounters/" + i, e.name(),
                    e.encounterKey() != null ? e.encounterKey() : "/encounters/" + i);
            encounterKeys.put(e.name(), value);
            if (e.encounterKey() != null) encounterKeys.put(e.encounterKey(), value);
        }
        List<CampaignManifestV2.EncounterDto> encounters = new ArrayList<>();
        for (int i = 0; i < size(v1.encounters()); i++) {
            var e = v1.encounters().get(i);
            List<CampaignManifestV2.CombatantDto> combatants = new ArrayList<>();
            for (int j = 0; j < size(e.combatants()); j++) {
                var c = e.combatants().get(j);
                String kind = normalizeKind(c.kind());
                combatants.add(new CampaignManifestV2.CombatantDto(
                        key(keyResolver, CampaignContentType.COMBATANT, "/encounters/" + i + "/combatants/" + j,
                                c.name(), "/encounters/" + i + "/combatants/" + j),
                        c.name(), c.initiative(), c.tieBreaker(), c.sortOrder(), c.maxHp(), c.currentHp(), c.tempHp(),
                        kind, c.groupId(), c.groupLeader(), ref(CampaignContentType.TOKEN, tokenKeys, c.tokenId()),
                        ref(CampaignContentType.STATBLOCK, statKeys, c.statBlockKey()),
                        ref(CampaignContentType.PARTY_MEMBER, partyKeys, c.partyMemberName()), c.defeated(), c.hidden(),
                        c.conditionsJson(), c.concentratingOn(), c.concentrationCheckPending(), c.legendaryActionsUsed(),
                        c.legendaryResistancesUsed(), c.legendaryActionsMax(), c.legendaryResistancesMax(),
                        c.rechargedAbilities(), c.notes()));
            }
            if (e.map() != null) warning(warnings, "LEGACY_REFERENCE_MIGRATED", "/encounters/" + i + "/map", e.map());
            if (!combatants.isEmpty()) {
                warning(warnings, "LEGACY_STATE_DEFAULTED", "/encounters/" + i + "/combatants/kind",
                        "Combatant kinds normalized to runtime vocabulary");
            }
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/encounters/" + i + "/combatLog",
                    "Combat log defaulted to empty");
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/encounters/" + i + "/lairActionTriggered",
                    "Lair action triggered defaulted to false");
            encounters.add(new CampaignManifestV2.EncounterDto(encounterKeys.get(e.name()), e.name(), combatants, e.status(),
                    e.round(), e.activeTurnIndex(), e.logSequence(), e.lairActionName(), e.lairActionDescription(),
                    ref(CampaignContentType.MAP, mapKeys, e.map()), false, List.of()));
        }

        Map<String, String> noteKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.NoteDto> notes = new ArrayList<>();
        for (int i = 0; i < size(v1.notes()); i++) {
            var n = v1.notes().get(i);
            String noteKey = key(keyResolver, CampaignContentType.NOTE, "/notes/" + i,
                    n.title(), "/notes/" + i);
            noteKeys.put(n.title(), noteKey);
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/notes/" + i + "/createdAt",
                    "Note createdAt defaulted to epoch");
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/notes/" + i + "/links",
                    "Note links defaulted to empty");
            notes.add(new CampaignManifestV2.NoteDto(noteKey, n.type(), n.title(), n.body(), n.tags(), n.dmOnly(),
                    Instant.EPOCH, List.of()));
        }

        Map<String, String> assignmentKeys = new LinkedHashMap<>();
        List<CampaignManifestV2.AssignmentDto> assignments = new ArrayList<>();
        for (int i = 0; i < size(v1.assignments()); i++) {
            var a = v1.assignments().get(i);
            String assignmentKey = key(keyResolver, CampaignContentType.ASSIGNMENT, "/assignments/" + i,
                    a.customText(), a.id().toString());
            assignmentKeys.put(a.id().toString(), assignmentKey);
            if (a.holderName() != null) warning(warnings, "LEGACY_REFERENCE_MIGRATED", "/assignments/" + i + "/holderName", a.holderName());
            assignments.add(new CampaignManifestV2.AssignmentDto(assignmentKey,
                    ref(CampaignContentType.PARTY_MEMBER, partyKeys, a.holderName()),
                    nullableCatalog(CampaignContentType.MAGIC_ITEM, a.magicItemKey()),
                    nullableCatalog(CampaignContentType.EQUIPMENT_ITEM, a.equipmentItemKey()),
                    a.customText(), a.quantity(), a.attuned(), "CARRIED"));
        }

        List<CampaignManifestV2.LedgerEntryDto> ledger = new ArrayList<>();
        for (int i = 0; i < size(v1.ledger()); i++) {
            var l = v1.ledger().get(i);
            ledger.add(new CampaignManifestV2.LedgerEntryDto(key(keyResolver, CampaignContentType.LEDGER_ENTRY,
                    "/ledger/" + i, l.note(), l.id().toString()),
                    l.timestamp(), l.inGameYear(), l.inGameMonth(), l.inGameDay(), l.kind(), l.direction(), l.amount(),
                    l.currency(), l.holder(), l.note(), ref(CampaignContentType.ASSIGNMENT, assignmentKeys, l.itemAssignmentRef())));
        }

        List<CampaignManifestV2.TimelineEventDto> timeline = new ArrayList<>();
        for (int i = 0; i < size(v1.timeline()); i++) {
            var t = v1.timeline().get(i);
            if (t.noteTitle() != null) warning(warnings, "LEGACY_REFERENCE_MIGRATED", "/timeline/" + i + "/noteTitle", t.noteTitle());
            timeline.add(new CampaignManifestV2.TimelineEventDto(key(keyResolver, CampaignContentType.TIMELINE_EVENT,
                    "/timeline/" + i, t.title(), t.id().toString()),
                    t.inGameYear(), t.inGameMonth(), t.inGameDay(), t.title(), t.body(),
                    ref(CampaignContentType.NOTE, noteKeys, t.noteTitle())));
        }

        Map<String, String> sceneKeys = new LinkedHashMap<>();
        for (int ai = 0; ai < size(v1.adventures()); ai++) {
            var a = v1.adventures().get(ai);
            for (int ci = 0; ci < size(a.chapters()); ci++) {
                var c = a.chapters().get(ci);
                for (int si = 0; si < size(c.scenes()); si++) {
                    var s = c.scenes().get(si);
                    String pointer = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si;
                    String sceneKey = key(keyResolver, CampaignContentType.SCENE, pointer, s.title(),
                            s.sceneKey() != null ? s.sceneKey() : pointer);
                    sceneKeys.put(s.sceneKey(), sceneKey);
                    sceneKeys.put(a.name() + "/" + c.title() + "/" + s.sceneKey(), sceneKey);
                }
            }
        }
        List<CampaignManifestV2.AdventureDto> adventures = new ArrayList<>();
        for (int ai = 0; ai < size(v1.adventures()); ai++) {
            var a = v1.adventures().get(ai);
            List<CampaignManifestV2.ChapterDto> chapters = new ArrayList<>();
            for (int ci = 0; ci < size(a.chapters()); ci++) {
                var c = a.chapters().get(ci);
                List<CampaignManifestV2.SceneDto> scenes = new ArrayList<>();
                for (int si = 0; si < size(c.scenes()); si++) {
                    var s = c.scenes().get(si);
                    String path = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si;
                    if (s.map() != null) warning(warnings, "LEGACY_REFERENCE_MIGRATED", path + "/map", s.map());
                    if (s.encounter() != null) warning(warnings, "LEGACY_REFERENCE_MIGRATED", path + "/encounter", s.encounter());
                    scenes.add(new CampaignManifestV2.SceneDto(sceneKeys.get(s.sceneKey()), s.title(), s.body(), s.status(),
                            s.sortOrder(), ref(CampaignContentType.MAP, mapKeys, s.map()), s.pin(),
                            ref(CampaignContentType.ENCOUNTER, encounterKeys, s.encounter()),
                            list(s.statblocks()).stream().map(value -> ref(CampaignContentType.STATBLOCK, statKeys, value)).toList(),
                            list(s.handouts()).stream().map(value -> ref(CampaignContentType.HANDOUT, handoutKeys, value)).toList(),
                            null, null, null, null, null, null, null, null, null));
                }
                chapters.add(new CampaignManifestV2.ChapterDto(
                        key(keyResolver, CampaignContentType.CHAPTER,
                                "/adventures/" + ai + "/chapters/" + ci, c.title(),
                                "/adventures/" + ai + "/chapters/" + ci),
                        c.title(), c.intro(), c.sortOrder(), scenes));
            }
            warning(warnings, "LEGACY_STATE_DEFAULTED", "/adventures/" + ai + "/createdAt",
                    "Adventure createdAt defaulted to epoch");
            adventures.add(new CampaignManifestV2.AdventureDto(
                    key(keyResolver, CampaignContentType.ADVENTURE, "/adventures/" + ai,
                            a.name(), "/adventures/" + ai), a.name(), a.description(),
                    a.sourceAttribution(), a.sortOrder(), chapters, Instant.EPOCH));
        }

        List<CampaignManifestV2.QuickNoteDto> quickNotes = new ArrayList<>();
        for (int i = 0; i < size(v1.quicknotes()); i++) {
            var q = v1.quicknotes().get(i);
            CampaignContentType type = CampaignContentType.valueOf(q.targetType());
            Map<String, String> index = switch (type) {
                case CAMPAIGN -> Map.of("CAMPAIGN", campaignKey);
                case MAP -> mapKeys;
                case PARTY_MEMBER -> partyKeys;
                case STATBLOCK -> statKeys;
                case NOTE -> noteKeys;
                case HANDOUT -> handoutKeys;
                case ENCOUNTER -> encounterKeys;
                case SCENE -> sceneKeys;
                default -> Map.of();
            };
            warning(warnings, "LEGACY_REFERENCE_MIGRATED", "/quicknotes/" + i + "/targetRef", q.targetRef());
            quickNotes.add(new CampaignManifestV2.QuickNoteDto(
                    key(keyResolver, CampaignContentType.QUICK_NOTE, "/quicknotes/" + i,
                            q.body(), "/quicknotes/" + i),
                    ref(type, index, q.targetRef()), q.body(), Instant.parse(q.createdAt())));
        }

        var manifest = new CampaignManifestV2(2, metadata, campaign, assets, party, statBlocks,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                handouts, maps,
                encounters, notes, quickNotes, assignments, ledger, timeline, adventures, null, List.of(), List.of(), List.of());
        return new CampaignPackageValidationResult(source, manifest, 1, assetsByKey, warnings, List.of("MIGRATED_FROM_V1"));
    }

    private static String normalizeKind(String kind) {
        if (kind == null) return "MONSTER";
        return switch (kind) {
            case "character", "player" -> "PC";
            case "creature" -> "MONSTER";
            case "hazard", "effect" -> "OBJECT";
            default -> kind;
        };
    }

    private static String addAsset(StagedCampaignPackage source, List<AssetDescriptor> descriptors,
                                   Map<String, Path> assets, String key, String path, String type,
                                   String originalName, String dataUrl) throws IOException {
        byte[] bytes = decodeDataUrl(dataUrl);
        Path destination = source.stagingDirectory().resolve(path).normalize();
        if (!destination.startsWith(source.stagingDirectory())) throw new IOException("Unsafe asset path");
        Files.createDirectories(destination.getParent());
        Files.write(destination, bytes);
        var descriptor = new AssetDescriptor(key, path, type, bytes.length, sha256(bytes), originalName);
        CampaignImportProblem signature = AssetSignatureValidator.validate(destination, descriptor);
        if (signature != null) throw new IOException(signature.message());
        descriptors.add(descriptor);
        assets.put(key, destination);
        return key;
    }

    private static ContentReference ref(CampaignContentType type, Map<String, String> index, String legacy) {
        if (legacy == null) return null;
        String key = index.get(legacy);
        if (key == null) throw new IllegalArgumentException("Unresolved validated legacy reference");
        return ContentReference.packageRef(type, key);
    }

    private static ContentReference catalog(CampaignContentType type, String sourceKey) {
        return ContentReference.catalogRef(type, CampaignCatalogService.RULESET, sourceKey);
    }

    private static ContentReference nullableCatalog(CampaignContentType type, String sourceKey) {
        return sourceKey == null ? null : catalog(type, sourceKey);
    }

    private static String key(PackageKeyResolver resolver, CampaignContentType type, String pointer,
                              String name, String identity) {
        String generated = PackageKeyGenerator.generate(type, name, identity);
        return resolver.resolve(type, pointer, name, generated);
    }

    private static void warning(List<CampaignImportProblem> warnings, String code, String path, String value) {
        warnings.add(problem(ImportSeverity.WARNING, code, path, value));
    }

    private static CampaignImportProblem problem(ImportSeverity severity, String code, String path, String message) {
        return new CampaignImportProblem(severity, code, path, message, null);
    }

    private static String mediaType(String dataUrl) {
        int separator = dataUrl == null ? -1 : dataUrl.indexOf(';');
        if (dataUrl == null || !dataUrl.startsWith("data:") || separator < 5) throw new IllegalArgumentException("Invalid data URL");
        return dataUrl.substring(5, separator);
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).endsWith(";base64")) throw new IllegalArgumentException("Invalid data URL");
        return Base64.getDecoder().decode(dataUrl.substring(comma + 1));
    }

    private static String extension(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported asset type");
        };
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static int size(List<?> values) { return values == null ? 0 : values.size(); }
    private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
    private static <K,V> Map<K,V> map(Map<K,V> values) { return values == null ? Map.of() : values; }
}
