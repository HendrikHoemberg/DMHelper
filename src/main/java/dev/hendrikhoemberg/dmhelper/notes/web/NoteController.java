package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/notes")
public class NoteController {

    private final NoteService noteService;
    private final QuickNoteService quickNoteService;
    private final CampaignRepository campaignRepository;
    private final MarkdownUtil markdownUtil;

    public NoteController(NoteService noteService,
                          QuickNoteService quickNoteService,
                          CampaignRepository campaignRepository,
                          MarkdownUtil markdownUtil) {
        this.noteService = noteService;
        this.quickNoteService = quickNoteService;
        this.campaignRepository = campaignRepository;
        this.markdownUtil = markdownUtil;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId,
                       @RequestParam(required = false) NoteType type,
                       @RequestParam(required = false, defaultValue = "") String search,
                       Model model) {
        var notes = (search != null && !search.isBlank())
                ? noteService.search(campaignId, search)
                : (type != null)
                    ? noteService.findByCampaignIdAndType(campaignId, type)
                    : noteService.findByCampaignId(campaignId);
        model.addAttribute("notes", notes);
        model.addAttribute("noteTypes", NoteType.values());
        model.addAttribute("selectedType", type);
        model.addAttribute("search", search);
        return "notes/list";
    }

    @GetMapping("/{noteId}")
    public String detail(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId,
                         Model model) {
        Note note = noteService.findById(noteId);
        model.addAttribute("note", note);

        String renderedBody = noteService.renderBody(note);
        model.addAttribute("renderedBody", markdownUtil.toHtml(renderedBody));

        var backlinks = noteService.findBacklinks(noteId);
        model.addAttribute("backlinks", backlinks);

        var quicknotes = quickNoteService.findByTarget(campaignId, "NOTE", noteId);
        model.addAttribute("quicknotes", quicknotes);

        return "notes/detail";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("noteTypes", NoteType.values());
        // Reached by an ordinary link, so it has to be a whole page: the bare form fragment
        // carries no <head>, and the browser renders it without any stylesheet.
        return "notes/form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam NoteType type,
                         @RequestParam String title,
                         @RequestParam(required = false) String body,
                         @RequestParam(required = false) String tags) {
        Note note = noteService.create(campaignId, type, title, body, tags);
        return "redirect:/campaigns/%s/notes/%s".formatted(campaignId, note.getId());
    }

    @GetMapping("/{noteId}/edit")
    public String editForm(@PathVariable UUID campaignId,
                           @PathVariable UUID noteId,
                           Model model) {
        Note note = noteService.findById(noteId);
        model.addAttribute("note", note);
        model.addAttribute("noteTypes", NoteType.values());
        return "notes/form";
    }

    @PutMapping("/{noteId}")
    public String update(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId,
                         @RequestParam NoteType type,
                         @RequestParam String title,
                         @RequestParam(required = false) String body,
                         @RequestParam(required = false) String tags) {
        noteService.update(noteId, type, title, body, tags);
        return "redirect:/campaigns/%s/notes/%s".formatted(campaignId, noteId);
    }

    @DeleteMapping("/{noteId}")
    public String delete(@PathVariable UUID campaignId,
                         @PathVariable UUID noteId) {
        noteService.delete(noteId);
        return "redirect:/campaigns/%s/notes".formatted(campaignId);
    }
}
