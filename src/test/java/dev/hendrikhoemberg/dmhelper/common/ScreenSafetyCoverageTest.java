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
 * Screen safety is the app's defence when a DM turns the laptop toward the table: the toggle
 * sets {@code data-screen-safety="TABLE_SAFE"} on the body, and JS/CSS then hides content
 * marked {@code data-screen-sensitive}. The machinery shipped, but untagged content stayed on
 * screen underneath the badge.
 *
 * <p>These tests pin which content must carry the tag. Read-aloud text is deliberately
 * excluded: it is the one kind a DM is meant to show or read to the table.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScreenSafetyCoverageTest {

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
        return s.length() <= 40 ? s : s.substring(0, 40) + "\u2026";
    }

    private static void assertBlockIsScreenSensitive(String html, String needle) {
        String tag = enclosingTag(html, needle, "structured-block");
        assertThat(tag)
                .as("a .structured-block must enclose %s", abbreviate(needle))
                .isNotNull();
        assertThat(tag)
                .as("this block is DM-facing and must be hidden when screen safety is TABLE_SAFE: %s", abbreviate(needle))
                .contains("data-screen-sensitive");
    }

    @Test
    void sceneSecretSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsScreenSensitive(scenePage(), PopulatedCampaignFixture.SECRET_BODY);
    }

    @Test
    void sceneTreasureSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsScreenSensitive(scenePage(), PopulatedCampaignFixture.TREASURE_BODY);
    }

    @Test
    void sceneDmAdviceIsHiddenFromTheTable() throws Exception {
        assertBlockIsScreenSensitive(scenePage(), "Lass die Gruppe suchen.");
    }

    @Test
    void readAloudStaysVisibleBecauseItIsMeantForThePlayers() throws Exception {
        String tag = enclosingTag(scenePage(), PopulatedCampaignFixture.READ_ALOUD_BODY, "structured-block");
        assertThat(tag).isNotNull();
        assertThat(tag)
                .as("read-aloud is the one section kind a DM shows the table; tagging it would defeat the feature")
                .doesNotContain("data-screen-sensitive");
    }

    @Test
    void cockpitSecretSectionIsHiddenFromTheTable() throws Exception {
        assertBlockIsScreenSensitive(cockpit(), PopulatedCampaignFixture.SECRET_BODY);
    }

    @Test
    void cockpitReadAloudStaysVisible() throws Exception {
        String tag = enclosingTag(cockpit(), PopulatedCampaignFixture.READ_ALOUD_BODY, "structured-block");
        assertThat(tag).isNotNull();
        assertThat(tag).doesNotContain("data-screen-sensitive");
    }

    /** Opening tag of the nearest enclosing element carrying {@code marker}, any element type. */
    private static void assertNearestMarkedBlockIsScreenSensitive(String html, String needle, String marker) {
        String tag = enclosingTag(html, needle, marker);
        assertThat(tag).as("a [%s] must enclose %s", marker, abbreviate(needle)).isNotNull();
        assertThat(tag)
                .as("DM-facing detail must be hidden when the laptop faces the table: %s", abbreviate(needle))
                .contains("data-screen-sensitive");
    }

    @Test
    void sceneChecksAreHiddenFromTheTable() throws Exception {
        assertNearestMarkedBlockIsScreenSensitive(scenePage(), "Der Absender wird klar.", "data-check-id");
    }

    @Test
    void sceneParticipantsAreHiddenFromTheTable() throws Exception {
        assertNearestMarkedBlockIsScreenSensitive(scenePage(), "Hinter der T", "data-participant-id");
    }

    @Test
    void sceneTransitionDmNotesAreHiddenFromTheTable() throws Exception {
        assertNearestMarkedBlockIsScreenSensitive(scenePage(), "Nur wenn die Truhe offen ist.", "data-transition-id");
    }

    @Test
    void cockpitChecksAreHiddenFromTheTable() throws Exception {
        String html = cockpit();
        int at = html.indexOf("Der Absender wird klar.");
        assertThat(at).as("cockpit must render the check").isGreaterThan(-1);
        assertThat(html.substring(Math.max(0, at - 600), at))
                .as("cockpit checks must be screen-sensitive")
                .contains("data-screen-sensitive");
    }

    @Test
    void cockpitOffersItsOwnScreenSafetyToggle() throws Exception {
        assertThat(cockpit())
                .as("the cockpit must be able to go table-safe without navigating away")
                .contains("screenSafetyCheckbox");
    }

    /** Asserts the element carrying {@code cssClass} also carries data-screen-sensitive. */
    private static void assertClassIsScreenSensitive(String html, String cssClass, String why) {
        int at = html.indexOf("class=\"" + cssClass);
        if (at < 0) {
            at = html.indexOf(cssClass);
        }
        assertThat(at).as("%s must render", cssClass).isGreaterThan(-1);
        int open = html.lastIndexOf('<', at);
        int close = html.indexOf('>', at);
        String tag = html.substring(open, close < 0 ? html.length() : close + 1);
        assertThat(tag).as("%s -- tag was: %s", why, tag).contains("data-screen-sensitive");
    }

    @Test
    void cockpitSceneSummaryIsHiddenFromTheTable() throws Exception {
        assertClassIsScreenSensitive(cockpit(), "scene-summary",
                "the scene summary is DM prose describing the room before players discover it");
    }

    @Test
    void cockpitSceneNotesAreHiddenFromTheTable() throws Exception {
        assertClassIsScreenSensitive(cockpit(), "scene-body",
                "the scene body is the DM's own notes");
    }

    @Test
    void cockpitTransitionsAreHiddenFromTheTable() throws Exception {
        assertClassIsScreenSensitive(cockpit(), "scene-transitions",
                "transitions spell out where the plot goes next");
    }

    @Test
    void cockpitQuestProgressIsHiddenFromTheTable() throws Exception {
        assertClassIsScreenSensitive(cockpit(), "quest-progress-panel",
                "quest names and objective states are plot structure the table should not read");
    }

    @Test
    void npcSecretIsHiddenFromTheTable() throws Exception {
        String html = npcPage();
        String needle = "War fr\u00fcher Ritter.";
        int at = html.indexOf(needle);
        assertThat(at).as("NPC secret must render").isGreaterThan(-1);
        String before = html.substring(Math.max(0, at - 300), at);
        assertThat(before)
                .as("an NPC's secret must be hidden when the laptop faces the table")
                .contains("data-screen-sensitive");
    }

    private String questPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/quests/{q}", seeded.campaignId(), seeded.questId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String locationPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/world/locations/{l}",
                        seeded.campaignId(), seeded.locationId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String factionPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/world/factions/{f}",
                        seeded.campaignId(), seeded.factionId()))
                .andReturn().getResponse().getContentAsString();
    }

    /** The field's own `u-mb-md` wrapper must carry data-screen-sensitive. */
    private static void assertFieldBlockIsScreenSensitive(String html, String needle, String why) {
        String tag = enclosingTag(html, needle, "u-mb-md");
        assertThat(tag).as("a .u-mb-md block must enclose %s", abbreviate(needle)).isNotNull();
        assertThat(tag).as("%s -- tag was: %s", why, tag).contains("data-screen-sensitive");
    }

    @Test
    void questPrerequisitesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(questPage(), PopulatedCampaignFixture.QUEST_PREREQUISITES,
                "prerequisites tell players exactly what gates the plot");
    }

    @Test
    void questRewardsAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(questPage(), PopulatedCampaignFixture.QUEST_REWARDS,
                "rewards are the payoff the DM has not offered yet");
    }

    @Test
    void questOutcomeNotesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(questPage(), PopulatedCampaignFixture.QUEST_OUTCOME_NOTES,
                "outcome notes describe how the quest resolves");
    }

    @Test
    void locationSecretsAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(locationPage(), PopulatedCampaignFixture.LOCATION_SECRETS,
                "a location's secrets are the thing players are meant to discover");
    }

    @Test
    void factionGoalsAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(factionPage(), "Ordnung herstellen",
                "faction goals are plot structure the table should not read");
    }

    @Test
    void factionReputationNotesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsScreenSensitive(factionPage(), PopulatedCampaignFixture.FACTION_REPUTATION_NOTES,
                "reputation notes record how the faction privately regards the party");
    }
}
