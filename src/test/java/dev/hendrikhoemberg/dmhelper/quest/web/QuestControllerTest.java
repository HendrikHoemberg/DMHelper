package dev.hendrikhoemberg.dmhelper.quest.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestController.class)
class QuestControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private QuestService questService;
    @MockitoBean private CampaignRepository campaignRepository;

    private UUID campaignId, questId;
    private Campaign campaign;
    private Quest quest;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        questId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");

        quest = new Quest();
        quest.setId(questId);
        quest.setCampaign(campaign);
        quest.setTitle("Find the Artifact");
        quest.setStatus(QuestStatus.ACTIVE);

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    }

    @Test
    void listQuestsRendersPage() throws Exception {
        when(questService.getQuests(campaignId)).thenReturn(List.of(quest));

        mockMvc.perform(get("/campaigns/{cid}/quests", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("quests"))
                .andExpect(model().attributeExists("campaign"))
                .andExpect(view().name("quest/list"));
    }

    @Test
    void questDetailRendersPage() throws Exception {
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);

        mockMvc.perform(get("/campaigns/{cid}/quests/{qid}", campaignId, questId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("quest"))
                .andExpect(view().name("quest/detail"));
    }

    @Test
    void createQuestRedirectsToDetail() throws Exception {
        when(questService.createQuest(any(), any())).thenReturn(quest);

        mockMvc.perform(post("/campaigns/{cid}/quests", campaignId)
                        .param("title", "New Quest"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/quests/" + questId));
    }

    @Test
    void createQuestShowsErrorOnValidationFailure() throws Exception {
        when(questService.createQuest(any(), any()))
                .thenThrow(new IllegalArgumentException("Title is required"));

        mockMvc.perform(post("/campaigns/{cid}/quests", campaignId)
                        .param("title", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Title is required"))
                .andExpect(view().name("quest/list"));
    }

    @Test
    void updateQuestRedirectsToDetail() throws Exception {
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);

        mockMvc.perform(put("/campaigns/{cid}/quests/{qid}", campaignId, questId)
                        .param("title", "Updated Quest"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/quests/" + questId));
    }

    @Test
    void deleteQuestReturnsRedirect() throws Exception {
        mockMvc.perform(delete("/campaigns/{cid}/quests/{qid}", campaignId, questId))
                .andExpect(status().isOk())
                .andExpect(header().exists("HX-Redirect"));
    }

    @Test
    void addObjectiveReturnsObjectiveList() throws Exception {
        QuestObjective objective = new QuestObjective();
        objective.setId(UUID.randomUUID());
        objective.setTitle("Find the map");
        quest.getObjectives().add(objective);
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);

        mockMvc.perform(post("/campaigns/{cid}/quests/{qid}/objectives", campaignId, questId)
                        .param("title", "Find the map"))
                .andExpect(status().isOk());
    }

    @Test
    void setObjectiveStatusReturnsUpdatedList() throws Exception {
        QuestObjective objective = new QuestObjective();
        objective.setId(UUID.randomUUID());
        objective.setTitle("Find the map");
        objective.setStatus(QuestObjectiveStatus.COMPLETED);
        quest.getObjectives().add(objective);
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);
        when(questService.getQuests(campaignId)).thenReturn(List.of(quest));
        when(questService.setObjectiveStatus(campaignId, objective.getId(), QuestObjectiveStatus.COMPLETED))
                .thenReturn(objective);

        mockMvc.perform(post("/campaigns/{cid}/quests/objectives/{oid}/status", campaignId, objective.getId())
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk());
    }

    @Test
    void addDependencyReturnsDependencyList() throws Exception {
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);

        mockMvc.perform(post("/campaigns/{cid}/quests/{qid}/objectives/{oid}/dependencies", campaignId, questId, UUID.randomUUID())
                        .param("prerequisiteObjectiveId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void addLinkReturnsLinkList() throws Exception {
        when(questService.getQuest(campaignId, questId)).thenReturn(quest);

        mockMvc.perform(post("/campaigns/{cid}/quests/{qid}/links", campaignId, questId)
                        .param("role", "REFERENCE")
                        .param("targetScope", "PACKAGE")
                        .param("targetType", "HANDOUT")
                        .param("targetId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
