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
    private final CampaignCatalogResolver catalog;

    public CampaignSemanticValidator(CampaignCatalogResolver catalog) {
        this.catalog = catalog;
    }

    public List<CampaignImportProblem> validate(CampaignExportDto dto) {
        List<CampaignImportProblem> problems = new ArrayList<>();
        V1Index index = buildIndex(dto);

        validateUniqueness(dto, index, problems);
        validateReferences(dto, index, problems);
        validateBounds(dto, index, problems);
        validateGrid(dto, index, problems);
        validateEncounterState(dto, index, problems);
        validateSheetCatalog(dto, index, problems);

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
        Map<String, List<SceneExportDto>> scenesByPath = new HashMap<>();
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
                            scenesByPath.computeIfAbsent(scenePath, key -> new ArrayList<>()).add(sc);
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
                copyMapList(scenesByPath), Map.copyOf(tokensById)
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

        Set<String> seenStatBlockKeys = new HashSet<>();
        if (dto.statBlocks() != null) {
            for (int i = 0; i < dto.statBlocks().size(); i++) {
                String key = dto.statBlocks().get(i).sourceKey();
                if (key != null && !seenStatBlockKeys.add(key)) {
                    problems.add(problem("DUPLICATE_REFERENCE", "/statBlocks/" + i + "/sourceKey",
                            "StatBlock source key '" + key + "' is used by multiple package statblocks.",
                            "Use a unique sourceKey for each package statblock."));
                }
            }
        }

        addDuplicateProblems(dto.party(), PartyMemberExportDto::characterName, "/party/", "/characterName",
                "Party member name", problems);
        addDuplicateProblems(dto.handouts(), HandoutExportDto::title, "/handouts/", "/title",
                "Handout title", problems);
        addDuplicateProblems(dto.notes(), NoteExportDto::title, "/notes/", "/title",
                "Note title", problems);
        addDuplicateProblems(dto.assignments(), a -> a.id() != null ? a.id().toString() : null,
                "/assignments/", "/id", "Assignment ID", problems);

        if (dto.adventures() != null) {
            for (int ai = 0; ai < dto.adventures().size(); ai++) {
                AdventureExportDto adventure = dto.adventures().get(ai);
                Set<String> chapterTitles = new HashSet<>();
                if (adventure.chapters() == null) continue;
                for (int ci = 0; ci < adventure.chapters().size(); ci++) {
                    ChapterExportDto chapter = adventure.chapters().get(ci);
                    if (chapter.title() != null && !chapterTitles.add(chapter.title())) {
                        problems.add(problem("DUPLICATE_REFERENCE",
                                "/adventures/" + ai + "/chapters/" + ci + "/title",
                                "Chapter title '" + chapter.title() + "' is duplicated within adventure '"
                                        + adventure.name() + "'.",
                                "Use a unique chapter title within each adventure."));
                    }
                    Set<String> sceneKeys = new HashSet<>();
                    if (chapter.scenes() == null) continue;
                    for (int si = 0; si < chapter.scenes().size(); si++) {
                        String sceneKey = chapter.scenes().get(si).sceneKey();
                        if (sceneKey != null && !sceneKeys.add(sceneKey)) {
                            problems.add(problem("DUPLICATE_REFERENCE",
                                    "/adventures/" + ai + "/chapters/" + ci + "/scenes/" + si + "/sceneKey",
                                    "Scene key '" + sceneKey + "' is duplicated within chapter '"
                                            + chapter.title() + "'.",
                                    "Use a unique sceneKey within each chapter."));
                        }
                    }
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
                        validateMapReference(base + "/map", sc.map(), index, problems);
                    }

                    if (sc.encounter() != null) {
                        validateEncounterReference(base + "/encounter", sc.encounter(), index, problems);
                    }

                    if (sc.statblocks() != null) {
                        for (int sbi = 0; sbi < sc.statblocks().size(); sbi++) {
                            String sbKey = sc.statblocks().get(sbi);
                            if (!hasStatBlock(sbKey, index)) {
                                problems.add(problem("UNRESOLVED_REFERENCE", base + "/statblocks/" + sbi,
                                        "StatBlock '" + sbKey + "' does not exist in this campaign document.",
                                        "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                            }
                        }
                    }

                    if (sc.handouts() != null) {
                        for (int hi = 0; hi < sc.handouts().size(); hi++) {
                            String hTitle = sc.handouts().get(hi);
                            List<HandoutExportDto> matches = index.handoutsByTitle.get(hTitle);
                            if (matches == null) {
                                problems.add(problem("UNRESOLVED_REFERENCE", base + "/handouts/" + hi,
                                        "Handout '" + hTitle + "' does not exist in this campaign document.",
                                        "Use handout title 'Warning Plaque' or define the missing handout."));
                            } else if (matches.size() > 1) {
                                problems.add(ambiguous(base + "/handouts/" + hi, "Handout title", hTitle));
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
                validateMapReference(base + "/map", enc.map(), index, problems);
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

                    if (c.statBlockKey() != null && !hasStatBlock(c.statBlockKey(), index)) {
                        problems.add(problem("UNRESOLVED_REFERENCE", cbase + "/statBlockKey",
                                "StatBlock '" + c.statBlockKey() + "' does not exist in this campaign document.",
                                "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                    }

                    if (c.partyMemberName() != null) {
                        validateNamedReference(index.partyByName.get(c.partyMemberName()),
                                cbase + "/partyMemberName", "Party member", c.partyMemberName(),
                                partySuggestion(index), problems);
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

                if (t.statBlockKey() != null && !hasStatBlock(t.statBlockKey(), index)) {
                    problems.add(problem("UNRESOLVED_REFERENCE", base + "/statBlockKey",
                            "StatBlock '" + t.statBlockKey() + "' does not exist in this campaign document.",
                            "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                }

                if (t.partyMemberName() != null) {
                    validateNamedReference(index.partyByName.get(t.partyMemberName()),
                            base + "/partyMemberName", "Party member", t.partyMemberName(),
                            partySuggestion(index), problems);
                }
            }
        }
    }

    private void validateAssignmentReferences(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        if (dto.assignments() == null) return;
        for (int ai = 0; ai < dto.assignments().size(); ai++) {
            AssignmentExportDto a = dto.assignments().get(ai);
            String base = "/assignments/" + ai;

            if (a.holderName() != null) {
                validateNamedReference(index.partyByName.get(a.holderName()),
                        base + "/holderName", "Party member", a.holderName(),
                        partySuggestion(index), problems);
            }

            int sourceCount = (a.magicItemKey() != null ? 1 : 0)
                    + (a.equipmentItemKey() != null ? 1 : 0)
                    + (a.customText() != null && !a.customText().isBlank() ? 1 : 0);
            if (sourceCount != 1) {
                problems.add(problem("INVALID_ASSIGNMENT_SOURCE", base,
                        "Assignment must contain exactly one item source, but found " + sourceCount + ".",
                        "Set exactly one of magicItemKey, equipmentItemKey, or non-blank customText."));
            }
            if (a.magicItemKey() != null && !catalog.hasMagicItem(a.magicItemKey())) {
                problems.add(unresolvedCatalog(base + "/magicItemKey", "Magic item", a.magicItemKey()));
            }
            if (a.equipmentItemKey() != null && !catalog.hasEquipmentItem(a.equipmentItemKey())) {
                problems.add(unresolvedCatalog(base + "/equipmentItemKey", "Equipment item", a.equipmentItemKey()));
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
                    if (!index.mapsByKey.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Map '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use map key 'map-crypt' or define the missing map."));
                    }
                    break;
                case "PARTY_MEMBER":
                    validateNamedReference(index.partyByName.get(qn.targetRef()),
                            base + "/targetRef", "Party member", qn.targetRef(),
                            partySuggestion(index), problems);
                    break;
                case "STATBLOCK":
                    if (!hasStatBlock(qn.targetRef(), index)) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "StatBlock '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use sourceKey 'custom_goblin-captain' or define the missing statblock."));
                    }
                    break;
                case "NOTE":
                    validateNamedReference(index.notesByTitle.get(qn.targetRef()),
                            base + "/targetRef", "Note", qn.targetRef(), noteSuggestion(index), problems);
                    break;
                case "HANDOUT":
                    validateNamedReference(index.handoutsByTitle.get(qn.targetRef()),
                            base + "/targetRef", "Handout", qn.targetRef(), handoutSuggestion(index), problems);
                    break;
                case "ENCOUNTER":
                    if (!index.encountersByKey.containsKey(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Encounter '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use encounterKey 'crypt-guardians' or define the missing encounter."));
                    }
                    break;
                case "SCENE":
                    List<SceneExportDto> scenes = index.scenesByPath.get(qn.targetRef());
                    if (scenes == null) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Scene '" + qn.targetRef() + "' does not exist in this campaign document.",
                                "Use the full Adventure/Chapter/sceneKey path of an existing scene."));
                    } else if (scenes.size() > 1) {
                        problems.add(ambiguous(base + "/targetRef", "Scene path", qn.targetRef()));
                    }
                    break;
                case "CAMPAIGN":
                    if (!"CAMPAIGN".equals(qn.targetRef())) {
                        problems.add(problem("UNRESOLVED_REFERENCE", base + "/targetRef",
                                "Campaign quick notes must use the exact target reference 'CAMPAIGN'.",
                                "Set targetRef to 'CAMPAIGN'."));
                    }
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
            if (t.noteTitle() != null) {
                validateNamedReference(index.notesByTitle.get(t.noteTitle()),
                        "/timeline/" + ti + "/noteTitle", "Note", t.noteTitle(),
                        noteSuggestion(index), problems);
            }
        }
    }

    private void validateBounds(CampaignExportDto dto, V1Index index, List<CampaignImportProblem> problems) {
        validateTokenBounds(dto, index, problems);
        validateMapDocumentBounds(dto, problems);
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
                if (t.positionX() < 0) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionX",
                            "Token positionX (" + t.positionX() + ") is negative.",
                            "Set positionX to a non-negative pixel coordinate."));
                } else if (t.positionX() + t.sizeCols() * m.grid().cellPx() > mapW) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionX",
                            "Token positionX (" + t.positionX() + ") with sizeCols=" + t.sizeCols()
                                    + ", cellPx=" + m.grid().cellPx() + " exceeds map width of " + mapW + " px.",
                            "Ensure x + sizeCols * cellPx <= map width in pixels."));
                }
                if (t.positionY() < 0) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionY",
                            "Token positionY (" + t.positionY() + ") is negative.",
                            "Set positionY to a non-negative pixel coordinate."));
                } else if (t.positionY() + t.sizeRows() * m.grid().cellPx() > mapH) {
                    problems.add(problem("OUT_OF_BOUNDS", base + "/positionY",
                            "Token positionY (" + t.positionY() + ") with sizeRows=" + t.sizeRows()
                                    + ", cellPx=" + m.grid().cellPx() + " exceeds map height of " + mapH + " px.",
                            "Ensure y + sizeRows * cellPx <= map height in pixels."));
                }
            }
        }
    }

    private void validateMapDocumentBounds(CampaignExportDto dto, List<CampaignImportProblem> problems) {
        if (dto.maps() == null) return;
        for (int mi = 0; mi < dto.maps().size(); mi++) {
            MapExportDto map = dto.maps().get(mi);
            if (map.document() == null || map.document().grid() == null) continue;
            int width = map.document().grid().width();
            int height = map.document().grid().height();
            String documentPath = "/maps/" + mi + "/document";

            for (int li = 0; li < map.document().layers().size(); li++) {
                var layer = map.document().layers().get(li);
                String layerPath = documentPath + "/layers/" + li;
                for (int ci = 0; ci < layer.cells().size(); ci++) {
                    var cell = layer.cells().get(ci);
                    String cellPath = layerPath + "/cells/" + ci;
                    validateCellCoordinate(cell.col(), width, cellPath + "/col", "col", problems);
                    validateCellCoordinate(cell.row(), height, cellPath + "/row", "row", problems);
                }
                for (int si = 0; si < layer.shapes().size(); si++) {
                    validateShapeBounds(layer.shapes().get(si), width, height,
                            layerPath + "/shapes/" + si + "/points", problems);
                }
                if (layer.image() != null) {
                    var image = layer.image();
                    if (image.width() <= 0 || image.height() <= 0) {
                        problems.add(problem("INVALID_GEOMETRY", layerPath + "/image",
                                "Map image width and height must be positive.",
                                "Set positive image dimensions in grid-cell units."));
                    } else if (image.x() < 0 || image.y() < 0
                            || image.x() + image.width() > width
                            || image.y() + image.height() > height) {
                        problems.add(problem("OUT_OF_BOUNDS", layerPath + "/image",
                                "Map image extent is outside the embedded grid bounds.",
                                "Keep x, y, width, and height within the embedded grid."));
                    }
                }
            }

            for (int pi = 0; pi < map.document().primitives().size(); pi++) {
                var primitive = map.document().primitives().get(pi);
                String primitivePath = documentPath + "/primitives/" + pi;
                validateCellCoordinate(primitive.startCol(), width, primitivePath + "/startCol", "startCol", problems);
                validateCellCoordinate(primitive.startRow(), height, primitivePath + "/startRow", "startRow", problems);
                validateCellCoordinate(primitive.endCol(), width, primitivePath + "/endCol", "endCol", problems);
                validateCellCoordinate(primitive.endRow(), height, primitivePath + "/endRow", "endRow", problems);
            }
        }
    }

    private static void validateCellCoordinate(int value, int bound, String path, String label,
                                               List<CampaignImportProblem> problems) {
        if (value < 0 || value >= bound) {
            problems.add(problem("OUT_OF_BOUNDS", path,
                    "Map " + label + " coordinate (" + value + ") is outside [0, " + bound + ").",
                    "Use a grid-cell coordinate within the embedded map grid."));
        }
    }

    private static void validateShapeBounds(dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto.ShapeDto shape,
                                            int width, int height, String path,
                                            List<CampaignImportProblem> problems) {
        List<Double> points = shape.points();
        boolean validArity = switch (shape.type()) {
            case "rect", "line" -> points.size() == 4;
            case "circle" -> points.size() == 3;
            case "polygon" -> points.size() >= 6 && points.size() % 2 == 0;
            default -> false;
        };
        if (!validArity) {
            problems.add(problem("INVALID_GEOMETRY", path,
                    "Shape type '" + shape.type() + "' has an invalid coordinate list.",
                    "Use the documented coordinate count for the shape type."));
            return;
        }

        boolean inBounds = switch (shape.type()) {
            case "rect" -> points.get(0) >= 0 && points.get(1) >= 0
                    && points.get(2) > 0 && points.get(3) > 0
                    && points.get(0) + points.get(2) <= width
                    && points.get(1) + points.get(3) <= height;
            case "circle" -> points.get(2) > 0
                    && points.get(0) - points.get(2) >= 0
                    && points.get(1) - points.get(2) >= 0
                    && points.get(0) + points.get(2) <= width
                    && points.get(1) + points.get(2) <= height;
            case "line", "polygon" -> coordinatePairsInBounds(points, width, height);
            default -> false;
        };
        if (!inBounds) {
            problems.add(problem("OUT_OF_BOUNDS", path,
                    "Shape coordinates or extent are outside the embedded grid bounds.",
                    "Keep every shape coordinate and extent within the embedded grid."));
        }
    }

    private static boolean coordinatePairsInBounds(List<Double> points, int width, int height) {
        for (int i = 0; i < points.size(); i += 2) {
            if (points.get(i) < 0 || points.get(i) > width
                    || points.get(i + 1) < 0 || points.get(i + 1) > height) {
                return false;
            }
        }
        return true;
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
        if (dto.party() == null) return;
        for (int pi = 0; pi < dto.party().size(); pi++) {
            var sheet = dto.party().get(pi).sheet();
            if (sheet == null) continue;
            String base = "/party/" + pi + "/sheet";

            if (sheet.speciesKey() != null && !catalog.hasSpecies(sheet.speciesKey())) {
                problems.add(unresolvedCatalog(base + "/speciesKey", "Species", sheet.speciesKey()));
            }
            if (sheet.backgroundKey() != null && !catalog.hasBackground(sheet.backgroundKey())) {
                problems.add(unresolvedCatalog(base + "/backgroundKey", "Background", sheet.backgroundKey()));
            }
            if (sheet.classLevels() != null) {
                for (int ci = 0; ci < sheet.classLevels().size(); ci++) {
                    String key = sheet.classLevels().get(ci).classSourceKey();
                    if (key != null && !catalog.hasCharacterClass(key)) {
                        problems.add(unresolvedCatalog(base + "/classLevels/" + ci + "/classSourceKey",
                                "Character class", key));
                    }
                }
            }
            if (sheet.featRefs() != null) {
                for (int fi = 0; fi < sheet.featRefs().size(); fi++) {
                    String key = sheet.featRefs().get(fi);
                    if (key != null && !catalog.hasFeat(key)) {
                        problems.add(unresolvedCatalog(base + "/featRefs/" + fi, "Feat", key));
                    }
                }
            }
            if (sheet.spells() != null) {
                for (int si = 0; si < sheet.spells().size(); si++) {
                    String key = sheet.spells().get(si).spellKey();
                    if (key != null && !catalog.hasSpell(key)) {
                        problems.add(unresolvedCatalog(base + "/spells/" + si + "/spellKey", "Spell", key));
                    }
                }
            }
        }
    }

    private static CampaignImportProblem unresolvedCatalog(String path, String type, String sourceKey) {
        return problem("UNRESOLVED_REFERENCE", path,
                type + " source key '" + sourceKey + "' does not exist in the " + type.toLowerCase() + " catalog.",
                "Use an existing " + type.toLowerCase() + " source key.");
    }

    private boolean hasStatBlock(String sourceKey, V1Index index) {
        return index.statblocksByKey.containsKey(sourceKey) || catalog.hasStatBlock(sourceKey);
    }

    private static <T> void validateNamedReference(List<T> matches, String path, String type,
                                                   String value, String missingSuggestion,
                                                   List<CampaignImportProblem> problems) {
        if (matches == null || matches.isEmpty()) {
            problems.add(problem("UNRESOLVED_REFERENCE", path,
                    type + " '" + value + "' does not exist in this campaign document.",
                    missingSuggestion));
        } else if (matches.size() > 1) {
            problems.add(ambiguous(path, type, value));
        }
    }

    private static String partySuggestion(V1Index index) {
        return index.partyByName.isEmpty()
                ? "Define the missing party member."
                : "Use character name '" + index.partyByName.keySet().iterator().next()
                        + "' or define the missing party member.";
    }

    private static String noteSuggestion(V1Index index) {
        return index.notesByTitle.isEmpty()
                ? "Define the missing note."
                : "Use note title '" + index.notesByTitle.keySet().iterator().next()
                        + "' or define the missing note.";
    }

    private static String handoutSuggestion(V1Index index) {
        return index.handoutsByTitle.isEmpty()
                ? "Define the missing handout."
                : "Use handout title '" + index.handoutsByTitle.keySet().iterator().next()
                        + "' or define the missing handout.";
    }

    private static CampaignImportProblem ambiguous(String path, String type, String value) {
        return problem("AMBIGUOUS_REFERENCE", path,
                type + " '" + value + "' matches multiple entities in this campaign document.",
                "Rename the duplicates or reference a unique key.");
    }

    private static <T> void addDuplicateProblems(List<T> values, Function<T, String> keyExtractor,
                                                 String pathPrefix, String pathSuffix, String label,
                                                 List<CampaignImportProblem> problems) {
        if (values == null) return;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String key = keyExtractor.apply(values.get(i));
            if (key != null && !seen.add(key)) {
                problems.add(problem("DUPLICATE_REFERENCE", pathPrefix + i + pathSuffix,
                        label + " '" + key + "' is used by multiple entities.",
                        "Use a unique " + label.toLowerCase() + "."));
            }
        }
    }

    private static void validateMapReference(String path, String value, V1Index index,
                                             List<CampaignImportProblem> problems) {
        if (index.mapsByKey.containsKey(value)) return;
        List<MapExportDto> matches = index.mapsByName.get(value);
        if (matches == null || matches.isEmpty()) {
            String suggestion = index.mapsByName.isEmpty()
                    ? "Define the missing map."
                    : "Use map name '" + index.mapsByName.keySet().iterator().next()
                            + "' or define the missing map.";
            problems.add(problem("UNRESOLVED_REFERENCE", path,
                    "Map '" + value + "' does not exist in this campaign document.", suggestion));
        } else if (matches.size() > 1) {
            String keys = matches.stream().map(MapExportDto::key).collect(Collectors.joining(", "));
            problems.add(problem("AMBIGUOUS_REFERENCE", path,
                    "Map name '" + value + "' matches multiple maps (" + keys + ").",
                    "Reference the map by its unique key instead of name."));
        }
    }

    private static void validateEncounterReference(String path, String value, V1Index index,
                                                   List<CampaignImportProblem> problems) {
        if (index.encountersByKey.containsKey(value)) return;
        List<EncounterExportDto> matches = index.encountersByName.get(value);
        if (matches == null || matches.isEmpty()) {
            String suggestion = index.encountersByKey.isEmpty()
                    ? "Define the missing encounter."
                    : "Use encounterKey '" + index.encountersByKey.keySet().iterator().next()
                            + "' or define the missing encounter.";
            problems.add(problem("UNRESOLVED_REFERENCE", path,
                    "Encounter '" + value + "' does not exist in this campaign document.", suggestion));
        } else if (matches.size() > 1) {
            problems.add(ambiguous(path, "Encounter name", value));
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
            Map<String, List<SceneExportDto>> scenesByPath,
            Map<String, TokenExportDto> tokensById
    ) {}
}
