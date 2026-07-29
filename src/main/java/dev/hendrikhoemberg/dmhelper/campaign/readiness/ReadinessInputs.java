package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import java.util.List;
import java.util.UUID;

public record ReadinessInputs(
        UUID campaignId,
        int partyMemberCount,
        List<SceneInput> scenes,
        List<LinkInput> runtimeLinks,
        List<OmissionInput> omissions) {

    public record SceneInput(
            UUID id,
            String title,
            boolean hostile,
            boolean hasLinkedEncounter,
            boolean anyParticipantHasStatblock,
            List<String> unresolvedStatblockParticipants,
            SceneMapRequirement declaredMapRequirement,
            boolean hasMap) {

        public boolean effectiveMapRequired() {
            if (declaredMapRequirement == SceneMapRequirement.REQUIRED) return true;
            if (declaredMapRequirement == SceneMapRequirement.OPTIONAL
                    || declaredMapRequirement == SceneMapRequirement.NONE) return false;
            return hostile;
        }

        public boolean encounterOperational() {
            return hasLinkedEncounter || anyParticipantHasStatblock;
        }
    }

    public record LinkInput(String key, String description, boolean resolved) {}

    public record OmissionInput(String area, String reason) {}
}
