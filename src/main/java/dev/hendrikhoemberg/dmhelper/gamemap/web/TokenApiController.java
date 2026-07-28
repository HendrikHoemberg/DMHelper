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

    @GetMapping({"/maps/{mapId}/tokens", "/maps/{mapId}/markers"})
    public List<MapMarkerDto> listTokens(@PathVariable UUID mapId) {
        return service.findByMapId(mapId);
    }

    @PostMapping("/maps/{mapId}/tokens")
    public ResponseEntity<MapMarkerDto> createToken(@PathVariable UUID mapId,
                                                    @RequestBody MapMarkerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(mapId, request));
    }

    @GetMapping("/tokens/{id}")
    public MapMarkerDto getToken(@PathVariable UUID id) {
        return TokenService.toMarkerDto(service.findEntityById(id));
    }

    @PatchMapping("/tokens/{id}/move")
    public MapMarkerDto moveToken(@PathVariable UUID id, @RequestBody TokenMoveRequest request) {
        return service.move(id, request);
    }

    @PutMapping("/tokens/{id}")
    public MapMarkerDto updateToken(@PathVariable UUID id, @RequestBody MapMarkerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens/{id}/duplicate")
    public MapMarkerDto duplicateToken(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "48") int offsetX,
                                       @RequestParam(defaultValue = "48") int offsetY) {
        return service.duplicate(id, offsetX, offsetY);
    }
}
