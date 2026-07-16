package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

public interface CampaignSectionExporter {
    String sectionName();
    int order();
    void exportSection(CampaignExportContext context, CampaignManifestAssembler target);
}
