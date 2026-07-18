package dev.hendrikhoemberg.dmhelper.threat.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
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
class ThreatExportClosureServiceTest {

    @Mock TrapRepository trapRepo;
    @Mock HazardRepository hazardRepo;
    @Mock SceneRepository sceneRepo;
    @Mock EncounterRepository encounterRepo;
    @Mock CombatantRepository combatantRepo;
    @Mock GameMapRepository mapRepo;
    @Mock MapThreatPinRepository pinRepo;
    @Mock StatBlockRepository statBlockRepo;
    @Mock ConditionRepository conditionRepo;
    @Mock EquipmentItemRepository equipmentRepo;
    @Mock MagicItemRepository magicItemRepo;

    private ThreatExportClosureService service() {
        return new ThreatExportClosureService(
                trapRepo, hazardRepo, sceneRepo, encounterRepo, combatantRepo, mapRepo, pinRepo,
                statBlockRepo, conditionRepo, equipmentRepo, magicItemRepo);
    }

    @Test
    void includesCampaignOwnedTrapsAndHazards() {
        UUID campaignId = UUID.randomUUID();
        UUID trapId = UUID.randomUUID();
        UUID hazardId = UUID.randomUUID();
        var campaign = new Campaign();
        campaign.setId(campaignId);

        var trap = new Trap();
        trap.setId(trapId);
        trap.setCampaign(campaign);
        var hazard = new Hazard();
        hazard.setId(hazardId);
        hazard.setCampaign(campaign);

        when(trapRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of(trap));
        when(hazardRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of(hazard));
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(encounterRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of());
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());
        when(trapRepo.findDetailedById(trapId)).thenReturn(Optional.of(trap));
        when(hazardRepo.findDetailedById(hazardId)).thenReturn(Optional.of(hazard));

