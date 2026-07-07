package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/maps")
public class GameMapController {

    private final GameMapService service;

    public GameMapController(GameMapService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", service.findByCampaignId(campaignId));
        return "maps/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        return "maps/_form :: form";
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
        return "maps/editor";
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                        @PathVariable UUID mapId) {
        service.delete(mapId);
        return ResponseEntity.ok().build();
    }
}
