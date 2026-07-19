package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;

import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.MAP;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.HANDOUT;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.NOTE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.PARTY_MEMBER;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SCENE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SESSION;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SESSION_SCENE_VISIT;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.QUEST;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.OBJECTIVE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SOURCE_ANNOTATION;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.SESSION_OBJECTIVE_CHANGE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.ENCOUNTER;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.ENCOUNTER_WAVE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.FACTION;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.WORLD_NPC;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.WORLD_LOCATION;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.WORLD_RELATIONSHIP;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.FACTION_CLOCK;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.ROLLABLE_TABLE;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.TRAP;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.HAZARD;
import static dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.AUDIO_CUE;

import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableEntryWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableReferenceWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableValidator;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableWrite;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCheckWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatValidator;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapDisarmMethodWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardWrite;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;

@Component
public class CampaignManifestV2SemanticValidator {

    private final CampaignCatalogService catalog;
    private final RollableTableValidator tableStructuralValidator = new RollableTableValidator();
    private final ThreatValidator threatStructuralValidator = new ThreatValidator();

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
            if (p.currentHp() < 0 || p.currentHp() > p.maxHp()) {
                error(problems, "INVALID_PARTY_CURRENT_HP", "/party/" + i + "/currentHp",
                        "Party member currentHp must be between 0 and maxHp");
            }
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
        for (int i = 0; i < size(m.customSpells()); i++) add(keys, CampaignContentType.SPELL,
                m.customSpells().get(i).key(), "/customSpells/" + i + "/key", problems);
        for (int i = 0; i < size(m.customConditions()); i++) add(keys, CampaignContentType.CONDITION,
                m.customConditions().get(i).key(), "/customConditions/" + i + "/key", problems);
        for (int i = 0; i < size(m.customRules()); i++) add(keys, CampaignContentType.RULE,
                m.customRules().get(i).key(), "/customRules/" + i + "/key", problems);
        for (int i = 0; i < size(m.customEquipment()); i++) add(keys, CampaignContentType.EQUIPMENT_ITEM,
                m.customEquipment().get(i).key(), "/customEquipment/" + i + "/key", problems);
        for (int i = 0; i < size(m.customMagicItems()); i++) add(keys, CampaignContentType.MAGIC_ITEM,
                m.customMagicItems().get(i).key(), "/customMagicItems/" + i + "/key", problems);
        for (int i = 0; i < size(m.customClasses()); i++) add(keys, CampaignContentType.CLASS,
                m.customClasses().get(i).key(), "/customClasses/" + i + "/key", problems);
        for (int i = 0; i < size(m.customSpecies()); i++) add(keys, CampaignContentType.SPECIES,
                m.customSpecies().get(i).key(), "/customSpecies/" + i + "/key", problems);
        for (int i = 0; i < size(m.customBackgrounds()); i++) add(keys, CampaignContentType.BACKGROUND,
                m.customBackgrounds().get(i).key(), "/customBackgrounds/" + i + "/key", problems);
        for (int i = 0; i < size(m.customFeats()); i++) add(keys, CampaignContentType.FEAT,
                m.customFeats().get(i).key(), "/customFeats/" + i + "/key", problems);
        for (int i = 0; i < size(m.handouts()); i++) add(keys, CampaignContentType.HANDOUT,
                m.handouts().get(i).key(), "/handouts/" + i + "/key", problems);
        for (int i = 0; i < size(m.maps()); i++) {
            var map = m.maps().get(i);
            add(keys, CampaignContentType.MAP, map.key(), "/maps/" + i + "/key", problems);
            for (int j = 0; j < size(map.tokens()); j++) {
                add(keys, CampaignContentType.TOKEN,
                        map.tokens().get(j).key(), "/maps/" + i + "/tokens/" + j + "/key", problems);
                validateTokenKind(map.tokens().get(j).kind(), "/maps/" + i + "/tokens/" + j + "/kind", problems);
            }
            if (map.document() != null) {
                var primitives = map.document().primitives();
                Set<String> regionKeys = new HashSet<>();
                for (int pi = 0; pi < size(primitives); pi++) {
                    var prim = primitives.get(pi);
                    if ("REGION".equals(prim.type()) && prim.key() != null) {
                        if (!regionKeys.add(prim.key())) {
                            error(problems, "DUPLICATE_REGION_KEY", "/maps/" + i + "/document/primitives/" + pi + "/key",
                                    "Duplicate region key within map");
                        }
                    }
                }
            }
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var encounter = m.encounters().get(i);
            add(keys, CampaignContentType.ENCOUNTER, encounter.key(), "/encounters/" + i + "/key", problems);
            Set<String> waveKeys = new HashSet<>();
            for (int j = 0; j < size(encounter.waves()); j++) {
                var w = encounter.waves().get(j);
                add(keys, ENCOUNTER_WAVE, w.key(), "/encounters/" + i + "/waves/" + j + "/key", problems);
                waveKeys.add(w.key());
            }
            for (int j = 0; j < size(encounter.combatants()); j++) {
                var c = encounter.combatants().get(j);
                add(keys, CampaignContentType.COMBATANT,
                        c.key(), "/encounters/" + i + "/combatants/" + j + "/key", problems);
                validateCombatantKind(c.kind(), "/encounters/" + i + "/combatants/" + j + "/kind", problems);
                if (c.waveKey() != null && !waveKeys.contains(c.waveKey())) {
                    warning(problems, "UNRESOLVED_WAVE_REFERENCE", "/encounters/" + i + "/combatants/" + j + "/waveKey",
                            "Combatant waveKey does not resolve to a wave in this encounter");
                }
                if (c.placementRegionKey() != null) {
                    boolean foundRegion = false;
                    if (encounter.mapRef() != null && encounter.mapRef().key() != null) {
                        for (int mi = 0; mi < size(m.maps()); mi++) {
                            var map = m.maps().get(mi);
                            if (map.key().equals(encounter.mapRef().key()) && map.document() != null) {
                                for (var prim : map.document().primitives()) {
                                    if ("REGION".equals(prim.type()) && c.placementRegionKey().equals(prim.key())) {
                                        foundRegion = true;
                                        break;
                                    }
                                }
                            }
                            if (foundRegion) break;
                        }
                    }
                    if (!foundRegion) {
                        warning(problems, "UNRESOLVED_PLACEMENT_REGION", "/encounters/" + i + "/combatants/" + j + "/placementRegionKey",
                                "placementRegionKey does not match any REGION primitive on the encounter's map");
                    }
                }
            }
            if (encounter.rewards() != null && encounter.rewards().questObjectiveRefs() != null) {
                for (int ri = 0; ri < encounter.rewards().questObjectiveRefs().size(); ri++) {
                    var ref = encounter.rewards().questObjectiveRefs().get(ri);
                    if (ref != null) {
                        check(ref, "/encounters/" + i + "/rewards/questObjectiveRefs/" + ri, keys, problems);
                    }
                }
            }
            for (int j = 0; j < size(encounter.combatLog()); j++) {
                var log = encounter.combatLog().get(j);
                add(keys, CampaignContentType.COMBAT_LOG_ENTRY, log.key(),
                        "/encounters/" + i + "/combatLog/" + j + "/key", problems);
                if (log.sequence() > encounter.logSequence()) {
                    error(problems, "COMBAT_LOG_SEQUENCE_EXCEEDS_ENCOUNTER",
                            "/encounters/" + i + "/combatLog/" + j + "/sequence",
                            "Log sequence exceeds encounter logSequence");
                }
                if (j > 0 && log.sequence() <= encounter.combatLog().get(j - 1).sequence()) {
                    error(problems, "COMBAT_LOG_SEQUENCE_NOT_STRICTLY_INCREASING",
                            "/encounters/" + i + "/combatLog/" + j + "/sequence",
                            "Log sequences must be strictly increasing");
                }
                if (log.combatantRef() != null) {
                    check(log.combatantRef(), "/encounters/" + i + "/combatLog/" + j + "/combatantRef", keys, problems);
                }
            }
        }
        for (int i = 0; i < size(m.notes()); i++) {
            var note = m.notes().get(i);
            add(keys, CampaignContentType.NOTE, note.key(), "/notes/" + i + "/key", problems);
            for (int j = 0; j < size(note.links()); j++) {
                var link = note.links().get(j);
                if (link.targetRef() != null) {
                    check(link.targetRef(), "/notes/" + i + "/links/" + j + "/targetRef", keys, problems);
                }
            }
        }
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
        if (m.session() != null) {
            uniqueKey(SESSION, m.session().key(), "/session/key", keys, problems);
            if (m.session().planNoteRef() != null)
                requireRefType(m.session().planNoteRef(), NOTE, "/session/planNoteRef", problems);
            if (m.session().workspaceMapRef() != null)
                requireRefType(m.session().workspaceMapRef(), MAP, "/session/workspaceMapRef", problems);
            requireAllRefType(m.session().attendeeRefs(), PARTY_MEMBER, "/session/attendeeRefs", problems);
            if ("CURTAIN".equals(m.session().presentationMode()) && m.session().presentedRef() != null)
                error(problems, "INVALID_SESSION_PRESENTATION", "/session/presentedRef",
                        "Curtain presentation cannot reference content.");
            if ("MAP".equals(m.session().presentationMode()))
                requireRefType(m.session().presentedRef(), MAP, "/session/presentedRef", problems);
            if ("HANDOUT".equals(m.session().presentationMode()))
                requireRefType(m.session().presentedRef(), HANDOUT, "/session/presentedRef", problems);
            if ("REVIEW".equals(m.session().status()) &&
                    (m.session().draftBody() == null || m.session().draftBody().isBlank()))
                error(problems, "MISSING_SESSION_DRAFT", "/session/draftBody",
                        "A session under review requires its persisted draft.");
            if (!"REVIEW".equals(m.session().status()) && m.session().draftBody() != null)
                error(problems, "UNEXPECTED_SESSION_DRAFT", "/session/draftBody",
                        "Only a session under review can carry a draft.");
            Set<ContentReference> attendeeRefs = new HashSet<>();
            for (int i = 0; i < m.session().attendeeRefs().size(); i++) {
                var ref = m.session().attendeeRefs().get(i);
                if (!attendeeRefs.add(ref))
                    error(problems, "DUPLICATE_SESSION_ATTENDEE", "/session/attendeeRefs/" + i,
                            "A party member can appear in session attendance only once.");
            }
            Instant previousVisit = null;
            Set<ContentReference> visitedScenes = new HashSet<>();
            for (int i = 0; i < m.session().sceneVisits().size(); i++) {
                var visit = m.session().sceneVisits().get(i);
                uniqueKey(SESSION_SCENE_VISIT, visit.key(), "/session/sceneVisits", keys, problems);
                requireRefType(visit.sceneRef(), SCENE, "/session/sceneVisits", problems);
                if (!visitedScenes.add(visit.sceneRef()))
                    error(problems, "DUPLICATE_SESSION_SCENE_VISIT", "/session/sceneVisits/" + i + "/sceneRef",
                            "A scene can be visited only once per session.");
                if (previousVisit != null && visit.visitedAt().isBefore(previousVisit))
                    error(problems, "SESSION_VISITS_NOT_MONOTONIC", "/session/sceneVisits/" + i + "/visitedAt",
                            "Session scene visits must be ordered by visitedAt.");
                if (visit.completedAt() != null && visit.completedAt().isBefore(visit.visitedAt()))
                    error(problems, "SESSION_VISIT_COMPLETES_BEFORE_VISIT", "/session/sceneVisits/" + i + "/completedAt",
                            "A scene visit cannot complete before it starts.");
                previousVisit = visit.visitedAt();
            }
        }
        for (int i = 0; i < size(m.diceRolls()); i++) {
            var roll = m.diceRolls().get(i);
            add(keys, CampaignContentType.DICE_ROLL, roll.key(), "/diceRolls/" + i + "/key", problems);
            if (roll.encounterRef() != null) {
                check(roll.encounterRef(), "/diceRolls/" + i + "/encounterRef", keys, problems);
            }
        }

