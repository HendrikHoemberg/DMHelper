package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/quicknotes")
public class QuickNoteApiController {

    private final QuickNoteService quickNoteService;

    public QuickNoteApiController(QuickNoteService quickNoteService) {
        this.quickNoteService = quickNoteService;
    }

    @GetMapping
    public List<QuickNoteDto> list(@PathVariable UUID campaignId,
                                   @RequestParam String targetType,
                                   @RequestParam UUID targetId) {
        return quickNoteService.findByTarget(campaignId, targetType, targetId)
                .stream().map(QuickNoteDto::from).toList();
    }

    @PostMapping
    public QuickNoteDto create(@PathVariable UUID campaignId,
                               @RequestParam String targetType,
                               @RequestParam UUID targetId,
                               @RequestParam String body) {
        QuickNote qn = quickNoteService.create(campaignId, targetType, targetId, body);
        return QuickNoteDto.from(qn);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                       @PathVariable UUID id) {
        quickNoteService.delete(campaignId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    public PromoteResultDto promote(@PathVariable UUID campaignId,
                                    @PathVariable UUID id,
                                    @RequestParam(required = false) String title,
                                    @RequestParam(required = false) NoteType type) {
        Note note = (title != null && type != null)
                ? quickNoteService.promoteToNote(campaignId, id, title, type)
                : quickNoteService.promoteToNote(campaignId, id);
        return PromoteResultDto.from(note, campaignId);
    }

    public record QuickNoteDto(UUID id, UUID campaignId, String targetType,
                                UUID targetId, String body, Instant createdAt) {
        static QuickNoteDto from(QuickNote qn) {
            return new QuickNoteDto(qn.getId(), qn.getCampaign().getId(),
                    qn.getTargetType(), qn.getTargetId(), qn.getBody(), qn.getCreatedAt());
        }
    }

    public record PromoteResultDto(UUID noteId, String url) {
        static PromoteResultDto from(Note n, UUID campaignId) {
            return new PromoteResultDto(n.getId(),
                    "/campaigns/%s/notes/%s".formatted(campaignId, n.getId()));
        }
    }
}
