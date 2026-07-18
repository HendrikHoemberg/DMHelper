package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableConsequenceDraft;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableConsequenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rollable-table-rolls")
public class RollableTableRollManagementController {

    private final TableConsequenceService consequenceService;

    public RollableTableRollManagementController(TableConsequenceService consequenceService) {
        this.consequenceService = consequenceService;
    }

    @GetMapping("/{rollId}/draft")
    public ResponseEntity<TableConsequenceDraft> getDraft(
            @PathVariable UUID rollId,
            @RequestParam UUID campaignId) {
        TableConsequenceDraft draft = consequenceService.preview(rollId, campaignId);
        return ResponseEntity.ok(draft);
    }

    @PostMapping("/{rollId}/encounter/confirm")
    public ResponseEntity<Void> confirmEncounter(
            @PathVariable UUID rollId,
            @RequestParam UUID campaignId,
            @RequestBody TableConsequenceService.ConfirmEncounterRequest request) {
        consequenceService.confirmEncounter(rollId, campaignId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{rollId}/reward/confirm")
    public ResponseEntity<Void> confirmReward(
            @PathVariable UUID rollId,
            @RequestParam UUID campaignId,
            @RequestBody TableConsequenceService.ConfirmRewardRequest request) {
        consequenceService.confirmReward(rollId, campaignId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{rollId}/discard")
    public ResponseEntity<Void> discard(
            @PathVariable UUID rollId,
            @RequestParam UUID campaignId) {
        consequenceService.discard(rollId, campaignId);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("not found")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.badRequest().build();
    }
}
