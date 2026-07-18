package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MapThreatPinService {

    private final MapThreatPinRepository pinRepository;
    private final GameMapRepository gameMapRepository;
    private final SceneRepository sceneRepository;
    private final ThreatReferenceResolver referenceResolver;

    public MapThreatPinService(MapThreatPinRepository pinRepository,
                               GameMapRepository gameMapRepository,
                               SceneRepository sceneRepository,
                               ThreatReferenceResolver referenceResolver) {
        this.pinRepository = pinRepository;
        this.gameMapRepository = gameMapRepository;
        this.sceneRepository = sceneRepository;
        this.referenceResolver = referenceResolver;
    }

    @Transactional(readOnly = true)
    public List<MapPinDto> listCombinedPins(UUID mapId) {
        requireMap(mapId);
        List<MapPinDto> pins = new ArrayList<>();
        for (Scene scene : sceneRepository.findByMapIdAndPinXNotNull(mapId)) {
            if (scene.getPinX() == null || scene.getPinY() == null) {
                continue;
            }
            pins.add(new MapPinDto(
                    MapPinDto.KIND_SCENE,
                    scene.getId(),
                    scene.getSceneKey(),
                    scene.getPinX(),
                    scene.getPinY(),
                    scene.getTitle(),
                    scene.getId(),
                    scene.getSceneKey(),
                    null,
                    null));
        }
        pins.addAll(listThreatPins(mapId));
        return List.copyOf(pins);
    }

    @Transactional(readOnly = true)
    public List<MapPinDto> listThreatPins(UUID mapId) {
        GameMap map = requireMap(mapId);
        UUID campaignId = campaignIdOf(map);
        return pinRepository.findByMapIdOrderBySortOrderAsc(mapId).stream()
                .map(pin -> toThreatDto(pin, campaignId))
                .toList();
    }

    public MapPinDto create(UUID mapId, MapThreatPinWrite write) {
        GameMap map = requireMap(mapId);
        validateWrite(map, write, null);

        MapThreatPin pin = new MapThreatPin();
        pin.setMap(map);
        applyWrite(pin, write);
        return toThreatDto(pinRepository.save(pin), campaignIdOf(map));
    }

    public MapPinDto update(UUID mapId, UUID pinId, MapThreatPinWrite write) {
        GameMap map = requireMap(mapId);
        MapThreatPin pin = requirePinOnMap(mapId, pinId);
        validateWrite(map, write, pinId);
        applyWrite(pin, write);
        return toThreatDto(pinRepository.save(pin), campaignIdOf(map));
    }

    public void delete(UUID mapId, UUID pinId) {
        requireMap(mapId);
        MapThreatPin pin = requirePinOnMap(mapId, pinId);
        pinRepository.delete(pin);
    }

    private void applyWrite(MapThreatPin pin, MapThreatPinWrite write) {
        pin.setPinKey(write.key().strip());
        pin.setThreatKind(write.threatKind());
        pin.setThreatId(write.threatId());
        pin.setXPx(write.x());
        pin.setYPx(write.y());
        String label = write.label();
        pin.setLabel(label != null && !label.isBlank() ? label.strip() : null);
        pin.setSortOrder(write.sortOrder());
    }

    private void validateWrite(GameMap map, MapThreatPinWrite write, UUID excludePinId) {
        if (write == null) {
            throw new IllegalArgumentException("Pin write is required");
        }
        if (write.key() == null || write.key().isBlank()
                || !ThreatValidationRules.KEY_PATTERN.matcher(write.key().strip()).matches()) {
            throw new IllegalArgumentException(
                    "Invalid pin key: must match " + ThreatValidationRules.KEY_PATTERN.pattern());
        }
        if (write.threatKind() == null || write.threatId() == null) {
            throw new IllegalArgumentException("threatKind and threatId are required");
        }

        int maxX = map.getGridWidth() * map.getCellSizePx();
        int maxY = map.getGridHeight() * map.getCellSizePx();
        if (write.x() < 0 || write.x() >= maxX || write.y() < 0 || write.y() >= maxY) {
            throw new IllegalArgumentException(
                    "Pin coordinates out of bounds: require 0 <= x < " + maxX
                            + " and 0 <= y < " + maxY);
        }

        UUID campaignId = map.getCampaign() != null ? map.getCampaign().getId() : null;
        referenceResolver.requireVisible(write.threatKind(), write.threatId(), campaignId);

        String key = write.key().strip();
        pinRepository.findByMapIdAndPinKey(map.getId(), key).ifPresent(existing -> {
            if (excludePinId == null || !existing.getId().equals(excludePinId)) {
                throw new IllegalArgumentException("Duplicate pin key on map: " + key);
            }
        });
    }

    private MapPinDto toThreatDto(MapThreatPin pin, UUID campaignId) {
        String title = pin.getLabel();
        if (title == null || title.isBlank()) {
            title = resolveThreatName(pin.getThreatKind(), pin.getThreatId(), campaignId);
        }
        return new MapPinDto(
                MapPinDto.KIND_THREAT,
                pin.getId(),
                pin.getPinKey(),
                pin.getXPx(),
                pin.getYPx(),
                title,
                null,
                null,
                pin.getThreatKind(),
                pin.getThreatId());
    }

    private static UUID campaignIdOf(GameMap map) {
        return map.getCampaign() != null ? map.getCampaign().getId() : null;
    }

    private String resolveThreatName(ThreatKind kind, UUID threatId, UUID campaignId) {
        try {
            Object threat = referenceResolver.requireVisible(kind, threatId, campaignId);
            return switch (threat) {
                case Trap t -> t.getName();
                case Hazard h -> h.getName();
                default -> kind != null ? kind.name() : "Threat";
            };
        } catch (NotFoundException ex) {
            return kind != null ? kind.name() : "Threat";
        }
    }

    private GameMap requireMap(UUID mapId) {
        return gameMapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
    }

    private MapThreatPin requirePinOnMap(UUID mapId, UUID pinId) {
        MapThreatPin pin = pinRepository.findById(pinId)
                .orElseThrow(() -> new NotFoundException("Map threat pin not found: " + pinId));
        if (pin.getMap() == null || !mapId.equals(pin.getMap().getId())) {
            throw new NotFoundException("Map threat pin not found on map: " + pinId);
        }
        return pin;
    }
}
