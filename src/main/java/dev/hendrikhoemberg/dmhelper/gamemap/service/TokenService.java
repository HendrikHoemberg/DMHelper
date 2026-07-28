package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TokenService {

    private final TokenRepository repository;
    private final GameMapRepository mapRepository;

    public TokenService(TokenRepository repository, GameMapRepository mapRepository) {
        this.repository = repository;
        this.mapRepository = mapRepository;
    }

    public record MapMarkerDto(UUID id, String name, String kind, int positionX, int positionY,
                               int sizeCols, int sizeRows, String color, boolean hidden,
                               UUID statBlockId, UUID partyMemberId,
                               String notes, String icon) {}

    public record MapMarkerRequest(String name, String kind, int positionX, int positionY,
                                   int sizeCols, int sizeRows, String color, boolean hidden,
                                   UUID statBlockId, UUID partyMemberId) {}

    public record TokenMoveRequest(int positionX, int positionY) {}

    public static MapMarkerDto toMarkerDto(Token t) {
        return new MapMarkerDto(t.getId(), t.getName(), t.getKind(),
                t.getPositionX(), t.getPositionY(), t.getSizeCols(), t.getSizeRows(),
                t.getColor(), t.isHidden(), t.getStatBlock() != null ? t.getStatBlock().getId() : null,
                t.getPartyMember() != null ? t.getPartyMember().getId() : null,
                t.getNotes(), t.getIcon());
    }

    @Transactional(readOnly = true)
    public List<MapMarkerDto> findByMapId(UUID mapId) {
        return repository.findByMapIdOrderByNameAsc(mapId).stream()
                .map(TokenService::toMarkerDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Token findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found: " + id));
    }

    public MapMarkerDto create(UUID mapId, MapMarkerRequest req) {
        GameMap map = mapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
        Token t = new Token();
        t.setMap(map);
        t.setName(req.name() != null ? req.name() : "Token");
        t.setKind(req.kind() != null ? req.kind() : "NPC");
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        t.setSizeCols(req.sizeCols() > 0 ? req.sizeCols() : 1);
        t.setSizeRows(req.sizeRows() > 0 ? req.sizeRows() : 1);
        t.setColor(req.color() != null ? req.color() : "#7b68ee");
        t.setHidden(req.hidden());
        return toMarkerDto(repository.save(t));
    }

    public MapMarkerDto move(UUID id, TokenMoveRequest req) {
        Token t = findEntityById(id);
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        return toMarkerDto(repository.save(t));
    }

    public MapMarkerDto update(UUID id, MapMarkerRequest req) {
        Token t = findEntityById(id);
        if (req.name() != null) t.setName(req.name());
        if (req.kind() != null) t.setKind(req.kind());
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        if (req.sizeCols() > 0) t.setSizeCols(req.sizeCols());
        if (req.sizeRows() > 0) t.setSizeRows(req.sizeRows());
        if (req.color() != null) t.setColor(req.color());
        t.setHidden(req.hidden());
        return toMarkerDto(repository.save(t));
    }

    public void delete(UUID id) {
        repository.delete(findEntityById(id));
    }

    public MapMarkerDto duplicate(UUID id, int offsetX, int offsetY) {
        Token original = findEntityById(id);
        Token copy = new Token();
        copy.setMap(original.getMap());
        copy.setName(original.getName());
        copy.setKind(original.getKind());
        copy.setPositionX(original.getPositionX() + offsetX);
        copy.setPositionY(original.getPositionY() + offsetY);
        copy.setSizeCols(original.getSizeCols());
        copy.setSizeRows(original.getSizeRows());
        copy.setColor(original.getColor());
        copy.setHidden(original.isHidden());
        copy.setStatBlock(original.getStatBlock());
        copy.setPartyMember(original.getPartyMember());
        return toMarkerDto(repository.save(copy));
    }
}
