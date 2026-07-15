package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Service
@Transactional
public class CampaignImportCoordinator {

    private final CampaignImportPreviewStore previewStore;
    private final CampaignService campaignService;

    public CampaignImportCoordinator(CampaignImportPreviewStore previewStore,
                                      CampaignService campaignService) {
        this.previewStore = previewStore;
        this.campaignService = campaignService;
    }

    public Campaign confirm(UUID previewId, boolean acceptWarnings) {
        PendingCampaignImport pending = previewStore.require(previewId);

        boolean hasWarnings = pending.result().problems().stream()
                .anyMatch(p -> p.severity() == dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity.WARNING);
        if (hasWarnings && !acceptWarnings) {
            throw new IllegalArgumentException("Warnings must be accepted to proceed");
        }

        String json = new JsonMapper().valueToTree(pending.result().manifest()).toString();
        Campaign campaign = campaignService.importFromJson(json);

        previewStore.discard(previewId);
        return campaign;
    }
}