        for (int qi = 0; qi < size(m.quests()); qi++) {
            var q = m.quests().get(qi);
            add(keys, QUEST, q.key(), "/quests/" + qi + "/key", problems);
            for (int oi = 0; oi < size(q.objectives()); oi++) {
                var o = q.objectives().get(oi);
                add(keys, OBJECTIVE, o.key(), "/quests/" + qi + "/objectives/" + oi + "/key", problems);
            }
        }

        for (int ai = 0; ai < size(m.annotations()); ai++) {
            add(keys, SOURCE_ANNOTATION, m.annotations().get(ai).key(), "/annotations/" + ai + "/key", problems);
        }

        if (m.session() != null && m.session().objectiveChanges() != null) {
            for (int i = 0; i < m.session().objectiveChanges().size(); i++) {
                add(keys, SESSION_OBJECTIVE_CHANGE, m.session().objectiveChanges().get(i).key(),
                        "/session/objectiveChanges/" + i + "/key", problems);
            }
        }

        for (int ai = 0; ai < size(m.adventures()); ai++) {
            for (int ci = 0; ci < size(m.adventures().get(ai).chapters()); ci++) {
                for (int si = 0; si < size(m.adventures().get(ai).chapters().get(ci).scenes()); si++) {
                    var scene = m.adventures().get(ai).chapters().get(ci).scenes().get(si);
                    if (scene.transitions() != null) {
                        for (int ti = 0; ti < scene.transitions().size(); ti++) {
                            add(keys, CampaignContentType.TRANSITION, scene.transitions().get(ti).key(),
                                    "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si + "/transitions/" + ti + "/key", problems);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < size(m.factions()); i++)
            add(keys, FACTION, m.factions().get(i).key(), "/factions/" + i + "/key", problems);
        for (int i = 0; i < size(m.worldLocations()); i++)
            add(keys, WORLD_LOCATION, m.worldLocations().get(i).key(), "/worldLocations/" + i + "/key", problems);
        for (int i = 0; i < size(m.worldNpcs()); i++)
            add(keys, WORLD_NPC, m.worldNpcs().get(i).key(), "/worldNpcs/" + i + "/key", problems);
        for (int i = 0; i < size(m.worldRelationships()); i++)
            add(keys, WORLD_RELATIONSHIP, m.worldRelationships().get(i).key(), "/worldRelationships/" + i + "/key", problems);
        for (int i = 0; i < size(m.factionClocks()); i++)
            add(keys, FACTION_CLOCK, m.factionClocks().get(i).key(), "/factionClocks/" + i + "/key", problems);
        for (int i = 0; i < size(m.rollableTables()); i++)
            add(keys, ROLLABLE_TABLE, m.rollableTables().get(i).key(), "/rollableTables/" + i + "/key", problems);
        for (int i = 0; i < size(m.traps()); i++)
            add(keys, TRAP, m.traps().get(i).key(), "/traps/" + i + "/key", problems);
        for (int i = 0; i < size(m.hazards()); i++)
            add(keys, HAZARD, m.hazards().get(i).key(), "/hazards/" + i + "/key", problems);
        for (int i = 0; i < size(m.audioCues()); i++)
            add(keys, AUDIO_CUE, m.audioCues().get(i).key(), "/audioCues/" + i + "/key", problems);

        validateAudioCues(m, problems);
        validateReferences(m, keys, problems);
        validateSpatialAndState(m, problems);
        validateAssets(m, problems);
        validateCalendar(m, problems);
        validateExclusionSemantics(m, problems);
        validateCurrentSceneRef(m, keys, problems);
        validateStructuredAdventureAndQuests(m, keys, problems);
        validateWorldEntities(m, keys, problems);
        validateTables(m, problems);
        validateThreats(m, keys, problems);
        return problems;
    }

    /**
     * Item-6 semantic rules: transition targets, link role/type pairs, GIVER contract,
     * unresolved check DCs without source annotations, and objective dependency graphs.
     */
    private void validateStructuredAdventureAndQuests(CampaignManifestV2 m,
                                                      Map<CampaignContentType, Set<String>> keys,
                                                      List<CampaignImportProblem> problems) {
        Set<String> annotatedCheckDcPaths = new HashSet<>();
        for (int ai = 0; ai < size(m.annotations()); ai++) {
            var ann = m.annotations().get(ai);
            if (ann.fieldPath() != null && ann.fieldPath().contains("/checks/")
                    && ann.fieldPath().endsWith("/dc")) {
                annotatedCheckDcPaths.add(ann.fieldPath());
            }
        }

        for (int ai = 0; ai < size(m.adventures()); ai++) {
            var adventure = m.adventures().get(ai);
            for (int ci = 0; ci < size(adventure.chapters()); ci++) {
                var chapter = adventure.chapters().get(ci);
                for (int si = 0; si < size(chapter.scenes()); si++) {
                    var scene = chapter.scenes().get(si);
                    String scenePath = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si;
                    validateSceneTransitions(scene, scenePath, problems);
                    validateSceneLinks(scene, scenePath, problems);
                    validateSceneChecks(scene, scenePath, annotatedCheckDcPaths, problems);
                }
            }
        }

        // Map objective key -> quest key for cross-quest checks
        Map<String, String> objectiveToQuest = new java.util.HashMap<>();
        for (int qi = 0; qi < size(m.quests()); qi++) {
            var q = m.quests().get(qi);
            for (int oi = 0; oi < size(q.objectives()); oi++) {
                objectiveToQuest.put(q.objectives().get(oi).key(), q.key());
            }
        }

        for (int qi = 0; qi < size(m.quests()); qi++) {
            var q = m.quests().get(qi);
            String qPath = "/quests/" + qi;
            validateQuestLinks(q, qPath, problems);
            validateQuestObjectiveDependencies(q, qPath, objectiveToQuest, problems);
        }
    }

    private static void validateSceneTransitions(CampaignManifestV2.SceneDto scene, String scenePath,
                                                 List<CampaignImportProblem> problems) {
        if (scene.transitions() == null) return;
        for (int ti = 0; ti < scene.transitions().size(); ti++) {
            var t = scene.transitions().get(ti);
            String path = scenePath + "/transitions/" + ti;
            if (t.targetSceneRef() != null) {
                requireRefType(t.targetSceneRef(), SCENE, path + "/targetSceneRef", problems);
            }
            String kind = t.kind();
            boolean hasTarget = t.targetSceneRef() != null;
            boolean hasExternal = t.externalDestination() != null && !t.externalDestination().isBlank();
            if ("CHOICE".equals(kind)) {
                if (!hasTarget) {
                    error(problems, "INVALID_TRANSITION_TARGET", path + "/targetSceneRef",
                            "CHOICE transitions require an in-campaign target scene");
                }
                if (hasExternal) {
                    error(problems, "INVALID_TRANSITION_TARGET", path + "/externalDestination",
                            "CHOICE transitions must not set externalDestination");
                }
            } else if ("ENTRANCE".equals(kind) || "EXIT".equals(kind)) {
                if (hasTarget == hasExternal) {
                    error(problems, "INVALID_TRANSITION_TARGET", path,
                            kind + " transitions require exactly one of targetSceneRef or externalDestination");
                }
            }
        }
    }

    private static void validateSceneLinks(CampaignManifestV2.SceneDto scene, String scenePath,
                                           List<CampaignImportProblem> problems) {
        if (scene.links() == null) return;
        for (int li = 0; li < scene.links().size(); li++) {
            var link = scene.links().get(li);
            String path = scenePath + "/links/" + li;
            if (link.targetRef() == null) continue;
            CampaignContentType expected = expectedSceneLinkType(link.role());
            if (expected != null) {
                requireRefType(link.targetRef(), expected, path + "/targetRef", problems);
            }
        }
    }

    private static CampaignContentType expectedSceneLinkType(String role) {
        if (role == null) return null;
        return switch (role) {
            case "HANDOUT" -> HANDOUT;
            case "RULE" -> CampaignContentType.RULE;
            case "QUEST" -> QUEST;
            case "TIMELINE_EVENT" -> CampaignContentType.TIMELINE_EVENT;
            case "RELATED_SCENE" -> SCENE;
            case "NPC", "LOCATION" -> NOTE;
            case "RANDOM_ENCOUNTERS" -> CampaignContentType.ROLLABLE_TABLE;
            default -> null; // REFERENCE: any type
        };
    }

    private static void validateSceneChecks(CampaignManifestV2.SceneDto scene, String scenePath,
                                            Set<String> annotatedCheckDcPaths,
                                            List<CampaignImportProblem> problems) {
        if (scene.checks() == null) return;
        for (int ci = 0; ci < scene.checks().size(); ci++) {
            var check = scene.checks().get(ci);
            if (check.dc() != null) continue;
            String dcPath = scenePath + "/checks/" + ci + "/dc";
            boolean hasAnnotation = annotatedCheckDcPaths.contains(dcPath);
            if (!hasAnnotation) {
                error(problems, "MISSING_SOURCE_ANNOTATION", dcPath,
                        "A check with no DC requires a source annotation for that field");
            }
        }
    }

    private static void validateQuestLinks(CampaignManifestV2.QuestDto q, String qPath,
                                           List<CampaignImportProblem> problems) {
        if (q.links() == null) return;
        int giverCount = 0;
        for (int li = 0; li < q.links().size(); li++) {
            var link = q.links().get(li);
            String path = qPath + "/links/" + li;
            if ("GIVER".equals(link.role())) {
                giverCount++;
                if (link.targetRef() == null) {
                    error(problems, "INVALID_GIVER", path + "/targetRef",
                            "GIVER link requires a target reference");
                } else {
                    CampaignContentType type = link.targetRef().type();
                    if (type != NOTE && type != CampaignContentType.STATBLOCK) {
                        error(problems, "INVALID_GIVER", path + "/targetRef",
                                "GIVER must target a NOTE or STATBLOCK");
                    }
                }
            } else if (link.targetRef() != null) {
                validateQuestLinkType(link, path + "/targetRef", problems);
            }
        }
        if (giverCount > 1) {
            error(problems, "MULTIPLE_GIVERS", qPath + "/links",
                    "A quest may have at most one GIVER link");
        }
    }

    private static void validateQuestLinkType(CampaignManifestV2.QuestLinkDto link, String path,
                                               List<CampaignImportProblem> problems) {
        if (link.targetRef() == null) return;
        String role = link.role();
        if (role == null) return;
        switch (role) {
            case "HANDOUT" -> requireRefType(link.targetRef(), HANDOUT, path, problems);
            case "RULE" -> requireRefType(link.targetRef(), CampaignContentType.RULE, path, problems);
            case "RELATED_SCENE" -> requireRefType(link.targetRef(), SCENE, path, problems);
            case "NPC" -> requireRefTypeOneOf(link.targetRef(), List.of(NOTE, WORLD_NPC), path, problems);
            case "LOCATION" -> requireRefTypeOneOf(link.targetRef(), List.of(NOTE, WORLD_LOCATION), path, problems);
            case "FACTION" -> requireRefTypeOneOf(link.targetRef(), List.of(NOTE, FACTION), path, problems);
            case "TIMELINE_EVENT" -> requireRefType(link.targetRef(), CampaignContentType.TIMELINE_EVENT, path, problems);
        }
    }

    private static void validateQuestObjectiveDependencies(CampaignManifestV2.QuestDto q, String qPath,
                                                           Map<String, String> objectiveToQuest,
                                                           List<CampaignImportProblem> problems) {
        if (q.objectives() == null) return;
        Set<String> questObjectiveKeys = new HashSet<>();
        for (var o : q.objectives()) {
            questObjectiveKeys.add(o.key());
        }

        // edge list for cycle detection: objective key -> set of prerequisite keys
        Map<String, Set<String>> edges = new java.util.HashMap<>();
        Set<String> edgePairs = new HashSet<>();

        for (int oi = 0; oi < q.objectives().size(); oi++) {
            var o = q.objectives().get(oi);
            String oPath = qPath + "/objectives/" + oi;
            List<ContentReference> prereqs = o.prerequisiteRefs();
            boolean hasPrereqs = prereqs != null && !prereqs.isEmpty();

            if (hasPrereqs && (o.completionMode() == null || o.completionMode().isBlank())) {
                error(problems, "INVALID_COMPLETION_MODE", oPath + "/completionMode",
                        "Objectives with prerequisites require a completionMode (ALL or ANY)");
            }
            if (!hasPrereqs) {
                continue;
            }

            Set<String> prereqKeys = edges.computeIfAbsent(o.key(), k -> new HashSet<>());
            for (int pi = 0; pi < prereqs.size(); pi++) {
                ContentReference ref = prereqs.get(pi);
                String pPath = oPath + "/prerequisiteRefs/" + pi;
                if (ref == null) continue;
                if (ref.type() != OBJECTIVE) {
                    error(problems, "INVALID_REFERENCE_TYPE", pPath,
                            "Objective prerequisites must reference type OBJECTIVE");
                    continue;
                }
                String prereqKey = ref.key();
                if (o.key().equals(prereqKey)) {
                    error(problems, "SELF_DEPENDENCY", pPath,
                            "An objective cannot depend on itself");
                    continue;
                }
                String pair = o.key() + "->" + prereqKey;
                if (!edgePairs.add(pair)) {
                    error(problems, "DUPLICATE_DEPENDENCY", pPath,
                            "Duplicate dependency edge");
                    continue;
                }
                String ownerQuest = objectiveToQuest.get(prereqKey);
                if (ownerQuest != null && !ownerQuest.equals(q.key())) {
                    error(problems, "CROSS_QUEST_DEPENDENCY", pPath,
                            "Objective dependencies must stay within the same quest");
                    continue;
                }
                if (ownerQuest == null && !questObjectiveKeys.contains(prereqKey)) {
                    // unresolved is already reported by validateReferences; skip graph edge
                    continue;
                }
                prereqKeys.add(prereqKey);
            }
        }

        // Cycle detection over this quest's graph (edges point to prerequisites)
        for (String start : edges.keySet()) {
            Set<String> visiting = new HashSet<>();
            Set<String> visited = new HashSet<>();
            if (hasCycle(start, edges, visiting, visited)) {
                error(problems, "DEPENDENCY_CYCLE", qPath + "/objectives",
                        "Objective dependency graph contains a cycle involving " + start);
                break;
            }
        }
    }

    /** DFS cycle detection where edges map node -> prerequisites (outgoing edges). */
    private static boolean hasCycle(String node, Map<String, Set<String>> edges,
                                    Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) return true;
        if (visited.contains(node)) return false;
        visiting.add(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            if (hasCycle(next, edges, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private void validateCurrentSceneRef(CampaignManifestV2 m, Map<CampaignContentType, Set<String>> keys,
                                         List<CampaignImportProblem> problems) {
        var ref = m.campaign().currentSceneRef();
        if (ref == null) return;
        if (ref.scope() != ContentReference.Scope.PACKAGE || ref.type() != CampaignContentType.SCENE) {
            error(problems, "INVALID_CURRENT_SCENE_REF", "/campaign/currentSceneRef",
                    "Current scene ref must be a package SCENE reference");
        }
        check(ref, "/campaign/currentSceneRef", keys, problems);
    }

    private void validateCalendar(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        var settings = m.campaign().settings();
        if (settings == null) return;
        var calendar = settings.calendar();
        if (calendar != null) {
            if (calendar.monthLengths() == null || calendar.monthLengths().isEmpty()) {
                error(problems, "CALENDAR_MONTH_LENGTHS_EMPTY", "/campaign/settings/calendar/monthLengths",
                        "Month lengths must be non-empty");
            } else {
                for (int i = 0; i < calendar.monthLengths().size(); i++) {
                    if (calendar.monthLengths().get(i) <= 0) {
                        error(problems, "CALENDAR_MONTH_LENGTH_NOT_POSITIVE",
                                "/campaign/settings/calendar/monthLengths/" + i,
                                "Each month length must be positive");
                    }
                }
            }
            if (calendar.monthNames() == null || calendar.monthNames().isEmpty()) {
                error(problems, "CALENDAR_MONTH_NAMES_EMPTY", "/campaign/settings/calendar/monthNames",
                        "Month names must be non-empty");
            }
            if (calendar.weekdayNames() == null || calendar.weekdayNames().isEmpty()) {
                error(problems, "CALENDAR_WEEKDAY_NAMES_EMPTY", "/campaign/settings/calendar/weekdayNames",
                        "Weekday names must be non-empty");
            }
            int numMonths = calendar.monthLengths() == null ? 0 : calendar.monthLengths().size();
            if (calendar.monthNames() != null && numMonths > 0 && calendar.monthNames().size() != numMonths) {
                error(problems, "CALENDAR_MONTH_ARRAYS_DIFFERENT_LENGTH",
                        "/campaign/settings/calendar", "monthLengths and monthNames must have same length");
            }
        }
        var currentDate = settings.currentDate();
        if (currentDate != null && calendar != null && calendar.monthLengths() != null
                && !calendar.monthLengths().isEmpty()) {
            int month = currentDate.month();
            int day = currentDate.day();
            if (month < 0 || month >= calendar.monthLengths().size()) {
                error(problems, "CALENDAR_CURRENT_DATE_MONTH_OUT_OF_RANGE",
                        "/campaign/settings/currentDate/month",
                        "Month index is outside the configured calendar range");
            } else if (day < 1 || day > calendar.monthLengths().get(month)) {
                error(problems, "CALENDAR_CURRENT_DATE_DAY_OUT_OF_RANGE",
                        "/campaign/settings/currentDate/day",
                        "Day is outside the configured month length");
            }
        }
        for (int i = 0; i < size(m.ledgerEntries()); i++) {
            var e = m.ledgerEntries().get(i);
            if (e.inGameMonth() != null && e.inGameDay() != null && calendar != null
                    && calendar.monthLengths() != null && !calendar.monthLengths().isEmpty()) {
                int calMonth = e.inGameMonth() - 1;
                if (calMonth >= 0 && calMonth < calendar.monthLengths().size()
                        && (e.inGameDay() < 1 || e.inGameDay() > calendar.monthLengths().get(calMonth))) {
                    error(problems, "LEDGER_DATE_OUT_OF_RANGE",
                            "/ledgerEntries/" + i, "Ledger entry date outside configured calendar range");
                }
            }
        }
        for (int i = 0; i < size(m.timelineEvents()); i++) {
            var e = m.timelineEvents().get(i);
            if (calendar != null && calendar.monthLengths() != null && !calendar.monthLengths().isEmpty()) {
                int calMonth = e.inGameMonth() - 1;
                if (calMonth >= 0 && calMonth < calendar.monthLengths().size()
                        && (e.inGameDay() < 1 || e.inGameDay() > calendar.monthLengths().get(calMonth))) {
                    error(problems, "TIMELINE_DATE_OUT_OF_RANGE",
                            "/timelineEvents/" + i, "Timeline event date outside configured calendar range");
                }
            }
        }
    }

    private void validateExclusionSemantics(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        var exclusions = m.metadata().exclusions();
        if (exclusions == null) return;
        boolean hasCombatLogExclusion = exclusions.contains(CampaignExportExclusion.COMBAT_LOG);
        boolean hasDiceHistoryExclusion = exclusions.contains(CampaignExportExclusion.DICE_HISTORY);

        if (hasCombatLogExclusion) {
            for (int i = 0; i < size(m.encounters()); i++) {
                if (size(m.encounters().get(i).combatLog()) > 0) {
                    error(problems, "COMBAT_LOG_EXCLUDED_BUT_NON_EMPTY",
                            "/encounters/" + i + "/combatLog",
                            "COMBAT_LOG exclusion is set but encounter has non-empty combat log");
                }
            }
        }

        if (hasDiceHistoryExclusion) {
            if (size(m.diceRolls()) > 0) {
                error(problems, "DICE_HISTORY_EXCLUDED_BUT_NON_EMPTY",
                        "/diceRolls",
                        "DICE_HISTORY exclusion is set but diceRolls is non-empty");
            }
        }
    }

    private void validateAudioCues(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        Set<String> cueKeys = new HashSet<>();
        for (int i = 0; i < size(m.audioCues()); i++) {
            var cue = m.audioCues().get(i);
            if (!cueKeys.add(cue.key())) {
                warning(problems, "DUPLICATE_AUDIO_CUE_KEY", "/audioCues/" + i + "/key",
                        "Duplicate audio cue key within the package: " + cue.key());
            }
            if (cue.providerReference() != null && !cue.providerReference().isBlank()) {
                String norm = cue.providerReference().strip().toLowerCase();
                for (int j = 0; j < i; j++) {
                    var other = m.audioCues().get(j);
                    if (other.providerReference() != null && !other.providerReference().isBlank()
                            && other.providerReference().strip().toLowerCase().equals(norm)) {
                        warning(problems, "DUPLICATE_NORMALIZED_PROVIDER_REF", "/audioCues/" + i + "/providerReference",
                                "Duplicate normalized provider reference");
                    }
                }
            }
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var enc = m.encounters().get(i);
            if (enc.victoryCueDurationSeconds() != null && enc.victoryCueRef() == null) {
                warning(problems, "VICTORY_DURATION_WITHOUT_CUE",
                        "/encounters/" + i + "/victoryCueDurationSeconds",
                        "Victory cue duration set but no victory cue reference");
            }
        }
    }

    private void validateReferences(CampaignManifestV2 m, Map<CampaignContentType, Set<String>> keys,
                                    List<CampaignImportProblem> problems) {
        if (m.session() != null) {
            check(m.session().planNoteRef(), "/session/planNoteRef", keys, problems);
            check(m.session().workspaceMapRef(), "/session/workspaceMapRef", keys, problems);
            check(m.session().presentedRef(), "/session/presentedRef", keys, problems);
            for (int i = 0; i < size(m.session().attendeeRefs()); i++)
                check(m.session().attendeeRefs().get(i), "/session/attendeeRefs/" + i, keys, problems);
            for (int i = 0; i < size(m.session().sceneVisits()); i++)
                check(m.session().sceneVisits().get(i).sceneRef(),
                        "/session/sceneVisits/" + i + "/sceneRef", keys, problems);
        }
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
                if (scene.checks() != null) for (int ci2 = 0; ci2 < scene.checks().size(); ci2++) {
                    check(scene.checks().get(ci2).ruleRef(), path + "/checks/" + ci2 + "/ruleRef", keys, problems);
                }
                if (scene.participants() != null) for (int pi = 0; pi < scene.participants().size(); pi++) {
                    check(scene.participants().get(pi).statblockRef(), path + "/participants/" + pi + "/statblockRef", keys, problems);
                    check(scene.participants().get(pi).noteRef(), path + "/participants/" + pi + "/noteRef", keys, problems);
                    check(scene.participants().get(pi).worldNpcRef(), path + "/participants/" + pi + "/worldNpcRef", keys, problems);
                }
                if (scene.transitions() != null) for (int ti = 0; ti < scene.transitions().size(); ti++) {
                    check(scene.transitions().get(ti).targetSceneRef(), path + "/transitions/" + ti + "/targetSceneRef", keys, problems);
                }
                if (scene.links() != null) for (int li = 0; li < scene.links().size(); li++) {
                    check(scene.links().get(li).targetRef(), path + "/links/" + li + "/targetRef", keys, problems);
                }
            }
        for (int qi = 0; qi < size(m.quests()); qi++) {
            var q = m.quests().get(qi);
            String qPath = "/quests/" + qi;
            if (q.objectives() != null) for (int oi = 0; oi < q.objectives().size(); oi++) {
                var o = q.objectives().get(oi);
                if (o.prerequisiteRefs() != null) for (int pi = 0; pi < o.prerequisiteRefs().size(); pi++) {
                    check(o.prerequisiteRefs().get(pi), qPath + "/objectives/" + oi + "/prerequisiteRefs/" + pi, keys, problems);
                }
            }
            if (q.links() != null) for (int li = 0; li < q.links().size(); li++) {
                check(q.links().get(li).targetRef(), qPath + "/links/" + li + "/targetRef", keys, problems);
            }
        }
        for (int ai = 0; ai < size(m.annotations()); ai++) {
            check(m.annotations().get(ai).ownerRef(), "/annotations/" + ai + "/ownerRef", keys, problems);
        }
        if (m.session() != null && m.session().objectiveChanges() != null) {
            for (int i = 0; i < m.session().objectiveChanges().size(); i++) {
                check(m.session().objectiveChanges().get(i).objectiveRef(),
                        "/session/objectiveChanges/" + i + "/objectiveRef", keys, problems);
            }
        }
        for (int i = 0; i < size(m.factions()); i++) {
            var dto = m.factions().get(i);
            check(dto.noteRef(), "/factions/" + i + "/noteRef", keys, problems);
        }
        for (int i = 0; i < size(m.worldLocations()); i++) {
            var dto = m.worldLocations().get(i);
            check(dto.parentLocationRef(), "/worldLocations/" + i + "/parentLocationRef", keys, problems);
            check(dto.mapRef(), "/worldLocations/" + i + "/mapRef", keys, problems);
            check(dto.noteRef(), "/worldLocations/" + i + "/noteRef", keys, problems);
            if (dto.occupantNpcRefs() != null) {
                for (int j = 0; j < dto.occupantNpcRefs().size(); j++) {
                    check(dto.occupantNpcRefs().get(j),
                            "/worldLocations/" + i + "/occupantNpcRefs/" + j, keys, problems);
                }
            }
            if (dto.encounterRefs() != null) {
                for (int j = 0; j < dto.encounterRefs().size(); j++) {
                    check(dto.encounterRefs().get(j),
                            "/worldLocations/" + i + "/encounterRefs/" + j, keys, problems);
                }
            }
            if (dto.travelLocationRefs() != null) {
                for (int j = 0; j < dto.travelLocationRefs().size(); j++) {
                    check(dto.travelLocationRefs().get(j),
                            "/worldLocations/" + i + "/travelLocationRefs/" + j, keys, problems);
                }
            }
        }
        for (int i = 0; i < size(m.worldNpcs()); i++) {
            var dto = m.worldNpcs().get(i);
            check(dto.factionRef(), "/worldNpcs/" + i + "/factionRef", keys, problems);
            check(dto.locationRef(), "/worldNpcs/" + i + "/locationRef", keys, problems);
            check(dto.noteRef(), "/worldNpcs/" + i + "/noteRef", keys, problems);
            check(dto.statblockRef(), "/worldNpcs/" + i + "/statblockRef", keys, problems);
        }
        for (int i = 0; i < size(m.worldRelationships()); i++) {
            var dto = m.worldRelationships().get(i);
            check(dto.fromRef(), "/worldRelationships/" + i + "/fromRef", keys, problems);
            check(dto.toRef(), "/worldRelationships/" + i + "/toRef", keys, problems);
        }
        for (int i = 0; i < size(m.factionClocks()); i++) {
            var dto = m.factionClocks().get(i);
            check(dto.factionRef(), "/factionClocks/" + i + "/factionRef", keys, problems);
            check(dto.objectiveRef(), "/factionClocks/" + i + "/objectiveRef", keys, problems);
            check(dto.sceneRef(), "/factionClocks/" + i + "/sceneRef", keys, problems);
        }

        // Audio cue reference cross-checks
        var campaignDto = m.campaign();
        if (campaignDto != null) {
            check(campaignDto.defaultCueRef(), "/campaign/defaultCueRef", keys, problems);
        }
        for (int ai = 0; ai < size(m.adventures()); ai++) {
            for (int ci = 0; ci < size(m.adventures().get(ai).chapters()); ci++) {
                for (int si = 0; si < size(m.adventures().get(ai).chapters().get(ci).scenes()); si++) {
                    var scene = m.adventures().get(ai).chapters().get(ci).scenes().get(si);
                    check(scene.sceneCueRef(),
                            "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si + "/sceneCueRef",
                            keys, problems);
                }
            }
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var enc = m.encounters().get(i);
            check(enc.combatCueRef(), "/encounters/" + i + "/combatCueRef", keys, problems);
            check(enc.victoryCueRef(), "/encounters/" + i + "/victoryCueRef", keys, problems);
        }
        for (int i = 0; i < size(m.worldLocations()); i++) {
            check(m.worldLocations().get(i).locationCueRef(),
                    "/worldLocations/" + i + "/locationCueRef", keys, problems);
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

    private static final Set<String> VALID_TOKEN_KINDS = Set.of("PC", "NPC", "MONSTER", "OBJECT");
    private static final Set<String> VALID_COMBATANT_KINDS =
            Set.of("PC", "NPC", "MONSTER", "OBJECT", "TRAP", "HAZARD");

    private static void validateTokenKind(String kind, String path, List<CampaignImportProblem> problems) {
        if (kind != null && !VALID_TOKEN_KINDS.contains(kind)) {
            error(problems, "INVALID_TOKEN_KIND", path, "Token kind must be one of PC, NPC, MONSTER, OBJECT");
        }
    }

    private static void validateCombatantKind(String kind, String path, List<CampaignImportProblem> problems) {
        if (kind != null && !VALID_COMBATANT_KINDS.contains(kind)) {
            error(problems, "INVALID_COMBATANT_KIND", path,
                    "Combatant kind must be one of PC, NPC, MONSTER, OBJECT, TRAP, HAZARD");
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

    private static void warning(List<CampaignImportProblem> problems, String code, String path, String message) {
        problems.add(new CampaignImportProblem(ImportSeverity.WARNING, code, path, message, null));
    }

    private static int size(List<?> values) { return values == null ? 0 : values.size(); }

    private static void uniqueKey(CampaignContentType type, String key, String path,
                                   Map<CampaignContentType, Set<String>> keys,
                                   List<CampaignImportProblem> problems) {
        if (!keys.computeIfAbsent(type, ignored -> new HashSet<>()).add(key)) {
            error(problems, "DUPLICATE_KEY", path, "Duplicate key within content type");
        }
    }

    private static void requireRefType(ContentReference ref, CampaignContentType expectedType,
                                       String path, List<CampaignImportProblem> problems) {
        if (ref == null) return;
        if (ref.type() != expectedType) {
            error(problems, "INVALID_REFERENCE_TYPE", path,
                    "Expected reference type " + expectedType + " but got " + ref.type());
        }
    }

    private static void requireAllRefType(List<ContentReference> refs, CampaignContentType expectedType,
                                          String path, List<CampaignImportProblem> problems) {
        if (refs == null) return;
        for (int i = 0; i < refs.size(); i++) {
            requireRefType(refs.get(i), expectedType, path + "/" + i, problems);
        }
    }

    private void validateWorldEntities(CampaignManifestV2 m,
                                        Map<CampaignContentType, Set<String>> keys,
                                        List<CampaignImportProblem> problems) {
        validateWorldReferenceTypes(m, problems);
        validateLocationParentCycles(m, problems);
        validateSelfRelationships(m, problems);
        validateClockRanges(m, problems);
    }

    private static void validateWorldReferenceTypes(CampaignManifestV2 m,
                                                     List<CampaignImportProblem> problems) {
        for (int i = 0; i < size(m.factions()); i++) {
            var dto = m.factions().get(i);
            if (dto.noteRef() != null) {
                requireRefType(dto.noteRef(), NOTE, "/factions/" + i + "/noteRef", problems);
            }
        }
        for (int i = 0; i < size(m.worldLocations()); i++) {
            var dto = m.worldLocations().get(i);
            if (dto.parentLocationRef() != null) {
                requireRefType(dto.parentLocationRef(), WORLD_LOCATION,
                        "/worldLocations/" + i + "/parentLocationRef", problems);
            }
            if (dto.mapRef() != null) {
                requireRefType(dto.mapRef(), MAP, "/worldLocations/" + i + "/mapRef", problems);
            }
            if (dto.noteRef() != null) {
                requireRefType(dto.noteRef(), NOTE, "/worldLocations/" + i + "/noteRef", problems);
            }
            if (dto.occupantNpcRefs() != null) {
                for (int j = 0; j < dto.occupantNpcRefs().size(); j++) {
                    requireRefType(dto.occupantNpcRefs().get(j), WORLD_NPC,
                            "/worldLocations/" + i + "/occupantNpcRefs/" + j, problems);
                }
            }
            if (dto.encounterRefs() != null) {
                for (int j = 0; j < dto.encounterRefs().size(); j++) {
                    requireRefType(dto.encounterRefs().get(j), ENCOUNTER,
                            "/worldLocations/" + i + "/encounterRefs/" + j, problems);
                }
            }
            if (dto.travelLocationRefs() != null) {
                for (int j = 0; j < dto.travelLocationRefs().size(); j++) {
                    requireRefType(dto.travelLocationRefs().get(j), WORLD_LOCATION,
                            "/worldLocations/" + i + "/travelLocationRefs/" + j, problems);
                }
            }
        }
        for (int i = 0; i < size(m.worldNpcs()); i++) {
            var dto = m.worldNpcs().get(i);
            if (dto.factionRef() != null) {
                requireWorldRefType(dto.factionRef(), FACTION,
                        "/worldNpcs/" + i + "/factionRef", problems);
            }
            if (dto.locationRef() != null) {
                requireWorldRefType(dto.locationRef(), WORLD_LOCATION,
                        "/worldNpcs/" + i + "/locationRef", problems);
            }
            if (dto.noteRef() != null) {
                requireWorldRefType(dto.noteRef(), NOTE,
                        "/worldNpcs/" + i + "/noteRef", problems);
            }
            if (dto.statblockRef() != null) {
                requireWorldRefType(dto.statblockRef(), CampaignContentType.STATBLOCK,
                        "/worldNpcs/" + i + "/statblockRef", problems);
            }
        }
        for (int i = 0; i < size(m.worldRelationships()); i++) {
            var dto = m.worldRelationships().get(i);
            if (dto.fromRef() != null) {
                requireRefTypeOneOf(dto.fromRef(),
                        List.of(WORLD_NPC, FACTION, WORLD_LOCATION),
                        "/worldRelationships/" + i + "/fromRef", problems);
            }
            if (dto.toRef() != null) {
                requireRefTypeOneOf(dto.toRef(),
                        List.of(WORLD_NPC, FACTION, WORLD_LOCATION),
                        "/worldRelationships/" + i + "/toRef", problems);
            }
        }
        for (int i = 0; i < size(m.factionClocks()); i++) {
            var dto = m.factionClocks().get(i);
            if (dto.factionRef() != null) {
                requireRefType(dto.factionRef(), FACTION,
                        "/factionClocks/" + i + "/factionRef", problems);
            }
            if (dto.objectiveRef() != null) {
                requireRefType(dto.objectiveRef(), OBJECTIVE,
                        "/factionClocks/" + i + "/objectiveRef", problems);
            }
            if (dto.sceneRef() != null) {
                requireRefType(dto.sceneRef(), SCENE,
                        "/factionClocks/" + i + "/sceneRef", problems);
            }
        }
    }

    private static void validateLocationParentCycles(CampaignManifestV2 m,
                                                      List<CampaignImportProblem> problems) {
        var locations = m.worldLocations();
        if (locations == null || locations.isEmpty()) return;
        Map<String, String> parentMap = new java.util.HashMap<>();
        for (var loc : locations) {
            if (loc.parentLocationRef() != null && loc.parentLocationRef().key() != null) {
                parentMap.put(loc.key(), loc.parentLocationRef().key());
            }
        }
        for (var loc : locations) {
            String start = loc.key();
            Set<String> visited = new HashSet<>();
            String current = start;
            boolean hasCycle = false;
            while (current != null && parentMap.containsKey(current)) {
                if (!visited.add(current)) {
                    hasCycle = true;
                    break;
                }
                current = parentMap.get(current);
                if (current != null && current.equals(start)) {
                    hasCycle = true;
                    break;
                }
            }
            if (hasCycle) {
                error(problems, "WORLD_LOCATION_CYCLE", "/worldLocations",
                        "Location parent chain contains a cycle involving " + start);
            }
        }
    }

    private static void validateSelfRelationships(CampaignManifestV2 m,
                                                   List<CampaignImportProblem> problems) {
        var relationships = m.worldRelationships();
        if (relationships == null) return;
        for (int i = 0; i < relationships.size(); i++) {
            var dto = relationships.get(i);
            if (dto.fromRef() != null && dto.toRef() != null
                    && dto.fromRef().key() != null && dto.fromRef().key().equals(dto.toRef().key())
                    && dto.fromRef().type() == dto.toRef().type()) {
                error(problems, "WORLD_RELATIONSHIP_SELF",
                        "/worldRelationships/" + i,
                        "Relationship references the same entity as both source and target");
            }
        }
    }

    private static void validateClockRanges(CampaignManifestV2 m,
                                             List<CampaignImportProblem> problems) {
        var clocks = m.factionClocks();
        if (clocks == null) return;
        for (int i = 0; i < clocks.size(); i++) {
            var dto = clocks.get(i);
            if (dto.segments() < 1) {
                error(problems, "FACTION_CLOCK_RANGE",
                        "/factionClocks/" + i + "/segments",
                        "Clock must have at least 1 segment");
            }
            if (dto.filled() > dto.segments()) {
                error(problems, "FACTION_CLOCK_RANGE",
                        "/factionClocks/" + i + "/filled",
                        "Clock filled segments cannot exceed total segments");
            }
        }
    }

    private static void requireWorldRefType(ContentReference ref, CampaignContentType expectedType,
                                             String path, List<CampaignImportProblem> problems) {
        if (ref == null) return;
        if (ref.type() != expectedType) {
            error(problems, "INVALID_WORLD_REFERENCE_TYPE", path,
                    "Expected reference type " + expectedType + " but got " + ref.type());
        }
    }

    private static void requireRefTypeOneOf(ContentReference ref, List<CampaignContentType> expectedTypes,
                                            String path, List<CampaignImportProblem> problems) {
        if (ref == null) return;
        if (!expectedTypes.contains(ref.type())) {
            error(problems, "INVALID_WORLD_REFERENCE_TYPE", path,
                    "Expected one of " + expectedTypes + " but got " + ref.type());
        }
    }

    private void validateTables(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        var tables = m.rollableTables();
        if (tables == null) return;
        for (int ti = 0; ti < tables.size(); ti++) {
            var table = tables.get(ti);
            String tablePath = "/rollableTables/" + ti;
            RollableTableWrite write = toRuntimeWrite(table);
            for (var problem : tableStructuralValidator.collectProblems(write, null)) {
                error(problems, problem.code(), tablePath + problem.path(), problem.message());
            }
        }
        validatePackageTableGraph(m, problems);
    }

    private static RollableTableWrite toRuntimeWrite(CampaignManifestV2.RollableTableDto table) {
        TableAddressMode mode = table.addressMode() == null ? null
                : TableAddressMode.valueOf(table.addressMode());
        TableCategory category = table.category() == null ? null
                : TableCategory.valueOf(table.category());
        List<RollableTableEntryWrite> entries = table.entries() == null ? null
                : table.entries().stream().map(entry -> new RollableTableEntryWrite(
                        entry.key(), entry.rangeStart(), entry.rangeEnd(), entry.weight(),
                        entry.resultText(), entry.quantityExpression(),
                        entry.references() == null ? null : entry.references().stream()
                                .map(CampaignManifestV2SemanticValidator::toRuntimeReference)
                                .toList())).toList();
        return new RollableTableWrite(table.sourceKey(), table.name(), table.description(), mode,
                table.rollExpression(), category, table.tags(), entries);
    }

    private static RollableTableReferenceWrite toRuntimeReference(ContentReference ref) {
        if (ref == null) return null;
        TableReferenceScope scope = ref.scope() == ContentReference.Scope.CATALOG
                ? TableReferenceScope.CATALOG : TableReferenceScope.ENTITY;
        return new RollableTableReferenceWrite(
                scope, ref.type(), null, ref.ruleset(), ref.sourceKey(), ref.key());
    }

    private void validatePackageTableGraph(CampaignManifestV2 manifest,
                                           List<CampaignImportProblem> problems) {
        Map<String, IndexedTable> byKey = new java.util.LinkedHashMap<>();
        for (int i = 0; i < size(manifest.rollableTables()); i++) {
            byKey.put(manifest.rollableTables().get(i).key(),
                    new IndexedTable(i, manifest.rollableTables().get(i)));
        }
        Set<String> reported = new HashSet<>();
        for (IndexedTable table : byKey.values()) {
            walkTableGraph(table, 0, new java.util.LinkedHashSet<>(), byKey, reported, problems);
        }
    }

    private void walkTableGraph(IndexedTable indexed, int depth, Set<String> visiting,
                                Map<String, IndexedTable> byKey, Set<String> reported,
                                List<CampaignImportProblem> problems) {
        visiting.add(indexed.table().key());
        for (int ei = 0; ei < size(indexed.table().entries()); ei++) {
            var entry = indexed.table().entries().get(ei);
            for (int ri = 0; ri < size(entry.references()); ri++) {
                ContentReference ref = entry.references().get(ri);
                if (ref == null || ref.type() != ROLLABLE_TABLE
                        || ref.scope() != ContentReference.Scope.PACKAGE) continue;
                IndexedTable target = byKey.get(ref.key());
                if (target == null) continue;
                String path = "/rollableTables/" + indexed.index() + "/entries/" + ei
                        + "/references/" + ri;
                if (visiting.contains(ref.key())) {
                    reportTableGraphProblem(reported, problems, "TABLE_REFERENCE_CYCLE", path,
                            "Table reference chain contains a cycle involving " + ref.key());
                } else if (depth >= 5) {
                    reportTableGraphProblem(reported, problems, "TABLE_REFERENCE_DEPTH_EXCEEDED", path,
                            "Table reference depth exceeds maximum (5)");
                } else {
                    walkTableGraph(target, depth + 1, visiting, byKey, reported, problems);
                }
            }
        }
        visiting.remove(indexed.table().key());
    }

    private void reportTableGraphProblem(Set<String> reported, List<CampaignImportProblem> problems,
                                         String code, String path, String message) {
        if (reported.add(code + ":" + path)) error(problems, code, path, message);
    }

    private record IndexedTable(int index, CampaignManifestV2.RollableTableDto table) {}

    private void validateThreats(CampaignManifestV2 m,
                                 Map<CampaignContentType, Set<String>> keys,
                                 List<CampaignImportProblem> problems) {
        for (int i = 0; i < size(m.traps()); i++) {
            var trap = m.traps().get(i);
            String path = "/traps/" + i;
            for (var problem : threatStructuralValidator.collectTrapProblems(toTrapWrite(trap), null)) {
                // Structural ref checks require target UUIDs; package refs are validated below.
                if ("UNRESOLVED_REFERENCE".equals(problem.code())
                        && problem.path() != null
                        && (problem.path().startsWith("/references")
                        || problem.path().startsWith("/statBlockId"))) {
                    continue;
                }
                error(problems, problem.code(), path + toPackageThreatPath(problem.path()), problem.message());
            }
            validateThreatPackageRefs(trap.conditionRefs(), path + "/conditionRefs",
                    CampaignContentType.CONDITION, keys, problems);
            validateThreatPackageRefs(trap.salvageItemRefs(), path + "/salvageItemRefs",
                    null, keys, problems);
            if (trap.statBlockRef() != null) {
                validatePackageOrCatalogRef(trap.statBlockRef(), path + "/statBlockRef",
                        CampaignContentType.STATBLOCK, keys, problems);
            }
        }
        for (int i = 0; i < size(m.hazards()); i++) {
            var hazard = m.hazards().get(i);
            String path = "/hazards/" + i;
            for (var problem : threatStructuralValidator.collectHazardProblems(toHazardWrite(hazard), null)) {
                if ("UNRESOLVED_REFERENCE".equals(problem.code())
                        && problem.path() != null
                        && problem.path().startsWith("/references")) {
                    continue;
                }
                error(problems, problem.code(), path + toPackageThreatPath(problem.path()), problem.message());
            }
            validateThreatPackageRefs(hazard.conditionRefs(), path + "/conditionRefs",
                    CampaignContentType.CONDITION, keys, problems);
            validateThreatPackageRefs(hazard.salvageItemRefs(), path + "/salvageItemRefs",
                    null, keys, problems);
        }
        validateThreatIntegrations(m, keys, problems);
    }

    private void validateThreatIntegrations(CampaignManifestV2 m,
                                            Map<CampaignContentType, Set<String>> keys,
                                            List<CampaignImportProblem> problems) {
        for (int ai = 0; ai < size(m.adventures()); ai++) {
            var adventure = m.adventures().get(ai);
            for (int ci = 0; ci < size(adventure.chapters()); ci++) {
                var chapter = adventure.chapters().get(ci);
                for (int si = 0; si < size(chapter.scenes()); si++) {
                    var scene = chapter.scenes().get(si);
                    for (int sec = 0; sec < size(scene.sections()); sec++) {
                        var section = scene.sections().get(sec);
                        if (section.threatRef() == null) continue;
                        String path = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si
                                + "/sections/" + sec + "/threatRef";
                        CampaignContentType expected = "TRAP".equals(section.kind()) ? TRAP
                                : "HAZARD".equals(section.kind()) ? HAZARD : null;
                        if (expected == null) {
                            error(problems, "INVALID_THREAT_REFERENCE_KIND", path,
                                    "Only TRAP/HAZARD scene sections may carry a threatRef");
                            continue;
                        }
                        if (section.threatRef().type() != expected) {
                            error(problems, "INVALID_THREAT_REFERENCE_KIND", path,
                                    "Scene section kind " + section.kind()
                                            + " requires threatRef type " + expected);
                        }
                        validatePackageOrCatalogRef(section.threatRef(), path, expected, keys, problems);
                    }
                }
            }
        }
        for (int ei = 0; ei < size(m.encounters()); ei++) {
            var encounter = m.encounters().get(ei);
            for (int ci = 0; ci < size(encounter.combatants()); ci++) {
                var combatant = encounter.combatants().get(ci);
                if (combatant.threatRef() == null) continue;
                String path = "/encounters/" + ei + "/combatants/" + ci + "/threatRef";
                CampaignContentType expected = "TRAP".equals(combatant.kind()) ? TRAP
                        : "HAZARD".equals(combatant.kind()) ? HAZARD : null;
                if (expected == null) {
                    error(problems, "INVALID_THREAT_REFERENCE_KIND", path,
                            "Only TRAP/HAZARD combatants may carry a threatRef");
                    continue;
                }
                if (combatant.threatRef().type() != expected) {
                    error(problems, "INVALID_THREAT_REFERENCE_KIND", path,
                            "Combatant kind " + combatant.kind()
                                    + " requires threatRef type " + expected);
                }
                validatePackageOrCatalogRef(combatant.threatRef(), path, expected, keys, problems);
            }
        }
        for (int mi = 0; mi < size(m.maps()); mi++) {
            var map = m.maps().get(mi);
            int maxX = map.grid() != null ? map.grid().w() * map.grid().cellPx() : Integer.MAX_VALUE;
            int maxY = map.grid() != null ? map.grid().h() * map.grid().cellPx() : Integer.MAX_VALUE;
            for (int pi = 0; pi < size(map.threatPins()); pi++) {
                var pin = map.threatPins().get(pi);
                String base = "/maps/" + mi + "/threatPins/" + pi;
                if (pin.x() < 0 || pin.x() >= maxX) {
                    error(problems, "THREAT_PIN_OUT_OF_BOUNDS", base + "/x",
                            "Threat pin x must be in [0, " + maxX + ")");
                }
                if (pin.y() < 0 || pin.y() >= maxY) {
                    error(problems, "THREAT_PIN_OUT_OF_BOUNDS", base + "/y",
                            "Threat pin y must be in [0, " + maxY + ")");
                }
                if (pin.threatRef() == null) {
                    error(problems, "UNRESOLVED_REFERENCE", base + "/threatRef",
                            "Threat pin requires a threatRef");
                    continue;
                }
                if (pin.threatRef().type() != TRAP && pin.threatRef().type() != HAZARD) {
                    error(problems, "INVALID_THREAT_REFERENCE_KIND", base + "/threatRef",
                            "Threat pin must reference TRAP or HAZARD");
                } else {
                    validatePackageOrCatalogRef(pin.threatRef(), base + "/threatRef",
                            pin.threatRef().type(), keys, problems);
                }
            }
        }
    }

    private void validateThreatPackageRefs(List<ContentReference> refs, String basePath,
                                           CampaignContentType expectedType,
                                           Map<CampaignContentType, Set<String>> keys,
                                           List<CampaignImportProblem> problems) {
        if (refs == null) return;
        for (int i = 0; i < refs.size(); i++) {
            ContentReference ref = refs.get(i);
            if (ref == null) continue;
            String path = basePath + "/" + i;
            if (expectedType != null && ref.type() != expectedType) {
                error(problems, "INVALID_THREAT_REFERENCE_ROLE_TYPE", path,
                        "Expected type " + expectedType + " but got " + ref.type());
                continue;
            }
            if (expectedType == null
                    && ref.type() != CampaignContentType.EQUIPMENT_ITEM
                    && ref.type() != CampaignContentType.MAGIC_ITEM) {
                error(problems, "INVALID_THREAT_REFERENCE_ROLE_TYPE", path,
                        "Salvage item refs must be EQUIPMENT_ITEM or MAGIC_ITEM");
                continue;
            }
            validatePackageOrCatalogRef(ref, path, ref.type(), keys, problems);
        }
    }

    private void validatePackageOrCatalogRef(ContentReference ref, String path,
                                             CampaignContentType expectedType,
                                             Map<CampaignContentType, Set<String>> keys,
                                             List<CampaignImportProblem> problems) {
        if (ref == null) return;
        if (ref.type() != expectedType) {
            error(problems, "INVALID_REFERENCE_TYPE", path,
                    "Expected type " + expectedType + " but got " + ref.type());
            return;
        }
        if (ref.scope() == ContentReference.Scope.CATALOG) {
            if (catalog.resolve(expectedType, ref.ruleset(), ref.sourceKey()).isEmpty()) {
                error(problems, "UNRESOLVED_REFERENCE", path,
                        "Catalog " + expectedType + " '" + ref.sourceKey() + "' not found");
            }
            return;
        }
        Set<String> packageKeys = keys.get(expectedType);
        if (packageKeys == null || !packageKeys.contains(ref.key())) {
            error(problems, "UNRESOLVED_REFERENCE", path,
                    "Package " + expectedType + " '" + ref.key() + "' not found");
        }
    }

    private static TrapWrite toTrapWrite(CampaignManifestV2.TrapDto dto) {
        ThreatSeverity severity = parseEnum(ThreatSeverity.class, dto.severity());
        ThreatResetMode resetMode = parseEnum(ThreatResetMode.class, dto.resetMode());
        List<TrapDisarmMethodWrite> methods = dto.disarmMethods() == null ? null
                : dto.disarmMethods().stream()
                .map(m -> new TrapDisarmMethodWrite(
                        m.key(), m.label(), m.ability(), m.skill(), m.tool(),
                        m.dc(), m.failureConsequence(), m.sortOrder()))
                .toList();
        List<DamageType> damageTypes = null;
        String damageExpression = null;
        if (dto.damage() != null) {
            damageExpression = dto.damage().expression();
            if (dto.damage().types() != null) {
                damageTypes = dto.damage().types().stream()
                        .map(t -> parseEnum(DamageType.class, t))
                        .filter(t -> t != null)
                        .toList();
            }
        }
        return new TrapWrite(
                dto.sourceKey(), dto.name(), dto.description(), severity,
                dto.minLevel(), dto.maxLevel(), dto.triggerDescription(), dto.triggerAreaHint(),
                dto.detectionPassiveThreshold(), toCheckWrite(dto.detectionCheck()),
                methods, dto.attackBonus(), toCheckWrite(dto.save()),
                damageExpression, damageTypes, dto.additionalEffect(),
                resetMode, dto.resetTiming(),
                null, // package-level statblock resolved separately
                dto.countermeasureNotes(),
                null);
    }

    private static HazardWrite toHazardWrite(CampaignManifestV2.HazardDto dto) {
        ThreatSeverity severity = parseEnum(ThreatSeverity.class, dto.severity());
        HazardExposureMode exposure = parseEnum(HazardExposureMode.class, dto.exposureMode());
        List<DamageType> damageTypes = null;
        String damageExpression = null;
        if (dto.damage() != null) {
            damageExpression = dto.damage().expression();
            if (dto.damage().types() != null) {
                damageTypes = dto.damage().types().stream()
                        .map(t -> parseEnum(DamageType.class, t))
                        .filter(t -> t != null)
                        .toList();
            }
        }
        return new HazardWrite(
                dto.sourceKey(), dto.name(), dto.description(), severity,
                dto.minLevel(), dto.maxLevel(), exposure,
                dto.exposureText(), dto.areaHint(), toCheckWrite(dto.check()),
                damageExpression, damageTypes,
                dto.escalationText(), dto.endingConditions(),
                null);
    }

    private static ThreatCheckWrite toCheckWrite(CampaignManifestV2.ThreatCheckDto dto) {
        if (dto == null) return null;
        return new ThreatCheckWrite(
                parseEnum(ThreatCheckMode.class, dto.mode()),
                dto.ability(), dto.skill(), dto.dc());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Map TrapWrite/HazardWrite problem paths onto package JSON pointer shapes
     * so agent tooling can target the manifest fields directly.
     */
    static String toPackageThreatPath(String writePath) {
        if (writePath == null || writePath.isEmpty()) {
            return "";
        }
        if (writePath.equals("/damageExpression") || writePath.startsWith("/damageExpression/")) {
            return "/damage/expression" + writePath.substring("/damageExpression".length());
        }
        if (writePath.equals("/damageTypes") || writePath.startsWith("/damageTypes/")) {
            return "/damage/types" + writePath.substring("/damageTypes".length());
        }
        if (writePath.equals("/statBlockId") || writePath.startsWith("/statBlockId/")) {
            return "/statBlockRef" + writePath.substring("/statBlockId".length());
        }
        return writePath;
    }
}
