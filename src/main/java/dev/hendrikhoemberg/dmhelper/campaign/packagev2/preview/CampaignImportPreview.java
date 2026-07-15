package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignImportPreview(
        UUID previewId,
        String status,
        int sourceFormatVersion,
        int targetFormatVersion,
        CampaignEntityCounts counts,
        long packageSizeBytes,
        long installedSizeBytes,
        int provenanceEntries,
        int missingProvenanceEntries,
        List<String> exclusions,
        List<String> migrations,
        List<CampaignImportProblem> problems,
        Instant expiresAt
) {}
