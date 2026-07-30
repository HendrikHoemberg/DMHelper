package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.CombatDifficultyCalculator.DifficultyResult;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementMoveRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.PlacementUpsertRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.ChangeEncounterMapRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.EncounterReadinessDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPrep;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterRewards;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatLogEntryDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterEndResult;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.AddFromLibraryRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantUpdateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ThreatCombatantRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ConcentrationCheckRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ConcentrationRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.RechargeCheckRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.RechargePrompt;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.UpdateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.StartCombatRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.InitiativeSetupIncompleteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EncounterApiController {

    private final EncounterService service;
    private final EncounterPlacementService placements;

    public EncounterApiController(EncounterService service, EncounterPlacementService placements) {
        this.service = service;
        this.placements = placements;
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

    @PostMapping("/encounters/{id}/reset")
    public EncounterDto reset(@PathVariable UUID id) {
        return service.resetEncounter(id);
    }

    @PostMapping("/encounters/{id}/end")
    public EncounterDto end(@PathVariable UUID id) {
        return service.endEncounter(id);
    }

    @PutMapping("/encounters/{id}/prep")
    public EncounterDto updatePrep(@PathVariable UUID id,
            @RequestBody EncounterPrep prep) {
        return service.updatePrep(id, prep);
    }

    @GetMapping("/encounters/{id}/prep")
    public EncounterPrep getPrep(@PathVariable UUID id) {
        return service.getPrep(id);
    }

    @PutMapping("/encounters/{id}/rewards")
    public EncounterDto updateRewards(@PathVariable UUID id,
            @RequestBody EncounterRewards rewards) {
        return service.updateRewards(id, rewards);
    }

    @GetMapping("/encounters/{id}/rewards")
    public EncounterRewards getRewards(@PathVariable UUID id) {
        return service.getRewards(id);
    }

    @PostMapping("/encounters/{id}/end-with-summary")
    public EncounterEndResult endWithSummary(@PathVariable UUID id) {
        return service.endEncounterWithSummary(id);
    }

    @PostMapping("/encounters/{id}/rewards/apply")
    public ResponseEntity<Void> applyRewards(@PathVariable UUID id,
            @RequestBody EncounterService.ApplyRewardsRequest req) {
        service.applyRewards(id, req);
        return ResponseEntity.ok().build();
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
        return service.findActiveByCampaignId(campaignId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/encounters/{id}/combatants")
    public List<CombatantDto> getCombatants(@PathVariable UUID id) {
        return service.getCombatants(id);
    }

    @GetMapping("/encounters/{eid}/combatants/{cid}/statblock")
    public ResponseEntity<EncounterService.StatblockRef> getCombatantStatblock(
            @PathVariable UUID eid, @PathVariable UUID cid) {
        var ref = service.getCombatantStatblock(cid);
        if (ref == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(ref);
    }

    @PostMapping("/encounters/{id}/combatants")
    public ResponseEntity<CombatantDto> addCombatant(@PathVariable UUID id,
                                                     @RequestBody CombatantCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCombatant(id, req));
    }

    @PostMapping("/encounters/{id}/combatants/from-threat")
    public ResponseEntity<CombatantDto> addThreatCombatant(@PathVariable UUID id,
                                                           @RequestBody ThreatCombatantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addThreatCombatant(id, req));
    }

    @PostMapping("/encounters/{id}/combatants/from-library")
    public ResponseEntity<List<CombatantDto>> addFromLibrary(
            @PathVariable UUID id, @RequestBody AddFromLibraryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addFromLibrary(id, req));
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

    @GetMapping("/campaigns/{campaignId}/encounters/{encounterId}/difficulty")
    public DifficultyResult getDifficulty(@PathVariable UUID campaignId, @PathVariable UUID encounterId) {
        return service.calculateDifficulty(campaignId, encounterId);
    }

    @PutMapping("/combatants/{id}/initiative")
    public CombatantDto setInitiative(@PathVariable UUID id, @RequestBody EncounterService.InitiativeRequest req) {
        return service.setInitiative(id, req.initiative());
    }

    @PostMapping("/encounters/{id}/auto-roll")
    public List<CombatantDto> autoRollInitiative(@PathVariable UUID id) {
        return service.rollUnsetNpcInitiatives(id);
    }

    @PostMapping("/encounters/{id}/start-combat")
    public EncounterDto startCombat(@PathVariable UUID id, @RequestBody StartCombatRequest request) {
        return service.startCombat(id, request.acceptUnset());
    }

    @GetMapping({
            "/encounters/{id}/initiative-setup/combatants",
            "/encounters/{id}/setup-combatants"
    })
    public List<CombatantDto> getSetupCombatants(@PathVariable UUID id) {
        return service.getInitiativeSetupCombatants(id);
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
    public EncounterDto setActiveTurn(@PathVariable UUID id, @RequestBody EncounterService.ActiveTurnRequest req) {
        return service.setActiveTurn(id, req.combatantId());
    }

    @PostMapping("/encounters/{id}/undo")
    public ResponseEntity<Void> undo(@PathVariable UUID id) {
        try {
            service.undo(id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/combatants/{id}/hp")
    public CombatantDto setHp(@PathVariable UUID id, @RequestBody EncounterService.HpRequest req) {
        return service.setHp(id, req.currentHp(), req.tempHp());
    }

    @PostMapping("/combatants/{id}/damage")
    public CombatantDto applyDamage(@PathVariable UUID id, @RequestBody EncounterService.DamageRequest req) {
        return service.applyDamage(id, req.amount());
    }

    @PutMapping("/combatants/{id}/defeated")
    public CombatantDto markDefeated(@PathVariable UUID id, @RequestBody EncounterService.DefeatedRequest req) {
        return service.markDefeated(id, req.defeated());
    }

    @PutMapping("/combatants/{id}/conditions")
    public CombatantDto toggleCondition(@PathVariable UUID id, @RequestBody EncounterService.ConditionToggleRequest req) {
        return service.toggleCondition(id, req.sourceKey(), req.durationRounds());
    }

    @PostMapping("/combatants/{id}/conditions/{key}/remove")
    public CombatantDto removeCondition(@PathVariable UUID id, @PathVariable String key) {
        return service.removeCondition(id, key);
    }

    @PostMapping("/encounters/{id}/tick-conditions")
    public ResponseEntity<Void> tickConditions(@PathVariable UUID id) {
        service.tickConditionDurations(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/combatants/{id}/concentration")
    public CombatantDto setConcentration(@PathVariable UUID id, @RequestBody ConcentrationRequest req) {
        return service.setConcentration(id, req.spellName());
    }

    @PostMapping("/combatants/{id}/concentration-check")
    public CombatantDto resolveConcentrationCheck(@PathVariable UUID id, @RequestBody ConcentrationCheckRequest req) {
        return service.resolveConcentrationCheck(id, req.passed());
    }

    @PostMapping("/combatants/{id}/legendary-action")
    public CombatantDto useLegendaryAction(@PathVariable UUID id) {
        return service.useLegendaryAction(id);
    }

    @PostMapping("/combatants/{id}/legendary-resistance")
    public CombatantDto useLegendaryResistance(@PathVariable UUID id) {
        return service.useLegendaryResistance(id);
    }

    @PostMapping("/encounters/{id}/reset-legendary")
    public ResponseEntity<Void> resetLegendary(@PathVariable UUID id) {
        service.resetLegendaryActions(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/encounters/{id}/lair-action")
    public ResponseEntity<Void> activateLairAction(@PathVariable UUID id) {
        service.activateLairAction(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/encounters/{id}/waves")
    public List<EncounterService.WaveDto> listWaves(@PathVariable UUID id) {
        return service.listWaves(id);
    }

    @PostMapping("/encounters/{id}/waves")
    public ResponseEntity<EncounterService.WaveDto> createWave(@PathVariable UUID id,
            @RequestBody EncounterService.CreateWaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWave(id, req));
    }

    @PutMapping("/encounters/{id}/waves/{waveId}")
    public EncounterService.WaveDto updateWave(@PathVariable UUID id, @PathVariable UUID waveId,
            @RequestBody EncounterService.UpdateWaveRequest req) {
        return service.updateWave(waveId, req);
    }

    @DeleteMapping("/encounters/{id}/waves/{waveId}")
    public ResponseEntity<Void> deleteWave(@PathVariable UUID id, @PathVariable UUID waveId) {
        service.deleteWave(waveId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/encounters/{id}/waves/{waveId}/spawn")
    public EncounterService.EncounterDto spawnWave(@PathVariable UUID id, @PathVariable UUID waveId) {
        return service.spawnWave(id, waveId);
    }

    @GetMapping("/combatants/{id}/recharge-prompts")
    public List<RechargePrompt> getRechargePrompts(@PathVariable UUID id) {
        return service.checkRechargeAbilities(id);
    }

    @PostMapping("/combatants/{id}/recharge-check")
    public ResponseEntity<Void> resolveRecharge(@PathVariable UUID id, @RequestBody RechargeCheckRequest req) {
        service.resolveRecharge(id, req.abilityName(), req.rollResult());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/encounters/{id}/placements")
    List<PlacementDto> listPlacements(@PathVariable UUID id) {
        return placements.list(id);
    }

    @PutMapping("/encounters/{id}/combatants/{combatantId}/placement")
    PlacementDto upsertPlacement(
            @PathVariable UUID id,
            @PathVariable UUID combatantId,
            @RequestBody PlacementUpsertRequest request) {
        return placements.upsert(id, combatantId, request);
    }

    @PatchMapping("/encounters/{id}/combatants/{combatantId}/placement/move")
    PlacementDto movePlacement(
            @PathVariable UUID id,
            @PathVariable UUID combatantId,
            @RequestBody PlacementMoveRequest request) {
        return placements.move(id, combatantId, request.positionX(), request.positionY());
    }

    @DeleteMapping("/encounters/{id}/combatants/{combatantId}/placement")
    ResponseEntity<Void> removePlacement(
            @PathVariable UUID id, @PathVariable UUID combatantId) {
        placements.remove(id, combatantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/encounters/{id}/placements/auto")
    List<PlacementDto> autoPlace(@PathVariable UUID id) {
        return placements.autoPlaceUnplaced(id);
    }

    @PostMapping("/encounters/{id}/placements/party")
    List<PlacementDto> placeMissingParty(@PathVariable UUID id) {
        return service.placeMissingParty(id);
    }

    @GetMapping("/encounters/{id}/readiness")
    EncounterReadinessDto readiness(@PathVariable UUID id) {
        return placements.readiness(id);
    }

    @PutMapping("/encounters/{id}/map")
    EncounterReadinessDto changeMap(
            @PathVariable UUID id,
            @RequestBody ChangeEncounterMapRequest request) {
        return placements.changeMapAndResetPlacements(id, request.mapId());
    }

    @ExceptionHandler(InitiativeSetupIncompleteException.class)
    public ResponseEntity<ProblemDetail> initiativeSetupConflict(InitiativeSetupIncompleteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Initiative Setup Incomplete");
        problem.setProperty("unsetCount", ex.unsetCount());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
