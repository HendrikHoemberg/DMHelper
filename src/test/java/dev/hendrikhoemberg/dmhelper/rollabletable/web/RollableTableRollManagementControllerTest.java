package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.EncounterTableDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.EncounterCreatureDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RewardTableDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RewardItemDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableConsequenceDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableConsequenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RollableTableRollManagementController.class)
class RollableTableRollManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TableConsequenceService consequenceService;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID rollId = UUID.randomUUID();

    @Test
    void getDraftReturnsEncounterDraft() throws Exception {
        UUID sbId = UUID.randomUUID();
        TableConsequenceDraft draft = new EncounterTableDraft("Goblins", "4 goblins appear",
                List.of(new EncounterCreatureDraft(sbId, "Goblin", 4)));
        when(consequenceService.preview(rollId, campaignId)).thenReturn(draft);

        mockMvc.perform(get("/api/v1/rollable-table-rolls/{rollId}/draft", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedName").value("Goblins"))
                .andExpect(jsonPath("$.creatures[0].displayName").value("Goblin"));
    }

    @Test
    void getDraftReturnsRewardDraft() throws Exception {
        UUID itemId = UUID.randomUUID();
        TableConsequenceDraft draft = new RewardTableDraft("treasure",
                List.of(new RewardItemDraft(CampaignContentType.EQUIPMENT_ITEM, itemId, "Longsword", 2)));
        when(consequenceService.preview(rollId, campaignId)).thenReturn(draft);

        mockMvc.perform(get("/api/v1/rollable-table-rolls/{rollId}/draft", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("Longsword"));
    }

    @Test
    void confirmEncounterReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/encounter/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Goblins\",\"creatures\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmRewardReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/reward/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void discardReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/discard", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void conflictStatusReturns409() throws Exception {
        doThrow(new IllegalStateException("is CONFIRMED")).when(consequenceService)
                .confirmEncounter(eq(rollId), eq(campaignId), any());

        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/encounter/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"creatures\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongDraftTypeReturns409() throws Exception {
        doThrow(new IllegalStateException("Draft type is REWARD; expected ENCOUNTER"))
                .when(consequenceService).confirmEncounter(eq(rollId), eq(campaignId), any());

        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/encounter/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"wrong type\",\"creatures\":[]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void notFoundReturns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(consequenceService)
                .confirmEncounter(eq(rollId), eq(campaignId), any());

        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/encounter/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"creatures\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void discardReturns409WhenNotPending() throws Exception {
        doThrow(new IllegalStateException("is CONFIRMED")).when(consequenceService)
                .discard(rollId, campaignId);

        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/discard", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void getDraftReturns404WhenNotFound() throws Exception {
        when(consequenceService.preview(rollId, campaignId))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/api/v1/rollable-table-rolls/{rollId}/draft", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDraftReturns409WhenNotPending() throws Exception {
        when(consequenceService.preview(rollId, campaignId))
                .thenThrow(new IllegalStateException("not in PENDING"));

        mockMvc.perform(get("/api/v1/rollable-table-rolls/{rollId}/draft", rollId)
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void badRequestReturns400() throws Exception {
        doThrow(new IllegalArgumentException("quantity must be between 1 and 50"))
                .when(consequenceService).confirmEncounter(eq(rollId), eq(campaignId), any());

        mockMvc.perform(post("/api/v1/rollable-table-rolls/{rollId}/encounter/confirm", rollId)
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"creatures\":[{\"statBlockId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
    }
}
