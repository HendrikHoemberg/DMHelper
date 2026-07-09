package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maps")
public class MapPinApiController {

    private final SceneRepository sceneRepository;

    public MapPinApiController(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    @GetMapping("/{id}/pins")
    public List<Map<String, Object>> getPins(@PathVariable UUID id) {
        return sceneRepository.findByMapIdAndPinXNotNull(id).stream()
                .map(s -> {
                    Map<String, Object> pin = new HashMap<>();
                    pin.put("sceneId", s.getId());
                    pin.put("sceneKey", s.getSceneKey());
                    pin.put("x", s.getPinX());
                    pin.put("y", s.getPinY());
                    pin.put("title", s.getTitle());
                    return pin;
                })
                .toList();
    }
}
