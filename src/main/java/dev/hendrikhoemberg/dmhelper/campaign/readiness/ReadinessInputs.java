package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import java.util.List;
import java.util.UUID;

public record ReadinessInputs(
        List<SceneInput> scenes,
        List<AssetInput> presentedAssets,
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

    public record AssetInput(
            UUID id,
            String title,
            Handout.AssetKind kind,
            Handout.SafetyClassification safety,
            boolean linkedForPresentation) {}

    public record LinkInput(String key, String description, boolean resolved) {}

    public record OmissionInput(String area, String reason) {}
}
