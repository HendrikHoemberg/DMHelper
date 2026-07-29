package dev.hendrikhoemberg.dmhelper.handout.web;

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
    public List<HandoutViewDto> list(@PathVariable UUID campaignId) {
        return handoutService.findByCampaignId(campaignId).stream()
                .map(HandoutViewDto::from)
                .toList();
    }

    @GetMapping("/handouts/{id}")
    public HandoutViewDto get(@PathVariable UUID id) {
        return HandoutViewDto.from(handoutService.findById(id));
    }

    @PutMapping("/handouts/{id}/present")
    public HandoutViewDto setPresented(@PathVariable UUID id, @RequestParam boolean presented) {
        return HandoutViewDto.from(handoutService.setPresented(id, presented));
    }
}
