package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class HandoutApiController {

    private final HandoutService handoutService;

    public HandoutApiController(HandoutService handoutService) {
        this.handoutService = handoutService;
    }

    @GetMapping("/campaigns/{campaignId}/handouts")
    public List<Handout> list(@PathVariable UUID campaignId) {
        return handoutService.findByCampaignId(campaignId);
    }

    @GetMapping("/handouts/{id}")
    public Handout get(@PathVariable UUID id) {
        return handoutService.findById(id);
    }

    @PutMapping("/handouts/{id}/present")
    public Handout setPresented(@PathVariable UUID id, @RequestParam boolean presented) {
        return handoutService.setPresented(id, presented);
    }

    @PutMapping("/handouts/{id}/dm-only")
    public Handout setDmOnly(@PathVariable UUID id, @RequestParam boolean dmOnly) {
        return handoutService.setDmOnly(id, dmOnly);
    }
}
