package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuickNoteApiController.class)
class QuickNoteApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private QuickNoteService quickNoteService;

    @Test
    void deleteScopesTheMutationToThePathCampaign() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/campaigns/{campaignId}/quicknotes/{id}", campaignId, noteId))
                .andExpect(status().isNoContent());

        verify(quickNoteService).delete(campaignId, noteId);
    }

    @Test
    void promoteScopesTheMutationToThePathCampaign() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        Note promoted = new Note();
        promoted.setId(UUID.randomUUID());
        promoted.setCampaign(campaign);
        when(quickNoteService.promoteToNote(campaignId, noteId)).thenReturn(promoted);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/quicknotes/{id}/promote", campaignId, noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value(promoted.getId().toString()));

        verify(quickNoteService).promoteToNote(campaignId, noteId);
    }
}
