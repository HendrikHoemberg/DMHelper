package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneParticipantDto;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class PreviewReadinessAssembler {

    public ReadinessInputs fromManifest(CampaignManifestV2 manifest) {
        List<ReadinessInputs.SceneInput> scenes = new ArrayList<>();
        List<ReadinessInputs.LinkInput> links = new ArrayList<>();
        if (manifest.adventures() != null) {
            manifest.adventures().forEach(adv -> {
                if (adv.chapters() == null) return;
                adv.chapters().forEach(ch -> {
                    if (ch.scenes() == null) return;
                    ch.scenes().forEach(scene -> {
                        scenes.add(sceneInput(scene));
                        if (scene.transitions() != null) {
                            scene.transitions().forEach(t -> {
                                boolean resolved = t.targetSceneRef() != null
                                        || (t.externalDestination() != null && !t.externalDestination().isBlank());
                                if (!resolved) {
                                    links.add(new ReadinessInputs.LinkInput(
                                            scene.title() + ":" + t.key(),
                                            "Transition '" + t.label() + "' in " + scene.title()
                                                    + "' has no resolved target",
                                            false));
                                }
                            });
                        }
                    });
                });
            });
        }

        List<ReadinessInputs.AssetInput> assets = new ArrayList<>();
        if (manifest.handouts() != null) {
            for (HandoutDto h : manifest.handouts()) {
                assets.add(new ReadinessInputs.AssetInput(
                        null, h.title(), kindOf(h), safetyOf(h), h.presented()));
            }
        }

        List<ReadinessInputs.OmissionInput> omissions = new ArrayList<>();
        if (manifest.metadata() != null && manifest.metadata().conversionOmissions() != null) {
            manifest.metadata().conversionOmissions().forEach(o ->
                    omissions.add(new ReadinessInputs.OmissionInput(o.area(), o.reason())));
        }

        return new ReadinessInputs(scenes, assets, links, omissions);
    }

    private static ReadinessInputs.SceneInput sceneInput(SceneDto scene) {
        boolean hostile = false;
        boolean anyStatblock = false;
        List<String> unresolved = new ArrayList<>();
        if (scene.participants() != null) {
            for (SceneParticipantDto p : scene.participants()) {
                boolean h = "HOSTILE".equals(p.disposition());
                hostile = hostile || h;
                if (p.statblockRef() != null) {
                    anyStatblock = true;
                } else if (h) {
                    unresolved.add(p.displayName() == null ? "Unnamed participant" : p.displayName());
                }
            }
        }
        SceneMapRequirement req = scene.mapRequirement() == null || scene.mapRequirement().isBlank()
                ? null : SceneMapRequirement.valueOf(scene.mapRequirement());
        return new ReadinessInputs.SceneInput(
                null, scene.title(), hostile,
                scene.encounterRef() != null, anyStatblock, unresolved,
                req, scene.mapRef() != null);
    }

    private static Handout.AssetKind kindOf(HandoutDto h) {
        return h.assetKind() == null || h.assetKind().isBlank()
                ? Handout.AssetKind.SOURCE_PAGE : Handout.AssetKind.valueOf(h.assetKind());
    }

    private static Handout.SafetyClassification safetyOf(HandoutDto h) {
        if (h.safetyClassification() != null) {
            return Handout.SafetyClassification.valueOf(h.safetyClassification());
        }
        return h.dmOnly() ? Handout.SafetyClassification.DM_SOURCE
                : Handout.SafetyClassification.UNREVIEWED;
    }
}
