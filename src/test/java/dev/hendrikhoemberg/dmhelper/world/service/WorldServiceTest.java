package dev.hendrikhoemberg.dmhelper.world.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:world-svc;DB_CLOSE_DELAY=-1")
class WorldServiceTest {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private WorldService worldService;

    @Autowired
    private CampaignPackageKeyService packageKeys;

    private WorldService.NpcCommand minimalNpc(String name) {
        return new WorldService.NpcCommand(name, null, null, null, null, null, null,
                null, null, null, null, null, WorldNpcStatus.UNKNOWN, null, null);
    }

    @Test
    void createsAndFindsNpc() {
        Campaign c = campaignService.create("NpcTest", null);
        WorldNpc npc = worldService.createNpc(c.getId(), minimalNpc("Mira"));
        assertThat(npc.getId()).isNotNull();
        assertThat(npc.getName()).isEqualTo("Mira");
        assertThat(npc.getCampaign().getId()).isEqualTo(c.getId());
    }

    @Test
    void updatesNpc() {
        Campaign c = campaignService.create("NpcUpd", null);
        WorldNpc npc = worldService.createNpc(c.getId(), minimalNpc("Mira"));
        WorldNpc updated = worldService.updateNpc(c.getId(), npc.getId(),
                new WorldService.NpcCommand("Mira Updated", "Leader", WorldDisposition.FRIENDLY,
                        null, null, null, null, "tall", "deep", "power", "none", null,
                        WorldNpcStatus.ALIVE, null, null));
        assertThat(updated.getName()).isEqualTo("Mira Updated");
        assertThat(updated.getRole()).isEqualTo("Leader");
        assertThat(updated.getDisposition()).isEqualTo(WorldDisposition.FRIENDLY);
    }

    @Test
    void deleteNpcRemovesPackageKeyBinding() {
        Campaign c = campaignService.create("Keys", null);
        WorldNpc npc = worldService.createNpc(c.getId(), minimalNpc("Mira"));
        String key = packageKeys.getOrCreate(c.getId(), CampaignContentType.WORLD_NPC, npc.getId(), npc.getName());
        assertThat(key).isNotBlank();
        worldService.deleteNpc(c.getId(), npc.getId());
        assertThat(packageKeys.find(c.getId(), CampaignContentType.WORLD_NPC, npc.getId())).isEmpty();
    }

    @Test
    void createLocationWithParent() {
        Campaign c = campaignService.create("LocTest", null);
        WorldLocation region = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "Region", LocationKind.REGION, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        WorldLocation site = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "Site", LocationKind.SITE, region.getId(), null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        assertThat(site.getParentLocation().getId()).isEqualTo(region.getId());
    }

