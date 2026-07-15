package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;

import java.util.Map;

public record PreparedV1Import(
        CampaignExportDto dto,
        Map<String, ImportedKey> importedKeysByV1Pointer,
        Map<String, HandoutImportSource> externalHandoutsByV1Pointer
) {
    public record ImportedKey(CampaignContentType type, String key) {}
}
