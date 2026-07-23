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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every runtime module inserted into or declared on the cockpit page must carry a stable
 * {@code data-runtime-module} key and a {@code data-table-safe-behavior} attribute on its
 * outer module shell. Registry keys are the source of truth for behavior values.
 *
 * <p>Static source scans catch declaration drift; MockMvc-rendered HTML asserts the
 * production workbench emits exactly one root per registry key with matching classification.
 */
@SpringBootTest
class RuntimeModuleSafetyContractTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path COCKPIT = TEMPLATES.resolve("session/cockpit.html");
    private static final CockpitModuleRegistry REGISTRY = CockpitModuleRegistry.standard();

    /** Valid table-safe behavior values. */
    private static final Set<String> VALID_BEHAVIORS = Set.of("FILTER", "HIDE", "PLAYER_PROJECTION");

    /** Regex to extract data-runtime-module declarations. */
    private static final Pattern MODULE_PATTERN = Pattern.compile(
            "data-runtime-module\\s*=\\s*\"([^\"]+)\"");

    /** Regex for th:attr-style module keys used on shells. */
    private static final Pattern ATTR_MODULE_PATTERN = Pattern.compile(
            "data-runtime-module\\s*=\\s*\\$\\{module\\.key\\}");

    /** Regex to extract data-table-safe-behavior declarations. */
    private static final Pattern BEHAVIOR_PATTERN = Pattern.compile(
            "data-table-safe-behavior\\s*=\\s*\"([^\"]+)\"");

    /** Shell/workbench fragments that declare the authoritative module roots. */
    private static final List<String> SHELL_FRAGMENTS = List.of(
            "session/cockpit.html",
            "session/_cockpit-workbench.html",
            "session/_cockpit-module-shell.html"
    );

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
            assertThat(root.attr("data-table-safe-behavior"))
                    .as("data-table-safe-behavior for %s must match the registry", definition.key())
                    .isEqualTo(definition.screenSafetyBehavior().name());
            assertThat(root.attr("data-module-key"))
                    .as("outer shell for %s should also carry data-module-key", definition.key())
                    .isEqualTo(definition.key());
        }

        assertThat(document.select(".runtime-story")).hasSize(1);
        assertThat(document.select(".runtime-story[data-runtime-module]")).isEmpty();
    }

    @Test
    void allRegistryModulesHaveExactlyOneAuthoritativeRoot() throws IOException {
        Map<String, String> keyToBehavior = collectModuleBehaviors();

        assertThat(keyToBehavior.keySet())
                .as("exactly the registry module keys must appear once as data-runtime-module")
                .containsExactlyInAnyOrderElementsOf(
                        REGISTRY.all().stream().map(CockpitModuleDefinition::key).toList());

        for (CockpitModuleDefinition definition : REGISTRY.all()) {
            assertThat(keyToBehavior.get(definition.key()))
                    .as("data-table-safe-behavior for %s must match the registry", definition.key())
                    .isEqualTo(definition.screenSafetyBehavior().name());
        }
    }

    @Test
    void everyModuleDeclaresAValidTableSafeBehavior() throws IOException {
        Map<String, String> keyToBehavior = collectModuleBehaviors();

        assertThat(keyToBehavior.values())
                .as("all data-table-safe-behavior values must be one of " + VALID_BEHAVIORS)
                .allMatch(VALID_BEHAVIORS::contains);
    }

    @Test
    void moduleShellFragmentDeclaresRuntimeAndSafetyAttributes() throws IOException {
        Path shell = TEMPLATES.resolve("session/_cockpit-module-shell.html");
        assertThat(shell).exists();
        String content = Files.readString(shell);
        assertThat(content)
                .contains("data-runtime-module")
                .contains("data-table-safe-behavior")
                .contains("data-module-key")
                .contains("class=\"cockpit-module\"");
        assertThat(ATTR_MODULE_PATTERN.matcher(content).find()
                || MODULE_PATTERN.matcher(content).find()).isTrue();
    }

    @Test
    void screenSafetyToggleExistsOnCockpitAndNavbar() throws IOException {
        String cockpit = Files.readString(COCKPIT);
        String navbar = Files.readString(TEMPLATES.resolve("fragments/navbar.html"));

        assertThat(cockpit)
                .as("cockpit must have a screen safety toggle with id screenSafetyCheckbox")
                .contains("screenSafetyCheckbox");
        assertThat(navbar)
                .as("navbar must have a screen safety toggle with id screenSafetyCheckbox")
                .contains("screenSafetyCheckbox");
    }

    @Test
    void noDmModeTerminologyRemains() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<Path> htmlFiles = files.filter(p -> p.toString().endsWith(".html")).toList();

            for (Path htmlFile : htmlFiles) {
                String content = Files.readString(htmlFile);
                assertThat(content)
                        .as("DM Mode terminology must not appear in " + htmlFile.getFileName())
                        .doesNotContain("dmMode", "dm-mode", "DM Mode", "DM_MODE", "DmMode");
            }
        }
    }

    @Test
    void screenSafetyEventNamesAreUsed() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        String safetyJs = Files.readString(Path.of("src/main/resources/static/js/screen-safety.js"));
        String keyboardJs = Files.readString(Path.of("src/main/resources/static/js/keyboard.js"));

        assertThat(safetyJs)
                .as("the central screen-safety controller must dispatch screen-safety-changed")
                .contains("screen-safety-changed");
        assertThat(js)
                .as("session-cockpit.js must not duplicate the central change event")
                .doesNotContain("new CustomEvent('screen-safety-changed'");
        assertThat(keyboardJs)
                .as("keyboard.js must dispatch screen-safety-toggle")
                .contains("screen-safety-toggle");
        assertThat(js)
                .as("session-cockpit.js must not reference dm-mode-changed")
                .doesNotContain("dm-mode-changed");
        assertThat(js)
                .as("session-cockpit.js must use screen safety terminology")
                .doesNotContain("dmMode")
                .contains("tableSafe");
    }

    @Test
    void layoutControllerReappliesScreenSafetyAndHandlesModuleState() throws IOException {
        String layoutJs = Files.readString(
                Path.of("src/main/resources/static/js/cockpit-layout.js"));
        assertThat(layoutJs)
                .as("layout must re-apply the existing Screen Safety controller after DOM moves")
                .contains("setScreenSafety")
                .contains("animate: false")
                .contains("reapplyScreenSafety");
        assertThat(layoutJs)
                .as("layout must consume cockpit:module-state and expose visibility queries")
                .contains("cockpit:module-state")
                .contains("isModuleVisible")
                .contains("_retryCallbacks")
                .contains("cockpit:module-visibility");
    }

    @Test
    void moduleShellDeclaresStateMessageAttributesAndRetry() throws IOException {
        Path shell = TEMPLATES.resolve("session/_cockpit-module-shell.html");
        String content = Files.readString(shell);
        assertThat(content)
                .as("shell chrome must carry state messages and an inline Retry action")
                .contains("data-module-status")
                .contains("data-module-error")
                .contains("data-module-retry")
                .contains("data-loading-message")
                .contains("data-empty-message")
                .contains("data-error-message")
                .contains("data-module-body");
    }

    private Document renderCockpit() throws Exception {
        String html = mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }

    /**
     * Collects literal {@code data-runtime-module="…"} declarations from cockpit shell sources.
     * Dynamic shell attributes use {@code ${module.key}} / {@code ${module.screenSafetyBehavior}}
     * and are expanded against the registry so each key still contributes exactly once.
     */
    private Map<String, String> collectModuleBehaviors() throws IOException {
        Map<String, String> keyToBehavior = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        boolean shellUsesDynamicKeys = false;

        for (String fragment : SHELL_FRAGMENTS) {
            Path path = TEMPLATES.resolve(fragment);
            if (!Files.exists(path)) {
                continue;
            }
            String content = Files.readString(path);
            if (ATTR_MODULE_PATTERN.matcher(content).find()
                    || content.contains("data-runtime-module=${module.key}")
                    || content.contains("data-runtime-module=${module.key},")) {
                shellUsesDynamicKeys = true;
            }

            Matcher moduleMatcher = MODULE_PATTERN.matcher(content);
            while (moduleMatcher.find()) {
                String key = moduleMatcher.group(1);
                int keyPos = moduleMatcher.start();
                int tagStart = content.lastIndexOf('<', keyPos);
                int tagEnd = content.indexOf('>', keyPos);
                if (tagStart < 0 || tagEnd < 0) {
                    continue;
                }
                String tagContent = content.substring(tagStart, tagEnd + 1);
                Matcher behaviorMatcher = BEHAVIOR_PATTERN.matcher(tagContent);
                String behavior = behaviorMatcher.find() ? behaviorMatcher.group(1) : null;
                if (keyToBehavior.containsKey(key)) {
                    duplicates.add(key);
                } else {
                    keyToBehavior.put(key, behavior);
                }
            }
        }

        if (shellUsesDynamicKeys) {
            for (CockpitModuleDefinition definition : REGISTRY.all()) {
                String key = definition.key();
                String behavior = definition.screenSafetyBehavior().name();
                if (keyToBehavior.containsKey(key)) {
                    duplicates.add(key);
                } else {
                    keyToBehavior.put(key, behavior);
                }
            }
        }

        assertThat(duplicates)
                .as("data-runtime-module keys must be unique across cockpit shell sources")
                .isEmpty();
        assertThat(keyToBehavior.values())
                .as("every data-runtime-module must declare data-table-safe-behavior")
                .doesNotContainNull();

        return keyToBehavior;
    }
}
