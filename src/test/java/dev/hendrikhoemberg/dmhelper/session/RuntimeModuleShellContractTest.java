package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleDefinition;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService.SessionWorkspace;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

/**
 * Every runtime module rendered on the cockpit page must carry a stable
 * {@code data-runtime-module} key on its outer module shell, and the registry is the source
 * of truth for which keys exist.
 *
 * <p>This is asserted against the MockMvc-rendered page rather than against template source:
 * what matters is that the production workbench emits exactly one root per registry key, not
 * which fragment happens to declare it.
 */
@SpringBootTest
class RuntimeModuleShellContractTest {

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private CockpitModuleRegistry registry;

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
        campaign.setName("Safety Contract Campaign");
        SessionWorkspace workspace = new SessionWorkspace(
                campaign,
                CampaignSession.idle(campaign),
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
    void renderedCockpitHasExactlyOneRootPerRegistryKeyWithMatchingBehavior() throws Exception {
        Document document = renderCockpit();
        List<Element> roots = document.select("[data-runtime-module]");

        assertThat(roots)
                .extracting(el -> el.attr("data-runtime-module"))
                .as("rendered data-runtime-module keys must match the registry exactly once each")
                .containsExactlyInAnyOrderElementsOf(
                        registry.all().stream().map(CockpitModuleDefinition::key).toList());

        for (CockpitModuleDefinition definition : registry.all()) {
            Element root = document.selectFirst(
                    "[data-runtime-module=" + definition.key() + "]");
            assertThat(root)
                    .as("registry module %s must render a data-runtime-module root", definition.key())
                    .isNotNull();
            assertThat(root.attr("data-module-key"))
                    .as("outer shell for %s should also carry data-module-key", definition.key())
                    .isEqualTo(definition.key());
        }

        assertThat(document.select(".runtime-story")).hasSize(1);
        assertThat(document.select(".runtime-story[data-runtime-module]")).isEmpty();
    }

    private Document renderCockpit() throws Exception {
        String html = mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }
}
