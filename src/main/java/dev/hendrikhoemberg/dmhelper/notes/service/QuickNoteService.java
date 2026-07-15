package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class QuickNoteService {

    private final QuickNoteRepository quickNoteRepository;
    private final CampaignRepository campaignRepository;
    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final StatBlockRepository statBlockRepository;
    private final GameMapRepository gameMapRepository;
    private final HandoutRepository handoutRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;

    public QuickNoteService(QuickNoteRepository quickNoteRepository,
                            CampaignRepository campaignRepository,
                            NoteService noteService,
                            NoteRepository noteRepository,
                            StatBlockRepository statBlockRepository,
                            GameMapRepository gameMapRepository,
                            HandoutRepository handoutRepository,
                            PartyMemberRepository partyMemberRepository,
                            SceneRepository sceneRepository,
                            EncounterRepository encounterRepository) {
        this.quickNoteRepository = quickNoteRepository;
        this.campaignRepository = campaignRepository;
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.statBlockRepository = statBlockRepository;
        this.gameMapRepository = gameMapRepository;
        this.handoutRepository = handoutRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.sceneRepository = sceneRepository;
        this.encounterRepository = encounterRepository;
    }

    private enum TargetType {
        CAMPAIGN, PARTY_MEMBER, MAP, ENCOUNTER, NOTE, HANDOUT, STATBLOCK, SCENE
    }

    private TargetType parseTargetType(String raw) {
        try {
            return TargetType.valueOf(raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported quick-note target type: " + raw);
        }
    }

    private QuickNote findByCampaignAndId(UUID campaignId, UUID id) {
        return quickNoteRepository.findByIdAndCampaignId(id, campaignId)
                .orElseThrow(() -> new NotFoundException("QuickNote not found"));
    }

    public QuickNote create(UUID campaignId, String targetType, UUID targetId, String body) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Quick-note body is required");
        }

        QuickNote qn = new QuickNote();
        qn.setCampaign(campaign);
        qn.setTargetType(parseTargetType(targetType).name());
        qn.setTargetId(targetId);
        qn.setBody(body.strip());
        return quickNoteRepository.save(qn);
    }

    @Transactional(readOnly = true)
    public List<QuickNote> findByTarget(UUID campaignId, String targetType, UUID targetId) {
        return quickNoteRepository.findByCampaignIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
                campaignId, targetType, targetId);
    }

    @Transactional(readOnly = true)
    public QuickNote findById(UUID id) {
        return quickNoteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QuickNote not found"));
    }

    public Note promoteToNote(UUID campaignId, UUID quickNoteId) {
        QuickNote qn = findByCampaignAndId(campaignId, quickNoteId);
        String targetLink = resolveTargetLink(qn);
        String title = qn.getBody().length() > 80
                ? qn.getBody().substring(0, 77) + "..."
                : qn.getBody();
        Note note = noteService.create(qn.getCampaign().getId(), NoteType.GENERIC, title, targetLink + qn.getBody(), "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public Note promoteToNote(UUID campaignId, UUID quickNoteId, String title, NoteType type) {
        QuickNote qn = findByCampaignAndId(campaignId, quickNoteId);
        Note note = noteService.create(qn.getCampaign().getId(), type, title, resolveTargetLink(qn) + qn.getBody(), "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public void delete(UUID campaignId, UUID id) {
        quickNoteRepository.delete(findByCampaignAndId(campaignId, id));
    }

    private String resolveTargetLink(QuickNote qn) {
        UUID targetId = qn.getTargetId();
        String link = switch (qn.getTargetType()) {
            case "STATBLOCK" -> {
                var sb = statBlockRepository.findById(targetId);
                yield sb.map(s -> "[[statblock:" + s.getName() + "]]\n\n").orElse("");
            }
            case "MAP" -> {
                var map = gameMapRepository.findById(targetId);
                yield map.map(m -> "[[map:" + m.getName() + "]]\n\n").orElse("");
            }
            case "HANDOUT" -> {
                var handout = handoutRepository.findById(targetId);
                yield handout.map(h -> "[[handout:" + h.getTitle() + "]]\n\n").orElse("");
            }
            case "NOTE" -> {
                var note = noteRepository.findById(targetId);
                yield note.map(n -> "[[" + n.getTitle() + "]]\n\n").orElse("");
            }
            case "PARTY_MEMBER" -> {
                var pm = partyMemberRepository.findById(targetId);
                yield pm.map(p -> "[[partymember:" + p.getCharacterName() + "]]\n\n").orElse("");
            }
            case "CAMPAIGN" -> {
                var camp = campaignRepository.findById(targetId);
                yield camp.map(c -> "[[campaign:" + c.getName() + "]]\n\n").orElse("");
            }
            case "SCENE" -> {
                var scene = sceneRepository.findById(targetId);
                yield scene.map(s -> "[[scene:" + s.getTitle() + "]]\n\n").orElse("");
            }
            case "ENCOUNTER" -> encounterRepository.findById(targetId)
                    .map(encounter -> "[[encounter:" + encounter.getName() + "]]\n\n")
                    .orElse("");
            default -> "";
        };
        return link;
    }
}
