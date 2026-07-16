package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;

import java.util.UUID;

public record CampaignExportContext(
        UUID campaignId,
        Campaign campaign,
        CampaignExportOptions options,
        CampaignPackageKeyService keyService,
        CampaignAssetCollector assets
) {
    public String key(CampaignContentType type, UUID entityId, String displayName) {
        return keyService.getOrCreate(campaignId, type, entityId, displayName);
    }

    public ContentReference packageRef(CampaignContentType type, UUID entityId, String displayName) {
        return ContentReference.packageRef(type, key(type, entityId, displayName));
    }

    public ContentReference catalogRef(CampaignContentType type, String sourceKey) {
        return ContentReference.catalogRef(type, null, sourceKey);
    }
}
