package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessRepairService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignScaleService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignValidationResult;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService service;
    private final NoteService noteService;
    private final PartyMemberService partyMemberService;
    private final AudioCueRepository audioCueRepository;
    private final CampaignScaleService scaleService;
    private final CampaignReadinessFacade readinessFacade;
    private final ReadinessRepairService repairService;
    private final AdventureService adventureService;
    private final EncounterRepository encounterRepository;

    public CampaignController(CampaignService service, NoteService noteService,
                              PartyMemberService partyMemberService,
                              AudioCueRepository audioCueRepository,
                              CampaignScaleService scaleService,
                              CampaignReadinessFacade readinessFacade,
                              ReadinessRepairService repairService,
                              AdventureService adventureService,
                              EncounterRepository encounterRepository) {
        this.service = service;
        this.noteService = noteService;
        this.partyMemberService = partyMemberService;
        this.audioCueRepository = audioCueRepository;
        this.scaleService = scaleService;
        this.readinessFacade = readinessFacade;
        this.repairService = repairService;
        this.adventureService = adventureService;
        this.encounterRepository = encounterRepository;
    }

    @GetMapping("/new")
    public String newForm() {
        return "campaigns/new";
    }

    @GetMapping
    public String list(Model model, HttpServletRequest request) {
        List<Campaign> campaigns = service.findAll();
        campaigns.forEach(this::addAuthorLine);
        model.addAttribute("campaigns", campaigns);
        model.addAttribute("campaignSigils", sigilsFor(campaigns));

        String fragment = request.getParameter("fragment");
        if ("form".equals(fragment)) {
            return "campaigns/_form";
        }
        if ("new-button".equals(fragment)) {
            return "campaigns/_new-button";
        }

        return "campaigns/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         Model model) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }
        Campaign campaign = service.create(name, description);
        addAuthorLine(campaign);
        model.addAttribute("campaign", campaign);
        model.addAttribute("sigil", CampaignSigil.from(campaign.getId()));
        return "campaigns/_card";
    }

    private Map<UUID, CampaignSigil> sigilsFor(List<Campaign> campaigns) {
        return campaigns.stream().collect(Collectors.toMap(
                Campaign::getId,
                campaign -> CampaignSigil.from(campaign.getId())));
    }

    private static final DateTimeFormatter AUTHOR_LINE_DATE =
            DateTimeFormatter.ofPattern("d MMMM").withZone(ZoneId.systemDefault());

    private void addAuthorLine(Campaign campaign) {
        int heroes = partyMemberService.findActiveByCampaignId(campaign.getId()).size();
        String opened = AUTHOR_LINE_DATE.format(campaign.getCreatedAt());
        campaign.setAuthorLine(heroes == 1
                ? "1 hero · opened " + opened
                : heroes + " heroes · opened " + opened);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("campaign", service.findById(id));
        model.addAttribute("campaignId", id);
        model.addAttribute("readiness", readinessFacade.reportForCampaign(id));
        model.addAttribute("repairService", repairService);
        model.addAttribute("scale", scaleService.scaleOf(id));
        var plans = noteService.findByCampaignIdAndType(id, NoteType.SESSION_PLAN);
        if (!plans.isEmpty()) {
            model.addAttribute("sessionPlan", plans.get(0));
        }
        model.addAttribute("partyMembers", partyMemberService.findActiveByCampaignId(id));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(id));
        model.addAttribute("recentNotes", noteService.findByCampaignId(id).stream()
                .sorted(java.util.Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(5)
                .toList());
        addRunEntryPoints(id, model);
        return "campaigns/detail";
    }

    @GetMapping("/{id}/settings")
    public String settings(@PathVariable UUID id, Model model) {
        model.addAttribute("campaign", service.findById(id));
        model.addAttribute("campaignId", id);
        return "campaigns/settings";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         Model model) {
        Campaign campaign = service.update(id, name, description);
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", id);
        model.addAttribute("readiness", readinessFacade.reportForCampaign(id));
        model.addAttribute("repairService", repairService);
        model.addAttribute("scale", scaleService.scaleOf(id));
        model.addAttribute("partyMembers", partyMemberService.findActiveByCampaignId(id));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(id));
        model.addAttribute("recentNotes", noteService.findByCampaignId(id).stream()
                .sorted(java.util.Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(5)
                .toList());
        addRunEntryPoints(id, model);
        return "campaigns/detail";
    }

    private void addRunEntryPoints(UUID campaignId, Model model) {
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        encounterRepository.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .ifPresent(e -> model.addAttribute("activeEncounterId", e.getId()));
    }

    @PutMapping("/{id}/milestone")
    @ResponseBody
    public ResponseEntity<Void> toggleMilestone(@PathVariable UUID id,
                                                @RequestParam boolean enabled) {
        service.setMilestoneMode(id, enabled);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns")
                .build();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportCampaign(@PathVariable UUID id) {
        Campaign campaign = service.findById(id);
        String json = service.exportToJson(id);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        String filename = URLEncoder.encode(
                campaign.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".dmcampaign.json",
                StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @PostMapping("/import")
    public Object importCampaign(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean dryRun,
                                  Model model) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        try {
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (dryRun) {
                CampaignValidationResult result = service.validateImport(json);
                return ResponseEntity.ok(DryRunResult.from(result));
            }
            Campaign campaign = service.importFromJson(json);
            model.addAttribute("campaign", campaign);
            model.addAttribute("sigil", CampaignSigil.from(campaign.getId()));
            return "campaigns/_card";
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    public record DryRunResult(
            boolean valid,
            String status,
            List<CampaignImportProblem> problems
    ) {
        static DryRunResult from(CampaignValidationResult result) {
            return new DryRunResult(
                    result.valid(),
                    result.valid() ? "READY" : "BLOCKED",
                    result.problems());
        }
    }
}
