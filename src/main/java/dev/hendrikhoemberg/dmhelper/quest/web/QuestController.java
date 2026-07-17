package dev.hendrikhoemberg.dmhelper.quest.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
public class QuestController {

    private final QuestService questService;
    private final CampaignRepository campaignRepository;

    public QuestController(QuestService questService, CampaignRepository campaignRepository) {
        this.questService = questService;
        this.campaignRepository = campaignRepository;
    }

    @GetMapping("/campaigns/{campaignId}/quests")
    public String listQuests(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        List<Quest> quests = questService.getQuests(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("quests", quests);
        return "quest/list";
    }

    @GetMapping("/campaigns/{campaignId}/quests/{questId}")
    public String questDetail(@PathVariable UUID campaignId, @PathVariable UUID questId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("quest", quest);
        return "quest/detail";
    }

    @GetMapping("/campaigns/{campaignId}/quests/create")
    public String createForm(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("quest", new Quest());
        return "quest/_form :: form";
    }

    @GetMapping("/campaigns/{campaignId}/quests/{questId}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID questId, Model model) {
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_form :: form";
    }

    @PostMapping("/campaigns/{campaignId}/quests")
    public String createQuest(@PathVariable UUID campaignId,
                              @RequestParam String title,
                              @RequestParam(required = false) QuestStatus status,
                              @RequestParam(required = false) String summary,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(required = false) String tags,
                              @RequestParam(required = false) String rewards,
                              @RequestParam(required = false) String prerequisites,
                              @RequestParam(required = false) String outcomeNotes,
                              Model model) {
        try {
            Quest quest = questService.createQuest(campaignId,
                    new QuestService.QuestCommand(title, status, summary, sourceLocator,
                            tags, rewards, prerequisites, outcomeNotes));
            return "redirect:/campaigns/" + campaignId + "/quests/" + quest.getId();
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new NotFoundException("Campaign not found"));
            model.addAttribute("campaign", campaign);
            model.addAttribute("campaignId", campaignId);
            model.addAttribute("quests", questService.getQuests(campaignId));
            return "quest/list";
        }
    }

    @PutMapping("/campaigns/{campaignId}/quests/{questId}")
    public String updateQuest(@PathVariable UUID campaignId,
                              @PathVariable UUID questId,
                              @RequestParam String title,
                              @RequestParam(required = false) QuestStatus status,
                              @RequestParam(required = false) String summary,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(required = false) String tags,
                              @RequestParam(required = false) String rewards,
                              @RequestParam(required = false) String prerequisites,
                              @RequestParam(required = false) String outcomeNotes,
                              Model model) {
        try {
            questService.updateQuest(campaignId, questId,
                    new QuestService.QuestCommand(title, status, summary, sourceLocator,
                            tags, rewards, prerequisites, outcomeNotes));
            return "redirect:/campaigns/" + campaignId + "/quests/" + questId;
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
            Quest quest = questService.getQuest(campaignId, questId);
            model.addAttribute("quest", quest);
            model.addAttribute("campaignId", campaignId);
            return "quest/detail";
        }
    }

