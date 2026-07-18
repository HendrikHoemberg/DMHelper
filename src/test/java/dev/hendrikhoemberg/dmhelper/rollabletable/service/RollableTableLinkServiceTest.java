package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableLinkRole;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollableTableLinkServiceTest {

    @Mock private SceneRepository sceneRepository;
    @Mock private SceneLinkRepository sceneLinkRepository;
    @Mock private WorldLocationTableLinkRepository worldLocationTableLinkRepository;
    @Mock private RollableTableRepository rollableTableRepository;
    @Mock private WorldLocationRepository worldLocationRepository;
    @Mock private TableReferenceResolver referenceResolver;

    @InjectMocks private RollableTableLinkService service;

    private UUID campaignId;
    private UUID sceneId;
    private UUID locationId;
    private Scene scene;
    private WorldLocation location;
    private Campaign campaign;
    private RollableTable srdTable;
    private RollableTable campaignTable;
    private RollableTable otherCampaignTable;
    private RollableTable globalTable;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        sceneId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);

        scene = new Scene();
        scene.setId(sceneId);

        location = new WorldLocation();
        location.setId(locationId);
        location.setName("Dungeon Entrance");
        location.setCampaign(campaign);

        srdTable = new RollableTable();
        srdTable.setId(UUID.randomUUID());
        srdTable.setSourceKey("srd-table");
        srdTable.setName("SRD Random Encounters");
        srdTable.setCategory(TableCategory.ENCOUNTER);
        srdTable.setSource(ContentSource.SRD);

        campaignTable = new RollableTable();
        campaignTable.setId(UUID.randomUUID());
        campaignTable.setSourceKey("campaign-table");
        campaignTable.setName("Campaign Random Encounters");
        campaignTable.setCategory(TableCategory.ENCOUNTER);
        campaignTable.setSource(ContentSource.CUSTOM);
        campaignTable.setCampaign(campaign);

        otherCampaignTable = new RollableTable();
        otherCampaignTable.setId(UUID.randomUUID());
        otherCampaignTable.setSourceKey("other-table");
        otherCampaignTable.setName("Other Campaign Table");
        otherCampaignTable.setCategory(TableCategory.GENERIC);
        otherCampaignTable.setSource(ContentSource.CUSTOM);
        Campaign otherCampaign = new Campaign();
        otherCampaign.setId(UUID.randomUUID());
        otherCampaignTable.setCampaign(otherCampaign);

        globalTable = new RollableTable();
        globalTable.setId(UUID.randomUUID());
        globalTable.setSourceKey("global-table");
        globalTable.setName("Global Custom Table");
        globalTable.setCategory(TableCategory.GENERIC);
        globalTable.setSource(ContentSource.CUSTOM);
    }

    private void stubVisibility(RollableTable table, boolean visible) {
        when(referenceResolver.isVisibleToCampaign(table.getId(), campaignId)).thenReturn(visible);
    }

    private SceneLink sceneLink(UUID targetId, SceneLinkRole role, String targetType, int sortOrder, String displayText) {
        SceneLink link = new SceneLink();
        link.setId(UUID.randomUUID());
        link.setScene(scene);
        link.setRole(role);
        link.setTargetScope(SceneLinkTargetScope.PACKAGE);
        link.setTargetType(targetType);
        link.setTargetId(targetId);
        link.setDisplayText(displayText);
        link.setSortOrder(sortOrder);
        return link;
    }

    private WorldLocationTableLink locationTableLink(RollableTable table, int sortOrder) {
        WorldLocationTableLink link = new WorldLocationTableLink();
        link.setId(UUID.randomUUID());
        link.setLocation(location);
        link.setTable(table);
        link.setRole(RollableTableLinkRole.RANDOM_ENCOUNTERS);
        link.setSortOrder(sortOrder);
        return link;
    }

    @Test
    void returnsEmptyListWhenSceneHasNoLinks() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of());

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).isEmpty();
    }

    @Test
    void includesDirectSceneLinkToRollableTable() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(srdTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Encounters")));
        when(rollableTableRepository.findById(srdTable.getId())).thenReturn(Optional.of(srdTable));
        stubVisibility(srdTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableId()).isEqualTo(srdTable.getId());
        assertThat(result.get(0).source()).isEqualTo("SCENE");
        assertThat(result.get(0).sourceLabel()).isEqualTo("Encounters");
    }

    @Test
    void includesLocationTableLinksViaLocationSceneLink() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(locationId, SceneLinkRole.LOCATION, "WORLD_LOCATION", 0, "Dungeon")));
        when(worldLocationRepository.findByIdAndCampaignId(locationId, campaignId))
                .thenReturn(Optional.of(location));
        when(worldLocationTableLinkRepository.findByLocationIdWithTable(locationId))
                .thenReturn(List.of(locationTableLink(srdTable, 0)));
        stubVisibility(srdTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableId()).isEqualTo(srdTable.getId());
        assertThat(result.get(0).source()).isEqualTo("LOCATION");
    }

    @Test
    void deduplicatesWhenSceneAndLocationLinkToSameTable() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(srdTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Scene link"),
                        sceneLink(locationId, SceneLinkRole.LOCATION, "WORLD_LOCATION", 1, "Dungeon")));
        when(rollableTableRepository.findById(srdTable.getId())).thenReturn(Optional.of(srdTable));
        when(worldLocationRepository.findByIdAndCampaignId(locationId, campaignId))
                .thenReturn(Optional.of(location));
        when(worldLocationTableLinkRepository.findByLocationIdWithTable(locationId))
                .thenReturn(List.of(locationTableLink(srdTable, 0)));
        stubVisibility(srdTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
    }

    @Test
    void includesSrdTableEvenWithoutCampaign() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(srdTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "SRD")));
        when(rollableTableRepository.findById(srdTable.getId())).thenReturn(Optional.of(srdTable));
        stubVisibility(srdTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableId()).isEqualTo(srdTable.getId());
    }

    @Test
    void includesGlobalCustomTableWithoutCampaign() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(globalTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Global")));
        when(rollableTableRepository.findById(globalTable.getId())).thenReturn(Optional.of(globalTable));
        stubVisibility(globalTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
    }

    @Test
    void includesCampaignScopedTableWhenMatchingCampaign() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(campaignTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Campaign")));
        when(rollableTableRepository.findById(campaignTable.getId())).thenReturn(Optional.of(campaignTable));
        stubVisibility(campaignTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableId()).isEqualTo(campaignTable.getId());
    }

    @Test
    void excludesTableFromOtherCampaign() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(otherCampaignTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Other")));
        when(rollableTableRepository.findById(otherCampaignTable.getId())).thenReturn(Optional.of(otherCampaignTable));
        stubVisibility(otherCampaignTable, false);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).isEmpty();
    }

    @Test
    void sceneLinksComeBeforeLocationLinks() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(locationId, SceneLinkRole.LOCATION, "WORLD_LOCATION", 0, "Dungeon"),
                        sceneLink(campaignTable.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "Encounters")));
        when(rollableTableRepository.findById(campaignTable.getId())).thenReturn(Optional.of(campaignTable));
        when(worldLocationRepository.findByIdAndCampaignId(locationId, campaignId))
                .thenReturn(Optional.of(location));
        when(worldLocationTableLinkRepository.findByLocationIdWithTable(locationId))
                .thenReturn(List.of(locationTableLink(srdTable, 0)));
        stubVisibility(campaignTable, true);
        stubVisibility(srdTable, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).extracting(LinkedRollableTableView::source)
                .containsExactly("SCENE", "LOCATION");
    }

    @Test
    void ignoresNonRandomEncounterSceneLinks() {
        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(srdTable.getId(), SceneLinkRole.REFERENCE, "ROLLABLE_TABLE", 0, "Ref"),
                        sceneLink(srdTable.getId(), SceneLinkRole.HANDOUT, "ROLLABLE_TABLE", 0, "Handout")));

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).isEmpty();
    }

    @Test
    void nameTiebreakerWithinSameSortOrder() {
        RollableTable tableA = new RollableTable();
        tableA.setId(UUID.randomUUID());
        tableA.setSourceKey("a");
        tableA.setName("A Encounters");
        tableA.setCategory(TableCategory.ENCOUNTER);
        tableA.setSource(ContentSource.SRD);

        RollableTable tableB = new RollableTable();
        tableB.setId(UUID.randomUUID());
        tableB.setSourceKey("b");
        tableB.setName("B Encounters");
        tableB.setCategory(TableCategory.ENCOUNTER);
        tableB.setSource(ContentSource.SRD);

        when(sceneRepository.findByIdAndCampaignId(campaignId, sceneId))
                .thenReturn(Optional.of(scene));
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId))
                .thenReturn(List.of(
                        sceneLink(tableB.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "B"),
                        sceneLink(tableA.getId(), SceneLinkRole.RANDOM_ENCOUNTERS, "ROLLABLE_TABLE", 0, "A")));
        when(rollableTableRepository.findById(tableA.getId())).thenReturn(Optional.of(tableA));
        when(rollableTableRepository.findById(tableB.getId())).thenReturn(Optional.of(tableB));
        stubVisibility(tableA, true);
        stubVisibility(tableB, true);

        var result = service.forScene(campaignId, sceneId);

        assertThat(result).extracting(LinkedRollableTableView::name)
                .containsExactly("A Encounters", "B Encounters");
    }
}
