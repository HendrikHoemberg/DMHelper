package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:world-persist;DB_CLOSE_DELAY=-1")
class WorldPersistenceTest {

    @Autowired CampaignRepository campaigns;
    @Autowired FactionRepository factions;
    @Autowired WorldNpcRepository npcs;
    @Autowired WorldLocationRepository locations;
    @Autowired WorldRelationshipRepository relationships;
    @Autowired FactionClockRepository clocks;
    @Autowired EntityManager entityManager;

    @Test
    void persistsNpcLocationFactionRelationshipAndClock() {
        Campaign c = new Campaign();
        c.setName("W");
        c = campaigns.save(c);

        Faction f = new Faction();
        f.setCampaign(c);
        f.setName("Iron Ring");
        f.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        f = factions.save(f);

        WorldLocation loc = new WorldLocation();
        loc.setCampaign(c);
        loc.setName("Harbor");
        loc.setKind(LocationKind.SETTLEMENT);
        loc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        loc = locations.save(loc);

        WorldNpc npc = new WorldNpc();
        npc.setCampaign(c);
        npc.setName("Mira");
        npc.setFaction(f);
        npc.setLocation(loc);
        npc.setStatus(WorldNpcStatus.ALIVE);
        npc.setDisposition(WorldDisposition.FRIENDLY);
        npc.setSecret("Works for the Ring");
        npc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        npc = npcs.save(npc);

        WorldRelationship rel = new WorldRelationship();
        rel.setCampaign(c);
        rel.setKind(RelationshipKind.MEMBER_OF);
        rel.setFromType("WORLD_NPC");
        rel.setFromId(npc.getId());
        rel.setToType("FACTION");
        rel.setToId(f.getId());
        rel.setDirected(true);
        rel.setKnowledge(RelationshipKnowledge.PUBLIC);
        rel.setStatus(RelationshipStatus.ACTIVE);
        relationships.save(rel);

        FactionClock clock = new FactionClock();
        clock.setCampaign(c);
        clock.setFaction(f);
        clock.setTitle("Ring influence");
        clock.setSegments(6);
        clock.setFilled(2);
        clocks.save(clock);

        entityManager.clear();

        var reloadedNpcs = npcs.findByCampaignIdOrderByNameAscIdAsc(c.getId());
        assertThat(reloadedNpcs).hasSize(1);
        assertThat(reloadedNpcs.getFirst().getName()).isEqualTo("Mira");
        assertThat(reloadedNpcs.getFirst().getDisposition()).isEqualTo(WorldDisposition.FRIENDLY);
        assertThat(reloadedNpcs.getFirst().getStatus()).isEqualTo(WorldNpcStatus.ALIVE);
        assertThat(reloadedNpcs.getFirst().getSecret()).isEqualTo("Works for the Ring");

        assertThat(relationships.findByCampaignIdOrderBySortOrderAscIdAsc(c.getId())).hasSize(1);
        assertThat(clocks.findByFactionIdOrderBySortOrderAscIdAsc(f.getId())).hasSize(1);
    }
}
