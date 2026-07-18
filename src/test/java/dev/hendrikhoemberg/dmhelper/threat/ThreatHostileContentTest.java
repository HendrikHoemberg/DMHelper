package dev.hendrikhoemberg.dmhelper.threat;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ThreatCombatantRequest;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hostile script/event-handler content in threat Markdown and plain mechanics
 * must render inert on detail, cockpit story, and encounter tracker surfaces.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ThreatHostileContentTest {

    private static final String HOSTILE_MD =
            "Safe intro\n\n<script>window.threatXss=true</script>"
                    + "<img src=x onerror=window.threatXss=true>"
                    + "[click](javascript:window.threatXss=true)";
    private static final String HOSTILE_PLAIN =
            "<script>window.threatMechXss=true</script> onerror=alert(1)";

    @LocalServerPort
    private int port;

    @Autowired private CampaignRepository campaignRepo;
    @Autowired private TrapRepository trapRepo;
    @Autowired private HazardRepository hazardRepo;
    @Autowired private AdventureRepository adventureRepo;
    @Autowired private ChapterRepository chapterRepo;
    @Autowired private SceneRepository sceneRepo;
    @Autowired private AdventureService adventureService;
    @Autowired private EncounterService encounterService;
    @Autowired private SessionLifecycleService sessionLifecycleService;

    @Test
    void hostileMarkdownAndMechanicsAreInertOnDetailCockpitAndTracker() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setName("Hostile Threat Campaign");
        campaign.setDescription("XSS regression for threats");
        campaign = campaignRepo.save(campaign);

        Trap trap = new Trap();
        trap.setSourceKey("hostile-trap");
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setName("Hostile Spike");
        trap.setDescription(HOSTILE_MD);
        trap.setSeverity(ThreatSeverity.DANGEROUS);
        trap.setResetMode(ThreatResetMode.NONE);
        trap.setTriggerDescription(HOSTILE_PLAIN);
        trap.setAdditionalEffect(HOSTILE_PLAIN);
        trap.setDamageExpression("2d6");
        trap.setCountermeasureNotes(HOSTILE_PLAIN);
        trap = trapRepo.save(trap);

        Hazard hazard = new Hazard();
        hazard.setSourceKey("hostile-hazard");
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(campaign);
        hazard.setName("Hostile Gas");
        hazard.setDescription(HOSTILE_MD);
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        hazard.setExposureText(HOSTILE_PLAIN);
        hazard.setEscalationText(HOSTILE_PLAIN);
        hazard = hazardRepo.save(hazard);

        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Hostile Module");
        adventure = adventureRepo.save(adventure);
        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch 1");
        chapter = chapterRepo.save(chapter);
        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Trap Hall");
        scene.setBody("Hallway");
        scene.setSortOrder(0);
        scene = sceneRepo.save(scene);

        SceneSection section = new SceneSection();
        section.setScene(scene);
        section.setKind(SceneSectionKind.TRAP);
        section.setLabel("Hostile Spike");
        section.setBody(HOSTILE_PLAIN);
        section.setSortOrder(0);
        section.setThreatKind(ThreatKind.TRAP);
        section.setThreatId(trap.getId());
        scene.getSections().add(section);
        sceneRepo.save(scene);
        adventureService.setCurrentScene(campaign.getId(), scene.getId());

        UUID encounterId = encounterService.create(campaign.getId(),
                new CreateRequest("Hostile Encounter", null)).id();
        encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, trap.getId(), null, 20, null));
        encounterService.activate(encounterId);

        sessionLifecycleService.start(campaign.getId(), null);

        var client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;

        String detail = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/library/traps/" + trap.getId())).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertInert(detail, "trap detail");
        assertThat(detail).contains("Hostile Spike");

        String hazardDetail = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/library/hazards/" + hazard.getId())).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertInert(hazardDetail, "hazard detail");

        String cockpit = client.send(
                HttpRequest.newBuilder().uri(URI.create(
                        base + "/campaigns/" + campaign.getId() + "/session")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertInert(cockpit, "session cockpit");
        assertThat(cockpit).contains("Hostile Spike");

        String tracker = client.send(
                HttpRequest.newBuilder().uri(URI.create(
                        base + "/api/v1/encounters/" + encounterId + "/combatants")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        // JSON may carry the original payload as string data; sanitized descriptionHtml must not
        // introduce live tags, and the tracker UI binds mechanics with x-text (see contract).
        assertThat(tracker).contains("Hostile Spike");
        assertThat(tracker).contains("threatCard");
        // descriptionHtml is Markdown-sanitized for card bodies.
        assertThat(tracker)
                .as("sanitized descriptionHtml must not embed live script elements")
                .doesNotContain("\"descriptionHtml\":\"<script>")
                .doesNotContain("descriptionHtml\":\"<p><script>");
        String trackerHtml = Files.readString(Path.of(
                "src/main/resources/templates/encounter/_tracker.html"));
        assertThat(trackerHtml)
                .as("tracker must bind threat mechanics with x-text, not x-html")
                .contains("x-text=\"activeThreatMechanics.triggerDescription\"")
                .doesNotContain("x-html=\"activeThreatMechanics");
    }

    private void assertInert(String html, String surface) {
        // Live executable forms are forbidden. Escaped plain-text echoes of the payload are OK.
        assertThat(html)
                .as(surface + " must not emit live script tags")
                .doesNotContain("<script>window.threatXss")
                .doesNotContain("<script>window.threatMechXss")
                .doesNotContain("<script>window.threatXss=true</script>");
        assertThat(html)
                .as(surface + " must not emit live onerror attributes on tags")
                .doesNotContainPattern("(?i)<[^>]+\\sonerror\\s*=");
        assertThat(html)
                .as(surface + " must not emit live javascript: links")
                .doesNotContain("href=\"javascript:")
                .doesNotContain("href='javascript:")
                .doesNotContainPattern("(?i)<a[^>]+href\\s*=\\s*[\"']?javascript:");
        // Description Markdown must be sanitized rather than raw HTML.
        assertThat(html)
                .as(surface + " must escape or strip the hostile img tag")
                .doesNotContain("<img src=x onerror=");
    }
}
