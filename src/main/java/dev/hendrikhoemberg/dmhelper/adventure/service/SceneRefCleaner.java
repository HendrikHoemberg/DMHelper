package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotation;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Transactional
public class SceneRefCleaner {

    private final SceneRepository sceneRepository;
    private final SceneTransitionRepository transitionRepository;
    private final SceneLinkRepository linkRepository;
    private final SourceAnnotationRepository sourceAnnotationRepository;
    private final CampaignPackageKeyService packageKeyService;
    private final EntityManager em;

    public SceneRefCleaner(SceneRepository sceneRepository,
                           SceneTransitionRepository transitionRepository,
                           SceneLinkRepository linkRepository,
                           SourceAnnotationRepository sourceAnnotationRepository,
                           CampaignPackageKeyService packageKeyService,
                           EntityManager em) {
        this.sceneRepository = sceneRepository;
        this.transitionRepository = transitionRepository;
        this.linkRepository = linkRepository;
        this.sourceAnnotationRepository = sourceAnnotationRepository;
        this.packageKeyService = packageKeyService;
        this.em = em;
    }

    public void detachEncounter(UUID encounterId) {
        for (Scene s : sceneRepository.findByEncounterId(encounterId)) {
            s.setEncounter(null);
            sceneRepository.save(s);
        }
    }

    public void detachMap(UUID mapId) {
        for (Scene s : sceneRepository.findByMapId(mapId)) {
            s.setMap(null);
            s.setPinX(null);
            s.setPinY(null);
            sceneRepository.save(s);
        }
    }

    public void detachStatBlock(UUID statBlockId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getStatBlocks().removeIf(sb -> sb.getId().equals(statBlockId))) {
                sceneRepository.save(s);
            }
        }
    }

    public void detachHandout(UUID handoutId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getHandouts().removeIf(h -> h.getId().equals(handoutId))) {
                sceneRepository.save(s);
            }
        }
    }

    public void detachScene(UUID sceneId) {
        for (SceneTransition t : transitionRepository.findByTargetSceneId(sceneId)) {
            t.setTargetScene(null);
            transitionRepository.save(t);
        }
        for (SceneLink l : linkRepository.findByTargetId(sceneId)) {
            if ("SCENE".equals(l.getTargetType())) {
                linkRepository.delete(l);
            }
        }
        cleanSourceAnnotations("SCENE", sceneId);
        for (SceneTransition t : transitionRepository.findBySceneIdOrderBySortOrderAsc(sceneId)) {
            cleanSourceAnnotations("SCENE_TRANSITION", t.getId());
        }
        em.flush();
    }

    private void cleanSourceAnnotations(String ownerType, UUID ownerId) {
        List<SourceAnnotation> annotations = sourceAnnotationRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
        if (!annotations.isEmpty()) {
            List<UUID> ids = annotations.stream().map(SourceAnnotation::getId).collect(Collectors.toList());
            packageKeyService.deleteBindings(annotations.get(0).getCampaign().getId(), CampaignContentType.SOURCE_ANNOTATION, ids);
            sourceAnnotationRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
        }
    }
}
