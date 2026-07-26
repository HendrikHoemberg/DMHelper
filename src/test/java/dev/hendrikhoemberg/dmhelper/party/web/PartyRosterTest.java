package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
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

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartyRosterTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/party", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyMemberIsOneScannableRow() {
        assertThat(body).contains("class=\"roster\"");
        assertThat(occurrences(body, "id=\"pm-card-")).isEqualTo(4);
    }

    @Test
    void aRowShowsTheDecisionFieldsWithoutOpeningAnything() {
        String row = rowFor(seeded.woundedMemberId());
        String summary = row.substring(row.indexOf("<summary"), row.indexOf("</summary>"));

        assertThat(summary).contains(PreparationSurfaceFixture.WOUNDED_MEMBER);
        assertThat(summary).contains(PreparationSurfaceFixture.WOUNDED_PLAYER);
        assertThat(summary).as("AC").contains(">15<");
        assertThat(summary).as("current and maximum HP").contains("9/24");
        assertThat(summary).as("passive perception").contains(">14<");
        assertThat(summary).as("passive insight").contains(">12<");
        assertThat(summary).as("passive investigation").contains(">13<");
        assertThat(summary).as("relevant conditions belong on the row")
                .contains(PreparationSurfaceFixture.CONDITION_NAME);
    }

    @Test
    void newMembersAreAppendedInsideTheRosterNotAfterIt() throws java.io.IOException {
        assertThat(body).as("the roster is the insertion container").contains("id=\"party-roster\"");

        String form = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates/party/_form.html"));
        assertThat(form)
                .as("an appended row must land inside the roster, or it renders outside the grid")
                .contains("'#party-roster'")
                .contains("'beforeend'");
    }

    @Test
    void aDeepLinkedRowOpensItself() {
        assertThat(body)
                .as("ContentDestinationRegistry links to /party#pm-card-<id>; a collapsed row hides it")
                .contains("#pm-card-")
                .contains("target.open = true");
    }

    @Test
    void temporaryHitPointsAreVisibleOnTheRow() {
        String row = rowForName(PreparationSurfaceFixture.HEALTHY_MEMBER);
        assertThat(row).contains("+5");
    }

    @Test
    void editingAndRemovingAreSecondaryNotOnTheRow() {
        String row = rowFor(seeded.woundedMemberId());
        String summary = row.substring(row.indexOf("<summary"), row.indexOf("</summary>"));

        assertThat(summary).as("the row itself stays scannable").doesNotContain("Remove");
        assertThat(summary).doesNotContain("hx-delete");
        assertThat(row).as("but the actions are still reachable once expanded").contains("Remove");
        assertThat(row).contains("quicknotes-strip");
    }

    @Test
    void deepLinkAnchorsSurvive() {
        assertThat(body)
                .as("ContentDestinationRegistry and the command palette link to /party#pm-card-<id>")
                .contains("id=\"pm-card-" + seeded.woundedMemberId() + "\"");
    }

    @Test
    void inactiveMembersAreMarkedRatherThanHidden() {
        String row = rowFor(seeded.inactiveMemberId());
        assertThat(row).contains("roster-row--inactive");
    }

    @Test
    void bulkActionsAreBehindADisclosure() {
        int bulkAt = body.indexOf("data-bulk-actions");
        assertThat(bulkAt).as("bulk controls must exist").isGreaterThan(-1);
        assertThat(body.substring(Math.max(0, bulkAt - 200), bulkAt))
                .as("seven bulk buttons must not compete with the roster")
                .contains("<details");
    }

    private String rowFor(java.util.UUID memberId) {
        int start = body.indexOf("id=\"pm-card-" + memberId + "\"");
        assertThat(start).as("row for %s", memberId).isGreaterThan(-1);
        int rowStart = body.lastIndexOf("<details", start);
        int rowEnd = body.indexOf("</details>", start);
        return body.substring(rowStart, rowEnd);
    }

    private String rowForName(String memberName) {
        int rosterStart = body.indexOf("class=\"roster\"");
        assertThat(rosterStart).as("roster section").isGreaterThan(-1);
        int nameAt = body.indexOf(memberName, rosterStart);
        assertThat(nameAt).as("row for %s", memberName).isGreaterThan(-1);
        int rowStart = body.lastIndexOf("<details", nameAt);
        int rowEnd = body.indexOf("</details>", nameAt);
        return body.substring(rowStart, rowEnd);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
