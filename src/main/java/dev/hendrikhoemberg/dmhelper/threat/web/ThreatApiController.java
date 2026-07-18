package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatDeletionImpact;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatValidationException;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapWrite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ThreatApiController {

    private final TrapService trapService;
    private final HazardService hazardService;
    private final CustomContentSupport customContentSupport;
    private final ThreatReferenceResolver referenceResolver;
    private final ConditionRepository conditionRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;
    private final StatBlockRepository statBlockRepository;

    public ThreatApiController(TrapService trapService,
                               HazardService hazardService,
                               CustomContentSupport customContentSupport,
                               ThreatReferenceResolver referenceResolver,
                               ConditionRepository conditionRepository,
                               EquipmentItemRepository equipmentItemRepository,
                               MagicItemRepository magicItemRepository,
                               StatBlockRepository statBlockRepository) {
        this.trapService = trapService;
        this.hazardService = hazardService;
        this.customContentSupport = customContentSupport;
        this.referenceResolver = referenceResolver;
        this.conditionRepository = conditionRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
        this.statBlockRepository = statBlockRepository;
    }

    // ── Traps ──────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/traps")
    public ResponseEntity<?> createTrap(@RequestParam(required = false) UUID campaignId,
                                        @RequestBody TrapWrite write) {
        try {
            ContentProvenance provenance = customContentSupport.defaultForCreate(null);
            Trap trap = trapService.create(campaignId, write, provenance);
            return ResponseEntity.created(URI.create("/library/traps/" + trap.getId()))
                    .body(ThreatWebMapper.fromTrap(trap));
        } catch (ThreatValidationException e) {
            return validationProblem(e);
        }
    }

    @PutMapping("/api/v1/traps/{id}")
    public ResponseEntity<?> updateTrap(@PathVariable UUID id, @RequestBody TrapWrite write) {
        try {
            Trap trap = trapService.updateCustom(id, write, null);
            return ResponseEntity.ok(ThreatWebMapper.fromTrap(trap));
        } catch (ThreatValidationException e) {
            return validationProblem(e);
        }
    }

    @PostMapping("/api/v1/traps/{id}/clone")
    public ResponseEntity<TrapResponse> cloneTrap(@PathVariable UUID id,
                                                  @RequestParam(required = false) UUID campaignId,
                                                  @RequestParam(required = false) String name) {
        Trap cloned = trapService.cloneAsCustom(id, campaignId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(ThreatWebMapper.fromTrap(cloned));
    }

    @PostMapping("/api/v1/traps/{id}/promote")
    public ResponseEntity<TrapResponse> promoteTrap(@PathVariable UUID id) {
        Trap trap = trapService.promoteToGlobal(id);
        return ResponseEntity.ok(ThreatWebMapper.fromTrap(trap));
    }

    @DeleteMapping("/api/v1/traps/{id}")
    public ResponseEntity<Void> deleteTrap(@PathVariable UUID id,
                                           @RequestParam(defaultValue = "false") boolean confirmed) {
        try {
            trapService.deleteCustom(id, confirmed);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/v1/traps/{id}/deletion-impact")
    public ResponseEntity<ThreatDeletionImpact> trapDeletionImpact(@PathVariable UUID id) {
        return ResponseEntity.ok(trapService.deletionImpact(id));
    }

    @GetMapping("/api/v1/traps/reference-options")
    public ResponseEntity<List<Map<String, String>>> trapReferenceOptions(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam String type,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(referenceOptions(campaignId, type, q));
    }

    // ── Hazards ────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/hazards")
    public ResponseEntity<?> createHazard(@RequestParam(required = false) UUID campaignId,
                                          @RequestBody HazardWrite write) {
        try {
            ContentProvenance provenance = customContentSupport.defaultForCreate(null);
            Hazard hazard = hazardService.create(campaignId, write, provenance);
            return ResponseEntity.created(URI.create("/library/hazards/" + hazard.getId()))
                    .body(ThreatWebMapper.fromHazard(hazard));
        } catch (ThreatValidationException e) {
            return validationProblem(e);
        }
    }

    @PutMapping("/api/v1/hazards/{id}")
    public ResponseEntity<?> updateHazard(@PathVariable UUID id, @RequestBody HazardWrite write) {
        try {
            Hazard hazard = hazardService.updateCustom(id, write, null);
            return ResponseEntity.ok(ThreatWebMapper.fromHazard(hazard));
        } catch (ThreatValidationException e) {
            return validationProblem(e);
        }
    }

    @PostMapping("/api/v1/hazards/{id}/clone")
    public ResponseEntity<HazardResponse> cloneHazard(@PathVariable UUID id,
                                                      @RequestParam(required = false) UUID campaignId,
                                                      @RequestParam(required = false) String name) {
        Hazard cloned = hazardService.cloneAsCustom(id, campaignId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(ThreatWebMapper.fromHazard(cloned));
    }

    @PostMapping("/api/v1/hazards/{id}/promote")
    public ResponseEntity<HazardResponse> promoteHazard(@PathVariable UUID id) {
        Hazard hazard = hazardService.promoteToGlobal(id);
        return ResponseEntity.ok(ThreatWebMapper.fromHazard(hazard));
    }

    @DeleteMapping("/api/v1/hazards/{id}")
    public ResponseEntity<Void> deleteHazard(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "false") boolean confirmed) {
        try {
            hazardService.deleteCustom(id, confirmed);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/api/v1/hazards/{id}/deletion-impact")
    public ResponseEntity<ThreatDeletionImpact> hazardDeletionImpact(@PathVariable UUID id) {
        return ResponseEntity.ok(hazardService.deletionImpact(id));
    }

    @GetMapping("/api/v1/hazards/reference-options")
    public ResponseEntity<List<Map<String, String>>> hazardReferenceOptions(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam String type,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(referenceOptions(campaignId, type, q));
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private ResponseEntity<ProblemDetail> validationProblem(ThreatValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("problems", e.problems());
        return ResponseEntity.badRequest().body(problem);
    }

    private List<Map<String, String>> referenceOptions(UUID campaignId, String type, String q) {
        String query = q != null ? q.strip().toLowerCase() : "";
        List<Map<String, String>> results = new ArrayList<>();

        CampaignContentType contentType;
        try {
            contentType = CampaignContentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return List.of();
        }

        switch (contentType) {
            case CONDITION -> {
                var items = query.isEmpty()
                        ? conditionRepository.findAll()
                        : conditionRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
                for (var item : items) {
                    addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                }
            }
            case EQUIPMENT_ITEM -> {
                var items = query.isEmpty()
                        ? equipmentItemRepository.findAll()
                        : equipmentItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
                for (var item : items) {
                    addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                }
            }
            case MAGIC_ITEM -> {
                var items = query.isEmpty()
                        ? magicItemRepository.findAll()
                        : magicItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
                for (var item : items) {
                    addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                }
            }
            case STATBLOCK -> {
                var items = query.isEmpty()
                        ? statBlockRepository.findAll()
                        : statBlockRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
                for (var item : items) {
                    addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                }
            }
            default -> {
                // no other reference types for traps/hazards
            }
        }

        return results;
    }

    private void addVisibleOption(List<Map<String, String>> results,
                                  CampaignContentType type,
                                  UUID id,
                                  String label,
                                  UUID campaignIdOrNull) {
        if (!referenceResolver.isVisibleToScope(type, id, campaignIdOrNull)) {
            return;
        }
        Map<String, String> option = new LinkedHashMap<>();
        option.put("id", id.toString());
        option.put("label", label);
        option.put("type", type.name());
        String url = destinationUrl(type, id);
        if (url != null) {
            option.put("url", url);
        }
        results.add(option);
    }

    private static String destinationUrl(CampaignContentType type, UUID id) {
        return switch (type) {
            case CONDITION -> "/library/conditions/" + id;
            case EQUIPMENT_ITEM -> "/library/equipment/" + id;
            case MAGIC_ITEM -> "/library/magic-items/" + id;
            case STATBLOCK -> "/library/statblocks/" + id;
            default -> null;
        };
    }
}
