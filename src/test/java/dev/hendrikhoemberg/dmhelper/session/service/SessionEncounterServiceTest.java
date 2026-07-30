package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.EncounterReadinessDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.ReadinessVerdict;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionEncounterServiceTest {

    @Mock private EncounterRepository encounterRepo;
    @Mock private EncounterService encounterService;
    @Mock private EncounterPlacementService placements;
    @Mock private CampaignSessionRepository sessions;
    @Mock private CampaignRepository campaignRepo;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private SessionEncounterService service;

    @Captor private ArgumentCaptor<SessionEncounterActivated> eventCaptor;

    private UUID campaignId;
    private UUID encounterId;
    private UUID mapId;
    private Campaign campaign;
    private GameMap map;
    private Encounter encounter;
    private CampaignSession session;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        encounterId = UUID.randomUUID();
        mapId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);

        map = new GameMap();
        map.setId(mapId);

        encounter = new Encounter();
        encounter.setId(encounterId);
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Goblin Ambush");
        encounter.setStatus(Encounter.Status.PLANNED);

        session = new CampaignSession();
        session.setCampaign(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setWorkspaceMap(null);
    }

    @Test
    void freshActivationSelectsEncounterMap() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(campaignId, encounterId, null);

        assertThat(result.encounterId()).isEqualTo(encounterId);
        assertThat(result.workspaceMapId()).isEqualTo(mapId);
        assertThat(result.status()).isEqualTo("PLANNED");
        assertThat(result.replacedEncounterId()).isNull();
        assertThat(result.replacedEncounterStatus()).isNull();
        verify(sessions).save(session);
        assertThat(session.getWorkspaceMap()).isSameAs(map);
    }

    @Test
    void replacingActiveEncounterWithoutDispositionIsRejected() {
        Encounter active = new Encounter();
        active.setId(UUID.randomUUID());
        active.setCampaign(campaign);
        active.setName("Active Fight");
        active.setStatus(Encounter.Status.ACTIVE);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.activate(campaignId, encounterId, null))
                .isInstanceOf(ActiveEncounterReplacementRequiredException.class)
                .satisfies(ex -> {
                    var e = (ActiveEncounterReplacementRequiredException) ex;
                    assertThat(e.getActiveEncounterId()).isEqualTo(active.getId());
                    assertThat(e.getActiveEncounterName()).isEqualTo("Active Fight");
                });

        verify(encounterService, never()).suspend(any());
        verify(encounterService, never()).endEncounter(any());
        verify(encounterService, never()).activateFresh(any());
        verify(encounterService, never()).resume(any());
    }

    @Test
    void replacingActiveEncounterWithSuspendSuspendsIt() {
        Encounter active = new Encounter();
        active.setId(UUID.randomUUID());
        active.setCampaign(campaign);
        active.setName("Active Fight");
        active.setStatus(Encounter.Status.ACTIVE);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(active));
        when(encounterService.suspend(active.getId())).thenReturn(active);
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(
                campaignId, encounterId, SessionEncounterService.ActiveEncounterDisposition.SUSPEND);

        assertThat(result.replacedEncounterId()).isEqualTo(active.getId());
        assertThat(result.replacedEncounterStatus()).isEqualTo("ACTIVE");
        verify(encounterService).suspend(active.getId());
        verify(encounterService, never()).endEncounter(any());
    }

    @Test
    void replacingActiveEncounterWithEndEndsIt() {
        Encounter active = new Encounter();
        active.setId(UUID.randomUUID());
        active.setCampaign(campaign);
        active.setName("Active Fight");
        active.setStatus(Encounter.Status.ACTIVE);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(active));
        when(encounterService.endEncounter(active.getId())).thenReturn(null);
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(
                campaignId, encounterId, SessionEncounterService.ActiveEncounterDisposition.END);

        assertThat(result.replacedEncounterId()).isEqualTo(active.getId());
        verify(encounterService).endEncounter(active.getId());
        verify(encounterService, never()).suspend(any());
    }

    @Test
    void resumeSuspendedEncounter() {
        encounter.setStatus(Encounter.Status.SUSPENDED);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(encounterService.resume(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(campaignId, encounterId, null);

        assertThat(result.status()).isEqualTo("SUSPENDED");
        verify(encounterService).resume(encounterId);
        verify(encounterService, never()).activateFresh(any());
    }

    @Test
    void notReadyEncounterThrowsEncounterNotReady() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        EncounterReadinessDto notReady = new EncounterReadinessDto(
                encounterId, false, ReadinessVerdict.BLOCKED, null, 0, 0, 0, List.of());
        when(placements.readiness(encounterId)).thenReturn(notReady);

        assertThatThrownBy(() -> service.activate(campaignId, encounterId, null))
                .isInstanceOf(EncounterNotReadyException.class)
                .satisfies(ex -> assertThat(((EncounterNotReadyException) ex).getReadiness().canRun()).isFalse());

        verify(encounterService, never()).activateFresh(any());
        verify(encounterService, never()).resume(any());
    }

    @Test
    void encounterNotInCampaignIsRejected() {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setId(UUID.randomUUID());
        encounter.setCampaign(otherCampaign);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));

        assertThatThrownBy(() -> service.activate(campaignId, encounterId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to campaign");
    }

    @Test
    void missingEncounterThrowsNotFound() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(campaignId, encounterId, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void publishEventOnActivation() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        service.activate(campaignId, encounterId, null);

        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().campaignId()).isEqualTo(campaignId);
        assertThat(eventCaptor.getValue().encounterId()).isEqualTo(encounterId);
    }

    @Test
    void activatingSameEncounterTwiceDoesNotReplace() {
        encounter.setStatus(Encounter.Status.ACTIVE);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(encounter));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(campaignId, encounterId, null);

        assertThat(result.replacedEncounterId()).isNull();
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(encounterService, never()).suspend(any());
        verify(encounterService, never()).endEncounter(any());
        verify(encounterService, never()).activateFresh(any());
        verify(encounterService, never()).resume(any());
    }

    @Test
    void missingSessionThrowsIllegalState() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(campaignId, encounterId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active campaign session");
    }

    @Test
    void workspaceMapNullWhenEncounterHasNoMap() {
        encounter.setMap(null);

        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(encounterService.activateFresh(encounterId)).thenReturn(encounter);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(campaignId, encounterId, null);

        assertThat(result.workspaceMapId()).isNull();
    }

    @Test
    void freshActivationSetsRoundToZeroAndCombatPhaseToSetup() {
        when(encounterRepo.findById(encounterId)).thenReturn(Optional.of(encounter));
        when(placements.readiness(encounterId)).thenReturn(ready());
        when(encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            encounter.setStatus(Encounter.Status.ACTIVE);
            encounter.setCombatPhase(Encounter.CombatPhase.SETUP);
            encounter.setRound(0);
            encounter.setActiveTurnIndex(-1);
            return encounter;
        }).when(encounterService).activateFresh(encounterId);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        SessionEncounterService.EncounterActivationDto result = service.activate(campaignId, encounterId, null);

        assertThat(encounter.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
        assertThat(encounter.getCombatPhase()).isEqualTo(Encounter.CombatPhase.SETUP);
        assertThat(encounter.getRound()).isZero();
        assertThat(encounter.getActiveTurnIndex()).isEqualTo(-1);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    private static EncounterReadinessDto ready() {
        return new EncounterReadinessDto(UUID.randomUUID(), true, ReadinessVerdict.RUNNABLE, UUID.randomUUID(),
                1, 1, 0, List.of());
    }
}
