package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;

import java.util.UUID;
import java.time.Instant;

public record PendingCampaignImport(
        UUID previewId,
        CampaignPackageValidationResult result,
        StagedCampaignPackage staging,
        Instant expiresAt
) {}