    @Test
    void rejectsLocationParentCycle() {
        Campaign c = campaignService.create("Cycle", null);
        WorldLocation a = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "A", LocationKind.REGION, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        WorldLocation b = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "B", LocationKind.SITE, a.getId(), null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        assertThatThrownBy(() -> worldService.updateLocation(c.getId(), a.getId(), new WorldService.LocationCommand(
                "A", LocationKind.REGION, b.getId(), null, null, null, null, null, null,
                List.of(), List.of(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectLocationSelfParent() {
        Campaign c = campaignService.create("SelfParent", null);
        WorldLocation loc = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "X", LocationKind.SITE, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        assertThatThrownBy(() -> worldService.updateLocation(c.getId(), loc.getId(), new WorldService.LocationCommand(
                "X", LocationKind.SITE, loc.getId(), null, null, null, null, null, null,
                List.of(), List.of(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void createsAndFindsFaction() {
        Campaign c = campaignService.create("FactionTest", null);
        Faction faction = worldService.createFaction(c.getId(), new WorldService.FactionCommand(
                "The Order", "World peace", "Gold", "Well-liked", null, null, null));
        assertThat(faction.getId()).isNotNull();
        assertThat(faction.getName()).isEqualTo("The Order");
    }

    @Test
    void createsRelationship() {
        Campaign c = campaignService.create("RelTest", null);
        WorldNpc npc1 = worldService.createNpc(c.getId(), minimalNpc("Alice"));
        WorldNpc npc2 = worldService.createNpc(c.getId(), minimalNpc("Bob"));
        WorldRelationship rel = worldService.createRelationship(c.getId(), new WorldService.RelationshipCommand(
                RelationshipKind.KNOWS, "WORLD_NPC", npc1.getId(), "WORLD_NPC", npc2.getId(),
                true, RelationshipKnowledge.PUBLIC, RelationshipStatus.ACTIVE,
                null, null, 0));
        assertThat(rel.getId()).isNotNull();
        assertThat(rel.getKind()).isEqualTo(RelationshipKind.KNOWS);
        assertThat(rel.getFromId()).isEqualTo(npc1.getId());
        assertThat(rel.getToId()).isEqualTo(npc2.getId());
    }

    @Test
    void rejectsSelfRelationship() {
        Campaign c = campaignService.create("SelfRel", null);
        WorldNpc npc = worldService.createNpc(c.getId(), minimalNpc("Narcissus"));
        assertThatThrownBy(() -> worldService.createRelationship(c.getId(),
                new WorldService.RelationshipCommand(
                        RelationshipKind.KNOWS, "WORLD_NPC", npc.getId(), "WORLD_NPC", npc.getId(),
                        true, RelationshipKnowledge.PUBLIC, RelationshipStatus.ACTIVE,
                        null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self");
    }

    @Test
    void createsFactionClock() {
        Campaign c = campaignService.create("ClockTest", null);
        Faction faction = worldService.createFaction(c.getId(), new WorldService.FactionCommand(
                "Clock Faction", null, null, null, null, null, null));
        FactionClock clock = worldService.createClock(c.getId(), new WorldService.ClockCommand(
                faction.getId(), "Doom", 6, 2, null, null, null, null, 0));
        assertThat(clock.getId()).isNotNull();
        assertThat(clock.getTitle()).isEqualTo("Doom");
        assertThat(clock.getSegments()).isEqualTo(6);
        assertThat(clock.getFilled()).isEqualTo(2);
    }

    @Test
    void rejectsClockFilledOutOfRange() {
        Campaign c = campaignService.create("ClockRange", null);
        Faction faction = worldService.createFaction(c.getId(), new WorldService.FactionCommand(
                "Range Faction", null, null, null, null, null, null));
        assertThatThrownBy(() -> worldService.createClock(c.getId(), new WorldService.ClockCommand(
                faction.getId(), "Bad", 6, 7, null, null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filled");
        assertThatThrownBy(() -> worldService.createClock(c.getId(), new WorldService.ClockCommand(
                faction.getId(), "Neg", 6, -1, null, null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filled");
    }

    @Test
    void deleteLocationCascadesNpcRefs() {
        Campaign c = campaignService.create("LocCascade", null);
        WorldLocation loc = worldService.createLocation(c.getId(), new WorldService.LocationCommand(
                "Town", LocationKind.SETTLEMENT, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null));
        WorldNpc npc = worldService.createNpc(c.getId(), new WorldService.NpcCommand(
                "Guard", null, null, null, loc.getId(), null, null,
                null, null, null, null, null, WorldNpcStatus.UNKNOWN, null, null));
        worldService.deleteLocation(c.getId(), loc.getId());
        assertThat(worldService.getNpc(c.getId(), npc.getId()).getLocation()).isNull();
    }

    @Test
    void deleteFactionRemovesClocks() {
        Campaign c = campaignService.create("FacCascade", null);
        Faction faction = worldService.createFaction(c.getId(), new WorldService.FactionCommand(
                "Doomed", null, null, null, null, null, null));
        worldService.createClock(c.getId(), new WorldService.ClockCommand(
                faction.getId(), "Clock 1", 4, 0, null, null, null, null, 0));
        worldService.deleteFaction(c.getId(), faction.getId());
        assertThat(worldService.getClocks(c.getId())).isEmpty();
    }

    @Test
    void deleteFactionNullsNpcRefs() {
        Campaign c = campaignService.create("FacNpc", null);
        Faction faction = worldService.createFaction(c.getId(), new WorldService.FactionCommand(
                "Old Guild", null, null, null, null, null, null));
        WorldNpc npc = worldService.createNpc(c.getId(), new WorldService.NpcCommand(
                "Guildy", null, null, faction.getId(), null, null, null,
                null, null, null, null, null, WorldNpcStatus.UNKNOWN, null, null));
        worldService.deleteFaction(c.getId(), faction.getId());
        assertThat(worldService.getNpc(c.getId(), npc.getId()).getFaction()).isNull();
    }

    @Test
    void validateCampaignOwnershipForNpcFaction() {
        Campaign c1 = campaignService.create("C1", null);
        Campaign c2 = campaignService.create("C2", null);
        Faction factionInC2 = worldService.createFaction(c2.getId(), new WorldService.FactionCommand(
                "C2 Faction", null, null, null, null, null, null));
        assertThatThrownBy(() -> worldService.createNpc(c1.getId(), new WorldService.NpcCommand(
                "Stray", null, null, factionInC2.getId(), null, null, null,
                null, null, null, null, null, WorldNpcStatus.UNKNOWN, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaign");
    }

    @Test
    void getNpcsByCampaign() {
        Campaign c = campaignService.create("GetNpcs", null);
        worldService.createNpc(c.getId(), minimalNpc("A"));
        worldService.createNpc(c.getId(), minimalNpc("B"));
        worldService.createNpc(c.getId(), minimalNpc("C"));
        assertThat(worldService.getNpcs(c.getId())).hasSize(3);
    }
}
