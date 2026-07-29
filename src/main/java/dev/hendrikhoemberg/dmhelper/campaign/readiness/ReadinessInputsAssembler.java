package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
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
        List<ReadinessInputs.SceneInput> sceneInputs = new ArrayList<>();
        for (Scene scene : scenes.findByCampaignIdOrderByChapterAndSort(campaignId)) {
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
            sceneInputs.add(new ReadinessInputs.SceneInput(
                    scene.getId(), scene.getTitle(), hostile,
                    scene.getEncounter() != null, anyStatblock, unresolved,
                    scene.getMapRequirement(), scene.getMap() != null));
        }

        return new ReadinessInputs(campaignId, partyMemberCount, sceneInputs, List.of(), List.of());
    }

    private static String participantLabel(SceneParticipant p) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) return p.getDisplayName().trim();
        return "Unnamed participant";
    }
}
