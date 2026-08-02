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
 * Renders the session cockpit through the real MVC stack so module shells, zones,
 * and layout chrome match production Thymeleaf output.
 */
@SpringBootTest
class CockpitWorkbenchTemplateContractTest {

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
        campaign.setName("Workbench Campaign");
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
    void workbenchRendersZonesShellsSplittersAndLayoutControls() throws Exception {
        Document document = renderWorkbench();

        assertThat(document.select("[data-cockpit-workbench]")).hasSize(1);
        assertThat(document.select("[data-cockpit-zone]")).extracting(e -> e.attr("data-cockpit-zone"))
                .containsExactlyInAnyOrder("PRIMARY", "LEFT_SUPPORT", "RIGHT_SUPPORT", "BOTTOM_UTILITY");
        assertThat(document.select("[role=separator][aria-orientation]")).hasSize(3);
        assertThat(document.select(".cockpit-module[data-module-key]")).extracting(e -> e.attr("data-module-key"))
                .containsExactlyInAnyOrderElementsOf(registry.all().stream()
                        .map(CockpitModuleDefinition::key).toList());
        assertThat(document.select(".cockpit-module[data-module-key]")).extracting(e -> e.attr("data-module-key"))
                .doesNotHaveDuplicates();
        assertThat(document.select("#cockpitLayoutModeButton")).singleElement()
                .satisfies(button -> assertThat(button.text()).contains("Edit layout"));
        assertThat(document.select("[data-layout-edit-only]")).allSatisfy(
                node -> assertThat(node.hasAttr("hidden")).isTrue());
    }

    @Test
    void explorationPresetPlacesModulesInZonesAndDepot() throws Exception {
        Document document = renderWorkbench();

        assertThat(document.select("[data-cockpit-zone=PRIMARY] .cockpit-module[data-module-key=story]"))
                .hasSize(1);
        assertThat(document.select("[data-cockpit-zone=LEFT_SUPPORT] .cockpit-module[data-module-key=session-plan]"))
                .hasSize(1);
        assertThat(document.select("[data-cockpit-zone=RIGHT_SUPPORT] .cockpit-module[data-module-key=party]"))
                .hasSize(1);
        assertThat(document.select("[data-cockpit-zone=BOTTOM_UTILITY] .cockpit-module[data-module-key=quick-notes]"))
                .hasSize(1);
        assertThat(document.select("[data-cockpit-zone=BOTTOM_UTILITY] .cockpit-module[data-module-key=audio]"))
                .hasSize(1);
        assertThat(document.select("[data-cockpit-zone=BOTTOM_UTILITY] .cockpit-module[data-module-key=reference]"))
                .hasSize(1);
        assertThat(document.select(".cockpit-module-depot .cockpit-module[data-module-key]"))
                .extracting(e -> e.attr("data-module-key"))
                .containsExactlyInAnyOrder("map", "encounter", "session-log");
        assertThat(document.select("[data-cockpit-zone=BOTTOM_UTILITY]").attr("data-collapsed"))
                .isEqualTo("false");
        assertThat(document.select("[data-cockpit-workbench]").attr("data-bottom-collapsed"))
                .isEqualTo("false");
    }

    @Test
    void layoutMutationControlsAreDisabledWithoutController() throws Exception {
        Document document = renderWorkbench();

        assertThat(document.select("#cockpitPresetPicker")).singleElement()
                .satisfies(el -> {
                    assertThat(el.hasAttr("disabled")).isTrue();
                    assertThat(el.attr("aria-disabled")).isEqualTo("true");
                });
        assertThat(document.select("#cockpitLayoutModeButton")).singleElement()
                .satisfies(el -> {
                    assertThat(el.hasAttr("disabled")).isTrue();
                    assertThat(el.attr("aria-disabled")).isEqualTo("true");
                });
        assertThat(document.select("#cockpitAddModuleButton")).singleElement()
                .satisfies(el -> {
                    assertThat(el.hasAttr("disabled")).isTrue();
                    assertThat(el.attr("aria-disabled")).isEqualTo("true");
                });
        assertThat(document.select("#cockpitFocusReturn")).hasSize(1);
        assertThat(document.select("[data-bottom-utility-toggle]")).singleElement()
                .satisfies(el -> {
                    assertThat(el.hasAttr("hidden")).isTrue();
                    assertThat(el.attr("aria-expanded")).isEqualTo("false");
                });
        assertThat(document.select("#cockpitAddModuleDialog")).hasSize(1);
        assertThat(document.select("#cockpitLayoutExitDialog")).hasSize(1);
        assertThat(document.select("#cockpitPresetNameDialog")).hasSize(1);
        assertThat(document.select("#cockpitLayoutNotice")).hasSize(1);
        assertThat(document.html()).contains("cockpitLayoutConfig");
    }

    private Document renderWorkbench() throws Exception {
        String html = mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }
}
