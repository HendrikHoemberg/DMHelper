package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.web.CampaignController;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(CampaignController.class)
class NotFoundPageAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CampaignService campaignService;

    @MockitoBean
    private NoteService noteService;

    @MockitoBean
    private PartyMemberService partyMemberService;

    @MockitoBean
    private AudioCueRepository audioCueRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void browserNavigationGetsStyled404Page() throws Exception {
        mockMvc.perform(get("/definitely-not-a-page")
                        .header("Accept", "text/html,application/xhtml+xml"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("wandered off the map")));
    }

    @Test
    void apiClientStillGetsProblemJson() throws Exception {
        mockMvc.perform(get("/definitely-not-a-page")
                        .header("Accept", "application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
