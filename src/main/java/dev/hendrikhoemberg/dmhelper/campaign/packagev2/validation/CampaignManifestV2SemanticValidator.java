package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;

import java.util.List;

public class CampaignManifestV2SemanticValidator {

    public List<CampaignImportProblem> validate(CampaignManifestV2 manifest) {
        return List.of();
    }
}
