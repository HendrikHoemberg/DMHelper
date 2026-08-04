package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.support.CampaignSemanticSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class CampaignSemanticSnapshotServiceTest {

    @Autowired CampaignSemanticSnapshotService snapshots;
    @Autowired CampaignRepository campaigns;
    @Autowired QuickNoteRepository quickNotes;
    @Autowired GameMapRepository maps;
    @Autowired CampaignPackageKeyService keys;
    @MockitoBean CampaignExportCoordinator exporter;

    @BeforeEach
    void returnAnEmptyExportArtifact() {
        when(exporter.export(any())).thenReturn(new CampaignPackageArtifact(
                "snapshot.json", MediaType.APPLICATION_JSON, null, Map.of()));
    }

    @Test
    void rejectsCampaignOwnedRowsThatTheExporterDidNotBind() {
        Campaign campaign = campaign();
        keys.bindImported(campaign.getId(), CampaignContentType.CAMPAIGN,
                campaign.getId(), "campaign");

        QuickNote omitted = new QuickNote();
        omitted.setCampaign(campaign);
        omitted.setTargetType("CAMPAIGN");
        omitted.setTargetId(campaign.getId());
        omitted.setBody("Must not disappear");
        quickNotes.save(omitted);

        assertThatThrownBy(() -> snapshots.snapshot(campaign.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Campaign-owned entity has no package key")
                .hasMessageContaining("QUICK_NOTE");
    }

    @Test
    void excludesOptimisticLockVersionsFromThePersistenceProjection() {
        Campaign campaign = campaign();
        keys.bindImported(campaign.getId(), CampaignContentType.CAMPAIGN,
                campaign.getId(), "campaign");

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Map");
        maps.save(map);
        keys.bindImported(campaign.getId(), CampaignContentType.MAP, map.getId(), "map");

        var snapshot = snapshots.snapshot(campaign.getId());

        assertThat(snapshot.persistenceProjection().path("entities").path("MAP:map").has("version"))
                .isFalse();
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setName("Snapshot campaign");
        campaign.setDescription("test");
        return campaigns.save(campaign);
    }
}
