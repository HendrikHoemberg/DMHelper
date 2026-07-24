package dev.hendrikhoemberg.dmhelper.campaign.readiness.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.*;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReadinessControllerTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignService campaignService;
    @Autowired AdventureService adventureService;
    @Autowired SceneParticipantRepository participantRepo;
    @Autowired HandoutRepository handouts;
    @Autowired ReadinessAcknowledgementRepository acks;
    @Autowired CampaignReadinessFacade facade;
    @Autowired EntityManager em;

    @Test
    void acceptingAnItemClearsBlocker() throws Exception {
        Campaign campaign = campaignService.create("Test Campaign", null);
        UUID cid = campaign.getId();

        Adventure adventure = adventureService.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "Chapter 1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "Ambush Encounter", null, null);

        SceneParticipant participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName("Ambush Brute");
        participant.setQuantity(1);
        participant.setDisposition(SceneParticipantDisposition.HOSTILE);
        participant.setSortOrder(0);
        scene.getParticipants().add(participant);
        participantRepo.save(participant);

        em.flush();

        CampaignReadinessReport report = facade.reportForCampaign(cid);
        assertThat(report.sessionReady()).isFalse();

        // Accept all blockers
        for (ReadinessItem blocker : report.byState(ReadinessState.BLOCKER)) {
            mvc.perform(post("/campaigns/{cid}/readiness/accept", cid)
                            .param("itemKey", blocker.key()))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString();
                        assertThat(body).contains("readiness-panel");
                    });
        }

        report = facade.reportForCampaign(cid);
        assertThat(report.sessionReady()).isTrue();
    }

    @Test
    void classifyAssetUpdatesKind() throws Exception {
        Campaign campaign = campaignService.create("Test Campaign", null);
        UUID cid = campaign.getId();

        Handout handout = new Handout();
        handout.setCampaign(campaign);
        handout.setTitle("Test Handout");
        handout.setFileName("test.pdf");
        handout.setAssetKind(Handout.AssetKind.SOURCE_PAGE);
        handouts.save(handout);

        em.flush();

        mvc.perform(post("/campaigns/{cid}/readiness/assets/{hid}/kind", cid, handout.getId())
                        .param("kind", "REGIONAL_MAP"))
                .andExpect(status().isOk());

        em.clear();
        Handout reloaded = handouts.findById(handout.getId()).orElseThrow();
        assertThat(reloaded.getAssetKind()).isEqualTo(Handout.AssetKind.REGIONAL_MAP);
    }

    @Test
    void unacceptRestoresBlocker() throws Exception {
        Campaign campaign = campaignService.create("Test Campaign", null);
        UUID cid = campaign.getId();

        Adventure adventure = adventureService.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "Chapter 1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "Ambush Encounter", null, null);

        SceneParticipant participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName("Ambush Brute");
        participant.setQuantity(1);
        participant.setDisposition(SceneParticipantDisposition.HOSTILE);
        participant.setSortOrder(0);
        scene.getParticipants().add(participant);
        participantRepo.save(participant);

        em.flush();

        // Accept all blockers first
        for (ReadinessItem blocker : facade.reportForCampaign(cid).byState(ReadinessState.BLOCKER)) {
            mvc.perform(post("/campaigns/{cid}/readiness/accept", cid)
                            .param("itemKey", blocker.key()))
                    .andExpect(status().isOk());
        }
        assertThat(facade.reportForCampaign(cid).sessionReady()).isTrue();

        // Unaccept the first blocker, verify session no longer ready
        ReadinessItem first = facade.reportForCampaign(cid).items().stream()
                .filter(i -> i.state() == ReadinessState.ACCEPTED)
                .findFirst().orElseThrow();

        mvc.perform(delete("/campaigns/{cid}/readiness/accept", cid)
                        .param("itemKey", first.key()))
                .andExpect(status().isOk());

        assertThat(facade.reportForCampaign(cid).sessionReady()).isFalse();
    }

    @Test
    void acceptedItemsRenderWithAnUndoControl() throws Exception {
        Campaign campaign = campaignService.create("Test Campaign", null);
        UUID cid = campaign.getId();

        Adventure adventure = adventureService.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "Chapter 1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "Ambush Encounter", null, null);

        SceneParticipant participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName("Ambush Brute");
        participant.setQuantity(1);
        participant.setDisposition(SceneParticipantDisposition.HOSTILE);
        participant.setSortOrder(0);
        scene.getParticipants().add(participant);
        participantRepo.save(participant);

        em.flush();

        ReadinessItem blocker = facade.reportForCampaign(cid).byState(ReadinessState.BLOCKER)
                .stream().findFirst().orElseThrow();

        // The fragment returned after accepting must surface the accepted item and an Undo control,
        // so an explicit choice never becomes invisible.
        mvc.perform(post("/campaigns/{cid}/readiness/accept", cid)
                        .param("itemKey", blocker.key()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("Accepted as explicit choices");
                    assertThat(body).contains(blocker.title());
                    assertThat(body).contains("Undo");
                    assertThat(body).contains("/readiness/accept?itemKey=");
                });
    }
}
