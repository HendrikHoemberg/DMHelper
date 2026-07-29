package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.SessionWorkspace;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CockpitHydrationTest {

    @Autowired
    private WebApplicationContext webContext;

    @MockitoBean
    private SessionWorkspaceService workspaces;

    @MockitoBean
    private AdventureService adventures;

    @MockitoBean
    private SceneEncounterSeedService encounterSeeder;

    @MockitoBean(name = "calendarService")
    private CalendarService calendarService;

    private MockMvc mvc;
    private final UUID campaignId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Hydration Campaign");
        SessionWorkspace workspace = new SessionWorkspace(
                campaign,
                runningSession(campaign),
                null,
                SessionWorkspaceService.SelectionSource.NONE,
                null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of(),
                new CalendarService.InGameDate(1492, 7, 12),
                null, List.of(), List.of());
        when(workspaces.load(any(UUID.class), isNull())).thenReturn(workspace);
        when(workspaces.load(any(UUID.class), any(UUID.class))).thenReturn(workspace);
        when(adventures.scenePickerGroups(any())).thenReturn(List.of());
        when(encounterSeeder.canSeed(any(), any())).thenReturn(false);
        when(calendarService.formatDate(any(), any())).thenReturn("12 July 1492");
    }

    @Test
    void everyModuleInTheLayoutDeclaresAnEndpointAndAnEmptyMessage() throws Exception {
        Document doc = renderCockpit();

        var shells = doc.select("[data-runtime-module]");
        assertThat(shells).isNotEmpty();

        for (var shell : shells) {
            String key = shell.attr("data-module-key");
            assertThat(shell.select("[data-module-content]").first())
                    .as("module %s must expose a content root the loader can fill", key)
                    .isNotNull();
        }
    }

    private Document renderCockpit() throws Exception {
        String html = mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }

    private static CampaignSession runningSession(Campaign campaign) {
        CampaignSession session = new CampaignSession();
        session.setCampaign(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(java.time.Instant.now());
        return session;
    }
}
