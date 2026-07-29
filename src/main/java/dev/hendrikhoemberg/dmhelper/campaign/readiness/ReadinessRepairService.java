package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ReadinessRepairService {

    private final SceneRepository scenes;

    public ReadinessRepairService(SceneRepository scenes) {
        this.scenes = scenes;
    }

    @Transactional(readOnly = true)
    public String hrefFor(UUID campaignId, ReadinessRepairKind kind, UUID targetId) {
        return switch (kind) {
            case SEED_ENCOUNTER, EDIT_SCENE_PARTICIPANTS, ASSIGN_SCENE_MAP -> {
                var scene = scenes.findById(targetId).orElse(null);
                if (scene == null) yield null;
                var adventureId = scene.getChapter().getAdventure().getId();
                yield "/campaigns/" + campaignId + "/adventures/" + adventureId + "/scenes/" + targetId;
            }
            case CLASSIFY_ASSET_KIND ->
                    "/campaigns/" + campaignId + "/handouts/" + targetId;
            case OPEN_PARTY_ROSTER ->
                    "/campaigns/" + campaignId + "/party";
            case ACCEPT_ITEM, NONE -> null;
        };
    }
}
