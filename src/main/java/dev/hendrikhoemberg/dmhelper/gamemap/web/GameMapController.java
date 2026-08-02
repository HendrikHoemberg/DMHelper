package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/maps")
public class GameMapController {

    private final GameMapService service;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;

    public GameMapController(GameMapService service,
                             SceneRepository sceneRepository,
                             EncounterRepository encounterRepository) {
        this.service = service;
        this.sceneRepository = sceneRepository;
        this.encounterRepository = encounterRepository;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        var maps = service.findByCampaignId(campaignId);
        model.addAttribute("maps", maps);
        model.addAttribute("mapReferences", referenceLabels(maps));
        return "maps/list";
    }

    /** Which scenes and encounters point at each map, as concise labels for the cards. */
    private Map<UUID, List<String>> referenceLabels(List<GameMap> maps) {
        Map<UUID, List<String>> labels = new HashMap<>();
        for (GameMap map : maps) {
            List<String> refs = new ArrayList<>();
            sceneRepository.findByMapId(map.getId())
                    .forEach(scene -> refs.add(scene.getTitle()));
            encounterRepository.findByMapIdOrderByNameAsc(map.getId())
                    .forEach(encounter -> refs.add(encounter.getName()));
            labels.put(map.getId(), refs);
        }
        return labels;
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId,
                          @RequestHeader(value = "HX-Request", defaultValue = "false") boolean htmxRequest,
                          Model model) {
        model.addAttribute("campaignId", campaignId);
        return htmxRequest ? "maps/_form :: form" : "maps/new";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "30") int gridWidth,
                         @RequestParam(defaultValue = "20") int gridHeight,
                         @RequestParam(defaultValue = "48") int cellSizePx,
                         Model model) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        var map = service.create(campaignId, name, gridWidth, gridHeight, cellSizePx);
        model.addAttribute("map", map);
        model.addAttribute("campaignId", campaignId);
        return "maps/_card :: card";
    }

    @GetMapping("/{mapId}/edit")
    public String edit(@PathVariable UUID campaignId,
                       @PathVariable UUID mapId,
                       Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("map", service.findById(mapId));
        model.addAttribute("editorPage", true);
        return "maps/editor";
    }

    @GetMapping("/{mapId}/play")
    public String play(@PathVariable UUID campaignId,
                       @PathVariable UUID mapId) {
        return "redirect:/campaigns/" + campaignId + "/session?mapId=" + mapId;
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                        @PathVariable UUID mapId) {
        service.delete(mapId);
        return ResponseEntity.ok().build();
    }
}
