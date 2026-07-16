package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry;
import dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry.CampaignType;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser.WikiLinkTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SessionPlanService {

    public record SessionPlan(UUID noteId, String title, String renderedBody, List<SessionPlanBeat> beats) {}
    public record SessionPlanBeat(int position, String type, UUID targetId, String label,
                                  String url, UUID mapId, boolean resolved) {}

    private final NoteRepository noteRepository;
    private final NoteService noteService;
    private final WikiLinkParser parser;
    private final MarkdownUtil markdown;
    private final ContentDestinationRegistry destinations;
    private final SceneRepository sceneRepository;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final HandoutRepository handoutRepository;

    public SessionPlanService(NoteRepository noteRepository,
                              NoteService noteService,
                              WikiLinkParser parser,
                              MarkdownUtil markdown,
                              ContentDestinationRegistry destinations,
                              SceneRepository sceneRepository,
                              GameMapRepository gameMapRepository,
                              EncounterRepository encounterRepository,
                              HandoutRepository handoutRepository) {
        this.noteRepository = noteRepository;
        this.noteService = noteService;
        this.parser = parser;
        this.markdown = markdown;
        this.destinations = destinations;
        this.sceneRepository = sceneRepository;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.handoutRepository = handoutRepository;
    }

    public Optional<SessionPlan> latest(UUID campaignId) {
        return noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN)
                .stream().findFirst().map(note -> {
                    List<SessionPlanBeat> beats = new ArrayList<>();
                    int position = 0;
                    for (WikiLinkTarget target : parser.extractReferences(note.getBody())) {
                        beats.add(resolve(campaignId, position++, target));
                    }
                    return new SessionPlan(note.getId(), note.getTitle(),
                            markdown.toHtml(noteService.renderBody(note)), List.copyOf(beats));
                });
    }

    private SessionPlanBeat resolve(UUID campaignId, int position, WikiLinkTarget target) {
        return switch (target.targetType()) {
            case "SCENE" -> sceneRepository
                    .findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, target.title())
                    .stream().findFirst()
                    .map(scene -> beat(position, "SCENE", scene.getId(), scene.getTitle(),
                            destinations.campaign(CampaignType.SCENE, campaignId, scene.getId(),
                                    scene.getChapter().getAdventure().getId()),
                            scene.getMap() == null ? null : scene.getMap().getId()))
                    .orElseGet(() -> broken(position, target));
            case "MAP" -> gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .filter(map -> map.getName().equalsIgnoreCase(target.title())).findFirst()
                    .map(map -> beat(position, "MAP", map.getId(), map.getName(),
                            destinations.campaign(CampaignType.MAP, campaignId, map.getId(), null), map.getId()))
                    .orElseGet(() -> broken(position, target));
            case "ENCOUNTER" -> encounterRepository.findByCampaignIdOrderByNameAsc(campaignId).stream()
                    .filter(encounter -> encounter.getName().equalsIgnoreCase(target.title())).findFirst()
                    .map(encounter -> beat(position, "ENCOUNTER", encounter.getId(), encounter.getName(),
                            destinations.campaign(CampaignType.ENCOUNTER, campaignId, encounter.getId(), null),
                            encounter.getMap() == null ? null : encounter.getMap().getId()))
                    .orElseGet(() -> broken(position, target));
            case "HANDOUT" -> handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId).stream()
                    .filter(handout -> handout.getTitle().equalsIgnoreCase(target.title())).findFirst()
                    .map(handout -> beat(position, "HANDOUT", handout.getId(), handout.getTitle(),
                            destinations.campaign(CampaignType.HANDOUT, campaignId, handout.getId(), null), null))
                    .orElseGet(() -> broken(position, target));
            default -> noteRepository.findByCampaignIdAndTitle(campaignId, target.title()).stream().findFirst()
                    .map(note -> beat(position, "NOTE", note.getId(), note.getTitle(),
                            destinations.campaign(CampaignType.NOTE, campaignId, note.getId(), null), null))
                    .orElseGet(() -> broken(position, target));
        };
    }

    private SessionPlanBeat beat(int position, String type, UUID targetId, String label, String url, UUID mapId) {
        return new SessionPlanBeat(position, type, targetId, label, url, mapId, true);
    }

    private SessionPlanBeat broken(int position, WikiLinkTarget target) {
        return new SessionPlanBeat(position, target.targetType(), null, target.title(), null, null, false);
    }
}
