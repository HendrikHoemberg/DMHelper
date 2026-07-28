package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

@Service
@Transactional
public class TokenService {

    private final TokenRepository repository;
    private final GameMapRepository mapRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final CombatantRepository combatantRepository;

    public TokenService(TokenRepository repository, GameMapRepository mapRepository,
                        PartyMemberRepository partyMemberRepository,
                        CombatantRepository combatantRepository) {
        this.repository = repository;
        this.mapRepository = mapRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.combatantRepository = combatantRepository;
    }

    public record TokenDto(UUID id, String name, String kind, int positionX, int positionY,
                           int sizeCols, int sizeRows, String color, boolean hidden,
                           Integer currentHp, Integer maxHp, boolean bloodied,
                           boolean dead, UUID statBlockId, UUID partyMemberId,
                           String notes, String icon, List<UUID> combatantIds) {}

    public record TokenCreateRequest(String name, String kind, int positionX, int positionY,
                                     int sizeCols, int sizeRows, String color, boolean hidden,
                                     Integer currentHp, Integer maxHp, UUID statBlockId, UUID partyMemberId) {}

    public record TokenMoveRequest(int positionX, int positionY) {}

    public record TokenHpRequest(int currentHp) {}

    public record TokenDeadRequest(boolean dead) {}

    public static TokenDto toDto(Token t) {
        boolean bloodied = t.getCurrentHp() != null && t.getMaxHp() != null
                && t.getMaxHp() > 0 && t.getCurrentHp() <= t.getMaxHp() / 2;
        return new TokenDto(t.getId(), t.getName(), t.getKind(),
                t.getPositionX(), t.getPositionY(), t.getSizeCols(), t.getSizeRows(),
                t.getColor(), t.isHidden(), t.getCurrentHp(), t.getMaxHp(), bloodied,
                t.isDead(), t.getStatBlock() != null ? t.getStatBlock().getId() : null,
                t.getPartyMember() != null ? t.getPartyMember().getId() : null,
                t.getNotes(), t.getIcon(), List.of());
    }

    @Transactional(readOnly = true)
    public List<TokenDto> findByMapId(UUID mapId) {
        return repository.findByMapIdOrderByNameAsc(mapId).stream()
                .map(token -> {
                    TokenDto dto = toDto(token);
                    return new TokenDto(
                            dto.id(), dto.name(), dto.kind(), dto.positionX(), dto.positionY(),
                            dto.sizeCols(), dto.sizeRows(), dto.color(), dto.hidden(),
                            dto.currentHp(), dto.maxHp(), dto.bloodied(), dto.dead(),
                            dto.statBlockId(), dto.partyMemberId(), dto.notes(), dto.icon(),
                            combatantRepository.findByTokenId(token.getId()).stream()
                                    .map(combatant -> combatant.getId()).toList());
                }).toList();
    }

    @Transactional(readOnly = true)
    public Token findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found: " + id));
    }

    public TokenDto create(UUID mapId, TokenCreateRequest req) {
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
        t.setCurrentHp(req.currentHp());
        t.setMaxHp(req.maxHp());
        return toDto(repository.save(t));
    }

    public TokenDto move(UUID id, TokenMoveRequest req) {
        Token t = findEntityById(id);
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        return toDto(repository.save(t));
    }

    public TokenDto updateHp(UUID id, TokenHpRequest req) {
        Token t = findEntityById(id);
        t.setCurrentHp(req.currentHp());
        return toDto(repository.save(t));
    }

    public TokenDto update(UUID id, TokenCreateRequest req) {
        Token t = findEntityById(id);
        if (req.name() != null) t.setName(req.name());
        if (req.kind() != null) t.setKind(req.kind());
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        if (req.sizeCols() > 0) t.setSizeCols(req.sizeCols());
        if (req.sizeRows() > 0) t.setSizeRows(req.sizeRows());
        if (req.color() != null) t.setColor(req.color());
        t.setHidden(req.hidden());
        t.setCurrentHp(req.currentHp());
        t.setMaxHp(req.maxHp());
        return toDto(repository.save(t));
    }

    public void delete(UUID id) {
        repository.delete(findEntityById(id));
    }

    public TokenDto duplicate(UUID id, int offsetX, int offsetY) {
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
        copy.setCurrentHp(original.getCurrentHp());
        copy.setMaxHp(original.getMaxHp());
        copy.setStatBlock(original.getStatBlock());
        copy.setPartyMember(original.getPartyMember());
        copy.setDead(original.isDead());
        return toDto(repository.save(copy));
    }

    public TokenDto markDead(UUID id, boolean dead) {
        Token t = findEntityById(id);
        t.setDead(dead);
        return toDto(repository.save(t));
    }

    public List<TokenDto> addPartyToMap(UUID mapId) {
        GameMap map = mapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
        List<PartyMember> members = partyMemberRepository
                .findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(map.getCampaign().getId());
        List<Token> existingTokens = repository.findByMapIdOrderByNameAsc(mapId);
        var representedPartyMembers = new HashSet<UUID>();
        int slot = 0;
        for (Token existing : existingTokens) {
            if (existing.getPartyMember() != null) {
                representedPartyMembers.add(existing.getPartyMember().getId());
                slot++;
            }
        }
        for (PartyMember pm : members) {
            if (!representedPartyMembers.add(pm.getId())) continue;
            Token t = new Token();
            t.setMap(map);
            t.setName(pm.getCharacterName());
            t.setKind("PC");
            int col = slot % map.getGridWidth();
            int row = Math.min(map.getGridHeight() - 1, slot / map.getGridWidth());
            t.setPositionX(col * map.getCellSizePx());
            t.setPositionY(row * map.getCellSizePx());
            t.setSizeCols(1);
            t.setSizeRows(1);
            t.setColor("#4a9eff");
            t.setHidden(false);
            t.setCurrentHp(pm.getCurrentHp());
            t.setMaxHp(pm.getMaxHp());
            t.setPartyMember(pm);
            repository.save(t);
            slot++;
        }
        return findByMapId(mapId);
    }
}
