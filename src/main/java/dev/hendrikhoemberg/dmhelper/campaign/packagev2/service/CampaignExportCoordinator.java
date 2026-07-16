package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CampaignExportCoordinator {

    private final CampaignService campaignService;
    private final CampaignPackageKeyService keyService;
    private final CampaignSectionRegistry registry;
    private final CampaignCatalogService catalogService;

    public CampaignExportCoordinator(CampaignService campaignService,
                                      CampaignPackageKeyService keyService,
                                      CampaignSectionRegistry registry,
                                      CampaignCatalogService catalogService) {
        this.campaignService = campaignService;
        this.keyService = keyService;
        this.registry = registry;
        this.catalogService = catalogService;
    }

    public CampaignPackageArtifact export(UUID campaignId) {
        return export(campaignId, CampaignExportOptions.complete());
    }

    public CampaignPackageArtifact export(UUID campaignId, CampaignExportOptions options) {
        var campaign = campaignService.findById(campaignId);
        var assetCollector = new CampaignAssetCollector();
        var context = new CampaignExportContext(campaignId, campaign, options, keyService, assetCollector);
        var assembler = new CampaignManifestAssembler();

        for (var exporter : registry.exporters()) {
            exporter.exportSection(context, assembler);
        }

        assembler.assets(assetCollector.assetDescriptors());

        var catalog = catalogService.snapshot();
        var metadata = new CampaignManifestV2.Metadata(
                context.key(dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.CAMPAIGN,
                        campaignId, campaign.getName()),
                Instant.now(), "DMHelper/0.0.1-SNAPSHOT",
                catalog.version(), catalog.sha256(),
                options.exclusions());
        var manifest = assembler.build(metadata);

        Map<String, InputStreamSource> sources = new LinkedHashMap<>();
        context.assets().assetSources().forEach((key, data) ->
                sources.put(key, new ByteArrayResource(data)));

        String filename = sanitizeFilename(campaign.getName());
        boolean hasAssets = !manifest.assets().isEmpty();
        return new CampaignPackageArtifact(
                filename + (hasAssets ? ".dmcampaign" : ".dmcampaign.json"),
                hasAssets ? MediaType.valueOf("application/vnd.dmhelper.campaign+zip")
                        : MediaType.APPLICATION_JSON,
                manifest, Map.copyOf(sources));
    }

    private static String sanitizeFilename(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
        return sanitized.isBlank() ? "campaign" : sanitized;
    }
}
