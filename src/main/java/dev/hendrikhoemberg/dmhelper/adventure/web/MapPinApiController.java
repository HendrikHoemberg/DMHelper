package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.threat.service.MapPinDto;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinService;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinWrite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maps")
public class MapPinApiController {

    private final MapThreatPinService mapThreatPinService;

    public MapPinApiController(MapThreatPinService mapThreatPinService) {
        this.mapThreatPinService = mapThreatPinService;
    }

    @GetMapping("/{id}/pins")
    public List<MapPinDto> getPins(@PathVariable UUID id) {
        return mapThreatPinService.listCombinedPins(id);
    }

    @PostMapping("/{id}/pins")
    public ResponseEntity<MapPinDto> createThreatPin(@PathVariable UUID id,
                                                     @RequestBody MapThreatPinWrite write) {
        MapPinDto created = mapThreatPinService.create(id, write);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/pins/{pinId}")
    public MapPinDto updateThreatPin(@PathVariable UUID id,
                                     @PathVariable UUID pinId,
                                     @RequestBody MapThreatPinWrite write) {
        return mapThreatPinService.update(id, pinId, write);
    }

    @DeleteMapping("/{id}/pins/{pinId}")
    public ResponseEntity<Void> deleteThreatPin(@PathVariable UUID id, @PathVariable UUID pinId) {
        mapThreatPinService.delete(id, pinId);
        return ResponseEntity.noContent().build();
    }
}
