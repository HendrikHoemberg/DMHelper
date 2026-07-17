package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration.FormatMigrationRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CampaignPackageValidationPipeline {

    private final CampaignManifestV2SchemaValidator schema;
    private final CampaignManifestV2SemanticValidator semantics;
    private final CampaignCatalogService catalog;
    private final FormatMigrationRegistry migrations;
    private final CampaignImportValidator v1Validator;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public CampaignPackageValidationPipeline(CampaignManifestV2SchemaValidator schema,
                                             CampaignManifestV2SemanticValidator semantics,
                                             CampaignCatalogService catalog,
                                             FormatMigrationRegistry migrations,
                                             CampaignImportValidator v1Validator) {
        this.schema = schema;
        this.semantics = semantics;
        this.catalog = catalog;
        this.migrations = migrations;
        this.v1Validator = v1Validator;
    }

    public CampaignPackageValidationResult validate(StagedCampaignPackage staged) {
        try {
            CampaignManifestV2 manifest;
            int sourceVersion;
            Map<String, Path> migratedAssets = Map.of();
            List<CampaignImportProblem> problems = new ArrayList<>();
            List<String> migrationLabels = List.of();
            if (staged.containerKind() == StagedCampaignPackage.ContainerKind.V1_JSON) {
                var migrated = migrations.toCurrent(staged, v1Validator);
                if (migrated == null) return unsupported(staged, 1);
                if (!migrated.valid() || migrated.manifest() == null) return migrated;
                manifest = migrated.manifest();
                sourceVersion = migrated.sourceFormatVersion();
                migratedAssets = migrated.assetsByKey();
                problems.addAll(migrated.problems());
                migrationLabels = migrated.migrations();
            } else {
                String json = Files.readString(staged.manifestPath());
                List<CampaignImportProblem> schemaProblems = schema.validate(json);
                if (!schemaProblems.isEmpty()) return new CampaignPackageValidationResult(staged, null, 2,
                        Map.of(), schemaProblems, List.of());
                manifest = mapper.readValue(json, CampaignManifestV2.class);
                sourceVersion = manifest.formatVersion();
            }

            List<CampaignImportProblem> canonicalSchema = schema.validate(mapper.writeValueAsString(manifest));
            if (!canonicalSchema.isEmpty()) return new CampaignPackageValidationResult(staged, null, sourceVersion,
                    migratedAssets, canonicalSchema, migrationLabels);
            problems.addAll(semantics.validate(manifest));
            if (manifest.metadata() != null && !catalog.snapshot().sha256().equals(manifest.metadata().catalogSha256())) {
                problems.add(new CampaignImportProblem(ImportSeverity.WARNING, ImportProblemCodes.CATALOG_SNAPSHOT_MISMATCH,
                        "/metadata/catalogSha256", "Catalog snapshot differs from this DMHelper installation", null));
            }

            Map<String, Path> assetsByKey = new LinkedHashMap<>(migratedAssets);
            for (int i = 0; i < (manifest.assets() == null ? 0 : manifest.assets().size()); i++) {
                var descriptor = manifest.assets().get(i);
                Path file = assetsByKey.get(descriptor.key());
                if (file == null) file = staged.assetsByNormalizedPath().get(descriptor.path());
                if (file == null) {
                    problems.add(new CampaignImportProblem(ImportSeverity.ERROR, ImportProblemCodes.ASSET_NOT_FOUND, "/assets/" + i,
                            "A declared asset is missing from the package", null));
                    continue;
                }
                assetsByKey.put(descriptor.key(), file);
                CampaignImportProblem validation = AssetSignatureValidator.validate(file, descriptor);
                if (validation != null) problems.add(new CampaignImportProblem(validation.severity(), validation.code(),
                        "/assets/" + i, validation.message(), validation.suggestion()));
            }
            return new CampaignPackageValidationResult(staged, manifest, sourceVersion, assetsByKey, problems, migrationLabels);
        } catch (Exception e) {
            return new CampaignPackageValidationResult(staged, null, 2, Map.of(),
                    List.of(new CampaignImportProblem(ImportSeverity.ERROR, ImportProblemCodes.VALIDATION_ERROR, "",
                            "Campaign package validation failed", null)), List.of());
        }
    }

    private static CampaignPackageValidationResult unsupported(StagedCampaignPackage staged, int version) {
        return new CampaignPackageValidationResult(staged, null, version, Map.of(),
                List.of(new CampaignImportProblem(ImportSeverity.ERROR, ImportProblemCodes.UNSUPPORTED_FORMAT_VERSION, "",
                        "No migration path exists for this format version", null)), List.of());
    }
}
