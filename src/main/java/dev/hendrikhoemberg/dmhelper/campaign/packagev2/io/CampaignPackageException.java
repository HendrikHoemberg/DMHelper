package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;

public final class CampaignPackageException extends IllegalArgumentException {

    private final CampaignImportProblem problem;

    public CampaignPackageException(CampaignImportProblem problem) {
        super(problem.message());
        this.problem = problem;
    }

    public CampaignImportProblem problem() {
        return problem;
    }
}
