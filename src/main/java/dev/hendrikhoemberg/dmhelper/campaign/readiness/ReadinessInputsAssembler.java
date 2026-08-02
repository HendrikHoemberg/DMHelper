package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ReadinessInputsAssembler {

    private final SceneRepository scenes;
    private final PartyMemberRepository partyMembers;

    public ReadinessInputsAssembler(SceneRepository scenes, PartyMemberRepository partyMembers) {
        this.scenes = scenes;
        this.partyMembers = partyMembers;
    }

    public ReadinessInputs fromCampaign(UUID campaignId) {
        int partyMemberCount = partyMembers.findByCampaignIdOrderByCharacterNameAsc(campaignId).size();
        List<ReadinessInputs.SceneInput> sceneInputs =
                scenes.findByCampaignIdOrderByChapterAndSort(campaignId).stream()
                        .map(ReadinessInputsAssembler::sceneInput)
                        .toList();

        return new ReadinessInputs(campaignId, partyMemberCount, sceneInputs, List.of(), List.of());
    }

    /**
     * The same inputs for many campaigns in a fixed number of queries, for the campaign index.
     * Deliberately builds {@link ReadinessInputs} rather than answering "ready?" directly:
     * the caller feeds these to the same {@link CampaignReadinessService#compute} the single
     * campaign path uses, so there is one definition of readiness, not one per call site.
     */
    public Map<UUID, ReadinessInputs> fromCampaigns(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) return Map.of();

        Map<UUID, Integer> partySizes = new HashMap<>();
        for (var member : partyMembers.findByCampaignIdIn(campaignIds)) {
            partySizes.merge(member.getCampaign().getId(), 1, Integer::sum);
        }

        Map<UUID, List<ReadinessInputs.SceneInput>> scenesByCampaign = new HashMap<>();
        for (Scene scene : scenes.findForReadinessByCampaignIds(campaignIds)) {
            UUID campaignId = scene.getChapter().getAdventure().getCampaign().getId();
            scenesByCampaign.computeIfAbsent(campaignId, ignored -> new ArrayList<>())
                    .add(sceneInput(scene));
        }

        Map<UUID, ReadinessInputs> inputs = new HashMap<>();
        for (UUID campaignId : campaignIds) {
            inputs.put(campaignId, new ReadinessInputs(
                    campaignId,
                    partySizes.getOrDefault(campaignId, 0),
                    scenesByCampaign.getOrDefault(campaignId, List.of()),
                    List.of(), List.of()));
        }
        return inputs;
    }

    private static ReadinessInputs.SceneInput sceneInput(Scene scene) {
        boolean hostile = false;
        boolean anyStatblock = false;
        List<String> unresolved = new ArrayList<>();
        for (SceneParticipant p : scene.getParticipants()) {
            boolean h = p.getDisposition() == SceneParticipantDisposition.HOSTILE;
            hostile = hostile || h;
            if (p.getStatBlock() != null) {
                anyStatblock = true;
            } else if (h) {
                unresolved.add(participantLabel(p));
            }
        }
        return new ReadinessInputs.SceneInput(
                scene.getId(), scene.getTitle(), hostile,
                scene.getEncounter() != null, anyStatblock, unresolved,
                scene.getMapRequirement(), scene.getMap() != null);
    }

    private static String participantLabel(SceneParticipant p) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) return p.getDisplayName().trim();
        return "Unnamed participant";
    }
}
