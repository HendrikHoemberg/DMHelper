package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration.FormatMigrationRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CampaignPackageValidationPipeline {

    private final CampaignManifestV2SchemaValidator schemaValidator;
    private final CampaignCatalogService catalogService;
    private final FormatMigrationRegistry migrationRegistry;
    private final CampaignImportValidator v1Validator;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public CampaignPackageValidationPipeline(CampaignManifestV2SchemaValidator schemaValidator,
                                              CampaignCatalogService catalogService,
                                              FormatMigrationRegistry migrationRegistry,
                                              CampaignImportValidator v1Validator) {
        this.schemaValidator = schemaValidator;
        this.catalogService = catalogService;
        this.migrationRegistry = migrationRegistry;
        this.v1Validator = v1Validator;
    }

    public CampaignPackageValidationResult validate(StagedCampaignPackage staged) {
        try {
            if (staged.containerKind() == StagedCampaignPackage.ContainerKind.V1_JSON) {
                var result = migrationRegistry.toCurrent(staged, v1Validator);
                if (result == null || !result.valid()) {
                    return result != null ? result : new CampaignPackageValidationResult(
                            staged, null, 1, Map.of(),
                            List.of(new CampaignImportProblem(ImportSeverity.ERROR,
                                    "UNSUPPORTED_FORMAT_VERSION", "", "No migration path", null)),
                            List.of());
                }
                CampaignManifestV2 manifest = result.manifest();
                var problems = schemaValidator.validate(mapper.writeValueAsString(manifest));
                if (!problems.isEmpty()) {
                    return new CampaignPackageValidationResult(staged, null, result.sourceFormatVersion(),
                            result.assetsByKey(), problems, result.migrations());
                }
                return result;
            }

            String json = Files.readString(staged.manifestPath());
            var schemaProblems = schemaValidator.validate(json);
            if (!schemaProblems.isEmpty()) {
                return new CampaignPackageValidationResult(staged, null, 2, Map.of(),
                        schemaProblems, List.of());
            }

            CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);

            List<CampaignImportProblem> semanticProblems = validateSemantics(manifest, staged);

            return new CampaignPackageValidationResult(staged, manifest, 2, Map.of(),
                    semanticProblems, List.of());
        } catch (Exception e) {
            return new CampaignPackageValidationResult(staged, null, 2, Map.of(),
                    List.of(new CampaignImportProblem(ImportSeverity.ERROR, "VALIDATION_ERROR", "",
                            e.getMessage(), null)),
                    List.of());
        }
    }

    private List<CampaignImportProblem> validateSemantics(CampaignManifestV2 manifest,
                                                           StagedCampaignPackage staged) {
        List<CampaignImportProblem> problems = new ArrayList<>();
        Map<String, String> keysByType = new HashMap<>();

        checkKeyUniqueness(manifest, keysByType, problems);
        checkCatalogReferences(manifest, problems);
        checkAssetDescriptors(manifest, staged, problems);

        return problems;
    }

    private void checkKeyUniqueness(CampaignManifestV2 m, Map<String, String> keys, List<CampaignImportProblem> problems) {
        if (m.campaign() != null && m.campaign().key() != null) {
            String fullKey = "CAMPAIGN:" + m.campaign().key();
            if (keys.containsKey(fullKey)) {
                problems.add(problem(ImportSeverity.ERROR, "DUPLICATE_KEY", "/campaign/key",
                        "Duplicate key: " + m.campaign().key()));
            } else {
                keys.put(fullKey, m.campaign().key());
            }
        }
    }

    private void checkCatalogReferences(CampaignManifestV2 m, List<CampaignImportProblem> problems) {
        String catalogHash = catalogService.snapshot().sha256();
        if (m.metadata() != null && m.metadata().catalogSha256() != null
                && !m.metadata().catalogSha256().equals(catalogHash)) {
            problems.add(problem(ImportSeverity.WARNING, "CATALOG_SNAPSHOT_MISMATCH",
                    "/metadata/catalogSha256",
                    "Catalog snapshot hash differs from running service"));
        }
    }

    private void checkAssetDescriptors(CampaignManifestV2 m, StagedCampaignPackage staged,
                                        List<CampaignImportProblem> problems) {
        if (m.assets() == null) return;
        for (int i = 0; i < m.assets().size(); i++) {
            var d = m.assets().get(i);
            String assetPath = getAssetPath(d);
            Path file = staged.assetsByNormalizedPath().get(assetPath);
            if (file == null) {
                problems.add(problem(ImportSeverity.ERROR, "ASSET_NOT_FOUND",
                        "/assets/" + i, "Staged asset not found: " + assetPath));
                continue;
            }
            var result = AssetSignatureValidator.validate(file, d);
            if (result != null) problems.add(result);
        }
    }

    private static String getAssetPath(dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor d) {
        String path = d.path();
        if (path == null) return null;
        return path.replaceFirst("^assets/", "");
    }

    private static CampaignImportProblem problem(ImportSeverity severity, String code, String path, String message) {
        return new CampaignImportProblem(severity, code, path, message, null);
    }
}
