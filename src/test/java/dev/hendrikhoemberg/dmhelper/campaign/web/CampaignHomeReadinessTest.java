package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignHomeReadinessTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignService campaignService;
    @Autowired AdventureService adventureService;
    @Autowired SceneParticipantRepository participantRepo;
    @Autowired EntityManager em;

    @Test
    void detailPageShowsActionableReadinessStatus() throws Exception {
        Campaign campaign = campaignService.create("Test Campaign", null);
        var adventure = adventureService.createAdventure(campaign.getId(), "Test Adventure", null, null);
        var chapter = adventureService.createChapter(adventure.getId(), "Chapter 1", null);
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

        mvc.perform(get("/campaigns/{id}", campaign.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("readiness"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Not ready — 3 blockers")));
    }
}
