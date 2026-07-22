package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds an encounter from the monsters a scene already describes.
 *
 * <p>An imported campaign carries zero encounters but 22 scenes with hostile participants, so
 * combat started with the DM retyping what the app already knew. Both halves of the job
 * existed: EncounterService.create makes the encounter, and addFromLibrary resolves a
 * statblock, parses its HP, creates N combatants, groups them and attaches them to the main
 * wave. A SceneParticipant carries exactly statBlock + quantity + displayName. This class is
 * the seam between them, so neither adventure nor encounter has to know about the other.
 */
@Service
public class SceneEncounterSeedService {

    /**
     * @param skippedParticipants display names of participants with no statblock. The real
     *                            package has 2 of 67. They are reported rather than given
     *                            invented HP, and rather than silently disappearing.
     * @param alreadyExisted      true when the scene was already linked to an encounter and
     *                            nothing was created or added.
     */
    public record SeedResult(UUID encounterId, String encounterName, int combatantsAdded,
                             List<String> skippedParticipants, boolean alreadyExisted) {}

    private final AdventureService adventures;
    private final EncounterService encounters;

    public SceneEncounterSeedService(AdventureService adventures, EncounterService encounters) {
        this.adventures = adventures;
        this.encounters = encounters;
    }

    /** True when this scene has at least one participant the app can turn into a combatant. */
    @Transactional(readOnly = true)
    public boolean canSeed(Scene scene) {
        if (scene.getParticipants() == null) {
            return false;
        }
        Hibernate.initialize(scene.getParticipants());
        return scene.getParticipants().stream().anyMatch(p -> p.getStatBlock() != null);
    }

    @Transactional
    public SeedResult seedFromScene(UUID campaignId, UUID sceneId) {
        Scene scene = adventures.findSceneById(sceneId);
        Hibernate.initialize(scene.getParticipants());
        for (SceneParticipant participant : scene.getParticipants()) {
            Hibernate.initialize(participant.getStatBlock());
        }

        // Re-running the action must not silently duplicate the encounter or double its
        // combatants -- a DM who clicks twice at the table would otherwise be running two.
        if (scene.getEncounter() != null) {
            Hibernate.initialize(scene.getEncounter());
            return new SeedResult(scene.getEncounter().getId(), scene.getEncounter().getName(),
                    0, List.of(), true);
        }

        String name = "Encounter: " + scene.getTitle();
        UUID mapId = scene.getMap() != null ? scene.getMap().getId() : null;
        var encounter = encounters.create(campaignId, new EncounterService.CreateRequest(name, mapId));

        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (SceneParticipant participant : scene.getParticipants()) {
            if (participant.getStatBlock() == null) {
                skipped.add(participant.getDisplayName());
                continue;
            }
            // displayName as groupName keeps the scene's own wording ("Späher der Redbrands")
            // rather than falling back to the statblock's catalog name.
            var combatants = encounters.addFromLibrary(encounter.id(),
                    new EncounterService.AddFromLibraryRequest(
                            participant.getStatBlock().getId(),
                            Math.max(1, participant.getQuantity()),
                            participant.getDisplayName(),
                            null, null, null, null));
            added += combatants.size();
        }

        adventures.linkEncounter(sceneId, encounter.id());
        return new SeedResult(encounter.id(), name, added, List.copyOf(skipped), false);
    }
}
