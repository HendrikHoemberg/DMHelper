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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

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
        }
        for (int i = 0; i < size(m.encounters()); i++) {
            var encounter = m.encounters().get(i);
            add(keys, CampaignContentType.ENCOUNTER, encounter.key(), "/encounters/" + i + "/key", problems);
            for (int j = 0; j < size(encounter.combatants()); j++) {
                add(keys, CampaignContentType.COMBATANT,
                        encounter.combatants().get(j).key(), "/encounters/" + i + "/combatants/" + j + "/key", problems);
                validateCombatantKind(encounter.combatants().get(j).kind(), "/encounters/" + i + "/combatants/" + j + "/kind", problems);
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

        validateReferences(m, keys, problems);
        validateSpatialAndState(m, problems);
        validateAssets(m, problems);
        validateCalendar(m, problems);
        validateExclusionSemantics(m, problems);
        validateCurrentSceneRef(m, keys, problems);
        validateStructuredAdventureAndQuests(m, keys, problems);
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
                CampaignContentType expected = expectedQuestLinkType(link.role());
                if (expected != null) {
                    requireRefType(link.targetRef(), expected, path + "/targetRef", problems);
                }
            }
        }
        if (giverCount > 1) {
            error(problems, "MULTIPLE_GIVERS", qPath + "/links",
                    "A quest may have at most one GIVER link");
        }
    }

    private static CampaignContentType expectedQuestLinkType(String role) {
        if (role == null) return null;
        return switch (role) {
            case "HANDOUT" -> HANDOUT;
            case "RULE" -> CampaignContentType.RULE;
            case "RELATED_SCENE" -> SCENE;
            case "NPC", "LOCATION", "FACTION" -> NOTE;
            case "TIMELINE_EVENT" -> CampaignContentType.TIMELINE_EVENT;
            default -> null; // REFERENCE, REWARD: flexible
        };
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

    private static final Set<String> VALID_KINDS = Set.of("PC", "NPC", "MONSTER", "OBJECT");

    private static void validateTokenKind(String kind, String path, List<CampaignImportProblem> problems) {
        if (kind != null && !VALID_KINDS.contains(kind)) {
            error(problems, "INVALID_TOKEN_KIND", path, "Token kind must be one of PC, NPC, MONSTER, OBJECT");
        }
    }

    private static void validateCombatantKind(String kind, String path, List<CampaignImportProblem> problems) {
        if (kind != null && !VALID_KINDS.contains(kind)) {
            error(problems, "INVALID_COMBATANT_KIND", path, "Combatant kind must be one of PC, NPC, MONSTER, OBJECT");
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
}
