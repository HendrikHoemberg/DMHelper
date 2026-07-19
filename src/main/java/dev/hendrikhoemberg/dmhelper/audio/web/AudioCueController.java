package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.service.AudioCueService;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/audio/cues")
public class AudioCueController {

    private final AudioCueService service;
    private final AudioCueRepository repository;
    private final MarkdownUtil markdownUtil;

    public AudioCueController(AudioCueService service,
                              AudioCueRepository repository,
                              MarkdownUtil markdownUtil) {
        this.service = service;
        this.repository = repository;
        this.markdownUtil = markdownUtil;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId,
                       @RequestParam(required = false) String text,
                       Model model) {
        var cues = service.listByCampaign(campaignId);
        if (text != null && !text.isBlank()) {
            String q = text.toLowerCase();
            cues = cues.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(q))
                            || (c.getCueKey() != null && c.getCueKey().toLowerCase().contains(q)))
                    .toList();
        }
        model.addAttribute("cues", cues);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("listTitle", "Audio Cues");
        model.addAttribute("createLabel", "New Cue");
        model.addAttribute("createHref", "/campaigns/" + campaignId + "/audio/cues/new");
        model.addAttribute("detailBase", "/campaigns/" + campaignId + "/audio/cues");
        return "audio/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("editorTitle", "New Audio Cue");
        return "audio/form";
    }

    @GetMapping("/{cueId}")
    public String detail(@PathVariable UUID campaignId,
                         @PathVariable UUID cueId,
                         Model model) {
        var cue = service.findById(cueId);
        String md = cue.getNotes();
        model.addAttribute("cue", cue);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("markdownNotes", md != null ? markdownUtil.toHtml(md) : "");
        model.addAttribute("listHref", "/campaigns/" + campaignId + "/audio/cues");
        model.addAttribute("listLabel", "Audio Cues");
        model.addAttribute("editHref", "/campaigns/" + campaignId + "/audio/cues/" + cueId + "/edit");
        model.addAttribute("apiBase", "/api/v1/campaigns/" + campaignId + "/audio/cues");
        return "audio/detail";
    }

    @GetMapping("/{cueId}/edit")
    public String editForm(@PathVariable UUID campaignId,
                           @PathVariable UUID cueId,
                           Model model) {
        var cue = service.findById(cueId);
        model.addAttribute("cue", cue);
        model.addAttribute("editorDto", AudioCueWebMapper.fromCue(cue));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("editorTitle", "Edit " + cue.getName());
        return "audio/form";
    }
}
