package dev.hendrikhoemberg.dmhelper.quest.web;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/quests")
public class QuestApiController {

    public record QuestDto(UUID id, String title, String status) {}
    public record ObjectiveDto(UUID id, String title, String status, int sortOrder) {}
    public record ObjectiveStatusRequest(String status) {}
    public record ObjectiveStatusResult(UUID objectiveId, String status, String sessionChangeId) {}

    private final QuestService questService;

    public QuestApiController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping
    List<QuestDto> listQuests(@PathVariable UUID campaignId) {
        return questService.getQuests(campaignId).stream()
                .map(q -> new QuestDto(q.getId(), q.getTitle(), q.getStatus().name()))
                .toList();
    }

    @GetMapping("/{questId}")
    QuestDto getQuest(@PathVariable UUID campaignId, @PathVariable UUID questId) {
        Quest q = questService.getQuest(campaignId, questId);
        return new QuestDto(q.getId(), q.getTitle(), q.getStatus().name());
    }

    @GetMapping("/{questId}/objectives")
    List<ObjectiveDto> getObjectives(@PathVariable UUID campaignId, @PathVariable UUID questId) {
        Quest q = questService.getQuest(campaignId, questId);
        return q.getObjectives().stream()
                .map(o -> new ObjectiveDto(o.getId(), o.getTitle(), o.getStatus().name(), o.getSortOrder()))
                .toList();
    }

    @PutMapping("/objectives/{objectiveId}/status")
    ObjectiveStatusResult setObjectiveStatus(@PathVariable UUID campaignId,
                                              @PathVariable UUID objectiveId,
                                              @RequestBody ObjectiveStatusRequest request) {
        QuestObjectiveStatus newStatus;
        try {
            newStatus = QuestObjectiveStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.status());
        }
        QuestObjective objective = questService.setObjectiveStatus(campaignId, objectiveId, newStatus);
        return new ObjectiveStatusResult(objective.getId(), objective.getStatus().name(),
                "objective-" + objective.getId());
    }
}
