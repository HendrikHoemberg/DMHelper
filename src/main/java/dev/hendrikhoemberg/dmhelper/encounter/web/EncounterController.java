package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ThreatCombatantRequest;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/encounters")
public class EncounterController {

    private final EncounterService encounterService;
    private final GameMapRepository mapRepo;
    private final AudioCueRepository audioCueRepository;
    private final EncounterRepository encounterRepository;

    public EncounterController(EncounterService encounterService, GameMapRepository mapRepo,
                               AudioCueRepository audioCueRepository,
                               EncounterRepository encounterRepository) {
        this.encounterService = encounterService;
        this.mapRepo = mapRepo;
        this.audioCueRepository = audioCueRepository;
        this.encounterRepository = encounterRepository;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("encounters", encounterService.list(campaignId));
        model.addAttribute("campaignId", campaignId);
        // Encounters store a bare mapId; give the cards a name to show instead of a raw UUID.
        var mapNames = new java.util.HashMap<UUID, String>();
        mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId)
                .forEach(m -> mapNames.put(m.getId(), m.getName()));
        model.addAttribute("mapNames", mapNames);
        return "encounter/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("encounter", null);
        model.addAttribute("maps", mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        return "encounter/_form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String name,
                         @RequestParam(required = false) UUID mapId) {
        encounterService.create(campaignId, new CreateRequest(name, mapId));
        return "redirect:/campaigns/" + campaignId + "/encounters";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("encounter", encounterService.getById(id));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        encounterRepository.findById(id).ifPresent(e -> {
            model.addAttribute("encounterEntity", e);
        });
        return "encounter/_form :: form";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) UUID mapId,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String lairActionName,
                         @RequestParam(required = false) String lairActionDescription) {
        encounterService.update(id, new EncounterService.UpdateRequest(name, mapId, status,
                lairActionName, lairActionDescription));
        return "redirect:/campaigns/" + campaignId + "/encounters";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        encounterService.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/encounters")
                .build();
    }

    @PostMapping("/{id}/run")
    public String run(@PathVariable UUID campaignId, @PathVariable UUID id) {
        if (!"ACTIVE".equals(encounterService.getById(id).status())) {
            encounterService.activate(id);
        }
        return "redirect:/campaigns/" + campaignId + "/session";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable UUID campaignId, @PathVariable UUID id) {
        encounterService.activate(id);
        return "redirect:/campaigns/" + campaignId + "/encounters/" + id;
    }

    @PostMapping("/{id}/end")
    public String end(@PathVariable UUID campaignId, @PathVariable UUID id) {
        encounterService.endEncounter(id);
        return "redirect:/campaigns/" + campaignId + "/encounters/" + id;
    }

    @PostMapping("/{encounterId}/combatants")
    public String addCombatant(@PathVariable UUID campaignId, @PathVariable UUID encounterId,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "10") int maxHp,
                               @RequestParam(defaultValue = "NPC") String kind) {
        encounterService.addCombatant(encounterId, new CombatantCreateRequest(name, maxHp, kind, null, null));
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    @PostMapping("/{encounterId}/combatants/from-threat")
    public String addThreatCombatant(@PathVariable UUID campaignId, @PathVariable UUID encounterId,
                                     @RequestParam ThreatKind threatKind,
                                     @RequestParam UUID threatId,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer initiative,
                                     @RequestParam(required = false) UUID waveId) {
        encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(threatKind, threatId, name, initiative, waveId));
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    @PostMapping("/{encounterId}/prefill/party")
    public String prefillFromParty(@PathVariable UUID campaignId, @PathVariable UUID encounterId) {
        encounterService.prefillFromParty(encounterId, campaignId);
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    private void addEncounterModel(UUID campaignId, UUID id, Model model) {
        model.addAttribute("encounter", encounterService.getById(id));
        model.addAttribute("combatants", encounterService.getCombatants(id));
        model.addAttribute("difficulty", encounterService.calculateDifficulty(campaignId, id));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        encounterRepository.findById(id).ifPresent(e -> {
            model.addAttribute("encounterEntity", e);
            model.addAttribute("encounterCombatCue", e.getCombatAudioCue());
            model.addAttribute("encounterVictoryCue", e.getVictoryAudioCue());
            model.addAttribute("encounterVictoryDuration", e.getVictoryCueDurationSeconds());
        });
        model.addAttribute("waves", encounterService.listWaves(id));
        model.addAttribute("prep", encounterService.getPrep(id));
        model.addAttribute("rewards", encounterService.getRewards(id));
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        addEncounterModel(campaignId, id, model);
        return "encounter/detail";
    }

    @GetMapping("/{id}/setup")
    public String setup(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        addEncounterModel(campaignId, id, model);
        model.addAttribute("maps", mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId));
        return "encounter/setup";
    }
}
