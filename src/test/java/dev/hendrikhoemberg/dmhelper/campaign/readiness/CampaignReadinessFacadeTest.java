package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CampaignReadinessFacadeTest {

    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignService campaignService;
    @Autowired AdventureService adventureService;
    @Autowired SceneParticipantRepository participantRepo;
    @Autowired EntityManager em;

    @Test
    void hostileSceneWithoutEncounterOrStatblockIsNotSessionReady() {
        Campaign campaign = campaignService.create("Test Campaign", null);
        UUID cid = campaign.getId();

        Adventure adventure = adventureService.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "Chapter 1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "Klarg's Ambush", null, null);

        SceneParticipant participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName("Klarg");
        participant.setQuantity(1);
        participant.setDisposition(SceneParticipantDisposition.HOSTILE);
        participant.setSortOrder(0);
        scene.getParticipants().add(participant);
        participantRepo.save(participant);

        em.flush();

        CampaignReadinessReport report = facade.reportForCampaign(cid);
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.blockerCount()).isGreaterThanOrEqualTo(1);
    }
}
