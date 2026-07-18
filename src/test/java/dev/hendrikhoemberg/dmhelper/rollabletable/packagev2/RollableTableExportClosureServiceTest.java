package dev.hendrikhoemberg.dmhelper.rollabletable.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReferenceRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollableTableExportClosureServiceTest {

    @Mock RollableTableRepository tableRepo;
    @Mock WorldLocationTableLinkRepository locationLinkRepo;
    @Mock RollableTableEntryReferenceRepository entryRefRepo;
    @Mock SceneRepository sceneRepo;
    @Mock WorldLocationRepository locationRepo;

    @Test
    void includesCampaignOwnedTables() {
        UUID campaignId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        var table = new RollableTable();
        table.setId(tableId);

        when(tableRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of(table));
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(tableRepo.findWithEntriesById(tableId)).thenReturn(Optional.of(table));

        var result = new RollableTableExportClosureService(
                tableRepo, locationLinkRepo, entryRefRepo, sceneRepo, locationRepo)
                .forCampaign(campaignId);

        assertThat(result.tableIds()).contains(tableId);
    }

    @Test
    void includesSceneReferencedTables() {
        UUID campaignId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        var scene = new Scene();
        var link = new SceneLink();
        link.setRole(SceneLinkRole.RANDOM_ENCOUNTERS);
        link.setTargetScope(dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.PACKAGE);
        link.setTargetId(tableId);
        scene.getLinks().add(link);

        var table = new RollableTable();
        table.setId(tableId);

        when(tableRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of());
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of(scene));
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(tableRepo.findWithEntriesById(tableId)).thenReturn(Optional.of(table));

        var result = new RollableTableExportClosureService(
                tableRepo, locationLinkRepo, entryRefRepo, sceneRepo, locationRepo)
                .forCampaign(campaignId);

        assertThat(result.tableIds()).contains(tableId);
    }

    @Test
    void walksNestedTableReferences() {
        UUID campaignId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        var childTable = new RollableTable();
        childTable.setId(childId);
        childTable.setEntries(List.of());

        var parentEntry = new RollableTableEntry();
        var ref = new RollableTableEntryReference();
        ref.setTargetScope(TableReferenceScope.ENTITY);
        ref.setTargetType("ROLLABLE_TABLE");
        ref.setTargetId(childId);
        parentEntry.getReferences().add(ref);

        var parentTable = new RollableTable();
        parentTable.setId(parentId);
        parentTable.setEntries(List.of(parentEntry));

        when(tableRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of(parentTable));
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(tableRepo.findWithEntriesById(parentId)).thenReturn(Optional.of(parentTable));
        when(tableRepo.findWithEntriesById(childId)).thenReturn(Optional.of(childTable));

        var result = new RollableTableExportClosureService(
                tableRepo, locationLinkRepo, entryRefRepo, sceneRepo, locationRepo)
                .forCampaign(campaignId);

        assertThat(result.tableIds()).contains(parentId, childId);
    }

    @Test
    void returnsEmptyForNoTables() {
        UUID campaignId = UUID.randomUUID();

        when(tableRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of());
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());

        var result = new RollableTableExportClosureService(
                tableRepo, locationLinkRepo, entryRefRepo, sceneRepo, locationRepo)
                .forCampaign(campaignId);

        assertThat(result.tableIds()).isEmpty();
        assertThat(result.libraryReferenceIds()).isEmpty();
    }
}
