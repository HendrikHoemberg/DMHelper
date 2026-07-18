package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableRollService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableValidationException;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroup;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollRequest;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableDeletionImpact;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rollable-tables")
public class RollableTableApiController {

    private final RollableTableService service;
    private final RollableTableRollService rollService;
    private final RollableTableRepository repository;
    private final CustomContentSupport customContentSupport;
    private final DiceEngine diceEngine;
    private final StatBlockRepository statBlockRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;
    private final NoteRepository noteRepository;
    private final EncounterRepository encounterRepository;
    private final HandoutRepository handoutRepository;
    private final TableReferenceResolver referenceResolver;

    public RollableTableApiController(RollableTableService service,
                                       RollableTableRollService rollService,
                                       RollableTableRepository repository,
                                       CustomContentSupport customContentSupport,
                                       DiceEngine diceEngine,
                                       StatBlockRepository statBlockRepository,
                                       EquipmentItemRepository equipmentItemRepository,
                                       MagicItemRepository magicItemRepository,
                                       NoteRepository noteRepository,
                                       EncounterRepository encounterRepository,
                                       HandoutRepository handoutRepository,
                                       TableReferenceResolver referenceResolver) {
        this.service = service;
        this.rollService = rollService;
        this.repository = repository;
        this.customContentSupport = customContentSupport;
        this.diceEngine = diceEngine;
        this.statBlockRepository = statBlockRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
        this.noteRepository = noteRepository;
        this.encounterRepository = encounterRepository;
        this.handoutRepository = handoutRepository;
        this.referenceResolver = referenceResolver;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam(required = false) UUID campaignId,
                                     @RequestBody RollableTableWrite write) {
        try {
            ContentProvenance provenance = customContentSupport.defaultForCreate(null);
            RollableTable table = service.create(campaignId, write, provenance);
            return ResponseEntity.created(URI.create("/library/tables/" + table.getId()))
                    .body(RollableTableWebMapper.from(table));
        } catch (RollableTableValidationException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setProperty("problems", e.problems());
            return ResponseEntity.badRequest().body(problem);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody RollableTableWrite write) {
        try {
            RollableTable table = service.updateCustom(id, write, null);
            return ResponseEntity.ok(RollableTableWebMapper.from(table));
        } catch (RollableTableValidationException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setProperty("problems", e.problems());
            return ResponseEntity.badRequest().body(problem);
        }
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<RollableTableResponse> clone(@PathVariable UUID id,
                                                       @RequestParam(required = false) UUID campaignId,
                                                       @RequestParam(required = false) String name) {
        RollableTable cloned = service.cloneAsCustom(id, campaignId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(RollableTableWebMapper.from(cloned));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<RollableTableResponse> promote(@PathVariable UUID id) {
        RollableTable table = service.promoteToGlobal(id);
        return ResponseEntity.ok(RollableTableWebMapper.from(table));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "false") boolean confirmed) {
        try {
            service.deleteCustom(id, confirmed);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/{id}/deletion-impact")
    public ResponseEntity<TableDeletionImpact> deletionImpact(@PathVariable UUID id) {
        return ResponseEntity.ok(service.deletionImpact(id));
    }

    @GetMapping("/reference-options")
    public ResponseEntity<List<Map<String, String>>> referenceOptions(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam String type,
            @RequestParam(required = false) String q) {
        String query = q != null ? q.strip().toLowerCase() : "";
        List<Map<String, String>> results = new ArrayList<>();

        CampaignContentType contentType;
        try {
            contentType = CampaignContentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(List.of());
        }

        switch (contentType) {
            case STATBLOCK -> {
                var items = query.isEmpty()
                        ? statBlockRepository.findAll()
                        : statBlockRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
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
            case NOTE -> {
                if (campaignId != null) {
                    var items = noteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
                    for (var item : items) {
                        if (query.isEmpty() || (item.getTitle() != null && item.getTitle().toLowerCase().contains(query))) {
                            addVisibleOption(results, contentType, item.getId(), item.getTitle(), campaignId);
                        }
                    }
                }
            }
            case ROLLABLE_TABLE -> {
                var items = repository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
                for (var item : items) {
                    addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                }
            }
            case ENCOUNTER -> {
                if (campaignId != null) {
                    var items = encounterRepository.findByCampaignIdOrderByNameAsc(campaignId);
                    for (var item : items) {
                        if (query.isEmpty() || (item.getName() != null && item.getName().toLowerCase().contains(query))) {
                            addVisibleOption(results, contentType, item.getId(), item.getName(), campaignId);
                        }
                    }
                }
            }
            case HANDOUT -> {
                if (campaignId != null) {
                    var items = handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId);
                    for (var item : items) {
                        if (query.isEmpty() || (item.getTitle() != null && item.getTitle().toLowerCase().contains(query))) {
                            addVisibleOption(results, contentType, item.getId(), item.getTitle(), campaignId);
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok(results);
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
        String url = destinationUrl(type, id, campaignIdOrNull);
        if (url != null) {
            option.put("url", url);
        }
        results.add(option);
    }

    private String destinationUrl(CampaignContentType type, UUID id, UUID campaignIdOrNull) {
        return switch (type) {
            case STATBLOCK -> "/library/statblocks/" + id;
            case EQUIPMENT_ITEM -> "/library/equipment/" + id;
            case MAGIC_ITEM -> "/library/magic-items/" + id;
            case ROLLABLE_TABLE -> "/library/tables/" + id;
            case NOTE -> campaignIdOrNull == null ? null
                    : "/campaigns/" + campaignIdOrNull + "/notes/" + id;
            case ENCOUNTER -> campaignIdOrNull == null ? null
                    : "/campaigns/" + campaignIdOrNull + "/encounters/" + id;
            case HANDOUT -> campaignIdOrNull == null ? null
                    : "/campaigns/" + campaignIdOrNull + "/handouts/" + id + "/present";
            default -> null;
        };
    }

    @PostMapping("/{id}/roll")
    public ResponseEntity<TableRollGroup> roll(@PathVariable UUID id,
                                                @RequestParam UUID campaignId,
                                                @RequestBody TableRollRequest request) {
        TableRollGroup group = rollService.roll(campaignId, id, request);
        return ResponseEntity.ok(group);
    }
}
