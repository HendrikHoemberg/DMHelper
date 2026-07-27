package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransitionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SceneTransitionService {

    private final SceneTransitionRepository transitionRepository;
    private final CampaignRepository campaignRepository;
    private final SessionActivityRecorder sessionActivity;

    public SceneTransitionService(SceneTransitionRepository transitionRepository,
                                   CampaignRepository campaignRepository,
                                   SessionActivityRecorder sessionActivity) {
        this.transitionRepository = transitionRepository;
        this.campaignRepository = campaignRepository;
        this.sessionActivity = sessionActivity;
    }

    public Scene followTransition(UUID campaignId, UUID transitionId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        SceneTransition transition = transitionRepository.findById(transitionId)
                .orElseThrow(() -> new NotFoundException("Transition not found"));

        if (!transition.getScene().getChapter().getAdventure().getCampaign().getId().equals(campaignId)) {
            throw new NotFoundException("Transition not found in campaign");
        }

        if (transition.getTargetScene() == null) {
            throw new IllegalArgumentException("This transition has no runtime target scene");
        }

        Scene target = transition.getTargetScene();
        campaign.setCurrentSceneId(target.getId());
        campaignRepository.save(campaign);

        sessionActivity.sceneSelected(campaignId, target);
        // The controller returns this entity as a DTO after this transaction ends.
        // Materialize every association that SessionApiController.sceneData reads.
        Hibernate.initialize(target);
        Hibernate.initialize(target.getMap());
        Hibernate.initialize(target.getEncounter());
        return target;
    }
}
