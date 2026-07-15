package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class V2CompatibilityAdapter {

    public PreparedV1Import prepare(PendingCampaignImport pending) {
        CampaignManifestV2 v2 = pending.result().manifest();
        Map<String, PreparedV1Import.ImportedKey> keys = new HashMap<>();
        Map<String, HandoutImportSource> handoutSources = new HashMap<>();

        if (v2.campaign() != null && v2.campaign().key() != null) {
            keys.put("/campaign", new PreparedV1Import.ImportedKey(CampaignContentType.CAMPAIGN, v2.campaign().key()));
        }

        CampaignExportDto dto = new CampaignExportDto(1,
                new CampaignExportDto.CampaignDto(
                        v2.campaign() != null ? v2.campaign().name() : "",
                        v2.campaign() != null ? v2.campaign().description() : null),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of());

        return new PreparedV1Import(dto, keys, handoutSources);
    }
}
