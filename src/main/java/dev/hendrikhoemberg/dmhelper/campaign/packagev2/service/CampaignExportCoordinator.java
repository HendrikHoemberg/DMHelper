package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CampaignExportCoordinator {

    private final CampaignService campaignService;
    private final CampaignCatalogService catalogService;
    private final CampaignPackageKeyService keyService;

    public CampaignExportCoordinator(CampaignService campaignService,
                                      CampaignCatalogService catalogService,
                                      CampaignPackageKeyService keyService) {
        this.campaignService = campaignService;
        this.catalogService = catalogService;
        this.keyService = keyService;
    }

    public CampaignPackageArtifact export(UUID campaignId) {
        var campaign = campaignService.findById(campaignId);
        var catalog = catalogService.snapshot();

        var metadata = new CampaignManifestV2.Metadata(
                "export-" + campaignId.toString().substring(0, 12),
                Instant.now(), "DMHelper/0.0.1-SNAPSHOT",
                catalog.version(), catalog.sha256(),
                List.of("CAMPAIGN_SETTINGS", "CURRENT_SCENE", "PARTY_CURRENT_HP",
                        "HANDOUT_PRESENTATION_STATE", "COMBAT_LOG", "DICE_HISTORY",
                        "CALENDAR_CONFIGURATION", "CALENDAR_CURRENT_DATE",
                        "CUSTOM_COMPENDIUM_NON_STATBLOCK", "STRUCTURED_SCENE_TRANSITIONS",
                        "QUESTS_AND_OBJECTIVES"));

        var campaignDto = new CampaignManifestV2.CampaignDto(
                keyService.getOrCreate(campaignId, CampaignContentType.CAMPAIGN, campaign.getId(), campaign.getName()),
                campaign.getName(), campaign.getDescription());

        List<AssetDescriptor> noAssets = List.of();
        List<CampaignManifestV2.PartyMemberDto> noParty = List.of();
        List<CampaignManifestV2.StatBlockDto> noStats = List.of();
        List<CampaignManifestV2.HandoutDto> noHandouts = List.of();
        List<CampaignManifestV2.MapDto> noMaps = List.of();
        List<CampaignManifestV2.EncounterDto> noEncounters = List.of();
        List<CampaignManifestV2.NoteDto> noNotes = List.of();
        List<CampaignManifestV2.QuickNoteDto> noQn = List.of();
        List<CampaignManifestV2.AssignmentDto> noAssign = List.of();
        List<CampaignManifestV2.LedgerEntryDto> noLedger = List.of();
        List<CampaignManifestV2.TimelineEventDto> noTimeline = List.of();
        List<CampaignManifestV2.AdventureDto> noAdv = List.of();

        var manifest = new CampaignManifestV2(2, metadata, campaignDto,
                noAssets, noParty, noStats, noHandouts, noMaps,
                noEncounters, noNotes, noQn, noAssign, noLedger, noTimeline, noAdv);

        String filename = sanitizeFilename(campaign.getName());
        boolean hasAssets = !manifest.assets().isEmpty();
        String fullFilename = hasAssets ? filename + ".dmcampaign" : filename + ".dmcampaign.json";
        MediaType mediaType = hasAssets
                ? MediaType.valueOf("application/vnd.dmhelper.campaign+zip")
                : MediaType.APPLICATION_JSON;

        return new CampaignPackageArtifact(fullFilename, mediaType, manifest, Map.of());
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }
}
