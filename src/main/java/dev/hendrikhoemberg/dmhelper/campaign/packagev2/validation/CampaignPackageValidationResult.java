package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CampaignPackageValidationResult(
        StagedCampaignPackage stagedPackage,
        CampaignManifestV2 manifest,
        int sourceFormatVersion,
        Map<String, Path> assetsByKey,
        List<CampaignImportProblem> problems,
        List<String> migrations
) {
    public boolean valid() {
        return problems.stream().noneMatch(p ->
                p.severity() == dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity.ERROR);
    }
}
