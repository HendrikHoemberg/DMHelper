package dev.hendrikhoemberg.dmhelper.quest.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.QuestDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.QuestLinkDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.QuestObjectiveDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestLink;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestLinkRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveDependency;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestObjectiveDependencyValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class QuestSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final QuestRepository questRepo;
    private final QuestObjectiveRepository objectiveRepo;
    private final QuestLinkRepository linkRepo;
    private final QuestObjectiveDependencyValidator dependencyValidator;

    public QuestSectionAdapter(QuestRepository questRepo,
                               QuestObjectiveRepository objectiveRepo,
                               QuestLinkRepository linkRepo,
                               QuestObjectiveDependencyValidator dependencyValidator) {
        this.questRepo = questRepo;
        this.objectiveRepo = objectiveRepo;
        this.linkRepo = linkRepo;
        this.dependencyValidator = dependencyValidator;
    }

    @Override
    public String sectionName() {
        return "Quest";
    }

    @Override
    public int order() {
        return 950;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        List<Quest> quests = questRepo.findByCampaignIdOrderByCreatedAtAscIdAsc(context.campaignId());
        List<QuestDto> dtos = quests.stream().map(q -> {
            String qKey = context.key(CampaignContentType.QUEST, q.getId(), q.getTitle());
            List<String> tags = q.getTags() == null || q.getTags().isBlank() ? List.of()
                    : List.of(q.getTags().split(","));
            List<QuestLink> links = linkRepo.findByQuestIdOrderBySortOrderAsc(q.getId());
            List<QuestLinkDto> linkDtos = links.stream().map(l -> {
                ContentReference targetRef = resolveLinkTarget(l, context);
                return new QuestLinkDto(l.getRole().name(), targetRef,
                        l.getDisplayText(), l.getCondition(), l.getSortOrder());
            }).toList();
            List<QuestObjective> objectives = objectiveRepo.findByQuestIdOrderBySortOrderAsc(q.getId());
            List<QuestObjectiveDto> objDtos = objectives.stream().map(o -> {
                String oKey = context.key(CampaignContentType.OBJECTIVE, o.getId(), o.getTitle());
                List<ContentReference> prereqRefs = o.getDependencies().stream()
                        .map(d -> {
                            String depKey = context.key(CampaignContentType.OBJECTIVE,
                                    d.getPrerequisiteObjective().getId(),
                                    d.getPrerequisiteObjective().getTitle());
                            return ContentReference.packageRef(CampaignContentType.OBJECTIVE, depKey);
                        })
                        .toList();
                return new QuestObjectiveDto(oKey, o.getTitle(), o.getDescription(),
                        o.getStatus().name(), o.getCompletionMode().name(),
                        o.getSortOrder(), prereqRefs, o.getSourceLocator());
            }).toList();
            return new QuestDto(qKey, q.getTitle(), q.getStatus().name(),
                    q.getSummary(), q.getSourceLocator(), tags,
                    q.getRewards(), q.getPrerequisites(), q.getOutcomeNotes(),
                    linkDtos, objDtos, q.getCreatedAt());
        }).toList();
        target.quests(dtos);
    }

    private static ContentReference resolveLinkTarget(QuestLink link, CampaignExportContext context) {
        if (link.getTargetScope() == dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.CATALOG) {
            return ContentReference.catalogRef(
                    CampaignContentType.valueOf(link.getTargetType()),
                    link.getCatalogRuleset(), link.getCatalogSourceKey());
        }
        return ContentReference.packageRef(
                CampaignContentType.valueOf(link.getTargetType()),
                link.getTargetId().toString());
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<QuestDto> dtos = source.quests();
        if (dtos == null) return;
        var campaign = context.campaign();
        for (QuestDto qDto : dtos) {
            Quest q = new Quest();
            q.setCampaign(campaign);
            q.setTitle(qDto.title());
            q.setStatus(dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus.valueOf(qDto.status()));
            q.setSummary(qDto.summary());
            q.setSourceLocator(qDto.sourceLocator());
            if (qDto.tags() != null) q.setTags(String.join(",", qDto.tags()));
            q.setRewards(qDto.rewards());
            q.setPrerequisites(qDto.prerequisites());
            q.setOutcomeNotes(qDto.outcomeNotes());
            if (qDto.createdAt() != null) q.setCreatedAt(qDto.createdAt());
            questRepo.save(q);
            context.register(CampaignContentType.QUEST, qDto.key(), q, q.getId());

            if (qDto.objectives() != null) {
                for (QuestObjectiveDto oDto : qDto.objectives()) {
                    QuestObjective o = new QuestObjective();
                    o.setQuest(q);
                    o.setTitle(oDto.title());
                    o.setDescription(oDto.description());
                    o.setStatus(dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus.valueOf(oDto.status()));
                    o.setCompletionMode(dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveCompletionMode.valueOf(oDto.completionMode()));
                    o.setSortOrder(oDto.sortOrder());
                    o.setSourceLocator(oDto.sourceLocator());
                    objectiveRepo.save(o);
                    q.getObjectives().add(o);
                    context.register(CampaignContentType.OBJECTIVE, oDto.key(), o, o.getId());
                }
            }

            if (qDto.links() != null) {
                for (QuestLinkDto lDto : qDto.links()) {
                    QuestLink l = new QuestLink();
                    l.setQuest(q);
                    l.setRole(dev.hendrikhoemberg.dmhelper.quest.data.QuestLinkRole.valueOf(lDto.role()));
                    l.setDisplayText(lDto.displayText());
                    l.setCondition(lDto.condition());
                    l.setSortOrder(lDto.sortOrder());
                    if (lDto.targetRef() != null) {
                        l.setTargetScope(lDto.targetRef().scope() == ContentReference.Scope.CATALOG
                                ? dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.CATALOG
                                : dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.PACKAGE);
                        l.setTargetType(lDto.targetRef().type().name());
                        if (lDto.targetRef().scope() == ContentReference.Scope.CATALOG) {
                            l.setCatalogRuleset(lDto.targetRef().ruleset());
                            l.setCatalogSourceKey(lDto.targetRef().sourceKey());
                        }
                    }
                    linkRepo.save(l);
                    q.getLinks().add(l);
                }
            }

            context.defer("quest-dependencies:" + qDto.key(), () -> {
                Quest quest = context.require(
                        ContentReference.packageRef(CampaignContentType.QUEST, qDto.key()),
                        CampaignContentType.QUEST, Quest.class);
                List<QuestObjectiveDependency> existing = new ArrayList<>();
                if (qDto.objectives() != null) {
                    for (int i = 0; i < qDto.objectives().size() && i < quest.getObjectives().size(); i++) {
                        var oDto = qDto.objectives().get(i);
                        var obj = quest.getObjectives().get(i);
                        if (oDto.prerequisiteRefs() != null) {
                            for (ContentReference prereqRef : oDto.prerequisiteRefs()) {
                                QuestObjective prereq = context.require(prereqRef, CampaignContentType.OBJECTIVE, QuestObjective.class);
                                if (!prereq.getQuest().getId().equals(quest.getId())) {
                                    throw new IllegalArgumentException(
                                            "Objective dependencies must stay within the same quest");
                                }
                                dependencyValidator.validate(obj.getId(), prereq.getId(), existing);
                                QuestObjectiveDependency dep = new QuestObjectiveDependency();
                                dep.setQuest(quest);
                                dep.setObjective(obj);
                                dep.setPrerequisiteObjective(prereq);
                                obj.getDependencies().add(dep);
                                existing.add(dep);
                            }
                        }
                    }
                }
            });
        }
    }
}
