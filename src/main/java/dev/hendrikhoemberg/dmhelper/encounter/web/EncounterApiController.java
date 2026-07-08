package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.UpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
}
