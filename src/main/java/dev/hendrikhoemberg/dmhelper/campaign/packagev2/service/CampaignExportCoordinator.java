package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration.LegacyV1ToV2Migration;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CampaignExportCoordinator {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CampaignService campaignService;
    private final CampaignPackageKeyService keyService;
    private final LegacyV1ToV2Migration migration;

    public CampaignExportCoordinator(CampaignService campaignService,
                                      CampaignPackageKeyService keyService,
                                      LegacyV1ToV2Migration migration) {
        this.campaignService = campaignService;
        this.keyService = keyService;
        this.migration = migration;
    }

    public CampaignPackageArtifact export(UUID campaignId) {
        var campaign = campaignService.findById(campaignId);
        String v1Json = campaignService.exportToJson(campaignId);
        CampaignExportDto v1 = MAPPER.readValue(v1Json, CampaignExportDto.class);
        Map<String, UUID> entityIds = campaignService.exportEntityIds(campaignId);

        java.nio.file.Path stagingDirectory;
        try {
            stagingDirectory = Files.createTempDirectory("dmhelper-package-export-");
        } catch (IOException e) {
            throw new IllegalStateException("Could not stage campaign export", e);
        }

        try (var staged = new StagedCampaignPackage(stagingDirectory,
                stagingDirectory.resolve("manifest.json"), Map.of(), v1Json.length(), v1Json.length(),
                StagedCampaignPackage.ContainerKind.V1_JSON)) {
            var result = migration.convert(staged, v1, v1Json, (type, pointer, displayName, generatedKey) -> {
                UUID entityId = entityIds.get(pointer);
                if (entityId == null) {
                    throw new IllegalStateException("Export entity is missing for " + pointer);
                }
                return keyService.getOrCreate(campaignId, type, entityId, displayName);
            });

            CampaignManifestV2 converted = result.manifest();
            var metadata = new CampaignManifestV2.Metadata(
                    converted.campaign().key(), Instant.now(), "DMHelper/0.0.1-SNAPSHOT",
                    converted.metadata().catalogVersion(), converted.metadata().catalogSha256(),
                    List.of());
            var manifest = new CampaignManifestV2(converted.formatVersion(), metadata, converted.campaign(),
                    converted.assets(), converted.party(), converted.customStatBlocks(), converted.handouts(),
                    converted.maps(), converted.encounters(), converted.notes(), converted.quickNotes(),
                    converted.assignments(), converted.ledgerEntries(), converted.timelineEvents(),
                    converted.adventures(), converted.diceRolls());

            Map<String, InputStreamSource> sources = new LinkedHashMap<>();
            result.assetsByKey().forEach((key, path) -> {
                try {
                    sources.put(key, new ByteArrayResource(Files.readAllBytes(path)));
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read validated export asset", e);
                }
            });

            String filename = sanitizeFilename(campaign.getName());
            boolean hasAssets = !manifest.assets().isEmpty();
            return new CampaignPackageArtifact(
                    filename + (hasAssets ? ".dmcampaign" : ".dmcampaign.json"),
                    hasAssets ? MediaType.valueOf("application/vnd.dmhelper.campaign+zip")
                            : MediaType.APPLICATION_JSON,
                    manifest, Map.copyOf(sources));
        } catch (IOException e) {
            throw new IllegalStateException("Could not migrate campaign export", e);
        }
    }

    private static String sanitizeFilename(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
        return sanitized.isBlank() ? "campaign" : sanitized;
    }
}