        var result = service().forCampaign(campaignId);
        assertThat(result.trapIds()).containsExactly(trapId);
        assertThat(result.hazardIds()).containsExactly(hazardId);
    }

    @Test
    void includesSceneCombatantAndPinReferencedThreats() {
        UUID campaignId = UUID.randomUUID();
        UUID globalTrapId = UUID.randomUUID();
        UUID globalHazardId = UUID.randomUUID();
        UUID pinTrapId = UUID.randomUUID();

        var scene = new Scene();
        var section = new SceneSection();
        section.setThreatKind(ThreatKind.TRAP);
        section.setThreatId(globalTrapId);
        scene.getSections().add(section);

        var encounter = new Encounter();
        encounter.setId(UUID.randomUUID());
        var combatant = new Combatant();
        combatant.setThreatKind(ThreatKind.HAZARD);
        combatant.setThreatId(globalHazardId);

        var map = new GameMap();
        map.setId(UUID.randomUUID());
        var pin = new MapThreatPin();
        pin.setThreatKind(ThreatKind.TRAP);
        pin.setThreatId(pinTrapId);

        when(trapRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of());
        when(hazardRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of());
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of(scene));
        when(encounterRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of(encounter));
        when(combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(combatant));
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of(map));
        when(pinRepo.findByMapIdOrderBySortOrderAsc(map.getId())).thenReturn(List.of(pin));
        when(trapRepo.findDetailedById(globalTrapId)).thenReturn(Optional.of(new Trap()));
        when(trapRepo.findDetailedById(pinTrapId)).thenReturn(Optional.of(new Trap()));
        when(hazardRepo.findDetailedById(globalHazardId)).thenReturn(Optional.of(new Hazard()));

        var result = service().forCampaign(campaignId);
        assertThat(result.trapIds()).containsExactlyInAnyOrder(globalTrapId, pinTrapId);
        assertThat(result.hazardIds()).containsExactly(globalHazardId);
    }

    @Test
    void seedsOnlyGlobalCustomLibraryDepsNotSrdCatalogTargets() {
        UUID campaignId = UUID.randomUUID();
        UUID trapId = UUID.randomUUID();
        UUID globalConditionId = UUID.randomUUID();
        UUID srdConditionId = UUID.randomUUID();
        UUID campaignConditionId = UUID.randomUUID();
        UUID srdStatBlockId = UUID.randomUUID();
        UUID globalStatBlockId = UUID.randomUUID();
        var campaign = new Campaign();
        campaign.setId(campaignId);

        var trap = new Trap();
        trap.setId(trapId);
        trap.setCampaign(campaign);

        var globalSb = new StatBlock();
        globalSb.setId(globalStatBlockId);
        globalSb.setSource(ContentSource.CUSTOM);
        trap.setStatBlock(globalSb);

        var globalCondRef = new ThreatReference();
        globalCondRef.setRole(ThreatReferenceRole.CONDITION);
        globalCondRef.setTargetType(CampaignContentType.CONDITION);
        globalCondRef.setTargetId(globalConditionId);
        var srdCondRef = new ThreatReference();
        srdCondRef.setRole(ThreatReferenceRole.CONDITION);
        srdCondRef.setTargetType(CampaignContentType.CONDITION);
        srdCondRef.setTargetId(srdConditionId);
        var campaignCondRef = new ThreatReference();
        campaignCondRef.setRole(ThreatReferenceRole.CONDITION);
        campaignCondRef.setTargetType(CampaignContentType.CONDITION);
        campaignCondRef.setTargetId(campaignConditionId);
        trap.getReferences().add(globalCondRef);
        trap.getReferences().add(srdCondRef);
        trap.getReferences().add(campaignCondRef);

        when(trapRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of(trap));
        when(hazardRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of());
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(encounterRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of());
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());
        when(trapRepo.findDetailedById(trapId)).thenReturn(Optional.of(trap));

        var globalCondition = new Condition();
        globalCondition.setId(globalConditionId);
        globalCondition.setSource(ContentSource.CUSTOM);
        var srdCondition = new Condition();
        srdCondition.setId(srdConditionId);
        srdCondition.setSource(ContentSource.SRD);
        var campaignCondition = new Condition();
        campaignCondition.setId(campaignConditionId);
        campaignCondition.setSource(ContentSource.CUSTOM);
        campaignCondition.setCampaign(campaign);
        when(conditionRepo.findById(globalConditionId)).thenReturn(Optional.of(globalCondition));
        when(conditionRepo.findById(srdConditionId)).thenReturn(Optional.of(srdCondition));
        when(conditionRepo.findById(campaignConditionId)).thenReturn(Optional.of(campaignCondition));
        when(statBlockRepo.findById(globalStatBlockId)).thenReturn(Optional.of(globalSb));

        // Ensure SRD statblocks would not be seeded if referenced
        var srdSb = new StatBlock();
        srdSb.setId(srdStatBlockId);
        srdSb.setSource(ContentSource.SRD);

        var result = service().forCampaign(campaignId);
        assertThat(result.libraryReferenceIds().get(CampaignContentType.CONDITION))
                .containsExactly(globalConditionId);
        assertThat(result.libraryReferenceIds().get(CampaignContentType.STATBLOCK))
                .containsExactly(globalStatBlockId);
        assertThat(result.libraryReferenceIds().get(CampaignContentType.CONDITION))
                .doesNotContain(srdConditionId, campaignConditionId);
    }

    @Test
    void returnsEmptyWhenNoThreats() {
        UUID campaignId = UUID.randomUUID();
        when(trapRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of());
        when(hazardRepo.findVisibleByCampaignId(campaignId)).thenReturn(List.of());
        when(sceneRepo.findByChapterAdventureCampaignId(campaignId)).thenReturn(List.of());
        when(encounterRepo.findByCampaignIdOrderByNameAsc(campaignId)).thenReturn(List.of());
        when(mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(List.of());

        var result = service().forCampaign(campaignId);
        assertThat(result.trapIds()).isEmpty();
        assertThat(result.hazardIds()).isEmpty();
        assertThat(result.libraryReferenceIds()).isEmpty();
    }
}
