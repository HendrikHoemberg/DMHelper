package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@Controller
public class SessionController {

    private final GameMapService gameMapService;

    public SessionController(GameMapService gameMapService) {
        this.gameMapService = gameMapService;
    }

    @GetMapping("/campaigns/{campaignId}/session")
    public String runSession(@PathVariable UUID campaignId) {
        List<GameMap> maps = gameMapService.findByCampaignId(campaignId);
        if (maps.isEmpty()) {
            return "redirect:/campaigns/" + campaignId + "/maps";
        }
        return "redirect:/campaigns/" + campaignId + "/maps/" + maps.get(0).getId() + "/play";
    }
}
