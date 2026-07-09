package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.CreateSheetRequest;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.LevelUpRequest;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.SheetDto;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.SheetResourceDto;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.SheetSpellDto;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.UpdateSheetRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}")
public class SheetApiController {

    private final SheetService sheetService;

    public SheetApiController(SheetService sheetService) {
        this.sheetService = sheetService;
    }

    @GetMapping("/party/{memberId}/sheet")
    public ResponseEntity<SheetDto> getSheet(@PathVariable UUID campaignId,
                                             @PathVariable UUID memberId) {
        return ResponseEntity.ok(sheetService.getSheetDtoByPartyMemberId(memberId));
    }

    @PutMapping("/party/{memberId}/sheet")
    public ResponseEntity<SheetDto> upsertSheet(@PathVariable UUID campaignId,
                                                 @PathVariable UUID memberId,
                                                 @RequestBody CreateSheetRequest request) {
        if (sheetService.hasSheet(memberId)) {
            SheetDto existing = sheetService.getSheetDtoByPartyMemberId(memberId);
            UpdateSheetRequest updateReq = new UpdateSheetRequest(
                request.abilityScores(), request.classLevels(),
                request.proficiencies(),
                request.speciesId(), request.backgroundId(),
                request.featRefs(), null, request.xp()
            );
            return ResponseEntity.ok(sheetService.updateSheet(existing.id(), updateReq));
        } else {
            return ResponseEntity.ok(sheetService.createSheet(request));
        }
    }

    @DeleteMapping("/party/{memberId}/sheet")
    public ResponseEntity<Void> deleteSheet(@PathVariable UUID campaignId,
                                            @PathVariable UUID memberId) {
        SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
        sheetService.deleteSheet(dto.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/party/{memberId}/sheet/level-up")
    public ResponseEntity<SheetDto> levelUp(@PathVariable UUID campaignId,
                                             @PathVariable UUID memberId,
                                             @RequestBody LevelUpRequest request) {
        SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
        return ResponseEntity.ok(sheetService.levelUp(dto.id(), request));
    }

    @PostMapping("/party/rest")
    public ResponseEntity<Map<String, Object>> batchRest(
            @PathVariable UUID campaignId,
            @RequestParam String type,
            @RequestParam List<UUID> members,
            @RequestParam(defaultValue = "1") int hitDiceSpent) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (UUID memberId : members) {
            try {
                if ("SHORT".equalsIgnoreCase(type)) {
                    sheetService.shortRest(
                        sheetService.getSheetDtoByPartyMemberId(memberId).id(), hitDiceSpent);
                } else {
                    sheetService.longRest(
                        sheetService.getSheetDtoByPartyMemberId(memberId).id(), 0);
                }
                results.add(Map.of("id", memberId.toString(), "status", "ok"));
            } catch (Exception e) {
                results.add(Map.of("id", memberId.toString(), "status", "error",
                    "message", e.getMessage()));
            }
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    @PostMapping("/party/xp")
    public ResponseEntity<Map<String, Object>> awardXp(
            @PathVariable UUID campaignId,
            @RequestBody Map<String, Object> body) {
        int amount = ((Number) body.get("amount")).intValue();
        String split = (String) body.getOrDefault("split", "EQUAL");

        if ("EQUAL".equals(split)) {
            @SuppressWarnings("unchecked")
            List<String> memberIds = (List<String>) body.get("memberIds");
            if (memberIds == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "memberIds required"));
            }
            int perMember = amount / memberIds.size();
            List<Map<String, Object>> results = new ArrayList<>();
            for (String id : memberIds) {
                try {
                    UUID memberId = UUID.fromString(id);
                    SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
                    SheetDto result = sheetService.awardXp(dto.id(), perMember);
                    results.add(Map.of("id", id, "xp", result.xp()));
                } catch (Exception e) {
                    results.add(Map.of("id", id, "error", e.getMessage()));
                }
            }
            return ResponseEntity.ok(Map.of("members", results));
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> awards = (Map<String, Object>) body.get("awards");
            if (awards == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "awards required for CUSTOM"));
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map.Entry<String, Object> entry : awards.entrySet()) {
                try {
                    UUID memberId = UUID.fromString(entry.getKey());
                    int xpAmount = ((Number) entry.getValue()).intValue();
                    SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
                    SheetDto result = sheetService.awardXp(dto.id(), xpAmount);
                    results.add(Map.of("id", entry.getKey(), "xp", result.xp()));
                } catch (Exception e) {
                    results.add(Map.of("id", entry.getKey(), "error", e.getMessage()));
                }
            }
            return ResponseEntity.ok(Map.of("members", results));
        }
    }

    @GetMapping("/party/{memberId}/sheet/resources")
    public ResponseEntity<List<SheetResourceDto>> listResources(
            @PathVariable UUID campaignId, @PathVariable UUID memberId) {
        SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
        return ResponseEntity.ok(dto.resources());
    }

    @PutMapping("/party/{memberId}/sheet/resources/{resourceId}")
    public ResponseEntity<SheetResourceDto> updateResource(
            @PathVariable UUID campaignId, @PathVariable UUID memberId,
            @PathVariable UUID resourceId,
            @RequestBody Map<String, Integer> body) {
        int currentUses = body.getOrDefault("currentUses", 0);
        return ResponseEntity.ok(sheetService.updateResource(resourceId, currentUses));
    }

    @PostMapping("/party/{memberId}/sheet/resources")
    public ResponseEntity<SheetResourceDto> createResource(
            @PathVariable UUID campaignId, @PathVariable UUID memberId,
            @RequestBody Map<String, Object> body) {
        UUID sheetId = sheetService.getSheetDtoByPartyMemberId(memberId).id();
        String name = (String) body.get("name");
        int maxUses = ((Number) body.get("maxUses")).intValue();
        String resetRule = (String) body.getOrDefault("resetRule", "LONG_REST");
        return ResponseEntity.ok(sheetService.createResource(sheetId, name, maxUses, resetRule));
    }

    @GetMapping("/party/{memberId}/sheet/spells")
    public ResponseEntity<List<SheetSpellDto>> listSpells(
            @PathVariable UUID campaignId, @PathVariable UUID memberId) {
        SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
        return ResponseEntity.ok(dto.spells());
    }

    @PostMapping("/party/{memberId}/sheet/spells")
    public ResponseEntity<SheetSpellDto> addSpell(
            @PathVariable UUID campaignId, @PathVariable UUID memberId,
            @RequestBody Map<String, Object> body) {
        UUID sheetId = sheetService.getSheetDtoByPartyMemberId(memberId).id();
        UUID spellId = UUID.fromString((String) body.get("spellId"));
        boolean prepared = (boolean) body.getOrDefault("prepared", false);
        String sourceClass = (String) body.get("sourceClass");
        return ResponseEntity.ok(sheetService.addSpell(sheetId, spellId, prepared, sourceClass));
    }

    @DeleteMapping("/party/{memberId}/sheet/spells/{spellRefId}")
    public ResponseEntity<Void> removeSpell(
            @PathVariable UUID campaignId, @PathVariable UUID memberId,
            @PathVariable UUID spellRefId) {
        sheetService.removeSpell(spellRefId);
        return ResponseEntity.noContent().build();
    }
}
