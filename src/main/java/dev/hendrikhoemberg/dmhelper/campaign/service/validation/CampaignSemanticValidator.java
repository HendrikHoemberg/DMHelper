package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.AdventureExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.AssignmentExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.ChapterExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.CombatantExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.EncounterExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.HandoutExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.LedgerExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.MapExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.MapExportDto.TokenExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.NoteExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.PartyMemberExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.QuickNoteExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.SceneExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.StatBlockExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto.TimelineExportDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class CampaignSemanticValidator {

    public CampaignSemanticValidator() {
    }

    public List<CampaignImportProblem> validate(CampaignExportDto dto) {
        List<CampaignImportProblem> problems = new ArrayList<>();
        V1Index index = buildIndex(dto);

        validateUniqueness(dto, index, problems);
        validateReferences(dto, index, problems);
        validateBounds(dto, index, problems);
        validateGrid(dto, index, problems);
        validateEncounterState(dto, index, problems);

        return problems.stream()
                .sorted(Comparator.comparing(CampaignImportProblem::path)
                        .thenComparing(CampaignImportProblem::code))
                .toList();
    }

    private V1Index buildIndex(CampaignExportDto dto) {
        Map<String, MapExportDto> mapsByKey = new HashMap<>();
        Map<String, List<MapExportDto>> mapsByName = new HashMap<>();
        Map<String, EncounterExportDto> encountersByKey = new HashMap<>();
        Map<String, List<EncounterExportDto>> encountersByName = new HashMap<>();
        Map<String, StatBlockExportDto> statblocksByKey = new HashMap<>();
        Map<String, List<StatBlockExportDto>> statblocksByName = new HashMap<>();
        Map<String, List<PartyMemberExportDto>> partyByName = new HashMap<>();
        Map<String, List<HandoutExportDto>> handoutsByTitle = new HashMap<>();
        Map<String, List<NoteExportDto>> notesByTitle = new HashMap<>();
        Map<String, AssignmentExportDto> assignmentsById = new HashMap<>();
        Map<String, SceneExportDto> scenesByPath = new HashMap<>();
        Map<String, TokenExportDto> tokensById = new HashMap<>();

        for (int i = 0; dto.maps() != null && i < dto.maps().size(); i++) {
            MapExportDto m = dto.maps().get(i);
            if (m.key() != null) mapsByKey.put(m.key(), m);
            mapsByName.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(m);
            if (m.tokens() != null) {
                for (TokenExportDto t : m.tokens()) {
                    if (t.id() != null) tokensById.put(t.id(), t);
                }
            }
        }

        for (int i = 0; dto.encounters() != null && i < dto.encounters().size(); i++) {
            EncounterExportDto e = dto.encounters().get(i);
            if (e.encounterKey() != null) encountersByKey.put(e.encounterKey(), e);
            encountersByName.computeIfAbsent(e.name(), k -> new ArrayList<>()).add(e);
        }

        for (int i = 0; dto.statBlocks() != null && i < dto.statBlocks().size(); i++) {
            StatBlockExportDto s = dto.statBlocks().get(i);
            if (s.sourceKey() != null) statblocksByKey.put(s.sourceKey(), s);
            statblocksByName.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(s);
        }

        for (int i = 0; dto.party() != null && i < dto.party().size(); i++) {
            PartyMemberExportDto p = dto.party().get(i);
            if (p.characterName() != null) {
                partyByName.computeIfAbsent(p.characterName(), k -> new ArrayList<>()).add(p);
            }
        }

        for (int i = 0; dto.handouts() != null && i < dto.handouts().size(); i++) {
            HandoutExportDto h = dto.handouts().get(i);
            if (h.title() != null) {
                handoutsByTitle.computeIfAbsent(h.title(), k -> new ArrayList<>()).add(h);
            }
        }

        for (int i = 0; dto.notes() != null && i < dto.notes().size(); i++) {
            NoteExportDto n = dto.notes().get(i);
            if (n.title() != null) {
                notesByTitle.computeIfAbsent(n.title(), k -> new ArrayList<>()).add(n);
            }
        }

        for (int i = 0; dto.assignments() != null && i < dto.assignments().size(); i++) {
            AssignmentExportDto a = dto.assignments().get(i);
            if (a.id() != null) assignmentsById.put(a.id().toString(), a);
        }

        for (int ai = 0; dto.adventures() != null && ai < dto.adventures().size(); ai++) {
            AdventureExportDto a = dto.adventures().get(ai);
            if (a.chapters() != null) {
                for (int ci = 0; ci < a.chapters().size(); ci++) {
                    ChapterExportDto ch = a.chapters().get(ci);
                    if (ch.scenes() != null) {
                        for (int si = 0; si < ch.scenes().size(); si++) {
                            SceneExportDto sc = ch.scenes().get(si);
                            String scenePath = a.name() + "/" + ch.title() + "/" + sc.sceneKey();
                            scenesByPath.put(scenePath, sc);
                            if (sc.sceneKey() != null) {
                                scenesByPath.putIfAbsent(sc.sceneKey(), sc);
                            }
                        }
                    }
                }
            }
        }

        return new V1Index(
                Map.copyOf(mapsByKey), copyMapList(mapsByName),
                Map.copyOf(encountersByKey), copyMapList(encountersByName),
                Map.copyOf(statblocksByKey), copyMapList(statblocksByName),
                copyMapList(partyByName), copyMapList(handoutsByTitle),
                copyMapList(notesByTitle), Map.copyOf(assignmentsById),
                Map.copyOf(scenesByPath), Map.copyOf(tokensById)
        );
    }

    private static <T> Map<String, List<T>> copyMapList(Map<String, List<T>> source) {
        return source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    private void validateUniqueness(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        Set<String> seenKeys = new HashSet<>();
        if (dto.maps() != null) {
            for (int i = 0; i < dto.maps().size(); i++) {
                MapExportDto m = dto.maps().get(i);
                if (m.key() != null && !seenKeys.add(m.key())) {
                    problems.add(problem("DUPLICATE_REFERENCE", "/maps/" + i + "/key",
                            "Map key '" + m.key() + "' is used by multiple maps.",
                            "Use a unique key for each map."));
                }
            }
        }

        Set<String> seenEncounterKeys = new HashSet<>();
        if (dto.encounters() != null) {
            for (int i = 0; i < dto.encounters().size(); i++) {
                EncounterExportDto e = dto.encounters().get(i);
                if (e.encounterKey() != null && !seenEncounterKeys.add(e.encounterKey())) {
                    problems.add(problem("DUPLICATE_REFERENCE", "/encounters/" + i + "/encounterKey",
                            "Encounter key '" + e.encounterKey() + "' is used by multiple encounters.",
                            "Use a unique encounterKey for each encounter."));
                }
            }
        }

        Set<String> seenTokenIds = new HashSet<>();
        if (dto.maps() != null) {
            for (int mi = 0; mi < dto.maps().size(); mi++) {
                MapExportDto m = dto.maps().get(mi);
                if (m.tokens() != null) {
                    for (int ti = 0; ti < m.tokens().size(); ti++) {
                        TokenExportDto t = m.tokens().get(ti);
                        if (t.id() != null && !seenTokenIds.add(t.id())) {
                            problems.add(problem("DUPLICATE_REFERENCE", "/maps/" + mi + "/tokens/" + ti + "/id",
                                    "Token ID '" + t.id() + "' is used by multiple tokens.",
                                    "Use a unique ID for each token."));
                        }
                    }
                }
            }
        }

        Set<String> seenAdventureNames = new HashSet<>();
        if (dto.adventures() != null) {
            for (int ai = 0; ai < dto.adventures().size(); ai++) {
                AdventureExportDto a = dto.adventures().get(ai);
                if (a.name() != null && !seenAdventureNames.add(a.name())) {
                    problems.add(problem("DUPLICATE_REFERENCE", "/adventures/" + ai + "/name",
                            "Adventure name '" + a.name() + "' is used by multiple adventures.",
                            "Use a unique name for each adventure."));
                }
            }
        }
    }

    private void validateReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        validateSceneReferences(dto, index, problems);
        validateEncounterReferences(dto, index, problems);
        validateTokenReferences(dto, index, problems);
        validateAssignmentReferences(dto, index, problems);
        validateQuickNoteReferences(dto, index, problems);
        validateLedgerReferences(dto, index, problems);
        validateTimelineReferences(dto, index, problems);
    }

    private void validateSceneReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.adventures() == null) return;
        for (int ai = 0; ai < dto.adventures().size(); ai++) {
            AdventureExportDto a = dto.adventures().get(ai);
            if (a.chapters() == null) continue;
            for (int ci = 0; ci < a.chapters().size(); ci++) {
                ChapterExportDto ch = a.chapters().get(ci);
                if (ch.scenes() == null) continue;
                for (int si = 0; si < ch.scenes().size(); si++) {
                    SceneExportDto sc = ch.scenes().get(si);
                    String base = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si;

                    if (sc.map() != null) {
                        List<MapExportDto> nameMatches = index.mapsByName.get(sc.map());
                        if (nameMatches == null || nameMatches.isEmpty()) {
                            resolveUniqueName(base, "map", sc.map(), "map",
                                    index.mapsByName, MapExportDto::name, index.mapsByKey.keySet(),
                                    "map key", problems);
                        } else if (nameMatches.size() > 1) {
                            String keys = nameMatches.stream()
                                    .map(MapExportDto::key).collect(Collectors.joining(", "));
                            problems.add(problem("AMBIGUOUS_REFERENCE", base + "/map",
                                    "Map name '" + sc.map() + "' matches multiple maps (" + keys + ").",
                                    "Reference the map by its unique key instead of name."));
                        }
                    }

                    if (sc.encounter() != null) {
                        if (!index.encountersByKey.containsKey(sc.encounter())) {
                            String suggestion = index.encountersByKey.isEmpty() ?
                                    "Define the missing encounter." :
                                    "Use encounterKey '" + index.encountersByKey.keySet().iterator().next() +
                                            "' or define the missing encounter.";
                            problems.add(problem("UNRESOLVED_REFERENCE", base + "/encounter",
                                    "Encounter '" + sc.encounter() + "' does not exist in this campaign document.",
                                    suggestion));
                        }
                    }

                    if (sc.statblocks() != null) {
                        for (int sbi = 0; sbi < sc.statblocks().size(); sbi++) {
                            String sbKey = sc.statblocks().get(sbi);
                            if (!index.statblocksByKey.containsKey(sbKey)) {
                                problems.add(problem("UNRESOLVED_REFERENCE", base + "/statblocks/" + sbi,
                                        "StatBlock '" + sbKey + "' does not exist in this campaign document.",
                                        "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                            }
                        }
                    }

                    if (sc.handouts() != null) {
                        for (int hi = 0; hi < sc.handouts().size(); hi++) {
                            String hTitle = sc.handouts().get(hi);
                            if (!index.handoutsByTitle.containsKey(hTitle)) {
                                problems.add(problem("UNRESOLVED_REFERENCE", base + "/handouts/" + hi,
                                        "Handout '" + hTitle + "' does not exist in this campaign document.",
                                        "Use handout title 'Warning Plaque' or define the missing handout."));
                            }
                        }
                    }
                }
            }
        }
    }

    private void validateEncounterReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.encounters() == null) return;
        for (int ei = 0; ei < dto.encounters().size(); ei++) {
            EncounterExportDto enc = dto.encounters().get(ei);
            String base = "/encounters/" + ei;

            if (enc.map() != null) {
                List<MapExportDto> nameMatches = index.mapsByName.get(enc.map());
                if (nameMatches == null || nameMatches.isEmpty()) {
                    resolveUniqueName(base, "map", enc.map(), "map",
                            index.mapsByName, MapExportDto::name, index.mapsByKey.keySet(),
                            "map key", problems);
                } else if (nameMatches.size() > 1) {
                    String keys = nameMatches.stream()
                            .map(MapExportDto::key).collect(Collectors.joining(", "));
                    problems.add(problem("AMBIGUOUS_REFERENCE", base + "/map",
                            "Map name '" + enc.map() + "' matches multiple maps (" + keys + ").",
                            "Reference the map by its unique key instead of name."));
                }
            }

            if (enc.combatants() != null) {
                for (int ci = 0; ci < enc.combatants().size(); ci++) {
                    CombatantExportDto c = enc.combatants().get(ci);
                    String cbase = base + "/combatants/" + ci;

                    if (c.tokenId() != null && !index.tokensById.containsKey(c.tokenId())) {
                        String suggestion = index.tokensById.isEmpty() ?
                                "Define the missing token." :
                                "Use token ID '" + index.tokensById.keySet().iterator().next() +
                                        "' or define the missing token.";
                        problems.add(problem("UNRESOLVED_REFERENCE", cbase + "/tokenId",
                                "Token '" + c.tokenId() + "' does not exist in this campaign document.",
                                suggestion));
                    }

                    if (c.statBlockKey() != null && !index.statblocksByKey.containsKey(c.statBlockKey())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", cbase + "/statBlockKey",
                                "StatBlock '" + c.statBlockKey() + "' does not exist in this campaign document.",
                                "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                    }

                    if (c.partyMemberName() != null && !index.partyByName.containsKey(c.partyMemberName())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", cbase + "/partyMemberName",
                                "Party member '" + c.partyMemberName() + "' does not exist in this campaign document.",
                                "Use character name 'Aria' or define the missing party member."));
                    }
                }
            }
        }
    }

    private void validateTokenReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.maps() == null) return;
        for (int mi = 0; mi < dto.maps().size(); mi++) {
            MapExportDto m = dto.maps().get(mi);
            if (m.tokens() == null) continue;
            for (int ti = 0; ti < m.tokens().size(); ti++) {
                TokenExportDto t = m.tokens().get(ti);
                String base = "/maps/" + mi + "/tokens/" + ti;

                if (t.statBlockKey() != null && !index.statblocksByKey.containsKey(t.statBlockKey())) {
                    problems.add(problem("UNRESOLVED_REFERENCE", base + "/statBlockKey",
                            "StatBlock '" + t.statBlockKey() + "' does not exist in this campaign document.",
                            "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                }

                if (t.partyMemberName() != null && !index.partyByName.containsKey(t.partyMemberName())) {
                    problems.add(problem("UNRESOLVED_REFERENCE", base + "/partyMemberName",
                            "Party member '" + t.partyMemberName() + "' does not exist in this campaign document.",
                            "Use character name 'Aria' or define the missing party member."));
                }
            }
        }
    }

    private void validateAssignmentReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.assignments() == null) return;
        for (int ai = 0; ai < dto.assignments().size(); ai++) {
            AssignmentExportDto a = dto.assignments().get(ai);
            String base = "/assignments/" + ai;

            if (a.holderName() != null && !index.partyByName.containsKey(a.holderName())) {
                problems.add(problem("UNRESOLVED_REFERENCE", base + "/holderName",
                        "Party member '" + a.holderName() + "' does not exist in this campaign document.",
                        "Use character name 'Aria' or define the missing party member."));
            }
        }
    }

    private void validateQuickNoteReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.quicknotes() == null) return;
        for (int qi = 0; qi < dto.quicknotes().size(); qi++) {
            QuickNoteExportDto qn = dto.quicknotes().get(qi);
            String base = "/quicknotes/" + qi;

            if (qn.targetRef() == null || qn.targetRef().isBlank()) {
                problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                        "Quick note target reference is blank for target type '" + qn.targetType() + "'.",
                        "Provide a non-blank target reference for the quick note."));
                continue;
            }

            if (qn.targetType() == null) continue;

            switch (qn.targetType()) {
                case "MAP":
                    if (!index.mapsByKey.containsKey(qn.targetRef()) && !index.mapsByName.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Map '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use map key 'map-crypt' or define the missing map."));
                    }
                    break;
                case "PARTY_MEMBER":
                    if (!index.partyByName.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Party member '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use character name 'Aria' or define the missing party member."));
                    }
                    break;
                case "STATBLOCK":
                    if (!index.statblocksByKey.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "StatBlock '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                    }
                    break;
                case "NOTE":
                    if (!index.notesByTitle.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Note '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use note title 'Crypt Lore' or define the missing note."));
                    }
                    break;
                case "HANDOUT":
                    if (!index.handoutsByTitle.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Handout '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use handout title 'Warning Plaque' or define the missing handout."));
                    }
                    break;
                case "ENCOUNTER":
                    if (!index.encountersByKey.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Encounter '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use encounterKey 'crypt-guardians' or define the missing encounter."));
                    }
                    break;
                case "SCENE":
                    if (!index.scenesByPath.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Scene '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use scene key 'crypt-entry' or define the missing scene."));
                    }
                    break;
                case "CAMPAIGN":
                    break;
            }
        }
    }

    private void validateLedgerReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.ledger() == null) return;
        for (int li = 0; li < dto.ledger().size(); li++) {
            LedgerExportDto l = dto.ledger().get(li);
            if (l.itemAssignmentRef() != null && !index.assignmentsById.containsKey(l.itemAssignmentRef())) {
                problems.add(problem("UNRESOLVED_REFERENCE", "/ledger/" + li + "/itemAssignmentRef",
                        "Assignment '" + l.itemAssignmentRef() + "' does not exist in this campaign document.",
                        "Use assignment ID '00000000-0000-0000-0000-000000000101' or define the missing assignment."));
            }
        }
    }

    private void validateTimelineReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.timeline() == null) return;
        for (int ti = 0; ti < dto.timeline().size(); ti++) {
            TimelineExportDto t = dto.timeline().get(ti);
            if (t.noteTitle() != null && !index.notesByTitle.containsKey(t.noteTitle())) {
                problems.add(problem("UNRESOLVED_REFERENCE", "/timeline/" + ti + "/noteTitle",
                        "Note '" + t.noteTitle() + "' does not exist in this campaign document.",
                        "Use note title 'Crypt Lore' or define the missing note."));
            }
        }
    }

    private void validateBounds(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        validateTokenBounds(dto, index, problems);
        validateScenePinBounds(dto, index, problems);
    }

    private void validateTokenBounds(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.maps() == null) return;
        for (int mi = 0; mi < dto.maps().size(); mi++) {
            MapExportDto m = dto.maps().get(mi);
            if (m.tokens() == null || m.grid() == null) continue;
            int mapW = m.grid().w() * m.grid().cellPx();
            int mapH = m.grid().h() * m.grid().cellPx();
            for (int ti = 0; ti < m.tokens().size(); ti++) {
                TokenExportDto t = m.tokens().get(ti);
                String base = "/maps/" + mi + "/tokens/" + ti;
                if (t.positionX() + t.sizeCols() * m.grid().cellPx() > mapW) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionX",
                            "Token positionX (" + t.positionX() + ") with sizeCols=" + t.sizeCols()
                                    + ", cellPx=" + m.grid().cellPx() + " exceeds map width of " + mapW + " px.",
                            "Ensure x + sizeCols * cellPx <= map width in pixels."));
                }
                if (t.positionY() + t.sizeRows() * m.grid().cellPx() > mapH) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionY",
                            "Token positionY (" + t.positionY() + ") with sizeRows=" + t.sizeRows()
                                    + ", cellPx=" + m.grid().cellPx() + " exceeds map height of " + mapH + " px.",
                            "Ensure y + sizeRows * cellPx <= map height in pixels."));
                }
            }
        }
    }

    private void validateScenePinBounds(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.adventures() == null) return;
        for (int ai = 0; ai < dto.adventures().size(); ai++) {
            AdventureExportDto a = dto.adventures().get(ai);
            if (a.chapters() == null) continue;
            for (int ci = 0; ci < a.chapters().size(); ci++) {
                ChapterExportDto ch = a.chapters().get(ci);
                if (ch.scenes() == null) continue;
                for (int si = 0; si < ch.scenes().size(); si++) {
                    SceneExportDto sc = ch.scenes().get(si);
                    if (sc.pin() == null || sc.map() == null) continue;
                    MapExportDto map = resolveMapByName(sc.map(), index);
                    if (map == null || map.grid() == null) continue;
                    int mapW = map.grid().w() * map.grid().cellPx();
                    int mapH = map.grid().h() * map.grid().cellPx();
                    Map<String, Integer> pin = sc.pin();
                    String base = "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si + "/pin";
                    Integer px = pin.get("x");
                    Integer py = pin.get("y");
                    if (px != null && (px < 0 || px >= mapW)) {
                        problems.add(problem("OUT_OF_BOUNDS", base,
                                "Pin x coordinate (" + px + ") is outside the map bounds [0, " + mapW + ").",
                                "Adjust pin x to be within the range 0 <= x < map width in pixels."));
                    }
                    if (py != null && (py < 0 || py >= mapH)) {
                        problems.add(problem("OUT_OF_BOUNDS", base,
                                "Pin y coordinate (" + py + ") is outside the map bounds [0, " + mapH + ").",
                                "Adjust pin y to be within the range 0 <= y < map height in pixels."));
                    }
                }
            }
        }
    }

    private MapExportDto resolveMapByName(String name, V1Index index) {
        if (index.mapsByKey.containsKey(name)) return index.mapsByKey.get(name);
        List<MapExportDto> byName = index.mapsByName.get(name);
        if (byName != null && byName.size() == 1) return byName.get(0);
        return null;
    }

    private void validateGrid(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.maps() == null) return;
        for (int mi = 0; mi < dto.maps().size(); mi++) {
            MapExportDto m = dto.maps().get(mi);
            if (m.grid() == null || m.document() == null || m.document().grid() == null) continue;
            MapExportDto.GridDto outer = m.grid();
            var inner = m.document().grid();

            boolean dimsMatch = inner.width() == outer.w() && inner.height() == outer.h();
            boolean cellPxMatch = inner.cellSizePx() == outer.cellPx();
            boolean gridTypeMatch = outer.gridType().toLowerCase().equals(inner.gridType().toLowerCase());

            if (!dimsMatch || !cellPxMatch || !gridTypeMatch) {
                problems.add(problem("GRID_MISMATCH", "/maps/" + mi + "/document/grid",
                        "Embedded map document grid (gridType='" + inner.gridType() + "') does not match outer map grid (gridType='" + outer.gridType() + "').",
                        "Set the embedded document grid gridType to the lowercase equivalent of the outer map grid type."));
            }
        }
    }

    private void validateEncounterState(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.encounters() == null) return;
        int activeCount = 0;
        int lastActiveIndex = -1;
        for (int ei = 0; ei < dto.encounters().size(); ei++) {
            EncounterExportDto enc = dto.encounters().get(ei);
            if ("ACTIVE".equals(enc.status())) {
                activeCount++;
                lastActiveIndex = ei;
            }
        }
        if (activeCount > 1) {
            problems.add(problem("INVALID_STATE", "/encounters/" + lastActiveIndex + "/status",
                    "Campaign has " + activeCount + " active encounters. At most one encounter may have ACTIVE status.",
                    "Set at most one encounter to ACTIVE status."));
        }

        for (int ei = 0; ei < dto.encounters().size(); ei++) {
            EncounterExportDto enc = dto.encounters().get(ei);
            if (enc.round() < 0) {
                problems.add(problem("INVALID_STATE", "/encounters/" + ei + "/round",
                        "Encounter round (" + enc.round() + ") is negative.",
                        "Set round to a non-negative integer."));
            }
            if (enc.logSequence() < 0) {
                problems.add(problem("INVALID_STATE", "/encounters/" + ei + "/logSequence",
                        "Encounter logSequence (" + enc.logSequence() + ") is negative.",
                        "Set logSequence to a non-negative integer."));
            }
            if ("ACTIVE".equals(enc.status())) {
                int maxIdx = enc.combatants() != null ? enc.combatants().size() - 1 : -1;
                if (enc.activeTurnIndex() != -1 && (enc.activeTurnIndex() < 0 || enc.activeTurnIndex() > maxIdx)) {
                    problems.add(problem("INVALID_STATE", "/encounters/" + ei + "/activeTurnIndex",
                            "Encounter activeTurnIndex (" + enc.activeTurnIndex() + ") is out of bounds for " + (maxIdx + 1) + " combatants.",
                            "Set activeTurnIndex to -1 or a valid combatant index."));
                }
            }
        }
    }

    private void validateSheetCatalog(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        // v1 checkpoint: sheet catalog lookups require repository injection.
        // This will be enabled when package-v2 typed catalog resolution is added.
    }

    private <T> void resolveUniqueName(String basePath, String typeLabel, String value, String thingLabel,
                                       Map<String, List<T>> nameIndex, Function<T, String> nameExtractor,
                                       Set<String> keySet, String keyLabel, List<CampaignImportProblem> problems) {
        if (nameIndex.containsKey(value)) {
            List<T> matches = nameIndex.get(value);
            if (matches.size() > 1) {
                String keys = matches.stream().map(nameExtractor).collect(Collectors.joining(", "));
                problems.add(problem("AMBIGUOUS_REFERENCE", basePath + "/" + typeLabel,
                        typeLabel.substring(0, 1).toUpperCase() + typeLabel.substring(1) + " name '" + value + "' matches multiple " + thingLabel + "s (" + keys + ").",
                        "Reference the " + typeLabel + " by its unique " + keyLabel + " instead of name."));
            }
        } else {
            String suggestion;
            if (!nameIndex.isEmpty()) {
                String firstName = nameIndex.keySet().iterator().next();
                suggestion = "Use " + typeLabel + " name '" + firstName + "' or define the missing " + thingLabel + ".";
            } else {
                suggestion = "Define the missing " + thingLabel + ".";
            }
            problems.add(problem("UNRESOLVED_REFERENCE", basePath + "/" + typeLabel,
                    typeLabel.substring(0, 1).toUpperCase() + typeLabel.substring(1) + " '" + value + "' does not exist in this campaign document.",
                    suggestion));
        }
    }

    private static CampaignImportProblem problem(String code, String path, String message, String suggestion) {
        return new CampaignImportProblem(ImportSeverity.ERROR, code, path, message, suggestion);
    }

    record V1Index(
            Map<String, MapExportDto> mapsByKey,
            Map<String, List<MapExportDto>> mapsByName,
            Map<String, EncounterExportDto> encountersByKey,
            Map<String, List<EncounterExportDto>> encountersByName,
            Map<String, StatBlockExportDto> statblocksByKey,
            Map<String, List<StatBlockExportDto>> statblocksByName,
            Map<String, List<PartyMemberExportDto>> partyByName,
            Map<String, List<HandoutExportDto>> handoutsByTitle,
            Map<String, List<NoteExportDto>> notesByTitle,
            Map<String, AssignmentExportDto> assignmentsById,
            Map<String, SceneExportDto> scenesByPath,
            Map<String, TokenExportDto> tokensById
    ) {}
}
