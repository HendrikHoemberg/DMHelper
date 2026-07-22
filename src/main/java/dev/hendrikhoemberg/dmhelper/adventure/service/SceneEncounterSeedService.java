package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SceneEncounterSeedService {

    public record SeedResult(UUID encounterId, String encounterName, int combatantsAdded,
                             List<String> skippedParticipants, boolean alreadyExisted) {}

    private final AdventureService adventures;
    private final EncounterService encounters;
    private final SceneRepository scenes;

    public SceneEncounterSeedService(AdventureService adventures,
                                     EncounterService encounters,
                                     SceneRepository scenes) {
        this.adventures = adventures;
        this.encounters = encounters;
        this.scenes = scenes;
    }

    @Transactional(readOnly = true)
    public boolean canSeed(UUID campaignId, UUID sceneId) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        Hibernate.initialize(scene.getParticipants());
        for (SceneParticipant participant : scene.getParticipants()) {
            Hibernate.initialize(participant.getStatBlock());
        }
        return scene.getEncounter() == null
                && scene.getParticipants().stream().anyMatch(p -> p.getStatBlock() != null);
    }

    @Transactional
    public SeedResult seedFromScene(UUID campaignId, UUID sceneId) {
        // Serialize creation for one scene. The cockpit action is a network request, so a
        // double-click can otherwise pass the null encounter check in two transactions and
        // leave an orphaned duplicate encounter behind.
        Scene scene = scenes.findByIdAndCampaignIdForEncounterSeed(campaignId, sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));
        Hibernate.initialize(scene.getParticipants());
        for (SceneParticipant participant : scene.getParticipants()) {
            Hibernate.initialize(participant.getStatBlock());
        }

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
            String participantName = participantLabel(participant);
            if (participant.getStatBlock() == null) {
                skipped.add(participantName);
                continue;
            }
            var combatants = encounters.addFromLibrary(encounter.id(),
                    new EncounterService.AddFromLibraryRequest(
                            participant.getStatBlock().getId(),
                            Math.max(1, participant.getQuantity()),
                            participantName,
                            null, null, null, null));
            added += combatants.size();
        }

        adventures.linkEncounter(sceneId, encounter.id());
        return new SeedResult(encounter.id(), name, added, List.copyOf(skipped), false);
    }

    private Scene findSceneInCampaign(UUID campaignId, UUID sceneId) {
        return scenes.findByIdAndCampaignId(campaignId, sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));
    }

    private String participantLabel(SceneParticipant participant) {
        if (participant.getDisplayName() != null && !participant.getDisplayName().isBlank()) {
            return participant.getDisplayName().trim();
        }
        if (participant.getStatBlock() != null
                && participant.getStatBlock().getName() != null
                && !participant.getStatBlock().getName().isBlank()) {
            return participant.getStatBlock().getName().trim();
        }
        return "Unnamed participant";
    }
}
