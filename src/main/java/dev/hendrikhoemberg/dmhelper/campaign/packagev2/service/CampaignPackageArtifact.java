package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriteRequest;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.MediaType;

import java.util.Map;

public record CampaignPackageArtifact(
        String filename,
        MediaType mediaType,
        CampaignManifestV2 manifest,
        Map<String, InputStreamSource> assetSources
) {
    public boolean zipped() { return !assetSources.isEmpty(); }
    public CampaignPackageWriteRequest writeRequest() {
        return new CampaignPackageWriteRequest(filename, manifest, assetSources);
    }
}
