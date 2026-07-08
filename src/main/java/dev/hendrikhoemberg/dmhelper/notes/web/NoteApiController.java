package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/notes")
public class NoteApiController {

    private final NoteService noteService;

    public NoteApiController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteSummaryDto> list(@PathVariable UUID campaignId,
                                     @RequestParam(required = false) NoteType type,
                                     @RequestParam(required = false, defaultValue = "") String search) {
        var notes = (search != null && !search.isBlank())
                ? noteService.search(campaignId, search)
                : (type != null)
                    ? noteService.findByCampaignIdAndType(campaignId, type)
                    : noteService.findByCampaignId(campaignId);
        return notes.stream().map(NoteSummaryDto::from).toList();
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<NoteDetailDto> get(@PathVariable UUID campaignId,
                                             @PathVariable UUID noteId) {
        Note note = noteService.findById(noteId);
        return ResponseEntity.ok(NoteDetailDto.from(note, noteService.renderBody(note),
                noteService.findBacklinks(noteId)));
    }

    @GetMapping("/autocomplete")
    public List<Map<String, String>> autocomplete(@PathVariable UUID campaignId,
                                                  @RequestParam String q) {
        return noteService.search(campaignId, q).stream()
                .map(n -> Map.of("id", n.getId().toString(), "title", n.getTitle(),
                        "type", n.getType().name()))
                .limit(10)
                .toList();
    }

    public record NoteSummaryDto(UUID id, String title, NoteType type,
                                  String snippet, String createdAt) {
        static NoteSummaryDto from(Note n) {
            String snippet = n.getBody() != null && n.getBody().length() > 120
                    ? n.getBody().substring(0, 117) + "..." : n.getBody();
            return new NoteSummaryDto(n.getId(), n.getTitle(), n.getType(),
                    snippet, n.getCreatedAt().toString());
        }
    }

    public record NoteDetailDto(UUID id, String title, NoteType type,
                                 String body, String renderedBody, String tags,
                                 boolean dmOnly, String createdAt,
                                 List<BacklinkDto> backlinks) {
        static NoteDetailDto from(Note n, String renderedBody, List<Note> backlinks) {
            return new NoteDetailDto(n.getId(), n.getTitle(), n.getType(),
                    n.getBody(), renderedBody, n.getTags(), n.isDmOnly(),
                    n.getCreatedAt().toString(),
                    backlinks.stream().map(BacklinkDto::from).toList());
        }

        record BacklinkDto(UUID id, String title, NoteType type) {
            static BacklinkDto from(Note n) {
                return new BacklinkDto(n.getId(), n.getTitle(), n.getType());
            }
        }
    }
}
