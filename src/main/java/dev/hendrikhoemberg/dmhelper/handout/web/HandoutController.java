package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout.SafetyClassification;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/handouts")
public class HandoutController {

    private final CampaignService campaignService;
    private final HandoutService handoutService;

    public HandoutController(CampaignService campaignService, HandoutService handoutService) {
        this.campaignService = campaignService;
        this.handoutService = handoutService;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignService.findById(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("handouts", handoutService.findByCampaignId(campaignId));
        return "handout/list";
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String create(@PathVariable UUID campaignId,
                         @RequestParam("file") MultipartFile file,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String tags) throws IOException {
        handoutService.create(campaignId, title, tags, file);
        return "redirect:/campaigns/" + campaignId + "/handouts";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId,
                         @PathVariable UUID id,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String tags) {
        handoutService.update(id, title, tags);
        return "redirect:/campaigns/" + campaignId + "/handouts";
    }

    @PutMapping("/{id}/present")
    public String setPresented(@PathVariable UUID campaignId,
                               @PathVariable UUID id,
                               @RequestParam boolean presented,
                               Model model) {
        Handout handout = handoutService.setPresented(id, presented);
        model.addAttribute("handout", handout);
        return "handout/_card :: card";
    }

    @PutMapping("/{id}/classification")
    public String classify(@PathVariable UUID campaignId,
                           @PathVariable UUID id,
                           @RequestParam("classification") SafetyClassification classification,
                           Model model) {
        Handout handout = handoutService.classify(campaignId, id, classification);
        model.addAttribute("handout", handout);
        return "handout/_card :: card";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        handoutService.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/handouts")
                .build();
    }

    @GetMapping("/{id}/present")
    public String presentOverlay(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("handout", handoutService.findById(id));
        model.addAttribute("campaignId", campaignId);
        return "handout/_present-overlay :: overlay";
    }
}
