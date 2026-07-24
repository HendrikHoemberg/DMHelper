package dev.hendrikhoemberg.dmhelper.campaign.readiness.web;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.*;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/readiness")
public class ReadinessController {

    private final CampaignReadinessFacade facade;
    private final HandoutRepository handouts;
    private final ReadinessAcknowledgementRepository acknowledgements;
    private final ReadinessRepairService repairService;

    public ReadinessController(CampaignReadinessFacade facade, HandoutRepository handouts,
                               ReadinessAcknowledgementRepository acknowledgements,
                               ReadinessRepairService repairService) {
        this.facade = facade;
        this.handouts = handouts;
        this.acknowledgements = acknowledgements;
        this.repairService = repairService;
    }

    @PostMapping("/assets/{handoutId}/kind")
    @Transactional
    public String classifyAsset(@PathVariable UUID campaignId, @PathVariable UUID handoutId,
                                @RequestParam String kind, Model model) {
        Handout handout = handouts.findById(handoutId).orElseThrow();
        if (!handout.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Handout does not belong to campaign");
        }
        handout.setAssetKind(Handout.AssetKind.valueOf(kind));
        handouts.save(handout);
        return renderFragment(campaignId, model);
    }

    @PostMapping("/accept")
    @Transactional
    public String accept(@PathVariable UUID campaignId, @RequestParam String itemKey, Model model) {
        if (!acknowledgements.existsByCampaignIdAndItemKey(campaignId, itemKey)) {
            var ack = new ReadinessAcknowledgement();
            ack.setCampaignId(campaignId);
            ack.setItemKey(itemKey);
            ack.setAcceptedAt(Instant.now());
            acknowledgements.save(ack);
        }
        return renderFragment(campaignId, model);
    }

    @DeleteMapping("/accept")
    @Transactional
    public String unaccept(@PathVariable UUID campaignId, @RequestParam String itemKey, Model model) {
        acknowledgements.deleteByCampaignIdAndItemKey(campaignId, itemKey);
        return renderFragment(campaignId, model);
    }

    private String renderFragment(UUID campaignId, Model model) {
        model.addAttribute("readiness", facade.reportForCampaign(campaignId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("repairService", repairService);
        return "campaigns/_readiness :: readiness";
    }
}
