package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SemanticValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignExportCoordinatorTest {

    @Test
    void rejectsAnAssembledManifestThatViolatesTheSchema() {
        UUID campaignId = UUID.randomUUID();
        var campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Valid campaign name");

        var campaignService = mock(CampaignService.class);
        when(campaignService.findById(campaignId)).thenReturn(campaign);
        var keyService = mock(CampaignPackageKeyService.class);
        when(keyService.getOrCreate(any(), any(), any(), any())).thenReturn("campaign-key");
        var catalog = mock(CampaignCatalogService.class);
        when(catalog.snapshot()).thenReturn(new CatalogSnapshot("test", "abc123", List.of()));
        var semantics = mock(CampaignManifestV2SemanticValidator.class);
        when(semantics.validate(any())).thenReturn(List.of());

        var registry = new CampaignSectionRegistry(List.of(new InvalidCampaignExporter()), List.of());
        var coordinator = new CampaignExportCoordinator(
                campaignService, keyService, registry, catalog,
                new CampaignManifestV2SchemaValidator(), semantics);

        assertThatThrownBy(() -> coordinator.export(campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/campaign/key");
    }

    private static class InvalidCampaignExporter implements CampaignSectionExporter {
        @Override public String sectionName() { return "invalid"; }
        @Override public int order() { return 1; }

        @Override
        public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
            target.campaign(new CampaignManifestV2.CampaignDto(
                    "INVALID KEY", "Campaign", null, java.time.Instant.EPOCH, null, null));
            target.party(List.of());
            target.customStatBlocks(List.of());
            target.customSpells(List.of());
            target.customConditions(List.of());
            target.customRules(List.of());
            target.customEquipment(List.of());
            target.customMagicItems(List.of());
            target.customClasses(List.of());
            target.customSpecies(List.of());
            target.customBackgrounds(List.of());
            target.customFeats(List.of());
            target.handouts(List.of());
            target.maps(List.of());
            target.encounters(List.of());
            target.notes(List.of());
            target.quickNotes(List.of());
            target.assignments(List.of());
            target.ledgerEntries(List.of());
            target.timelineEvents(List.of());
            target.adventures(List.of());
            target.diceRolls(List.of());
        }
    }
}
