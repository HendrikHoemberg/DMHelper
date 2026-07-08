package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class EncounterService {

    private final EncounterRepository encounterRepo;
    private final CampaignRepository campaignRepo;
    private final EntityManager em;
    private final GameMapRepository mapRepo;

    public EncounterService(EncounterRepository encounterRepo, CampaignRepository campaignRepo,
                            EntityManager em, GameMapRepository mapRepo) {
        this.encounterRepo = encounterRepo;
        this.campaignRepo = campaignRepo;
        this.em = em;
        this.mapRepo = mapRepo;
    }

    public record CreateRequest(String name, UUID mapId) {}

    public record UpdateRequest(String name, UUID mapId, String status, String lairActionName,
                                String lairActionDescription) {}

    public record EncounterDto(UUID id, UUID campaignId, UUID mapId, String name, String status,
                               int round, int activeTurnIndex, int combatantCount,
                               String lairActionName, String lairActionDescription) {}

    static EncounterDto toDto(Encounter e) {
        return new EncounterDto(e.getId(), e.getCampaign().getId(),
                e.getMap() != null ? e.getMap().getId() : null,
                e.getName(), e.getStatus().name(), e.getRound(), e.getActiveTurnIndex(),
                0,
                e.getLairActionName(), e.getLairActionDescription());
    }

    public EncounterDto create(UUID campaignId, CreateRequest req) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName(req.name());
        if (req.mapId() != null) {
            GameMap map = mapRepo.findById(req.mapId())
                    .orElseThrow(() -> new NotFoundException("Map not found: " + req.mapId()));
            e.setMap(map);
        }
        return toDto(encounterRepo.save(e));
    }

    public EncounterDto update(UUID id, UpdateRequest req) {
        Encounter e = findEntityById(id);
        e.setName(req.name());
        if (req.mapId() != null) {
            GameMap map = mapRepo.findById(req.mapId())
                    .orElseThrow(() -> new NotFoundException("Map not found: " + req.mapId()));
            e.setMap(map);
        } else {
            e.setMap(null);
        }
        if (req.status() != null) {
            e.setStatus(Encounter.Status.valueOf(req.status()));
        }
        e.setLairActionName(req.lairActionName());
        e.setLairActionDescription(req.lairActionDescription());
        return toDto(encounterRepo.save(e));
    }

    public void delete(UUID id) {
        encounterRepo.delete(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public EncounterDto getById(UUID id) {
        return toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<EncounterDto> list(UUID campaignId) {
        return encounterRepo.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .map(EncounterService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EncounterDto> findActiveByCampaignId(UUID campaignId) {
        return encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .map(EncounterService::toDto);
    }

    public EncounterDto activate(UUID id) {
        Encounter e = findEntityById(id);
        e.setStatus(Encounter.Status.ACTIVE);
        e.setRound(1);
        return toDto(encounterRepo.save(e));
    }

    public EncounterDto endEncounter(UUID id) {
        Encounter e = findEntityById(id);
        e.setStatus(Encounter.Status.DONE);
        return toDto(encounterRepo.save(e));
    }

    Encounter findEntityById(UUID id) {
        return encounterRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
    }
}
