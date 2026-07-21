package dev.hendrikhoemberg.dmhelper.common;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * DM Mode is the app's only defence when a DM turns the laptop toward the table: the navbar
 * toggle sets body.dm-mode-off, and CSS then hides .dm-only / [data-dm-only]. The machinery
 * and the "PLAYER-SAFE" badge shipped, but almost nothing was tagged -- a section labelled
 * "Secret" stayed on screen underneath the badge.
 *
 * <p>These tests pin which content must carry the tag. Read-aloud text is deliberately
 * excluded: it is the one kind a DM is meant to show or read to the table.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DmModeCoverageTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private AdventureService adventures;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    private String scenePage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String cockpit() throws Exception {
        adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        return mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String npcPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/world/npcs/{n}", seeded.campaignId(), seeded.npcId()))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Opening tag of the nearest enclosing element that carries {@code marker} in its
     * attributes, searching backwards from the first occurrence of {@code needle}.
     */
    private static String enclosingTag(String html, String needle, String marker) {
        int at = html.indexOf(needle);
        assertThat(at).as("content %s must be present in the page", abbreviate(needle)).isGreaterThan(-1);
        int cursor = at;
        while (cursor > 0) {
            int open = html.lastIndexOf("<div", cursor - 1);
            if (open < 0) {
                return null;
            }
            int close = html.indexOf('>', open);
            String tag = html.substring(open, close < 0 ? html.length() : close + 1);
            if (tag.contains(marker)) {
                return tag;
            }
            cursor = open;
        }
        return null;
    }

    private static String abbreviate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static void assertBlockIsDmOnly(String html, String needle) {
        String tag = enclosingTag(html, needle, "structured-block");
        assertThat(tag)
                .as("a .structured-block must enclose %s", abbreviate(needle))
                .isNotNull();
        assertThat(tag)
                .as("this block is DM-facing and must be hidden when DM Mode is off: %s", abbreviate(needle))
                .contains("dm-only");
    }

    @Test
    void sceneSecretSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsDmOnly(scenePage(), PopulatedCampaignFixture.SECRET_BODY);
    }

    @Test
    void sceneTreasureSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsDmOnly(scenePage(), PopulatedCampaignFixture.TREASURE_BODY);
    }

    @Test
    void sceneDmAdviceIsHiddenFromTheTable() throws Exception {
        assertBlockIsDmOnly(scenePage(), "Lass die Gruppe suchen.");
    }

    @Test
    void readAloudStaysVisibleBecauseItIsMeantForThePlayers() throws Exception {
        String tag = enclosingTag(scenePage(), PopulatedCampaignFixture.READ_ALOUD_BODY, "structured-block");
        assertThat(tag).isNotNull();
        assertThat(tag)
                .as("read-aloud is the one section kind a DM shows the table; tagging it would defeat the feature")
                .doesNotContain("dm-only");
    }

    @Test
    void cockpitSecretSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsDmOnly(cockpit(), PopulatedCampaignFixture.SECRET_BODY);
    }

    @Test
    void cockpitReadAloudStaysVisible() throws Exception {
        String tag = enclosingTag(cockpit(), PopulatedCampaignFixture.READ_ALOUD_BODY, "structured-block");
        assertThat(tag).isNotNull();
        assertThat(tag).doesNotContain("dm-only");
    }

    /** Opening tag of the nearest enclosing element carrying {@code marker}, any element type. */
    private static void assertNearestMarkedBlockIsDmOnly(String html, String needle, String marker) {
        String tag = enclosingTag(html, needle, marker);
        assertThat(tag).as("a .%s must enclose %s", marker, abbreviate(needle)).isNotNull();
        assertThat(tag)
                .as("DM-facing detail must be hidden when the laptop faces the table: %s", abbreviate(needle))
                .contains("dm-only");
    }

    @Test
    void sceneChecksAreHiddenFromTheTable() throws Exception {
        // A DC and its success/failure outcomes tell players exactly what to roll and expect.
        assertNearestMarkedBlockIsDmOnly(scenePage(), "Der Absender wird klar.", "data-check-id");
    }

    @Test
    void sceneParticipantsAreHiddenFromTheTable() throws Exception {
        // Monster counts and placement hints are the ambush.
        assertNearestMarkedBlockIsDmOnly(scenePage(), "Hinter der T", "data-participant-id");
    }

    @Test
    void sceneTransitionDmNotesAreHiddenFromTheTable() throws Exception {
        assertNearestMarkedBlockIsDmOnly(scenePage(), "Nur wenn die Truhe offen ist.", "data-transition-id");
    }

    @Test
    void cockpitChecksAreHiddenFromTheTable() throws Exception {
        String html = cockpit();
        int at = html.indexOf("Der Absender wird klar.");
        assertThat(at).as("cockpit must render the check").isGreaterThan(-1);
        assertThat(html.substring(Math.max(0, at - 600), at))
                .as("cockpit checks are DM-only")
                .contains("dm-only");
    }

    @Test
    void cockpitOffersItsOwnDmModeToggle() throws Exception {
        // The cockpit renders its own topbar instead of fragments/navbar, so it inherited
        // neither the toggle nor the script that initialises it -- leaving the one screen
        // that is actually open at the table with no way to hide DM content.
        assertThat(cockpit())
                .as("the cockpit must be able to go player-safe without navigating away")
                .contains("dmModeCheckbox");
    }

    /** Asserts the element carrying {@code cssClass} also carries dm-only. */
    private static void assertClassIsDmOnly(String html, String cssClass, String why) {
        int at = html.indexOf("class=\"" + cssClass);
        if (at < 0) {
            at = html.indexOf(cssClass);
        }
        assertThat(at).as("%s must render", cssClass).isGreaterThan(-1);
        int open = html.lastIndexOf('<', at);
        int close = html.indexOf('>', at);
        String tag = html.substring(open, close < 0 ? html.length() : close + 1);
        assertThat(tag).as("%s -- tag was: %s", why, tag).contains("dm-only");
    }

    @Test
    void cockpitSceneSummaryIsHiddenFromTheTable() throws Exception {
        assertClassIsDmOnly(cockpit(), "scene-summary",
                "the scene summary is DM prose describing the room before players discover it");
    }

    @Test
    void cockpitSceneNotesAreHiddenFromTheTable() throws Exception {
        assertClassIsDmOnly(cockpit(), "scene-body",
                "the scene body is the DM's own notes");
    }

    @Test
    void cockpitTransitionsAreHiddenFromTheTable() throws Exception {
        assertClassIsDmOnly(cockpit(), "scene-transitions",
                "transitions spell out where the plot goes next");
    }

    @Test
    void cockpitQuestProgressIsHiddenFromTheTable() throws Exception {
        assertClassIsDmOnly(cockpit(), "quest-progress-panel",
                "quest names and objective states are plot structure the table should not read");
    }

    @Test
    void npcSecretIsHiddenFromTheTable() throws Exception {
        String html = npcPage();
        String needle = "War früher Ritter.";
        int at = html.indexOf(needle);
        assertThat(at).as("NPC secret must render").isGreaterThan(-1);
        String before = html.substring(Math.max(0, at - 300), at);
        assertThat(before)
                .as("an NPC's secret must be hidden when the laptop faces the table")
                .contains("dm-only");
    }
}
