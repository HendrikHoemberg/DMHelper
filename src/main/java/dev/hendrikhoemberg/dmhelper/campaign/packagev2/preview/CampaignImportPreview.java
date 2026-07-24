package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport;
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
        List<CampaignExportExclusion> exclusions,
        List<String> migrations,
        List<CampaignImportProblem> problems,
        Instant expiresAt,
        CampaignReadinessReport readiness
) {
    public CampaignImportPreview(UUID previewId, String status, int sourceFormatVersion,
            int targetFormatVersion, CampaignEntityCounts counts, long packageSizeBytes,
            long installedSizeBytes, int provenanceEntries, int missingProvenanceEntries,
            List<CampaignExportExclusion> exclusions, List<String> migrations,
            List<CampaignImportProblem> problems, Instant expiresAt) {
        this(previewId, status, sourceFormatVersion, targetFormatVersion, counts, packageSizeBytes,
                installedSizeBytes, provenanceEntries, missingProvenanceEntries, exclusions,
                migrations, problems, expiresAt,
                new CampaignReadinessReport(List.of()));
    }
}
