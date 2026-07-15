package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.springframework.core.io.InputStreamSource;

import java.util.Map;

public record CampaignPackageWriteRequest(
        String filename,
        CampaignManifestV2 manifest,
        Map<String, InputStreamSource> assetSources
) {}
