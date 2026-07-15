package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;

public interface CampaignFormatMigration {
    int sourceVersion();
    CampaignPackageValidationResult migrate(StagedCampaignPackage source,
                                             CampaignImportValidator v1Validator);
}
