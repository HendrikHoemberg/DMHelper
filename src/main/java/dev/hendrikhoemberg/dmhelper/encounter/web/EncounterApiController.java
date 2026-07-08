package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatLogEntryDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantUpdateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.PrefillMapRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.UpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EncounterApiController {

    private final EncounterService service;

    public EncounterApiController(EncounterService service) {
        this.service = service;
    }

    @PostMapping("/campaigns/{campaignId}/encounters")
    public ResponseEntity<EncounterDto> create(@PathVariable UUID campaignId,
                                               @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(campaignId, req));
    }

    @PutMapping("/encounters/{id}")
    public EncounterDto update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/encounters/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/encounters/{id}/activate")
    public EncounterDto activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/encounters/{id}/end")
    public EncounterDto end(@PathVariable UUID id) {
        return service.endEncounter(id);
    }

    @GetMapping("/campaigns/{campaignId}/encounters")
    public List<EncounterDto> list(@PathVariable UUID campaignId) {
        return service.list(campaignId);
    }

    @GetMapping("/encounters/{id}")
    public EncounterDto getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/campaigns/{campaignId}/encounters/active")
    public ResponseEntity<EncounterDto> getActive(@PathVariable UUID campaignId) {
        return ResponseEntity.of(service.findActiveByCampaignId(campaignId));
    }

    @GetMapping("/encounters/{id}/combatants")
    public List<CombatantDto> getCombatants(@PathVariable UUID id) {
        return service.getCombatants(id);
    }

    @PostMapping("/encounters/{id}/combatants")
    public ResponseEntity<CombatantDto> addCombatant(@PathVariable UUID id,
                                                     @RequestBody CombatantCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCombatant(id, req));
    }

    @PutMapping("/combatants/{id}")
    public CombatantDto updateCombatant(@PathVariable UUID id, @RequestBody CombatantUpdateRequest req) {
        return service.updateCombatant(id, req);
    }

    @DeleteMapping("/combatants/{id}")
    public ResponseEntity<Void> removeCombatant(@PathVariable UUID id) {
        service.removeCombatant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/encounters/{id}/prefill/map")
    public List<CombatantDto> prefillFromMap(@PathVariable UUID id, @RequestBody PrefillMapRequest req) {
        return service.prefillFromMap(id, req.mapId());
    }

    @PostMapping("/encounters/{id}/prefill/party")
    public List<CombatantDto> prefillFromParty(@PathVariable UUID id) {
        EncounterDto e = service.getById(id);
        return service.prefillFromParty(id, e.campaignId());
    }

    @PostMapping("/combatants/{id}/split")
    public CombatantDto splitGroupMember(@PathVariable UUID id) {
        return service.splitGroupMember(id);
    }

    @GetMapping("/encounters/{id}/log")
    public List<CombatLogEntryDto> getLog(@PathVariable UUID id) {
        return service.getLog(id);
    }

    @PutMapping("/combatants/{id}/initiative")
    public CombatantDto setInitiative(@PathVariable UUID id, @RequestBody EncounterService.InitiativeRequest req) {
        return service.setInitiative(id, req.initiative());
    }

    @PostMapping("/encounters/{id}/auto-roll")
    public List<CombatantDto> autoRollInitiative(@PathVariable UUID id) {
        return service.autoRollInitiative(id);
    }

    @PutMapping("/encounters/{id}/combatants/reorder")
    public List<CombatantDto> reorderCombatants(@PathVariable UUID id,
                                                @RequestBody EncounterService.ReorderRequest req) {
        return service.reorderCombatants(id, req.orderedIds());
    }

    @PostMapping("/encounters/{id}/next-turn")
    public EncounterDto nextTurn(@PathVariable UUID id) {
        return service.nextTurn(id);
    }

    @PostMapping("/encounters/{id}/previous-turn")
    public EncounterDto previousTurn(@PathVariable UUID id) {
        return service.previousTurn(id);
    }

    @PutMapping("/encounters/{id}/active-turn")
    public EncounterDto setActiveTurn(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        return service.setActiveTurn(id, body.get("combatantId"));
    }

    @PostMapping("/encounters/{id}/undo")
    public ResponseEntity<Void> undo(@PathVariable UUID id) {
        service.undo(id);
        return ResponseEntity.ok().build();
    }
}
