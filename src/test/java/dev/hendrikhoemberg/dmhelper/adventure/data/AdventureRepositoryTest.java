package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdventureRepositoryTest {

    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
    }

    private Adventure adventure(String name, int sortOrder) {
        Adventure a = new Adventure();
        a.setCampaign(campaign);
        a.setName(name);
        a.setSortOrder(sortOrder);
        return adventureRepository.save(a);
    }

    private Chapter chapter(Adventure a, String title, int sortOrder) {
        Chapter c = new Chapter();
        c.setAdventure(a);
        c.setTitle(title);
        c.setSortOrder(sortOrder);
        return chapterRepository.save(c);
    }

    private Scene scene(Chapter c, String title, int sortOrder) {
        Scene s = new Scene();
        s.setChapter(c);
        s.setTitle(title);
        s.setSortOrder(sortOrder);
        return sceneRepository.save(s);
    }

    @Test
    void persistsGraphAndOrdersBySortOrder() {
        Adventure a2 = adventure("Module Two", 1);
        Adventure a1 = adventure("Module One", 0);
        Chapter ch2 = chapter(a1, "Chapter 2", 1);
        Chapter ch1 = chapter(a1, "Chapter 1", 0);
        Scene s2 = scene(ch1, "The Shrine", 1);
        Scene s1 = scene(ch1, "The Gate", 0);
        s1.setSceneKey("1");
        sceneRepository.save(s1);

        var adventures = adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaign.getId());
        assertThat(adventures).extracting(Adventure::getName)
                .containsExactly("Module One", "Module Two");

        var chapters = chapterRepository.findByAdventureIdOrderBySortOrderAsc(a1.getId());
        assertThat(chapters).extracting(Chapter::getTitle)
                .containsExactly("Chapter 1", "Chapter 2");

        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch1.getId());
        assertThat(scenes).extracting(Scene::getTitle)
                .containsExactly("The Gate", "The Shrine");
        assertThat(scenes.get(0).getSceneKey()).isEqualTo("1");
        assertThat(scenes.get(0).getStatus()).isEqualTo(SceneStatus.UNVISITED);
    }

    @Test
    void findsScenesAcrossCampaignAndByTitle() {
        Adventure a = adventure("Module", 0);
        Chapter ch = chapter(a, "Ch", 0);
        scene(ch, "Throne Room", 0);

        assertThat(sceneRepository.findByChapterAdventureCampaignId(campaign.getId())).hasSize(1);
        assertThat(sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(
                campaign.getId(), "throne room")).hasSize(1);
    }
}
