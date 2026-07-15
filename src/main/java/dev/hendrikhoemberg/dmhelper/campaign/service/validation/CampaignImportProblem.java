package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

public record CampaignImportProblem(
        ImportSeverity severity,
        String code,
        String path,
        String message,
        String suggestion
) {
    public CampaignImportProblem {
        path = path == null || path.isBlank() ? "/" : path;
    }
}
