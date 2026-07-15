package dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignFormatMigration;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FormatMigrationRegistry {

    private final Map<Integer, CampaignFormatMigration> migrations;

    public FormatMigrationRegistry(List<CampaignFormatMigration> migrationList) {
        var builder = new java.util.TreeMap<Integer, CampaignFormatMigration>();
        for (var m : migrationList) {
            builder.put(m.sourceVersion(), m);
        }
        this.migrations = java.util.Collections.unmodifiableMap(builder);
    }

    public CampaignPackageValidationResult toCurrent(StagedCampaignPackage source,
                                                       CampaignImportValidator v1Validator) {
        if (source.containerKind() == StagedCampaignPackage.ContainerKind.V2_JSON
                || source.containerKind() == StagedCampaignPackage.ContainerKind.V2_ZIP) {
            return null;
        }
        var migration = migrations.get(1);
        if (migration == null) {
            return new CampaignPackageValidationResult(source, null, 1, Map.of(),
                    List.of(new CampaignImportProblem(ImportSeverity.ERROR,
                            "UNSUPPORTED_FORMAT_VERSION", "",
                            "No migration for version 1", null)),
                    List.of());
        }
        return migration.migrate(source, v1Validator);
    }
}
