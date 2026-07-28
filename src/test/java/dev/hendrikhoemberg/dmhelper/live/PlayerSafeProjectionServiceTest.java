package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacement;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWave;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PlayerSafeProjectionService.class, RuntimeTokenProjectionService.class})
class PlayerSafeProjectionServiceTest {

    @Autowired private PlayerSafeProjectionService service;
    @Autowired private jakarta.persistence.EntityManager em;

    private Campaign campaign;
    private GameMap gameMap;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);

        gameMap = new GameMap();
        gameMap.setCampaign(campaign);
        gameMap.setName("Test Map");
        gameMap.setGridWidth(20);
        gameMap.setGridHeight(15);
        gameMap.setCellSizePx(48);
        em.persist(gameMap);
        em.flush();
    }

    @Test
    void shouldStripHiddenTokens() {
        Token visible = new Token();
        visible.setMap(gameMap);
        visible.setName("Goblin");
        visible.setHidden(false);
        em.persist(visible);

        Token hidden = new Token();
        hidden.setMap(gameMap);
        hidden.setName("Assassin");
        hidden.setHidden(true);
        em.persist(hidden);
        em.flush();

        var result = service.projectTokens(gameMap, null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Goblin");
    }

    @Test
    void hiddenCombatantAndHiddenMarkerAreNotProjected() {
        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setMap(gameMap);
        enc.setName("Battle");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc.setRound(1);
        em.persist(enc);

        Combatant visibleCbt = new Combatant();
        visibleCbt.setEncounter(enc);
        visibleCbt.setName("Visible Orc");
        visibleCbt.setKind("MONSTER");
        visibleCbt.setHidden(false);
        visibleCbt.setMaxHp(20);
        visibleCbt.setCurrentHp(20);
        em.persist(visibleCbt);

        Combatant hiddenCbt = new Combatant();
        hiddenCbt.setEncounter(enc);
        hiddenCbt.setName("Hidden Assassin");
        hiddenCbt.setKind("MONSTER");
        hiddenCbt.setHidden(true);
        hiddenCbt.setMaxHp(20);
        hiddenCbt.setCurrentHp(20);
        em.persist(hiddenCbt);

        EncounterTokenPlacement visiblePl = new EncounterTokenPlacement();
        visiblePl.setEncounter(enc);
        visiblePl.setCombatant(visibleCbt);
        visiblePl.setMap(gameMap);
        visiblePl.setPositionX(0);
        visiblePl.setPositionY(0);
        em.persist(visiblePl);

        EncounterTokenPlacement hiddenPl = new EncounterTokenPlacement();
        hiddenPl.setEncounter(enc);
        hiddenPl.setCombatant(hiddenCbt);
        hiddenPl.setMap(gameMap);
        hiddenPl.setPositionX(5);
        hiddenPl.setPositionY(5);
        em.persist(hiddenPl);

        Token visibleMarker = new Token();
        visibleMarker.setMap(gameMap);
        visibleMarker.setName("Chest");
        visibleMarker.setHidden(false);
        em.persist(visibleMarker);

        Token hiddenMarker = new Token();
        hiddenMarker.setMap(gameMap);
        hiddenMarker.setName("Trap");
        hiddenMarker.setHidden(true);
        em.persist(hiddenMarker);
        em.flush();

        var result = service.projectTokens(gameMap, enc);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(LiveTableState.TokenSnapshot::name)
                .containsExactlyInAnyOrder("Visible Orc", "Chest");
    }

    @Test
    void detachedMapContainsNoActiveEncounterOverlay() {
        GameMap otherMap = new GameMap();
        otherMap.setCampaign(campaign);
        otherMap.setName("Other Map");
        otherMap.setGridWidth(10);
        otherMap.setGridHeight(10);
        otherMap.setCellSizePx(48);
        em.persist(otherMap);

        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setMap(otherMap);
        enc.setName("Battle on Other Map");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc.setRound(1);
        em.persist(enc);

        Combatant cbt = new Combatant();
        cbt.setEncounter(enc);
        cbt.setName("Goblin");
        cbt.setKind("MONSTER");
        cbt.setHidden(false);
        cbt.setMaxHp(10);
        cbt.setCurrentHp(10);
        em.persist(cbt);

        EncounterTokenPlacement pl = new EncounterTokenPlacement();
        pl.setEncounter(enc);
        pl.setCombatant(cbt);
        pl.setMap(otherMap);
        pl.setPositionX(0);
        pl.setPositionY(0);
        em.persist(pl);

        Token marker = new Token();
        marker.setMap(gameMap);
        marker.setName("Potion");
        marker.setHidden(false);
        em.persist(marker);
        em.flush();

        var result = service.projectTokens(gameMap, null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Potion");
    }

    @Test
    void visibleCombatantsDefeatedAndBloodiedAreDerivedFromCombatantState() {
        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setMap(gameMap);
        enc.setName("Battle");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc.setRound(1);
        em.persist(enc);

        Combatant defeated = new Combatant();
        defeated.setEncounter(enc);
        defeated.setName("Dead Orc");
        defeated.setKind("MONSTER");
        defeated.setHidden(false);
        defeated.setMaxHp(20);
        defeated.setCurrentHp(0);
        defeated.setDefeated(true);
        em.persist(defeated);

        Combatant bloodied = new Combatant();
        bloodied.setEncounter(enc);
        bloodied.setName("Wounded Goblin");
        bloodied.setKind("MONSTER");
        bloodied.setHidden(false);
        bloodied.setMaxHp(20);
        bloodied.setCurrentHp(5);
        em.persist(bloodied);

        Combatant healthy = new Combatant();
        healthy.setEncounter(enc);
        healthy.setName("Healthy Troll");
        healthy.setKind("MONSTER");
        healthy.setHidden(false);
        healthy.setMaxHp(30);
        healthy.setCurrentHp(30);
        em.persist(healthy);

        for (Combatant c : List.of(defeated, bloodied, healthy)) {
            EncounterTokenPlacement pl = new EncounterTokenPlacement();
            pl.setEncounter(enc);
            pl.setCombatant(c);
            pl.setMap(gameMap);
            pl.setPositionX(0);
            pl.setPositionY(0);
            em.persist(pl);
        }
        em.flush();

        var result = service.projectTokens(gameMap, enc);
        assertThat(result).hasSize(3);

        var deadToken = result.stream().filter(t -> t.name().equals("Dead Orc")).findFirst().orElseThrow();
        assertThat(deadToken.dead()).isTrue();

        var woundedToken = result.stream().filter(t -> t.name().equals("Wounded Goblin")).findFirst().orElseThrow();
        assertThat(woundedToken.dead()).isFalse();
        assertThat(woundedToken.bloodied()).isTrue();

        var healthyToken = result.stream().filter(t -> t.name().equals("Healthy Troll")).findFirst().orElseThrow();
        assertThat(healthyToken.dead()).isFalse();
        assertThat(healthyToken.bloodied()).isFalse();
    }

    @Test
    void suspendedEncounterPlacementsAreAbsentFromPlayer() {
        Encounter enc = new Encounter();
        enc.setCampaign(campaign);
        enc.setMap(gameMap);
        enc.setName("Paused Battle");
        enc.setStatus(Encounter.Status.SUSPENDED);
        enc.setRound(1);
        em.persist(enc);

        Combatant cbt = new Combatant();
        cbt.setEncounter(enc);
        cbt.setName("Paused Orc");
        cbt.setKind("MONSTER");
        cbt.setHidden(false);
        cbt.setMaxHp(20);
        cbt.setCurrentHp(20);
        em.persist(cbt);

        EncounterTokenPlacement pl = new EncounterTokenPlacement();
        pl.setEncounter(enc);
        pl.setCombatant(cbt);
        pl.setMap(gameMap);
        pl.setPositionX(0);
        pl.setPositionY(0);
        em.persist(pl);

        Token marker = new Token();
        marker.setMap(gameMap);
        marker.setName("Barrel");
        marker.setHidden(false);
        em.persist(marker);
        em.flush();

        var result = service.projectTokens(gameMap, null);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Barrel");
    }

    @Test
    void shouldStripAnnotationsLayer() {
        gameMap.setDocument("{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]},{\"id\":\"l2\",\"name\":\"Annotations\",\"type\":\"ANNOTATIONS\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}");
        em.merge(gameMap);
        em.flush();

        var result = service.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.layers()).hasSize(1);
        assertThat(result.layers().getFirst().name()).isEqualTo("Terrain");
    }

    @Test
    void shouldReProjectMapDocumentAfterModification() {
        String initialDoc = "{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[{\"col\":1,\"row\":1,\"terrain\":\"floor\"}],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}";
        String modifiedDoc = "{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[{\"col\":5,\"row\":5,\"terrain\":\"wall\"}],\"shapes\":[]}],\"primitives\":[],\"customTerrain\":[]}";

        gameMap.setDocument(initialDoc);
        em.merge(gameMap);
        em.flush();

        MapDocumentDto firstProjection = service.projectMapDocument(gameMap);
        assertThat(firstProjection.layers().getFirst().cells().getFirst().terrain()).isEqualTo("floor");

        gameMap.setDocument(modifiedDoc);
        em.merge(gameMap);
        em.flush();

        MapDocumentDto secondProjection = service.projectMapDocument(gameMap);
        assertThat(secondProjection.layers().getFirst().cells().getFirst().terrain()).isEqualTo("wall");
    }

    @Test
    void shouldStripInvisibleLayerContent() {
        gameMap.setDocument("{\"schemaVersion\":1,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"SQUARE\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"l1\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[]},{\"id\":\"l2\",\"name\":\"DM Secrets\",\"type\":\"OBJECTS\",\"visible\":false,\"locked\":true,\"cells\":[{\"col\":5,\"row\":5,\"terrain\":\"wall\"}],\"shapes\":[{\"type\":\"rect\",\"points\":[1,1,3,3],\"fill\":\"#ff0000\",\"stroke\":\"#000\",\"strokeWidth\":1}],\"image\":{\"dataUrl\":\"data:image/png;base64,abc123\",\"x\":0,\"y\":0,\"width\":10,\"height\":10}}],\"primitives\":[],\"customTerrain\":[]}");
        em.merge(gameMap);
        em.flush();

        var result = service.projectMapDocument(gameMap);
        assertThat(result).isNotNull();
        assertThat(result.layers()).hasSize(2);

        var invisibleLayer = result.layers().get(1);
        assertThat(invisibleLayer.name()).isEqualTo("DM Secrets");
        assertThat(invisibleLayer.visible()).isFalse();
        assertThat(invisibleLayer.cells()).isEmpty();
        assertThat(invisibleLayer.shapes()).isEmpty();
        assertThat(invisibleLayer.image()).isNull();
    }

    @Test
    void nullZeroNegativeInitiativeAllDistinct() {
        Encounter enc = new Encounter();
        enc.setCampaign(gameMap.getCampaign());
        enc.setName("Init Test");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc.setRound(1);
        em.persist(enc);

        Combatant nullInit = new Combatant();
        nullInit.setEncounter(enc);
        nullInit.setName("Null Init");
        nullInit.setInitiative(null);
        nullInit.setSortOrder(0);
        nullInit.setKind("MONSTER");
        nullInit.setHidden(false);
        nullInit.setMaxHp(10);
        nullInit.setCurrentHp(10);
        em.persist(nullInit);

        Combatant zeroInit = new Combatant();
        zeroInit.setEncounter(enc);
        zeroInit.setName("Zero Init");
        zeroInit.setInitiative(0);
        zeroInit.setSortOrder(1);
        zeroInit.setKind("MONSTER");
        zeroInit.setHidden(false);
        zeroInit.setMaxHp(10);
        zeroInit.setCurrentHp(10);
        em.persist(zeroInit);

        Combatant negInit = new Combatant();
        negInit.setEncounter(enc);
        negInit.setName("Negative Init");
        negInit.setInitiative(-3);
        negInit.setSortOrder(2);
        negInit.setKind("MONSTER");
        negInit.setHidden(false);
        negInit.setMaxHp(10);
        negInit.setCurrentHp(10);
        em.persist(negInit);
        em.flush();

        var snapshots = service.projectCombatants(List.of(negInit, zeroInit, nullInit), -1);
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots).filteredOn(s -> "Null Init".equals(s.name()))
                .allMatch(s -> s.initiative() == null);
        assertThat(snapshots).filteredOn(s -> "Zero Init".equals(s.name()))
                .allMatch(s -> s.initiative() == 0);
        assertThat(snapshots).filteredOn(s -> "Negative Init".equals(s.name()))
                .allMatch(s -> s.initiative() == -3);
    }

    @Test
    void playerPayloadOmitsPrepRewardsAndDmOnlyRegions() throws Exception {
        JsonMapper mapper = new JsonMapper();

        Encounter enc = new Encounter();
        enc.setCampaign(gameMap.getCampaign());
        enc.setName("Danger Room");
        enc.setPrepJson("{\"tactics\":\"ambush from shadows\",\"morale\":\"fanatic\"}");
        enc.setRewardsJson("{\"xpTotal\":1000,\"xpPerPc\":250,\"notes\":\"hidden treasure\"}");
        enc.setStatus(Encounter.Status.ACTIVE);
        enc.setRound(1);
        em.persist(enc);

        EncounterWave wave = new EncounterWave();
        wave.setEncounter(enc);
        wave.setWaveKey("wave-main");
        wave.setName("Main");
        wave.setSortOrder(0);
        wave.setStatus(WaveStatus.ACTIVE);
        wave.setTriggerKind(WaveTriggerKind.MANUAL);
        em.persist(wave);

        Combatant cbt = new Combatant();
        cbt.setEncounter(enc);
        cbt.setName("Shadow Assassin");
        cbt.setInitiative(18);
        cbt.setSortOrder(0);
        cbt.setMaxHp(30);
        cbt.setCurrentHp(30);
        cbt.setKind("MONSTER");
        cbt.setHidden(false);
        cbt.setWave(wave);
        cbt.setStartX(5);
        cbt.setStartY(10);
        cbt.setPlacementRegionKey("secret-room");
        em.persist(cbt);

        GameMap safetyMap = new GameMap();
        safetyMap.setCampaign(gameMap.getCampaign());
        safetyMap.setName("Secret Base");
        safetyMap.setGridWidth(20);
        safetyMap.setGridHeight(15);
        safetyMap.setCellSizePx(48);
        safetyMap.setDocument("{\"schemaVersion\":2,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"square\",\"movementMode\":\"GRID\",\"showGrid\":true},\"layers\":[{\"id\":\"terrain\",\"name\":\"Terrain\",\"type\":\"TERRAIN\",\"visible\":true,\"locked\":false,\"cells\":[],\"shapes\":[],\"playerVisible\":true}],\"primitives\":[{\"type\":\"REGION\",\"startCol\":0,\"startRow\":0,\"endCol\":5,\"endRow\":5,\"terrain\":\"dungeon\",\"key\":\"secret-region-key\",\"label\":\"Secret Room\",\"playerVisible\":false}],\"customTerrain\":[]}");
        em.persist(safetyMap);
        em.flush();

        var combatants = service.projectCombatants(List.of(cbt), 0);
        String combatantJson = mapper.writeValueAsString(combatants);
        assertThat(combatantJson)
                .doesNotContain("tactics")
                .doesNotContain("rewards")
                .doesNotContain("ambush from shadows")
                .doesNotContain("hidden treasure");

        var projectedMap = service.projectMapDocument(safetyMap);
        assertThat(projectedMap.primitives()).noneMatch(p -> "secret-region-key".equals(p.key()));
        String mapJson = mapper.writeValueAsString(projectedMap);
        assertThat(mapJson).doesNotContain("secret-region-key");
    }
}
