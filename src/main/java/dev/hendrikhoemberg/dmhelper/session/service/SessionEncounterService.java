package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.EncounterReadinessDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SessionEncounterService {

    private final EncounterRepository encounterRepo;
    private final EncounterService encounterService;
    private final EncounterPlacementService placements;
    private final CampaignSessionRepository sessions;
    private final CampaignRepository campaignRepo;
    private final ApplicationEventPublisher events;

    public enum ActiveEncounterDisposition { SUSPEND, END }

    public record EncounterActivationDto(
        UUID encounterId, UUID workspaceMapId, String status,
        UUID replacedEncounterId, String replacedEncounterStatus) {}

    public SessionEncounterService(EncounterRepository encounterRepo,
                                    EncounterService encounterService,
                                    EncounterPlacementService placements,
                                    CampaignSessionRepository sessions,
                                    CampaignRepository campaignRepo,
                                    ApplicationEventPublisher events) {
        this.encounterRepo = encounterRepo;
        this.encounterService = encounterService;
        this.placements = placements;
        this.sessions = sessions;
        this.campaignRepo = campaignRepo;
        this.events = events;
    }

    public EncounterActivationDto activate(UUID campaignId, UUID encounterId, ActiveEncounterDisposition disposition) {
        Encounter requested = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found"));
        if (!requested.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Encounter does not belong to campaign");
        }

        EncounterReadinessDto readiness = placements.readiness(encounterId);
        if (!readiness.canRun()) {
            throw new EncounterNotReadyException(readiness);
        }

        Encounter active = encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .filter(a -> !a.getId().equals(encounterId))
                .orElse(null);
        if (active != null && disposition == null) {
            throw new ActiveEncounterReplacementRequiredException(active.getId(), active.getName());
        }

        Encounter replaced = active;
        if (replaced != null && disposition == ActiveEncounterDisposition.SUSPEND) {
            encounterService.suspend(replaced.getId());
        }
        if (replaced != null && disposition == ActiveEncounterDisposition.END) {
            encounterService.endEncounter(replaced.getId());
        }

        Encounter activated = switch (requested.getStatus()) {
            case ACTIVE -> requested;
            case SUSPENDED -> encounterService.resume(encounterId);
            default -> encounterService.activateFresh(encounterId);
        };

        var session = sessions.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("No active campaign session"));
        session.setWorkspaceMap(activated.getMap());
        sessions.save(session);

        events.publishEvent(new SessionEncounterActivated(campaignId, encounterId));

        return new EncounterActivationDto(
                encounterId,
                activated.getMap() != null ? activated.getMap().getId() : null,
                activated.getStatus().name(),
                replaced == null ? null : replaced.getId(),
                replaced == null ? null : replaced.getStatus().name());
    }
}
