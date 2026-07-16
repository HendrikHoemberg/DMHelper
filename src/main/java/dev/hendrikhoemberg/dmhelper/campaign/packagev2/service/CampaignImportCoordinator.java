package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@Transactional
public class CampaignImportCoordinator {

    private final CampaignImportPreviewStore previewStore;
    private final CampaignService campaignService;
    private final CampaignPackageKeyService packageKeyService;
    private final CampaignSectionRegistry registry;

    public CampaignImportCoordinator(CampaignImportPreviewStore previewStore,
                                      CampaignService campaignService,
                                      CampaignPackageKeyService packageKeyService,
                                      CampaignSectionRegistry registry) {
        this.previewStore = previewStore;
        this.campaignService = campaignService;
        this.packageKeyService = packageKeyService;
        this.registry = registry;
    }

    public Campaign confirm(UUID previewId, boolean acceptWarnings) {
        PendingCampaignImport pending = previewStore.require(previewId);

        boolean hasWarnings = pending.result().problems().stream()
                .anyMatch(p -> p.severity() == ImportSeverity.WARNING);
        if (hasWarnings && !acceptWarnings) {
            throw new IllegalArgumentException("Warnings must be accepted to proceed");
        }

        revalidateAssets(pending);

        var manifest = pending.result().manifest();
        Campaign campaign = campaignService.create(
                manifest.campaign().name(), manifest.campaign().description());

        var context = new CampaignImportContext(campaign.getId(), packageKeyService, pending);
        context.setCampaign(campaign);

        for (var importer : registry.importers()) {
            importer.importSection(manifest, context);
        }

        context.runDeferred();

        discardAfterCommit(previewId);
        return context.campaign();
    }

    private static void revalidateAssets(PendingCampaignImport pending) {
        for (var descriptor : pending.result().manifest().assets()) {
            var path = pending.result().assetsByKey().get(descriptor.key());
            if (path == null) {
                throw new IllegalStateException("Validated campaign asset is missing");
            }
            var problem = AssetSignatureValidator.validate(path, descriptor);
            if (problem != null) {
                throw new IllegalStateException("Validated campaign asset changed before confirmation: "
                        + problem.code());
            }
        }
    }

    private void discardAfterCommit(UUID previewId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            previewStore.discard(previewId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                previewStore.discard(previewId);
            }
        });
    }
}
