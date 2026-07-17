package dev.hendrikhoemberg.dmhelper.quest.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.adventure.data.TagCodec;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class QuestService {

    public record QuestCommand(
            String title, QuestStatus status, String summary, String sourceLocator,
            String tags, String rewards, String prerequisites, String outcomeNotes) {}

    public record QuestObjectiveCommand(
            String title, String description, QuestObjectiveStatus status,
            QuestObjectiveCompletionMode completionMode, int sortOrder,
            String sourceLocator) {}

    public record QuestLinkCommand(
            QuestLinkRole role, SceneLinkTargetScope targetScope, String targetType, UUID targetId,
            String catalogRuleset, String catalogSourceKey, String displayText, String condition,
            int sortOrder) {}

    private final QuestRepository questRepository;
    private final QuestObjectiveRepository objectiveRepository;
    private final QuestObjectiveDependencyRepository dependencyRepository;
    private final QuestLinkRepository linkRepository;
    private final CampaignRepository campaignRepository;
    private final SessionActivityRecorder activityRecorder;
    private final QuestObjectiveDependencyValidator dependencyValidator;
    private final CampaignPackageKeyService packageKeys;
    private final SessionReferenceCleaner sessionRefCleaner;

    public QuestService(QuestRepository questRepository,
                        QuestObjectiveRepository objectiveRepository,
                        QuestObjectiveDependencyRepository dependencyRepository,
                        QuestLinkRepository linkRepository,
                        CampaignRepository campaignRepository,
                        SessionActivityRecorder activityRecorder,
                        QuestObjectiveDependencyValidator dependencyValidator,
                        CampaignPackageKeyService packageKeys,
                        SessionReferenceCleaner sessionRefCleaner) {
        this.questRepository = questRepository;
        this.objectiveRepository = objectiveRepository;
        this.dependencyRepository = dependencyRepository;
        this.linkRepository = linkRepository;
        this.campaignRepository = campaignRepository;
        this.activityRecorder = activityRecorder;
        this.dependencyValidator = dependencyValidator;
        this.packageKeys = packageKeys;
        this.sessionRefCleaner = sessionRefCleaner;
    }

    // ---- Quest CRUD ----

    public Quest createQuest(UUID campaignId, QuestCommand cmd) {
        campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        Quest quest = new Quest();
        quest.setCampaign(campaign);
        applyQuestCommand(quest, cmd);
        return questRepository.save(quest);
    }

    public Quest updateQuest(UUID campaignId, UUID questId, QuestCommand cmd) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        applyQuestCommand(quest, cmd);
        return questRepository.save(quest);
    }

    public void deleteQuest(UUID campaignId, UUID questId) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        questRepository.delete(quest);
        packageKeys.deleteBindings(campaignId, CampaignContentType.QUEST, List.of(questId));
    }

    @Transactional(readOnly = true)
    public List<Quest> getQuests(UUID campaignId) {
        return questRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public Quest getQuest(UUID campaignId, UUID questId) {
        return findQuestInCampaign(campaignId, questId);
    }

    // ---- Objective management ----

    public QuestObjective addObjective(UUID campaignId, UUID questId, QuestObjectiveCommand cmd) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        QuestObjective objective = new QuestObjective();
        objective.setQuest(quest);
        applyObjectiveCommand(objective, cmd);
        quest.getObjectives().add(objective);
        return objectiveRepository.save(objective);
    }

    public QuestObjective updateObjective(UUID campaignId, UUID questId, UUID objectiveId, QuestObjectiveCommand cmd) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        QuestObjective objective = findObjectiveInQuest(quest, objectiveId);
        applyObjectiveCommand(objective, cmd);
        return objectiveRepository.save(objective);
    }

    public void deleteObjective(UUID campaignId, UUID questId, UUID objectiveId) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        QuestObjective objective = findObjectiveInQuest(quest, objectiveId);
        sessionRefCleaner.detachObjective(objectiveId);
        dependencyRepository.deleteByObjectiveId(objectiveId);
        dependencyRepository.deleteByPrerequisiteObjectiveId(objectiveId);
        objectiveRepository.delete(objective);
        quest.getObjectives().remove(objective);
        renumberObjectives(quest);
        packageKeys.deleteBindings(campaignId, CampaignContentType.OBJECTIVE, List.of(objectiveId));
    }

    public record ObjectiveStatusUpdate(QuestObjective objective, UUID sessionChangeId) {}

    public ObjectiveStatusUpdate setObjectiveStatus(UUID campaignId, UUID objectiveId, QuestObjectiveStatus nextStatus) {
        QuestObjective objective = objectiveRepository.findById(objectiveId)
                .orElseThrow(() -> new NotFoundException("Objective not found"));
        if (!objective.getQuest().getCampaign().getId().equals(campaignId)) {
            throw new NotFoundException("Objective not found in campaign");
        }
        QuestObjectiveStatus previousStatus = objective.getStatus();
        if (previousStatus == nextStatus) {
            return new ObjectiveStatusUpdate(objective, null);
        }
        objective.setStatus(nextStatus);
        QuestObjective saved = objectiveRepository.save(objective);
        UUID sessionChangeId = activityRecorder
                .recordObjectiveChange(campaignId, objectiveId, previousStatus, nextStatus)
                .orElse(null);
        return new ObjectiveStatusUpdate(saved, sessionChangeId);
    }

    // ---- Dependencies ----

    public QuestObjectiveDependency addDependency(UUID campaignId, UUID objectiveId, UUID prerequisiteObjectiveId) {
        QuestObjective objective = objectiveRepository.findById(objectiveId)
                .orElseThrow(() -> new NotFoundException("Objective not found"));
        QuestObjective prerequisite = objectiveRepository.findById(prerequisiteObjectiveId)
                .orElseThrow(() -> new NotFoundException("Prerequisite objective not found"));
        UUID questId = objective.getQuest().getId();
        if (!questId.equals(prerequisite.getQuest().getId())) {
            throw new IllegalArgumentException("Objectives must belong to the same quest");
        }
        findQuestInCampaign(campaignId, questId);
        List<QuestObjectiveDependency> existing = dependencyRepository.findByQuestId(questId);
        dependencyValidator.validate(objectiveId, prerequisiteObjectiveId, existing);

        QuestObjectiveDependency dep = new QuestObjectiveDependency();
        dep.setQuest(objective.getQuest());
        dep.setObjective(objective);
        dep.setPrerequisiteObjective(prerequisite);
        return dependencyRepository.save(dep);
    }

    public void removeDependency(UUID campaignId, UUID objectiveId, UUID prerequisiteObjectiveId) {
        QuestObjective objective = objectiveRepository.findById(objectiveId)
                .orElseThrow(() -> new NotFoundException("Objective not found"));
        findQuestInCampaign(campaignId, objective.getQuest().getId());
        dependencyRepository.deleteById(
                new QuestObjectiveDependency.QuestObjectiveDependencyId(
                        objective.getQuest().getId(), objectiveId, prerequisiteObjectiveId));
    }

    // ---- Link management ----

    public QuestLink addLink(UUID campaignId, UUID questId, QuestLinkCommand cmd) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        validateGiverContract(quest, null, cmd);
        QuestLink link = new QuestLink();
        link.setQuest(quest);
        applyLinkCommand(link, cmd);
        quest.getLinks().add(link);
        return linkRepository.save(link);
    }

    public QuestLink updateLink(UUID campaignId, UUID questId, UUID linkId, QuestLinkCommand cmd) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        QuestLink link = findLinkInQuest(quest, linkId);
        validateGiverContract(quest, linkId, cmd);
        applyLinkCommand(link, cmd);
        return linkRepository.save(link);
    }

    private void validateGiverContract(Quest quest, UUID updatingLinkId, QuestLinkCommand cmd) {
        if (cmd.role() != QuestLinkRole.GIVER) {
            return;
        }
        String targetType = cmd.targetType();
        if (targetType == null || !(targetType.equals("NOTE") || targetType.equals("STATBLOCK"))) {
            throw new IllegalArgumentException("GIVER link must target a NOTE or STATBLOCK");
        }
        boolean hasOtherGiver = quest.getLinks().stream()
                .anyMatch(l -> l.getRole() == QuestLinkRole.GIVER
                        && (updatingLinkId == null || !updatingLinkId.equals(l.getId())));
        if (hasOtherGiver) {
            throw new IllegalArgumentException("Quest already has a GIVER link");
        }
    }

    public void deleteLink(UUID campaignId, UUID questId, UUID linkId) {
        Quest quest = findQuestInCampaign(campaignId, questId);
        QuestLink link = findLinkInQuest(quest, linkId);
        linkRepository.delete(link);
        quest.getLinks().remove(link);
        renumberLinks(quest);
    }

    // ---- Internal helpers ----

    private Quest findQuestInCampaign(UUID campaignId, UUID questId) {
        return questRepository.findByIdAndCampaignId(questId, campaignId)
                .orElseThrow(() -> new NotFoundException("Quest not found in campaign"));
    }

    private QuestObjective findObjectiveInQuest(Quest quest, UUID objectiveId) {
        return quest.getObjectives().stream()
                .filter(o -> o.getId().equals(objectiveId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Objective not found in quest"));
    }

    private QuestLink findLinkInQuest(Quest quest, UUID linkId) {
        return quest.getLinks().stream()
                .filter(l -> l.getId().equals(linkId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Link not found in quest"));
    }

    private void applyQuestCommand(Quest quest, QuestCommand cmd) {
        if (cmd.title() != null) quest.setTitle(cmd.title());
        if (cmd.status() != null) quest.setStatus(cmd.status());
        if (cmd.summary() != null) quest.setSummary(cmd.summary());
        if (cmd.sourceLocator() != null) quest.setSourceLocator(cmd.sourceLocator());
        quest.setTags(normalizeTags(cmd.tags()));
        if (cmd.rewards() != null) quest.setRewards(cmd.rewards());
        if (cmd.prerequisites() != null) quest.setPrerequisites(cmd.prerequisites());
        if (cmd.outcomeNotes() != null) quest.setOutcomeNotes(cmd.outcomeNotes());
    }

    private void applyObjectiveCommand(QuestObjective objective, QuestObjectiveCommand cmd) {
        if (cmd.title() != null) objective.setTitle(cmd.title());
        if (cmd.description() != null) objective.setDescription(cmd.description());
        if (cmd.status() != null) objective.setStatus(cmd.status());
        if (cmd.completionMode() != null) objective.setCompletionMode(cmd.completionMode());
        objective.setSortOrder(cmd.sortOrder());
        if (cmd.sourceLocator() != null) objective.setSourceLocator(cmd.sourceLocator());
    }

    private void applyLinkCommand(QuestLink link, QuestLinkCommand cmd) {
        if (cmd.role() != null) link.setRole(cmd.role());
        if (cmd.targetScope() != null) link.setTargetScope(cmd.targetScope());
        if (cmd.targetType() != null) link.setTargetType(cmd.targetType());
        if (cmd.targetId() != null) link.setTargetId(cmd.targetId());
        if (cmd.catalogRuleset() != null) link.setCatalogRuleset(cmd.catalogRuleset());
        if (cmd.catalogSourceKey() != null) link.setCatalogSourceKey(cmd.catalogSourceKey());
        if (cmd.displayText() != null) link.setDisplayText(cmd.displayText());
        if (cmd.condition() != null) link.setCondition(cmd.condition());
        link.setSortOrder(cmd.sortOrder());
    }

    private void renumberObjectives(Quest quest) {
        List<QuestObjective> remaining = objectiveRepository.findByQuestIdOrderBySortOrderAsc(quest.getId());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        objectiveRepository.saveAll(remaining);
    }

    private void renumberLinks(Quest quest) {
        List<QuestLink> remaining = linkRepository.findByQuestIdOrderBySortOrderAsc(quest.getId());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
        linkRepository.saveAll(remaining);
    }

    private String normalizeTags(String tags) {
        return TagCodec.format(TagCodec.parse(tags));
    }
}
