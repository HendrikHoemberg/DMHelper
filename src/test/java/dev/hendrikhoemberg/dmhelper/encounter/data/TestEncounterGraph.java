package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.EntityManager;

public final class TestEncounterGraph {

    private TestEncounterGraph() {}

    public static Combatant persistCombatant(EntityManager entityManager) {
        Campaign campaign = new Campaign();
        campaign.setName("Test Campaign");
        entityManager.persist(campaign);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Test Map");
        map.setSortOrder(0);
        entityManager.persist(map);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Test Encounter");
        entityManager.persist(encounter);

        Combatant combatant = new Combatant();
        combatant.setEncounter(encounter);
        combatant.setName("Test Combatant");
        combatant.setSortOrder(0);
        combatant.setMaxHp(10);
        combatant.setCurrentHp(10);
        combatant.setKind("NPC");
        entityManager.persist(combatant);

        entityManager.flush();
        return combatant;
    }

    public static EncounterTokenPlacement placement(Combatant combatant, GameMap map,
                                                     int positionX, int positionY) {
        EncounterTokenPlacement p = new EncounterTokenPlacement();
        p.setCombatant(combatant);
        p.setEncounter(combatant.getEncounter());
        p.setMap(map);
        p.setPositionX(positionX);
        p.setPositionY(positionY);
        return p;
    }
}
