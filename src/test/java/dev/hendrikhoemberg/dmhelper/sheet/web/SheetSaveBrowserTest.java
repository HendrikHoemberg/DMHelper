package dev.hendrikhoemberg.dmhelper.sheet.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.CreateSheetRequest;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.ClassLevelEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sheet's editable sections are saved by scripts, not by a plain form post, so only a
 * real browser proves they reach the server. Each test drives the control a DM would click
 * and then reloads, because "the checkbox looks ticked" is not evidence that anything
 * persisted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class SheetSaveBrowserTest {

    @LocalServerPort private int port;

    @Autowired private CampaignRepository campaigns;
    @Autowired private PartyMemberRepository partyMembers;
    @Autowired private SheetService sheetService;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures.clear();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private PartyMember newMember(String name) {
        Campaign campaign = new Campaign();
        campaign.setName("Sheet Save Test");
        campaign.setDescription("Test campaign");
        campaign = campaigns.save(campaign);

        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName(name);
        member.setAc(16);
        member.setMaxHp(12);
        member.setCurrentHp(12);
        member.setInitiativeBonus(1);
        member.setSpeed(30);
        member.setPassivePerception(10);
        member.setPassiveInsight(10);
        member.setPassiveInvestigation(10);
        return partyMembers.save(member);
    }

    private PartyMember memberWithSheet(String name) {
        PartyMember member = newMember(name);
        Map<String, Object> proficiencies = Map.of(
                "skills", List.of(), "expertise", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of());
        sheetService.createSheet(new CreateSheetRequest(
                member.getId(),
                Map.of("str", 16, "dex", 12, "con", 14, "int", 10, "wis", 12, "cha", 8),
                List.of(new ClassLevelEntry("srd-2024_fighter", 1, List.of())),
                proficiencies, null, null, List.of(), 0));
        return member;
    }

    private String sheetUrl(PartyMember member) {
        return "http://127.0.0.1:" + port + "/campaigns/" + member.getCampaign().getId()
                + "/party/" + member.getId() + "/sheet";
    }

    @Test
    void savingProficienciesPersistsTheTickedSkill() {
        PartyMember member = memberWithSheet("Prof Saver");
        page.navigate(sheetUrl(member));

        page.check("input[type=checkbox][name=skills][value=athletics]");
        page.click("button:has-text('Save Proficiencies')");
        page.waitForTimeout(1500);

        page.navigate(sheetUrl(member));
        boolean stillTicked = (Boolean) page.evaluate(
                "() => document.querySelector(\"input[name='skills'][value='athletics']\").checked");

        assertThat(stillTicked)
                .as("Athletics stays proficient after Save Proficiencies and a reload")
                .isTrue();
    }

    @Test
    void savingLiveStatePersistsCurrentHp() {
        PartyMember member = memberWithSheet("HP Saver");
        page.navigate(sheetUrl(member));

        page.fill("#ls-currentHp", "4");
        page.click("button:has-text('Save Live State')");
        page.waitForTimeout(1500);

        page.navigate(sheetUrl(member));
        String hp = page.inputValue("#ls-currentHp");

        assertThat(hp).as("current HP entered on the sheet survives a reload").isEqualTo("4");
    }

    /**
     * The wizard invites free text ("Comma-separated skill names"), so a DM types the skill
     * the way the rules spell it. Everything downstream keys on lowercase snake_case, so the
     * entry has to be normalised or it is silently dropped.
     */
    @Test
    void skillsTypedTheWayADmWritesThemReachTheSheet() {
        PartyMember member = newMember("Wizard Walker");
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + member.getCampaign().getId()
                + "/party/" + member.getId() + "/sheet/create");

        page.selectOption("#class-select", new com.microsoft.playwright.options.SelectOption().setLabel("Fighter"));
        page.waitForTimeout(500);
        page.click("button:has-text('Next')");           // -> scores
        page.click("button:visible:has-text('Next')");   // -> skills
        page.fill("#skills-input-free", "Athletics, Animal Handling");
        page.click("button:visible:has-text('Next')");   // -> species/background
        page.click("button:visible:has-text('Next')");   // -> review
        page.click("button:visible:has-text('Create Sheet')");
        page.waitForTimeout(2000);

        page.navigate(sheetUrl(member));
        boolean athletics = (Boolean) page.evaluate(
                "() => document.querySelector(\"input[name='skills'][value='athletics']\").checked");
        boolean animalHandling = (Boolean) page.evaluate(
                "() => document.querySelector(\"input[name='skills'][value='animal_handling']\").checked");

        assertThat(athletics).as("\"Athletics\" is stored as the athletics proficiency").isTrue();
        assertThat(animalHandling)
                .as("\"Animal Handling\" is stored as the animal_handling proficiency").isTrue();
    }
}
