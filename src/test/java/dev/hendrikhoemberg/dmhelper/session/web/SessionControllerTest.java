package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.service.SessionPlanService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.SessionWorkspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private SessionWorkspaceService workspaces;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void rendersCockpitEvenWhenNoMapExists() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        when(workspaces.load(campaignId, null)).thenReturn(ws);
        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/cockpit"))
                .andExpect(model().attribute("workspace", ws));
    }

    @Test
    void passesExplicitMapSelectionToWorkspacePolicy() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(workspaces.load(campaignId, mapId)).thenReturn(mapWorkspace());
        mvc.perform(get("/campaigns/{id}/session", campaignId).param("mapId", mapId.toString()))
                .andExpect(status().isOk());
        verify(workspaces).load(campaignId, mapId);
    }

    @Test
    void attendanceEditorIncludesAnInactiveStoredAttendee() throws Exception {
        SessionWorkspace ws = emptyWorkspace();
        PartyMember inactive = new PartyMember();
        inactive.setId(UUID.randomUUID());
        inactive.setCharacterName("Retired Hero");
        inactive.setActive(false);
        ws.session().setStatus(CampaignSession.Status.RUNNING);
        ws.session().getAttendees().add(inactive);
        when(workspaces.load(campaignId, null)).thenReturn(ws);

        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attribute("attendanceMembers", List.of(inactive)))
                .andExpect(model().attribute("attendeeIds", List.of(inactive.getId().toString())));
    }

    static SessionWorkspace emptyWorkspace() {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        return new SessionWorkspace(
                campaign,
                CampaignSession.idle(campaign),
                null,
                SessionWorkspaceService.SelectionSource.NONE,
                null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12));
    }

    static SessionWorkspace mapWorkspace() {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        var map = new dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap();
        map.setId(UUID.randomUUID());
        map.setName("Test Map");
        return new SessionWorkspace(
                campaign,
                CampaignSession.idle(campaign),
                map,
                SessionWorkspaceService.SelectionSource.EXPLICIT_MAP,
                null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12));
    }
}
