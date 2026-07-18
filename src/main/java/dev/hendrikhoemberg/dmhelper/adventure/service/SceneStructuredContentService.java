package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SceneStructuredContentService {

    private final SceneRepository sceneRepository;
    private final SceneSectionRepository sectionRepository;
    private final SceneCheckRepository checkRepository;
    private final SceneParticipantRepository participantRepository;
    private final SceneLinkRepository linkRepository;
    private final SceneTransitionRepository transitionRepository;
    private final RollableTableRepository rollableTableRepository;
    private final TableReferenceResolver referenceResolver;
    private final EntityManager em;

    public SceneStructuredContentService(SceneRepository sceneRepository,
                                          SceneSectionRepository sectionRepository,
                                          SceneCheckRepository checkRepository,
                                          SceneParticipantRepository participantRepository,
                                          SceneLinkRepository linkRepository,
                                          SceneTransitionRepository transitionRepository,
                                          RollableTableRepository rollableTableRepository,
                                          TableReferenceResolver referenceResolver,
                                          EntityManager em) {
        this.sceneRepository = sceneRepository;
        this.sectionRepository = sectionRepository;
        this.checkRepository = checkRepository;
        this.participantRepository = participantRepository;
        this.linkRepository = linkRepository;
        this.transitionRepository = transitionRepository;
        this.rollableTableRepository = rollableTableRepository;
        this.referenceResolver = referenceResolver;
        this.em = em;
    }

    public record SceneMetadataCommand(
            String summary, String sourceLocator, String tags, String mapRegionKey) {}

    public record SceneSectionCommand(
            SceneSectionKind kind, String label, String body,
            String sourceLocator, int sortOrder) {}

    public record SceneCheckCommand(
            String label, String ability, String skill, Integer dc,
            SceneCheckVisibility visibility, String success, String failure,
            String partial, String ruleScope, String ruleRuleset,
            String ruleSourceKey, String sourceLocator, int sortOrder) {}

    public record SceneParticipantCommand(
            String displayName, int quantity, SceneParticipantDisposition disposition,
            String placementHint, UUID statBlockId, UUID noteId,
            String sourceLocator, int sortOrder) {}

    public record SceneTransitionCommand(
            SceneTransitionKind kind, String label, UUID targetSceneId,
            String externalDestination, String condition, String dmNote,
            String sourceLocator, int sortOrder) {}

    public record SceneLinkCommand(
            SceneLinkRole role, SceneLinkTargetScope targetScope, String targetType, UUID targetId,
            String catalogRuleset, String catalogSourceKey, String displayText, String condition,
            int sortOrder) {}

    private Scene findSceneInCampaign(UUID campaignId, UUID sceneId) {
        return sceneRepository.findByIdAndCampaignId(campaignId, sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));
    }

    public void updateMetadata(UUID campaignId, UUID sceneId, SceneMetadataCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        scene.setSummary(cmd.summary());
        scene.setSourceLocator(cmd.sourceLocator());
        scene.setTags(normalizeTags(cmd.tags()));
        scene.setMapRegionKey(cmd.mapRegionKey());
        sceneRepository.save(scene);
    }

    private String normalizeTags(String tags) {
        return TagCodec.format(TagCodec.parse(tags));
    }

    // ---- Sections ----

    public SceneSection addSection(UUID campaignId, UUID sceneId, SceneSectionCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        if (cmd.label() == null || cmd.body() == null) {
            throw new IllegalArgumentException("Section label and body are required");
        }
        SceneSection section = new SceneSection();
        section.setScene(scene);
        section.setKind(cmd.kind());
        section.setLabel(cmd.label());
        section.setBody(cmd.body());
        section.setSourceLocator(cmd.sourceLocator());
        section.setSortOrder(cmd.sortOrder());
        scene.getSections().add(section);
        return sectionRepository.save(section);
    }

    public void updateSection(UUID campaignId, UUID sceneId, UUID sectionId, SceneSectionCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneSection section = scene.getSections().stream()
                .filter(s -> s.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Section not found in scene"));
        section.setKind(cmd.kind());
        section.setLabel(cmd.label());
        section.setBody(cmd.body());
        section.setSourceLocator(cmd.sourceLocator());
        section.setSortOrder(cmd.sortOrder());
        sectionRepository.save(section);
    }

    public void deleteSection(UUID campaignId, UUID sceneId, UUID sectionId) {
        findSceneInCampaign(campaignId, sceneId);
        em.createQuery("DELETE FROM SceneSection s WHERE s.id = :id AND s.scene.id = :sceneId")
                .setParameter("id", sectionId)
                .setParameter("sceneId", sceneId)
                .executeUpdate();
        em.flush();
        em.clear();
        var remaining = sectionRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        sectionRepository.saveAll(remaining);
    }

    @Transactional(readOnly = true)
    public List<SceneSection> getSections(UUID campaignId, UUID sceneId) {
        findSceneInCampaign(campaignId, sceneId);
        return sectionRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
    }

    // ---- Checks ----

    public SceneCheck addCheck(UUID campaignId, UUID sceneId, SceneCheckCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneCheck check = new SceneCheck();
        check.setScene(scene);
        check.setLabel(cmd.label());
        check.setAbility(cmd.ability());
        check.setSkill(cmd.skill());
        check.setDc(cmd.dc());
        check.setVisibility(cmd.visibility());
        check.setSuccess(cmd.success());
        check.setFailure(cmd.failure());
        check.setPartial(cmd.partial());
        check.setRuleScope(cmd.ruleScope());
        check.setRuleRuleset(cmd.ruleRuleset());
        check.setRuleSourceKey(cmd.ruleSourceKey());
        check.setSourceLocator(cmd.sourceLocator());
        check.setSortOrder(cmd.sortOrder());
        scene.getChecks().add(check);
        return checkRepository.save(check);
    }

    public void updateCheck(UUID campaignId, UUID sceneId, UUID checkId, SceneCheckCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneCheck check = scene.getChecks().stream()
                .filter(c -> c.getId().equals(checkId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Check not found in scene"));
        check.setLabel(cmd.label());
        check.setAbility(cmd.ability());
        check.setSkill(cmd.skill());
        check.setDc(cmd.dc());
        check.setVisibility(cmd.visibility());
        check.setSuccess(cmd.success());
        check.setFailure(cmd.failure());
        check.setPartial(cmd.partial());
        check.setRuleScope(cmd.ruleScope());
        check.setRuleRuleset(cmd.ruleRuleset());
        check.setRuleSourceKey(cmd.ruleSourceKey());
        check.setSourceLocator(cmd.sourceLocator());
        check.setSortOrder(cmd.sortOrder());
        checkRepository.save(check);
    }

    public void deleteCheck(UUID campaignId, UUID sceneId, UUID checkId) {
        findSceneInCampaign(campaignId, sceneId);
        em.createQuery("DELETE FROM SceneCheck c WHERE c.id = :id AND c.scene.id = :sceneId")
                .setParameter("id", checkId)
                .setParameter("sceneId", sceneId)
                .executeUpdate();
        em.flush();
        em.clear();
        var remaining = checkRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        checkRepository.saveAll(remaining);
    }

    @Transactional(readOnly = true)
    public List<SceneCheck> getChecks(UUID campaignId, UUID sceneId) {
        findSceneInCampaign(campaignId, sceneId);
        return checkRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
    }

    // ---- Participants ----

    public SceneParticipant addParticipant(UUID campaignId, UUID sceneId, SceneParticipantCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneParticipant participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName(cmd.displayName());
        participant.setQuantity(cmd.quantity());
        participant.setDisposition(cmd.disposition());
        participant.setPlacementHint(cmd.placementHint());
        if (cmd.statBlockId() != null) {
            var sb = new dev.hendrikhoemberg.dmhelper.library.data.StatBlock();
            sb.setId(cmd.statBlockId());
            participant.setStatBlock(sb);
        }
        if (cmd.noteId() != null) {
            var note = new dev.hendrikhoemberg.dmhelper.notes.data.Note();
            note.setId(cmd.noteId());
            participant.setNote(note);
        }
        participant.setSourceLocator(cmd.sourceLocator());
        participant.setSortOrder(cmd.sortOrder());
        scene.getParticipants().add(participant);
        return participantRepository.save(participant);
    }

    public void updateParticipant(UUID campaignId, UUID sceneId, UUID participantId, SceneParticipantCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneParticipant participant = scene.getParticipants().stream()
                .filter(p -> p.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Participant not found in scene"));
        participant.setDisplayName(cmd.displayName());
        participant.setQuantity(cmd.quantity());
        participant.setDisposition(cmd.disposition());
        participant.setPlacementHint(cmd.placementHint());
        participant.setSourceLocator(cmd.sourceLocator());
        participant.setSortOrder(cmd.sortOrder());
        participantRepository.save(participant);
    }

    public void deleteParticipant(UUID campaignId, UUID sceneId, UUID participantId) {
        findSceneInCampaign(campaignId, sceneId);
        em.createQuery("DELETE FROM SceneParticipant p WHERE p.id = :id AND p.scene.id = :sceneId")
                .setParameter("id", participantId)
                .setParameter("sceneId", sceneId)
                .executeUpdate();
        em.flush();
        em.clear();
        var remaining = participantRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        participantRepository.saveAll(remaining);
    }

    @Transactional(readOnly = true)
    public List<SceneParticipant> getParticipants(UUID campaignId, UUID sceneId) {
        findSceneInCampaign(campaignId, sceneId);
        return participantRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
    }

    // ---- Links ----

    public SceneLink addLink(UUID campaignId, UUID sceneId, SceneLinkCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        validateTableLink(campaignId, cmd);
        SceneLink link = new SceneLink();
        link.setScene(scene);
        link.setRole(cmd.role());
        link.setTargetScope(cmd.targetScope());
        link.setTargetType(cmd.targetType());
        link.setTargetId(cmd.targetId());
        link.setCatalogRuleset(cmd.catalogRuleset());
        link.setCatalogSourceKey(cmd.catalogSourceKey());
        link.setDisplayText(cmd.displayText());
        link.setCondition(cmd.condition());
        link.setSortOrder(cmd.sortOrder());
        scene.getLinks().add(link);
        return linkRepository.save(link);
    }

    public void updateLink(UUID campaignId, UUID sceneId, UUID linkId, SceneLinkCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        validateTableLink(campaignId, cmd);
        SceneLink link = scene.getLinks().stream()
                .filter(l -> l.getId().equals(linkId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Link not found in scene"));
        link.setRole(cmd.role());
        link.setTargetScope(cmd.targetScope());
        link.setTargetType(cmd.targetType());
        link.setTargetId(cmd.targetId());
        link.setCatalogRuleset(cmd.catalogRuleset());
        link.setCatalogSourceKey(cmd.catalogSourceKey());
        link.setDisplayText(cmd.displayText());
        link.setCondition(cmd.condition());
        link.setSortOrder(cmd.sortOrder());
        linkRepository.save(link);
    }

    public void deleteLink(UUID campaignId, UUID sceneId, UUID linkId) {
        findSceneInCampaign(campaignId, sceneId);
        em.createQuery("DELETE FROM SceneLink l WHERE l.id = :id AND l.scene.id = :sceneId")
                .setParameter("id", linkId)
                .setParameter("sceneId", sceneId)
                .executeUpdate();
        em.flush();
        em.clear();
        var remaining = linkRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        linkRepository.saveAll(remaining);
    }

    @Transactional(readOnly = true)
    public List<SceneLink> getLinks(UUID campaignId, UUID sceneId) {
        findSceneInCampaign(campaignId, sceneId);
        return linkRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
    }

    // ---- Transitions ----

    public SceneTransition addTransition(UUID campaignId, UUID sceneId, SceneTransitionCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        validateTransitionCommand(campaignId, cmd);
        SceneTransition transition = new SceneTransition();
        transition.setScene(scene);
        transition.setKind(cmd.kind());
        transition.setLabel(cmd.label());
        if (cmd.targetSceneId() != null) {
            transition.setTargetScene(findSceneInCampaign(campaignId, cmd.targetSceneId()));
        }
        transition.setExternalDestination(cmd.externalDestination());
        transition.setCondition(cmd.condition());
        transition.setDmNote(cmd.dmNote());
        transition.setSourceLocator(cmd.sourceLocator());
        transition.setSortOrder(cmd.sortOrder());
        scene.getTransitions().add(transition);
        return transitionRepository.save(transition);
    }

    public void updateTransition(UUID campaignId, UUID sceneId, UUID transitionId, SceneTransitionCommand cmd) {
        Scene scene = findSceneInCampaign(campaignId, sceneId);
        SceneTransition transition = scene.getTransitions().stream()
                .filter(t -> t.getId().equals(transitionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Transition not found in scene"));
        validateTransitionCommand(campaignId, cmd);
        transition.setKind(cmd.kind());
        transition.setLabel(cmd.label());
        transition.setTargetScene(cmd.targetSceneId() != null ? findSceneInCampaign(campaignId, cmd.targetSceneId()) : null);
        transition.setExternalDestination(cmd.externalDestination());
        transition.setCondition(cmd.condition());
        transition.setDmNote(cmd.dmNote());
        transition.setSourceLocator(cmd.sourceLocator());
        transition.setSortOrder(cmd.sortOrder());
        transitionRepository.save(transition);
    }

    public void deleteTransition(UUID campaignId, UUID sceneId, UUID transitionId) {
        findSceneInCampaign(campaignId, sceneId);
        em.createQuery("DELETE FROM SceneTransition t WHERE t.id = :id AND t.scene.id = :sceneId")
                .setParameter("id", transitionId)
                .setParameter("sceneId", sceneId)
                .executeUpdate();
        em.flush();
        em.clear();
        var remaining = transitionRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        transitionRepository.saveAll(remaining);
    }

    @Transactional(readOnly = true)
    public List<SceneTransition> getTransitions(UUID campaignId, UUID sceneId) {
        findSceneInCampaign(campaignId, sceneId);
        return transitionRepository.findBySceneIdOrderBySortOrderAsc(sceneId);
    }

    private void validateTransitionCommand(UUID campaignId, SceneTransitionCommand cmd) {
        if (cmd.kind() == SceneTransitionKind.CHOICE) {
            if (cmd.targetSceneId() == null) {
                throw new IllegalArgumentException("CHOICE transition requires a target scene");
            }
            if (cmd.externalDestination() != null) {
                throw new IllegalArgumentException("CHOICE transition cannot have an external destination");
            }
        } else {
            boolean hasTarget = cmd.targetSceneId() != null;
            boolean hasExternal = cmd.externalDestination() != null;
            if (hasTarget && hasExternal) {
                throw new IllegalArgumentException("ENTRANCE/EXIT transition must not have both target scene and external destination");
            }
            if (!hasTarget && !hasExternal) {
                throw new IllegalArgumentException("ENTRANCE/EXIT transition requires either a target scene or an external destination");
            }
        }
    }

    private void validateTableLink(UUID campaignId, SceneLinkCommand cmd) {
        if ("ROLLABLE_TABLE".equals(cmd.targetType())) {
            if (cmd.targetScope() != SceneLinkTargetScope.PACKAGE) {
                throw new IllegalArgumentException("ROLLABLE_TABLE links require PACKAGE scope");
            }
            if (cmd.targetId() == null) {
                throw new IllegalArgumentException("ROLLABLE_TABLE links require a target ID");
            }
            RollableTable table = rollableTableRepository.findById(cmd.targetId())
                    .orElseThrow(() -> new IllegalArgumentException("ROLLABLE_TABLE not found: " + cmd.targetId()));
            if (!referenceResolver.isVisibleToCampaign(table.getId(), campaignId)) {
                throw new IllegalArgumentException("ROLLABLE_TABLE is not visible to this campaign");
            }
        }
    }
}
