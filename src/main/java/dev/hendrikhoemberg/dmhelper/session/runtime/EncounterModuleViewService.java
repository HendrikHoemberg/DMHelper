package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EncounterModuleViewService {

    private final AdventureService adventures;
    private final EncounterRepository encounters;
    private final CombatantRepository combatants;
    private final SceneRepository scenes;

    public EncounterModuleViewService(AdventureService adventures,
                                      EncounterRepository encounters,
                                      CombatantRepository combatants,
                                      SceneRepository scenes) {
        this.adventures = adventures;
        this.encounters = encounters;
        this.combatants = combatants;
        this.scenes = scenes;
    }

    public record PlannedEncounterView(UUID id, String name, UUID mapId, String mapName,
                                       boolean ready, int combatantCount, int unplacedCount) {}

    public record EncounterModuleView(List<PlannedEncounterView> plannedEncounters) {}

    public EncounterModuleView buildView(UUID campaignId, CockpitModuleMode mode) {
        Scene currentScene = adventures.getCurrentScene(campaignId).orElse(null);
        UUID currentMapId = currentScene != null && currentScene.getMap() != null
                ? currentScene.getMap().getId() : null;
        UUID currentChapterId = currentScene != null ? currentScene.getChapter().getId() : null;

        List<Encounter> allPlanned = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.PLANNED)
                .toList();

        Map<UUID, Scene> encounterSceneMap = allPlanned.stream()
                .flatMap(e -> scenes.findByEncounterId(e.getId()).stream()
                        .findFirst().map(s -> Map.entry(e.getId(), s)).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        List<PlannedEncounterView> views = allPlanned.stream()
                .sorted(Comparator
                        .<Encounter, Integer>comparing(e -> {
                            if (currentMapId != null && e.getMap() != null
                                    && e.getMap().getId().equals(currentMapId)) return 0;
                            Scene linked = encounterSceneMap.get(e.getId());
                            if (currentChapterId != null && linked != null
                                    && linked.getChapter().getId().equals(currentChapterId)) return 1;
                            return 2;
                        })
                        .thenComparing(e -> {
                            Scene linked = encounterSceneMap.get(e.getId());
                            if (linked == null) return Integer.MAX_VALUE;
                            return linked.getChapter().getSortOrder();
                        })
                        .thenComparing(Encounter::getName))
                .map(e -> {
                    var combatantList = combatants.findByEncounterIdOrderBySortOrderAsc(e.getId());
                    int combatantCount = combatantList.size();
                    long unplacedCount = combatantList.stream()
                            .filter(c -> c.getPlacement() == null).count();
                    boolean ready = combatantCount > 0 && unplacedCount == 0;
                    return new PlannedEncounterView(
                            e.getId(), e.getName(),
                            e.getMap() != null ? e.getMap().getId() : null,
                            e.getMap() != null ? e.getMap().getName() : null,
                            ready, combatantCount, (int) unplacedCount);
                })
                .limit(8)
                .toList();

        return new EncounterModuleView(views);
    }
}
