package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SessionTestFixtures {

    public record CampaignWithEncounters(
            UUID campaignId,
            UUID currentMapId,
            UUID currentSceneId,
            UUID adventureId,
            List<UUID> encounterIds,
            List<UUID> chapterIds
    ) {}

    private final CampaignRepository campaigns;
    private final AdventureRepository adventures;
    private final ChapterRepository chapters;
    private final SceneRepository scenes;
    private final GameMapRepository maps;
    private final EncounterRepository encounters;
    private final CombatantRepository combatants;

    public SessionTestFixtures(CampaignRepository campaigns,
                               AdventureRepository adventures,
                               ChapterRepository chapters,
                               SceneRepository scenes,
                               GameMapRepository maps,
                               EncounterRepository encounters,
                               CombatantRepository combatants) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.chapters = chapters;
        this.scenes = scenes;
        this.maps = maps;
        this.encounters = encounters;
        this.combatants = combatants;
    }

    @Transactional
    public CampaignWithEncounters campaignWithEncountersAcrossFourChapters() {
        Campaign campaign = new Campaign();
        campaign.setName("Encounter Sorting Test");
        campaign = campaigns.save(campaign);
        UUID campaignId = campaign.getId();

        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Test Adventure");
        adventure.setSortOrder(0);
        adventure = adventures.save(adventure);
        UUID adventureId = adventure.getId();

        GameMap currentMap = new GameMap();
        currentMap.setCampaign(campaign);
        currentMap.setName("Current Map");
        currentMap.setGridWidth(30);
        currentMap.setGridHeight(20);
        currentMap.setCellSizePx(48);
        currentMap = maps.save(currentMap);
        UUID currentMapId = currentMap.getId();

        GameMap otherMap = new GameMap();
        otherMap.setCampaign(campaign);
        otherMap.setName("Other Map");
        otherMap.setGridWidth(20);
        otherMap.setGridHeight(15);
        otherMap.setCellSizePx(48);
        otherMap = maps.save(otherMap);

        List<UUID> chapterIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Chapter ch = new Chapter();
            ch.setAdventure(adventure);
            ch.setTitle("Chapter " + (i + 1));
            ch.setSortOrder(i);
            ch = chapters.save(ch);
            chapterIds.add(ch.getId());
        }

        Scene currentScene = new Scene();
        currentScene.setChapter(chapters.findById(chapterIds.get(0)).orElseThrow());
        currentScene.setTitle("Current Scene");
        currentScene.setMap(currentMap);
        currentScene.setSortOrder(0);
        currentScene = scenes.save(currentScene);
        UUID currentSceneId = currentScene.getId();

        campaign.setCurrentSceneId(currentSceneId);
        campaigns.save(campaign);

        for (int i = 0; i < 3; i++) {
            Scene chapterOneScene = new Scene();
            chapterOneScene.setChapter(chapters.findById(chapterIds.get(0)).orElseThrow());
            chapterOneScene.setTitle("Chapter 1 Scene " + (i + 1));
            chapterOneScene.setMap(i == 0 ? currentMap : otherMap);
            chapterOneScene.setSortOrder(i + 1);
            chapterOneScene = scenes.save(chapterOneScene);

            Encounter e = new Encounter();
            e.setCampaign(campaign);
            e.setName("Ch1 Enc " + (i + 1));
            e.setStatus(Encounter.Status.PLANNED);
            e.setMap(i == 0 ? currentMap : otherMap);
            e = encounters.save(e);

            chapterOneScene.setEncounter(e);
            scenes.save(chapterOneScene);
        }

        for (int ch = 1; ch < 4; ch++) {
            for (int i = 0; i < 2; i++) {
                Scene s = new Scene();
                s.setChapter(chapters.findById(chapterIds.get(ch)).orElseThrow());
                s.setTitle("Chapter " + (ch + 1) + " Scene " + (i + 1));
                s.setMap(otherMap);
                s.setSortOrder(i);
                s = scenes.save(s);

                Encounter e = new Encounter();
                e.setCampaign(campaign);
                e.setName("Ch" + (ch + 1) + " Enc " + (i + 1));
                e.setStatus(Encounter.Status.PLANNED);
                e.setMap(otherMap);
                e = encounters.save(e);

                s.setEncounter(e);
                scenes.save(s);
            }
        }

        List<UUID> encounterIds = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(enc -> enc.getStatus() == Encounter.Status.PLANNED)
                .map(Encounter::getId)
                .toList();

        return new CampaignWithEncounters(campaignId, currentMapId, currentSceneId,
                adventureId, encounterIds, chapterIds);
    }
}
