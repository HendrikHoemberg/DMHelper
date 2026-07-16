package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CampaignSemanticSnapshotService {

    private final CampaignExportCoordinator exporter;

    public CampaignSemanticSnapshotService(CampaignExportCoordinator exporter) {
        this.exporter = exporter;
    }

    public CampaignSemanticSnapshot snapshot(UUID campaignId) {
        CampaignPackageArtifact artifact = exporter.export(campaignId);
        return CampaignSemanticSnapshot.from(artifact.manifest());
    }
}
