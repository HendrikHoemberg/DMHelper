package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
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
    private final V2CompatibilityAdapter compatibilityAdapter;
    private final CampaignPackageKeyService packageKeyService;

    public CampaignImportCoordinator(CampaignImportPreviewStore previewStore,
                                      CampaignService campaignService,
                                      V2CompatibilityAdapter compatibilityAdapter,
                                      CampaignPackageKeyService packageKeyService) {
        this.previewStore = previewStore;
        this.campaignService = campaignService;
        this.compatibilityAdapter = compatibilityAdapter;
        this.packageKeyService = packageKeyService;
    }

    public Campaign confirm(UUID previewId, boolean acceptWarnings) {
        PendingCampaignImport pending = previewStore.require(previewId);

        boolean hasWarnings = pending.result().problems().stream()
                .anyMatch(p -> p.severity() == dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity.WARNING);
        if (hasWarnings && !acceptWarnings) {
            throw new IllegalArgumentException("Warnings must be accepted to proceed");
        }

        revalidateAssets(pending);
        PreparedV1Import prepared = compatibilityAdapter.prepare(pending);
        CampaignPersistenceReceipt receipt = campaignService.importValidated(
                prepared.dto(), prepared.externalHandoutsByV1Pointer());
        prepared.importedKeysByV1Pointer().forEach((pointer, importedKey) ->
                packageKeyService.bindImported(receipt.campaign().getId(), importedKey.type(),
                        receipt.requireEntityId(pointer), importedKey.key()));

        discardAfterCommit(previewId);
        return receipt.campaign();
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
