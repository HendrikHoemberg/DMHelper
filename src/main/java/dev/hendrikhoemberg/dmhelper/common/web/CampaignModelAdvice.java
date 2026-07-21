package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@ControllerAdvice(annotations = Controller.class)
public class CampaignModelAdvice {

    private final CampaignRepository campaigns;

    public CampaignModelAdvice(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable(name = "campaignId", required = false) UUID campaignId,
                            Model model) {
        if (campaignId == null) {
            return;
        }
        campaigns.findById(campaignId).ifPresent(c -> {
            model.addAttribute("campaign", c);
            model.addAttribute("campaignId", campaignId);
        });
    }
}
