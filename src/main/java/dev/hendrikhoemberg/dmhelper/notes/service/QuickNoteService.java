package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public QuickNoteService(QuickNoteRepository quickNoteRepository,
                            CampaignRepository campaignRepository,
                            NoteService noteService,
                            NoteRepository noteRepository,
                            StatBlockRepository statBlockRepository,
                            GameMapRepository gameMapRepository,
                            HandoutRepository handoutRepository) {
        this.quickNoteRepository = quickNoteRepository;
        this.campaignRepository = campaignRepository;
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.statBlockRepository = statBlockRepository;
        this.gameMapRepository = gameMapRepository;
        this.handoutRepository = handoutRepository;
    }

    public QuickNote create(UUID campaignId, String targetType, UUID targetId, String body) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));

        QuickNote qn = new QuickNote();
        qn.setCampaign(campaign);
        qn.setTargetType(targetType);
        qn.setTargetId(targetId);
        qn.setBody(body);
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

    public Note promoteToNote(UUID quickNoteId) {
        QuickNote qn = findById(quickNoteId);
        String targetLink = resolveTargetLink(qn);
        Note note = noteService.create(qn.getCampaign().getId(),
            NoteType.GENERIC,
            qn.getBody().length() > 80 ? qn.getBody().substring(0, 77) + "..." : qn.getBody(),
            targetLink + qn.getBody(),
            "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public Note promoteToNote(UUID quickNoteId, String title, NoteType type) {
        QuickNote qn = findById(quickNoteId);
        String targetLink = resolveTargetLink(qn);
        Note note = noteService.create(qn.getCampaign().getId(), type, title,
            targetLink + qn.getBody(), "");
        quickNoteRepository.delete(qn);
        return note;
    }

    public void delete(UUID id) {
        QuickNote qn = findById(id);
        quickNoteRepository.delete(qn);
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
            default -> "";
        };
        return link;
    }
}
