package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;

public interface CampaignSectionImporter {
    String sectionName();
    int order();
    void importSection(CampaignManifestV2 source, CampaignImportContext context);
}
