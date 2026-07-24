package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CampaignReadinessService {

    public CampaignReadinessReport compute(ReadinessInputs inputs, Set<String> acceptedKeys) {
        List<ReadinessItem> items = new ArrayList<>();

        for (ReadinessInputs.SceneInput scene : inputs.scenes()) {
            if (scene.hostile() && !scene.encounterOperational()) {
                items.add(blockerOrAccepted(
                        "encounter:" + scene.id(), ReadinessCategory.ENCOUNTER,
                        "No runnable encounter: " + scene.title(),
                        "Hostile scene has no linked encounter and no participant statblocks to seed one.",
                        ReadinessRepairKind.EDIT_SCENE_PARTICIPANTS, scene.id(), acceptedKeys));
            } else if (scene.hostile() && !scene.hasLinkedEncounter() && scene.anyParticipantHasStatblock()) {
                items.add(new ReadinessItem(
                        "encounter:" + scene.id(), ReadinessCategory.NEXT_ACTION, ReadinessState.RESOLVED,
                        "Ready to seed encounter: " + scene.title(),
                        "Seed the encounter from its participants before the session.",
                        ReadinessRepairKind.SEED_ENCOUNTER, scene.id()));
            }

            if (scene.hostile() && !scene.unresolvedStatblockParticipants().isEmpty()) {
                items.add(blockerOrAccepted(
                        "statblock:" + scene.id(), ReadinessCategory.STATBLOCK,
                        "Participants missing statblocks: " + scene.title(),
                        String.join(", ", scene.unresolvedStatblockParticipants()),
                        ReadinessRepairKind.EDIT_SCENE_PARTICIPANTS, scene.id(), acceptedKeys));
            }

            if (scene.effectiveMapRequired() && !scene.hasMap()) {
                items.add(blockerOrAccepted(
                        "map:" + scene.id(), ReadinessCategory.MAP,
                        "Scene requires a map: " + scene.title(),
                        "No playable or reference map is linked to this scene.",
                        ReadinessRepairKind.ASSIGN_SCENE_MAP, scene.id(), acceptedKeys));
            }
        }

        for (ReadinessInputs.AssetInput asset : inputs.presentedAssets()) {
            if (asset.linkedForPresentation() && !asset.safety().isPresentable()) {
                items.add(blockerOrAccepted(
                        "asset:" + asset.id(), ReadinessCategory.ASSET,
                        "Unsafe asset linked for presentation: " + asset.title(),
                        "Classified " + asset.safety() + " / " + asset.kind()
                                + "; not player-safe. Review or reclassify before presenting.",
                        ReadinessRepairKind.REVIEW_HANDOUT_SAFETY, asset.id(), acceptedKeys));
            }
        }

        for (ReadinessInputs.LinkInput link : inputs.runtimeLinks()) {
            if (!link.resolved()) {
                items.add(new ReadinessItem(
                        "link:" + link.key(), ReadinessCategory.RUNTIME_LINK,
                        acceptedKeys.contains("link:" + link.key())
                                ? ReadinessState.ACCEPTED : ReadinessState.RESOLVED,
                        "Unresolved link: " + link.description(),
                        "Advisory: this runtime link has no resolved target.",
                        ReadinessRepairKind.NONE, null));
            }
        }

        for (ReadinessInputs.OmissionInput omission : inputs.omissions()) {
            String key = "omission:" + omission.area();
            items.add(new ReadinessItem(
                    key, ReadinessCategory.OMISSION,
                    acceptedKeys.contains(key) ? ReadinessState.ACCEPTED : ReadinessState.RESOLVED,
                    "Converter omitted: " + omission.area(),
                    omission.reason() == null ? "Declared omission." : omission.reason(),
                    ReadinessRepairKind.ACCEPT_ITEM, null));
        }

        return new CampaignReadinessReport(items);
    }

    private ReadinessItem blockerOrAccepted(String key, ReadinessCategory category,
                                            String title, String detail,
                                            ReadinessRepairKind repairKind,
                                            java.util.UUID targetId, Set<String> acceptedKeys) {
        ReadinessState state = acceptedKeys.contains(key)
                ? ReadinessState.ACCEPTED : ReadinessState.BLOCKER;
        return new ReadinessItem(key, category, state, title, detail, repairKind, targetId);
    }
}
