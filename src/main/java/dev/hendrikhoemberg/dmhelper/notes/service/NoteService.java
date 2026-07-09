package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteLinkRepository noteLinkRepository;
    private final CampaignRepository campaignRepository;
    private final StatBlockService statBlockService;
    private final GameMapRepository gameMapRepository;
    private final HandoutRepository handoutRepository;
    private final EncounterRepository encounterRepository;
    private final WikiLinkParser wikiLinkParser;
    private final SceneRepository sceneRepository;

    public NoteService(NoteRepository noteRepository,
                       NoteLinkRepository noteLinkRepository,
                       CampaignRepository campaignRepository,
                       StatBlockService statBlockService,
                       GameMapRepository gameMapRepository,
                       HandoutRepository handoutRepository,
                       EncounterRepository encounterRepository,
                       WikiLinkParser wikiLinkParser,
                       SceneRepository sceneRepository) {
        this.noteRepository = noteRepository;
        this.noteLinkRepository = noteLinkRepository;
        this.campaignRepository = campaignRepository;
        this.statBlockService = statBlockService;
        this.gameMapRepository = gameMapRepository;
        this.handoutRepository = handoutRepository;
        this.encounterRepository = encounterRepository;
        this.wikiLinkParser = wikiLinkParser;
        this.sceneRepository = sceneRepository;
    }

    public Note create(UUID campaignId, NoteType type, String title, String body, String tags, boolean dmOnly) {
        Note note = create(campaignId, type, title, body, tags);
        note.setDmOnly(dmOnly);
        return noteRepository.save(note);
    }

    public Note create(UUID campaignId, NoteType type, String title, String body, String tags) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        Note note = new Note();
        note.setCampaign(campaign);
        note.setType(type);
        note.setTitle(title);
        note.setBody(body);
        note.setTags(tags);
        note = noteRepository.save(note);

        rebuildLinks(note);
        return note;
    }

    @Transactional(readOnly = true)
    public Note findById(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note not found"));
    }

    @Transactional(readOnly = true)
    public List<Note> findByCampaignId(UUID campaignId) {
        return noteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<Note> findByCampaignIdAndType(UUID campaignId, NoteType type) {
        return noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, type);
    }

    @Transactional(readOnly = true)
    public List<Note> search(UUID campaignId, String search) {
        if (search == null || search.isBlank()) {
            return findByCampaignId(campaignId);
        }
        return noteRepository.searchByCampaignId(campaignId, search);
    }

    public Note update(UUID id, NoteType type, String title, String body, String tags) {
        Note note = findById(id);
        note.setType(type);
        note.setTitle(title);
        note.setBody(body);
        note.setTags(tags);
        note = noteRepository.save(note);

        rebuildLinks(note);
        return note;
    }

    public void delete(UUID id) {
        Note note = findById(id);
        noteLinkRepository.deleteBySourceNoteId(id);
        noteRepository.delete(note);
    }

    @Transactional(readOnly = true)
    public List<Note> findBacklinks(UUID noteId) {
        List<NoteLink> incomingLinks = noteLinkRepository.findByTargetTypeAndTargetId("NOTE", noteId);
        return incomingLinks.stream()
                .map(NoteLink::getSourceNote)
                .distinct()
                .toList();
    }

    public String renderBody(Note note) {
        String body = note.getBody();
        if (body == null || body.isBlank()) return "";

        var targets = wikiLinkParser.extractReferences(body);
        var refs = new ArrayList<WikiLinkParser.WikiLinkReference>();

        for (var target : targets) {
            String url = null;
            boolean resolved = false;

            switch (target.targetType()) {
                case "NOTE" -> {
                    var notes = noteRepository.findByCampaignIdAndTitle(
                            note.getCampaign().getId(), target.title());
                    if (!notes.isEmpty()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/notes/" + notes.get(0).getId();
                        resolved = true;
                    }
                }
                case "STATBLOCK" -> {
                    var hits = statBlockService.search(null, null, null, target.title());
                    if (!hits.isEmpty()) {
                        url = "/library/statblocks/" + hits.get(0).getId();
                        resolved = true;
                    }
                }
                case "HANDOUT" -> {
                    var handouts = handoutRepository.findByCampaignIdOrderByTitleAsc(
                            note.getCampaign().getId());
                    var match = handouts.stream()
                            .filter(h -> h.getTitle().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/handouts";
                        resolved = true;
                    }
                }
                case "MAP" -> {
                    var maps = gameMapRepository.findByCampaignIdOrderBySortOrderAsc(
                            note.getCampaign().getId());
                    var match = maps.stream()
                            .filter(m -> m.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/maps/" + match.get().getId() + "/battle";
                        resolved = true;
                    }
                }
                case "ENCOUNTER" -> {
                    var encounters = encounterRepository.findByCampaignIdOrderByNameAsc(
                            note.getCampaign().getId());
                    var match = encounters.stream()
                            .filter(enc -> enc.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        url = "/campaigns/" + note.getCampaign().getId() + "/encounters/" + match.get().getId();
                        resolved = true;
                    }
                }
                case "SCENE" -> {
                    var scenes = sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(
                            note.getCampaign().getId(), target.title());
                    if (!scenes.isEmpty()) {
                        var s = scenes.getFirst();
                        url = "/campaigns/" + note.getCampaign().getId() + "/adventures/"
                                + s.getChapter().getAdventure().getId() + "/scenes/" + s.getId();
                        resolved = true;
                    }
                }
            }

            refs.add(new WikiLinkParser.WikiLinkReference(
                    target.targetType(),
                    target.title(),
                    url, resolved));
        }

        if (refs.isEmpty()) return body;
        return wikiLinkParser.replaceLinks(body, refs);
    }

    public void rebuildLinks(Note note) {
        noteLinkRepository.deleteBySourceNoteId(note.getId());

        var targets = wikiLinkParser.extractReferences(note.getBody());
        for (var target : targets) {
            NoteLink link = new NoteLink();
            link.setSourceNote(note);
            link.setTargetType(target.targetType());
            link.setDisplayText(target.title());

            boolean found = switch (target.targetType()) {
                case "NOTE" -> {
                    var notes = noteRepository.findByCampaignIdAndTitle(
                            note.getCampaign().getId(), target.title());
                    if (!notes.isEmpty()) {
                        link.setTargetId(notes.get(0).getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "STATBLOCK" -> {
                    var hits = statBlockService.search(null, null, null, target.title());
                    if (!hits.isEmpty()) {
                        link.setTargetId(hits.get(0).getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "HANDOUT" -> {
                    var handouts = handoutRepository.findByCampaignIdOrderByTitleAsc(
                            note.getCampaign().getId());
                    var match = handouts.stream()
                            .filter(h -> h.getTitle().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        link.setTargetId(match.get().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "MAP" -> {
                    var maps = gameMapRepository.findByCampaignIdOrderBySortOrderAsc(
                            note.getCampaign().getId());
                    var match = maps.stream()
                            .filter(m -> m.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        link.setTargetId(match.get().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "ENCOUNTER" -> {
                    var encounters = encounterRepository.findByCampaignIdOrderByNameAsc(
                            note.getCampaign().getId());
                    var match = encounters.stream()
                            .filter(enc -> enc.getName().equalsIgnoreCase(target.title()))
                            .findFirst();
                    if (match.isPresent()) {
                        link.setTargetId(match.get().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                case "SCENE" -> {
                    var scenes = sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(
                            note.getCampaign().getId(), target.title());
                    if (!scenes.isEmpty()) {
                        link.setTargetId(scenes.getFirst().getId());
                        yield true;
                    }
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
                default -> {
                    link.setTargetId(UUID.randomUUID());
                    yield false;
                }
            };
            link.setResolved(found);
            noteLinkRepository.save(link);
        }
    }
}
