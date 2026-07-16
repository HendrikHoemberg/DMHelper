package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionApiController.class)
class SessionApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionLifecycleService lifecycle;

    @MockitoBean
    private AdventureService adventures;

    @MockitoBean
    private SessionWorkspaceService workspaces;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void startsWithNullableMapAndReturnsTypedState() throws Exception {
        CampaignSession running = mock(CampaignSession.class);
        when(running.getStatus()).thenReturn(CampaignSession.Status.RUNNING);
        when(running.getWorkspaceMap()).thenReturn(null);
        when(running.getPresentationMode()).thenReturn(CampaignSession.PresentationMode.CURTAIN);
        when(running.getAttendees()).thenReturn(List.of());
        when(running.getDraftBody()).thenReturn(null);
        when(lifecycle.start(campaignId, null)).thenReturn(running);

        mvc.perform(post("/api/v1/campaigns/{id}/session/start", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"mapId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.presentationMode").value("CURTAIN"))
                .andExpect(jsonPath("$.attendeeIds").isArray())
                .andExpect(jsonPath("$.draftBody").doesNotExist());
    }

    @Test
    void rejectsInvalidSceneDirectionBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{id}/session/current-scene/step", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"direction\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adventures);
    }
}
