package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import java.util.List;
import java.util.Optional;

public record CampaignValidationResult(
        Optional<CampaignExportDto> campaign,
        List<CampaignImportProblem> problems
) {
    public CampaignValidationResult {
        campaign = campaign == null ? Optional.empty() : campaign;
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public boolean valid() {
        return problems.stream().noneMatch(p -> p.severity() == ImportSeverity.ERROR);
    }

    public CampaignExportDto requireImportable() {
        if (!valid() || campaign.isEmpty()) {
            String summary = problems.stream()
                    .filter(p -> p.severity() == ImportSeverity.ERROR)
                    .map(p -> p.path() + ": " + p.message())
                    .findFirst()
                    .orElse("Campaign import did not produce a validated document");
            throw new IllegalArgumentException(summary);
        }
        return campaign.get();
    }
}
