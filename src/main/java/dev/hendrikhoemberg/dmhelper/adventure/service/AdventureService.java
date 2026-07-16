package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CampaignRepository campaignRepository;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final StatBlockRepository statBlockRepository;
    private final HandoutRepository handoutRepository;
    private final SessionActivityRecorder sessionActivity;
    private final SessionReferenceCleaner sessionRefCleaner;

    public AdventureService(AdventureRepository adventureRepository,
                            ChapterRepository chapterRepository,
                            SceneRepository sceneRepository,
                            CampaignRepository campaignRepository,
                            GameMapRepository gameMapRepository,
                            EncounterRepository encounterRepository,
                            StatBlockRepository statBlockRepository,
                            HandoutRepository handoutRepository,
                            SessionActivityRecorder sessionActivity,
                            SessionReferenceCleaner sessionRefCleaner) {
        this.adventureRepository = adventureRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.campaignRepository = campaignRepository;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.statBlockRepository = statBlockRepository;
        this.handoutRepository = handoutRepository;
        this.sessionActivity = sessionActivity;
        this.sessionRefCleaner = sessionRefCleaner;
    }

    // ---- Adventures ----

    public Adventure createAdventure(UUID campaignId, String name, String description, String sourceAttribution) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Adventure a = new Adventure();
        a.setCampaign(campaign);
        a.setName(name);
        a.setDescription(description);
        a.setSourceAttribution(sourceAttribution);
        a.setSortOrder(adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId).size());
        return adventureRepository.save(a);
    }

    @Transactional(readOnly = true)
    public Adventure findAdventureById(UUID id) {
        return adventureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Adventure not found"));
    }

    @Transactional(readOnly = true)
    public List<Adventure> findAdventuresByCampaign(UUID campaignId) {
        return adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId);
    }

    public Adventure updateAdventure(UUID id, String name, String description, String sourceAttribution) {
        Adventure a = findAdventureById(id);
        a.setName(name);
        a.setDescription(description);
        a.setSourceAttribution(sourceAttribution);
        return adventureRepository.save(a);
    }

    public void deleteAdventure(UUID id) {
        Adventure a = findAdventureById(id);
        for (Chapter ch : chapterRepository.findByAdventureIdOrderBySortOrderAsc(id)) {
            deleteChapterInternal(ch);
        }
        UUID campaignId = a.getCampaign().getId();
        adventureRepository.delete(a);
        renumberAdventures(campaignId);
    }

    public void moveAdventure(UUID id, int direction) {
        Adventure a = findAdventureById(id);
        var siblings = adventureRepository.findByCampaignIdOrderBySortOrderAsc(a.getCampaign().getId());
        int idx = indexOfId(siblings.stream().map(Adventure::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Adventure other = siblings.get(target);
        int tmp = a.getSortOrder();
        a.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        adventureRepository.save(a);
        adventureRepository.save(other);
    }

    // ---- Chapters ----

    public Chapter createChapter(UUID adventureId, String title, String intro) {
        Adventure a = findAdventureById(adventureId);
        Chapter ch = new Chapter();
        ch.setAdventure(a);
        ch.setTitle(title);
        ch.setIntro(intro);
        ch.setSortOrder(chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId).size());
        return chapterRepository.save(ch);
    }

    @Transactional(readOnly = true)
    public Chapter findChapterById(UUID id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chapter not found"));
    }

    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByAdventure(UUID adventureId) {
        return chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
    }

    public Chapter updateChapter(UUID id, String title, String intro) {
        Chapter ch = findChapterById(id);
        ch.setTitle(title);
        ch.setIntro(intro);
        return chapterRepository.save(ch);
    }

    public void deleteChapter(UUID id) {
        Chapter ch = findChapterById(id);
        UUID adventureId = ch.getAdventure().getId();
        deleteChapterInternal(ch);
        renumberChapters(adventureId);
    }

    public void moveChapter(UUID id, int direction) {
        Chapter ch = findChapterById(id);
        var siblings = chapterRepository.findByAdventureIdOrderBySortOrderAsc(ch.getAdventure().getId());
        int idx = indexOfId(siblings.stream().map(Chapter::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Chapter other = siblings.get(target);
        int tmp = ch.getSortOrder();
        ch.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        chapterRepository.save(ch);
        chapterRepository.save(other);
    }

    // ---- Scenes ----

    public Scene createScene(UUID chapterId, String title, String sceneKey, String body) {
        Chapter ch = findChapterById(chapterId);
        Scene s = new Scene();
        s.setChapter(ch);
        s.setTitle(title);
        s.setSceneKey(sceneKey);
        s.setBody(body);
        s.setSortOrder(sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId).size());
        return sceneRepository.save(s);
    }

    @Transactional(readOnly = true)
    public Scene findSceneById(UUID id) {
        return sceneRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scene not found"));
    }

    public Scene updateScene(UUID id, String title, String sceneKey, String body) {
        Scene s = findSceneById(id);
        s.setTitle(title);
        s.setSceneKey(sceneKey);
        s.setBody(body);
        return sceneRepository.save(s);
    }

    public void deleteScene(UUID id) {
        sessionRefCleaner.detachScene(id);
        Scene s = findSceneById(id);
        UUID chapterId = s.getChapter().getId();
        clearCursorIfCurrent(s);
        sceneRepository.delete(s);
        renumberScenes(chapterId);
    }

    public void moveScene(UUID id, int direction) {
        Scene s = findSceneById(id);
        var siblings = sceneRepository.findByChapterIdOrderBySortOrderAsc(s.getChapter().getId());
        int idx = indexOfId(siblings.stream().map(Scene::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Scene other = siblings.get(target);
        int tmp = s.getSortOrder();
        s.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        sceneRepository.save(s);
        sceneRepository.save(other);
    }

    public Scene moveSceneToChapter(UUID sceneId, UUID chapterId) {
        Scene s = findSceneById(sceneId);
        Chapter target = findChapterById(chapterId);
        UUID oldChapterId = s.getChapter().getId();
        if (oldChapterId.equals(chapterId)) return s;
        s.setChapter(target);
        s.setSortOrder(sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId).size());
        Scene saved = sceneRepository.save(s);
        renumberScenes(oldChapterId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Scene> flattenedScenes(UUID adventureId) {
        List<Scene> flat = new ArrayList<>();
        for (Chapter ch : chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId)) {
            flat.addAll(sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId()));
        }
        return flat;
    }

    // ---- Run mode: status & cursor ----

    public Scene setStatus(UUID sceneId, SceneStatus status) {
        Scene s = findSceneById(sceneId);
        s.setStatus(status);
        Scene saved = sceneRepository.save(s);
        if (status == SceneStatus.DONE) sessionActivity.sceneCompleted(saved);
        return saved;
    }

    public Scene setCurrentScene(UUID campaignId, UUID sceneId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Scene s = findSceneById(sceneId);
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);
        if (s.getStatus() == SceneStatus.UNVISITED) {
            s.setStatus(SceneStatus.VISITED);
            s = sceneRepository.save(s);
        }
        sessionActivity.sceneSelected(campaignId, s);
        return s;
    }

    public void clearCurrentScene(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        campaign.setCurrentSceneId(null);
        campaignRepository.save(campaign);
    }

    public Optional<Scene> getCurrentScene(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (campaign.getCurrentSceneId() == null) return Optional.empty();
        var scene = sceneRepository.findById(campaign.getCurrentSceneId());
        if (scene.isEmpty()) {
            campaign.setCurrentSceneId(null);
            campaignRepository.save(campaign);
        }
        return scene;
    }

    public Optional<Scene> stepCurrentScene(UUID campaignId, int direction) {
        var currentOpt = getCurrentScene(campaignId);
        if (currentOpt.isEmpty()) return Optional.empty();
        Scene current = currentOpt.get();
        List<Scene> flat = flattenedScenes(current.getChapter().getAdventure().getId());
        int idx = indexOfId(flat.stream().map(Scene::getId).toList(), current.getId());
        int target = idx + direction;
        if (target < 0 || target >= flat.size()) return Optional.of(current);
        return Optional.of(setCurrentScene(campaignId, flat.get(target).getId()));
    }

    // ---- Scene links ----

    public Scene linkMap(UUID sceneId, UUID mapId, Integer pinX, Integer pinY) {
        Scene s = findSceneById(sceneId);
        var map = gameMapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found"));
        s.setMap(map);
        s.setPinX(pinX);
        s.setPinY(pinY);
        return sceneRepository.save(s);
    }

    public Scene unlinkMap(UUID sceneId) {
        Scene s = findSceneById(sceneId);
        s.setMap(null);
        s.setPinX(null);
        s.setPinY(null);
        return sceneRepository.save(s);
    }

    public Scene linkEncounter(UUID sceneId, UUID encounterId) {
        Scene s = findSceneById(sceneId);
        var enc = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found"));
        s.setEncounter(enc);
        return sceneRepository.save(s);
    }

    public Scene unlinkEncounter(UUID sceneId) {
        Scene s = findSceneById(sceneId);
        s.setEncounter(null);
        return sceneRepository.save(s);
    }

    public Scene addStatBlock(UUID sceneId, UUID statBlockId) {
        Scene s = findSceneById(sceneId);
        var sb = statBlockRepository.findById(statBlockId)
                .orElseThrow(() -> new NotFoundException("StatBlock not found"));
        if (s.getStatBlocks().stream().noneMatch(x -> x.getId().equals(statBlockId))) {
            s.getStatBlocks().add(sb);
        }
        return sceneRepository.save(s);
    }

    public Scene removeStatBlock(UUID sceneId, UUID statBlockId) {
        Scene s = findSceneById(sceneId);
        s.getStatBlocks().removeIf(x -> x.getId().equals(statBlockId));
        return sceneRepository.save(s);
    }

    public Scene addHandout(UUID sceneId, UUID handoutId) {
        Scene s = findSceneById(sceneId);
        var h = handoutRepository.findById(handoutId)
                .orElseThrow(() -> new NotFoundException("Handout not found"));
        if (s.getHandouts().stream().noneMatch(x -> x.getId().equals(handoutId))) {
            s.getHandouts().add(h);
        }
        return sceneRepository.save(s);
    }

    public Scene removeHandout(UUID sceneId, UUID handoutId) {
        Scene s = findSceneById(sceneId);
        s.getHandouts().removeIf(x -> x.getId().equals(handoutId));
        return sceneRepository.save(s);
    }

    // ---- internals ----

    private void deleteChapterInternal(Chapter ch) {
        for (Scene s : sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId())) {
            sessionRefCleaner.detachScene(s.getId());
            clearCursorIfCurrent(s);
            sceneRepository.delete(s);
        }
        chapterRepository.delete(ch);
    }

    private void clearCursorIfCurrent(Scene scene) {
        Campaign campaign = scene.getChapter().getAdventure().getCampaign();
        if (scene.getId().equals(campaign.getCurrentSceneId())) {
            campaign.setCurrentSceneId(null);
            campaignRepository.save(campaign);
        }
    }

    private void renumberAdventures(UUID campaignId) {
        var list = adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        adventureRepository.saveAll(list);
    }

    private void renumberChapters(UUID adventureId) {
        var list = chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        chapterRepository.saveAll(list);
    }

    private void renumberScenes(UUID chapterId) {
        var list = sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        sceneRepository.saveAll(list);
    }

    private int indexOfId(List<UUID> ids, UUID id) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(id)) return i;
        }
        return -1;
    }
}
