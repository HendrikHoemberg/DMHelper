package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
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

    public EncounterController(EncounterService encounterService, GameMapRepository mapRepo) {
        this.encounterService = encounterService;
        this.mapRepo = mapRepo;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("encounters", encounterService.list(campaignId));
        model.addAttribute("campaignId", campaignId);
        return "encounter/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("encounter", null);
        model.addAttribute("maps", mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId));
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
        encounterService.addCombatant(encounterId, new CombatantCreateRequest(name, maxHp, kind, null, null, null));
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    @PostMapping("/{encounterId}/prefill/map")
    public String prefillFromMap(@PathVariable UUID campaignId, @PathVariable UUID encounterId) {
        var enc = encounterService.getById(encounterId);
        if (enc.mapId() != null) {
            encounterService.prefillFromMap(encounterId, enc.mapId());
        }
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    @PostMapping("/{encounterId}/prefill/party")
    public String prefillFromParty(@PathVariable UUID campaignId, @PathVariable UUID encounterId) {
        encounterService.prefillFromParty(encounterId, campaignId);
        return "redirect:/campaigns/" + campaignId + "/encounters/" + encounterId;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("encounter", encounterService.getById(id));
        model.addAttribute("combatants", encounterService.getCombatants(id));
        model.addAttribute("campaignId", campaignId);
        return "encounter/detail";
    }
}