    @DeleteMapping("/campaigns/{campaignId}/quests/{questId}")
    public ResponseEntity<Void> deleteQuest(@PathVariable UUID campaignId, @PathVariable UUID questId) {
        questService.deleteQuest(campaignId, questId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/quests")
                .build();
    }

    // ---- Objectives ----

    @PostMapping("/campaigns/{campaignId}/quests/{questId}/objectives")
    public String addObjective(@PathVariable UUID campaignId,
                               @PathVariable UUID questId,
                               @RequestParam String title,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) QuestObjectiveStatus status,
                               @RequestParam(required = false) QuestObjectiveCompletionMode completionMode,
                               @RequestParam(defaultValue = "0") int sortOrder,
                               @RequestParam(required = false) String sourceLocator,
                               Model model) {
        try {
            questService.addObjective(campaignId, questId,
                    new QuestService.QuestObjectiveCommand(title, description, status,
                            completionMode, sortOrder, sourceLocator));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_objective-list :: objectiveList";
    }

    @PutMapping("/campaigns/{campaignId}/quests/{questId}/objectives/{objectiveId}")
    public String updateObjective(@PathVariable UUID campaignId,
                                  @PathVariable UUID questId,
                                  @PathVariable UUID objectiveId,
                                  @RequestParam String title,
                                  @RequestParam(required = false) String description,
                                  @RequestParam(required = false) QuestObjectiveStatus status,
                                  @RequestParam(required = false) QuestObjectiveCompletionMode completionMode,
                                  @RequestParam(defaultValue = "0") int sortOrder,
                                  @RequestParam(required = false) String sourceLocator,
                                  Model model) {
        try {
            questService.updateObjective(campaignId, questId, objectiveId,
                    new QuestService.QuestObjectiveCommand(title, description, status,
                            completionMode, sortOrder, sourceLocator));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_objective-list :: objectiveList";
    }

    @DeleteMapping("/campaigns/{campaignId}/quests/{questId}/objectives/{objectiveId}")
    public String deleteObjective(@PathVariable UUID campaignId,
                                  @PathVariable UUID questId,
                                  @PathVariable UUID objectiveId,
                                  Model model) {
        questService.deleteObjective(campaignId, questId, objectiveId);
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_objective-list :: objectiveList";
    }

    @PostMapping("/campaigns/{campaignId}/quests/objectives/{objectiveId}/status")
    public String setObjectiveStatus(@PathVariable UUID campaignId,
                                     @PathVariable UUID objectiveId,
                                     @RequestParam QuestObjectiveStatus status,
                                     Model model) {
        try {
            questService.setObjectiveStatus(campaignId, objectiveId, status);
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        UUID questId = findQuestForObjective(campaignId, objectiveId);
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_objective-list :: objectiveList";
    }

    // ---- Dependencies ----

    @PostMapping("/campaigns/{campaignId}/quests/{questId}/objectives/{objectiveId}/dependencies")
    public String addDependency(@PathVariable UUID campaignId,
                                @PathVariable UUID questId,
                                @PathVariable UUID objectiveId,
                                @RequestParam UUID prerequisiteObjectiveId,
                                Model model) {
        try {
            questService.addDependency(campaignId, objectiveId, prerequisiteObjectiveId);
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_dependency-list :: dependencyList";
    }

    @DeleteMapping("/campaigns/{campaignId}/quests/{questId}/dependencies")
    public String removeDependency(@PathVariable UUID campaignId,
                                   @PathVariable UUID questId,
                                   @RequestParam UUID objectiveId,
                                   @RequestParam UUID prerequisiteObjectiveId,
                                   Model model) {
        questService.removeDependency(campaignId, objectiveId, prerequisiteObjectiveId);
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_dependency-list :: dependencyList";
    }

    // ---- Links ----

    @PostMapping("/campaigns/{campaignId}/quests/{questId}/links")
    public String addLink(@PathVariable UUID campaignId,
                          @PathVariable UUID questId,
                          @RequestParam QuestLinkRole role,
                          @RequestParam SceneLinkTargetScope targetScope,
                          @RequestParam String targetType,
                          @RequestParam UUID targetId,
                          @RequestParam(required = false) String displayText,
                          @RequestParam(required = false) String condition,
                          @RequestParam(defaultValue = "0") int sortOrder,
                          Model model) {
        try {
            questService.addLink(campaignId, questId,
                    new QuestService.QuestLinkCommand(role, targetScope, targetType, targetId,
                            null, null, displayText, condition, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_link-list :: linkList";
    }

    @DeleteMapping("/campaigns/{campaignId}/quests/{questId}/links/{linkId}")
    public String deleteLink(@PathVariable UUID campaignId,
                             @PathVariable UUID questId,
                             @PathVariable UUID linkId,
                             Model model) {
        questService.deleteLink(campaignId, questId, linkId);
        Quest quest = questService.getQuest(campaignId, questId);
        model.addAttribute("quest", quest);
        model.addAttribute("campaignId", campaignId);
        return "quest/_link-list :: linkList";
    }

    private UUID findQuestForObjective(UUID campaignId, UUID objectiveId) {
        return questService.getQuests(campaignId).stream()
                .filter(q -> q.getObjectives().stream().anyMatch(o -> o.getId().equals(objectiveId)))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Quest not found for objective"))
                .getId();
    }
}
