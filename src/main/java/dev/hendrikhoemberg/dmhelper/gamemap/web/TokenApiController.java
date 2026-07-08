package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TokenApiController {

    private final TokenService service;

    public TokenApiController(TokenService service) {
        this.service = service;
    }

    @GetMapping("/maps/{mapId}/tokens")
    public List<TokenDto> listTokens(@PathVariable UUID mapId) {
        return service.findByMapId(mapId);
    }

    @PostMapping("/maps/{mapId}/tokens")
    public ResponseEntity<TokenDto> createToken(@PathVariable UUID mapId,
                                                @RequestBody TokenCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(mapId, request));
    }

    @GetMapping("/tokens/{id}")
    public TokenDto getToken(@PathVariable UUID id) {
        return TokenService.toDto(service.findEntityById(id));
    }

    @PatchMapping("/tokens/{id}/move")
    public TokenDto moveToken(@PathVariable UUID id, @RequestBody TokenMoveRequest request) {
        return service.move(id, request);
    }

    @PatchMapping("/tokens/{id}/hp")
    public TokenDto updateHp(@PathVariable UUID id, @RequestBody TokenHpRequest request) {
        return service.updateHp(id, request);
    }

    @PutMapping("/tokens/{id}")
    public TokenDto updateToken(@PathVariable UUID id, @RequestBody TokenCreateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens/{id}/duplicate")
    public TokenDto duplicateToken(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "48") int offsetX,
                                   @RequestParam(defaultValue = "48") int offsetY) {
        return service.duplicate(id, offsetX, offsetY);
    }

    @PatchMapping("/tokens/{id}/dead")
    public TokenDto markDead(@PathVariable UUID id, @RequestBody TokenDeadRequest request) {
        return service.markDead(id, request.dead());
    }

    @PostMapping("/maps/{mapId}/tokens/add-party")
    public List<TokenDto> addPartyToMap(@PathVariable UUID mapId) {
        return service.addPartyToMap(mapId);
    }
}
