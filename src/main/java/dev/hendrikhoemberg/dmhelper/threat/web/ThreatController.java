package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Controller
public class ThreatController {

    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final TrapService trapService;
    private final HazardService hazardService;
    private final MarkdownUtil markdownUtil;

    public ThreatController(TrapRepository trapRepository,
                            HazardRepository hazardRepository,
                            TrapService trapService,
                            HazardService hazardService,
                            MarkdownUtil markdownUtil) {
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.trapService = trapService;
        this.hazardService = hazardService;
        this.markdownUtil = markdownUtil;
    }

    // ── Trap pages ─────────────────────────────────────────────────────────

    @GetMapping("/library/traps")
    public String listTraps(@RequestParam(required = false) UUID campaignId,
                            @RequestParam(required = false) String text,
                            @RequestParam(required = false) String severity,
                            Model model) {
        List<Trap> traps = trapRepository.findVisibleByCampaignId(campaignId);
        Stream<Trap> stream = traps.stream();
        if (severity != null && !severity.isBlank()) {
            stream = stream.filter(t -> t.getSeverity() != null
                    && t.getSeverity().name().equalsIgnoreCase(severity));
        }
        if (text != null && !text.isBlank()) {
            String q = text.toLowerCase();
            stream = stream.filter(t -> (t.getName() != null && t.getName().toLowerCase().contains(q))
                    || (t.getDescription() != null && t.getDescription().toLowerCase().contains(q))
                    || (t.getTriggerDescription() != null && t.getTriggerDescription().toLowerCase().contains(q)));
        }
        traps = stream.toList();

        model.addAttribute("kind", ThreatKind.TRAP);
        model.addAttribute("items", traps);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("listTitle", "Traps");
        model.addAttribute("createLabel", "New Trap");
        model.addAttribute("createHref", "/library/traps/new");
        model.addAttribute("detailBase", "/library/traps");
        return "threat/list";
    }

    @GetMapping("/library/traps/new")
    public String newTrapForm(@RequestParam(required = false) UUID campaignId, Model model) {
        model.addAttribute("kind", ThreatKind.TRAP);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("editorTitle", "New Trap");
        return "threat/form";
    }

    @GetMapping("/library/traps/{id}")
    public String trapDetail(@PathVariable UUID id, Model model) {
        Trap trap = trapService.findDetailedById(id);
        String md = trap.getDescription();
        model.addAttribute("kind", ThreatKind.TRAP);
        model.addAttribute("threat", trap);
        model.addAttribute("markdownDescription", md != null ? markdownUtil.toHtml(md) : "");
        model.addAttribute("campaignId", trap.getCampaign() != null ? trap.getCampaign().getId() : null);
        model.addAttribute("listHref", "/library/traps");
        model.addAttribute("listLabel", "Traps");
        model.addAttribute("editHref", "/library/traps/" + id + "/edit");
        model.addAttribute("apiBase", "/api/v1/traps");
        return "threat/detail";
    }

    @GetMapping("/library/traps/{id}/edit")
    public String editTrap(@PathVariable UUID id, Model model) {
        Trap trap = trapService.findDetailedById(id);
        if (trap.getSource() != ContentSource.CUSTOM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trap is read-only");
        }
        model.addAttribute("kind", ThreatKind.TRAP);
        model.addAttribute("threat", trap);
        model.addAttribute("editorDto", ThreatWebMapper.fromTrap(trap));
        model.addAttribute("campaignId", trap.getCampaign() != null ? trap.getCampaign().getId() : null);
        model.addAttribute("editorTitle", "Edit " + trap.getName());
        return "threat/form";
    }

    // ── Hazard pages ───────────────────────────────────────────────────────

    @GetMapping("/library/hazards")
    public String listHazards(@RequestParam(required = false) UUID campaignId,
                              @RequestParam(required = false) String text,
                              @RequestParam(required = false) String severity,
                              Model model) {
        List<Hazard> hazards = hazardRepository.findVisibleByCampaignId(campaignId);
        Stream<Hazard> stream = hazards.stream();
        if (severity != null && !severity.isBlank()) {
            stream = stream.filter(h -> h.getSeverity() != null
                    && h.getSeverity().name().equalsIgnoreCase(severity));
        }
        if (text != null && !text.isBlank()) {
            String q = text.toLowerCase();
            stream = stream.filter(h -> (h.getName() != null && h.getName().toLowerCase().contains(q))
                    || (h.getDescription() != null && h.getDescription().toLowerCase().contains(q))
                    || (h.getExposureText() != null && h.getExposureText().toLowerCase().contains(q)));
        }
        hazards = stream.toList();

        model.addAttribute("kind", ThreatKind.HAZARD);
        model.addAttribute("items", hazards);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("listTitle", "Hazards");
        model.addAttribute("createLabel", "New Hazard");
        model.addAttribute("createHref", "/library/hazards/new");
        model.addAttribute("detailBase", "/library/hazards");
        return "threat/list";
    }

    @GetMapping("/library/hazards/new")
    public String newHazardForm(@RequestParam(required = false) UUID campaignId, Model model) {
        model.addAttribute("kind", ThreatKind.HAZARD);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("editorTitle", "New Hazard");
        return "threat/form";
    }

    @GetMapping("/library/hazards/{id}")
    public String hazardDetail(@PathVariable UUID id, Model model) {
        Hazard hazard = hazardService.findDetailedById(id);
        String md = hazard.getDescription();
        model.addAttribute("kind", ThreatKind.HAZARD);
        model.addAttribute("threat", hazard);
        model.addAttribute("markdownDescription", md != null ? markdownUtil.toHtml(md) : "");
        model.addAttribute("campaignId", hazard.getCampaign() != null ? hazard.getCampaign().getId() : null);
        model.addAttribute("listHref", "/library/hazards");
        model.addAttribute("listLabel", "Hazards");
        model.addAttribute("editHref", "/library/hazards/" + id + "/edit");
        model.addAttribute("apiBase", "/api/v1/hazards");
        return "threat/detail";
    }

    @GetMapping("/library/hazards/{id}/edit")
    public String editHazard(@PathVariable UUID id, Model model) {
        Hazard hazard = hazardService.findDetailedById(id);
        if (hazard.getSource() != ContentSource.CUSTOM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This hazard is read-only");
        }
        model.addAttribute("kind", ThreatKind.HAZARD);
        model.addAttribute("threat", hazard);
        model.addAttribute("editorDto", ThreatWebMapper.fromHazard(hazard));
        model.addAttribute("campaignId", hazard.getCampaign() != null ? hazard.getCampaign().getId() : null);
        model.addAttribute("editorTitle", "Edit " + hazard.getName());
        return "threat/form";
    }
}
