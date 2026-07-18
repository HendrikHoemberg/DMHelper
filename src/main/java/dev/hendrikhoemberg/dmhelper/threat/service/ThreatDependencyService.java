package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ThreatDependencyService {

    public static final String DEP_KIND_SCENE = "SCENE";
    public static final String DEP_KIND_COMBATANT = "COMBATANT";
    public static final String DEP_KIND_MAP_PIN = "MAP_PIN";

    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final SceneSectionRepository sceneSectionRepository;
    private final CombatantRepository combatantRepository;
    private final MapThreatPinRepository mapThreatPinRepository;

    public ThreatDependencyService(TrapRepository trapRepository,
                                   HazardRepository hazardRepository,
                                   SceneSectionRepository sceneSectionRepository,
                                   CombatantRepository combatantRepository,
                                   MapThreatPinRepository mapThreatPinRepository) {
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.sceneSectionRepository = sceneSectionRepository;
        this.combatantRepository = combatantRepository;
        this.mapThreatPinRepository = mapThreatPinRepository;
    }

    public ThreatDeletionImpact computeDeletionImpact(ThreatKind kind, UUID threatId) {
        List<ThreatDependency> deps = new ArrayList<>();
        String name = resolveName(kind, threatId);

        for (var section : sceneSectionRepository.findByThreatKindAndThreatId(kind, threatId)) {
            var scene = section.getScene();
            String label = scene != null ? scene.getTitle() : "Scene";
            if (section.getLabel() != null && !section.getLabel().isBlank()) {
                label = label + " / " + section.getLabel();
            }
            deps.add(new ThreatDependency(
                    DEP_KIND_SCENE, scene != null ? scene.getId() : section.getId(),
                    label, "/sections/" + section.getId()));
        }

        for (var combatant : combatantRepository.findByThreatKindAndThreatId(kind, threatId)) {
            deps.add(new ThreatDependency(
                    DEP_KIND_COMBATANT, combatant.getId(),
                    combatant.getName() != null ? combatant.getName() : name,
                    "/combatants/" + combatant.getId()));
        }

        for (var pin : mapThreatPinRepository.findByThreatKindAndThreatId(kind, threatId)) {
            String label = pin.getLabel() != null && !pin.getLabel().isBlank()
                    ? pin.getLabel()
                    : pin.getPinKey();
            deps.add(new ThreatDependency(
                    DEP_KIND_MAP_PIN, pin.getId(),
                    label, "/mapPins/" + pin.getId()));
        }

        return new ThreatDeletionImpact(kind, threatId, List.copyOf(deps));
    }

    private String resolveName(ThreatKind kind, UUID threatId) {
        return switch (kind) {
            case TRAP -> trapRepository.findById(threatId).map(t -> t.getName()).orElse("Trap");
            case HAZARD -> hazardRepository.findById(threatId).map(h -> h.getName()).orElse("Hazard");
        };
    }
}
