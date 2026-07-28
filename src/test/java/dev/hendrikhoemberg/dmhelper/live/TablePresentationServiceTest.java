package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntry;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TablePresentationServiceTest {

    @Mock PlayerSafeProjectionService projection;
    @Mock GameMapRepository maps;
    @Mock EncounterRepository encounters;
    @Mock CombatantRepository combatants;
    @Mock HandoutRepository handouts;
    @Mock CampaignSessionRepository sessions;
    @Mock SessionAuditEntryRepository auditEntryRepository;

    private TablePresentationService service;
    private UUID campaignId;
    private CampaignSession session;
    private GameMap map;

    @BeforeEach
    void setUp() {
        service = new TablePresentationService(projection, maps, encounters, combatants, handouts, sessions, auditEntryRepository);
        campaignId = UUID.randomUUID();
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        session = CampaignSession.idle(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        map = new GameMap();
        map.setId(UUID.randomUUID());
        map.setCampaign(campaign);
        map.setName("Lower Crypt");
        session.setPresentationMode(CampaignSession.PresentationMode.MAP);
        session.setPresentedMap(map);
    }

    @Test
    void restoresTheLatestOpenPresentationOnApplicationStartup() {
        when(sessions.findFirstByStatusNotOrderByUpdatedAtDesc(CampaignSession.Status.IDLE))
                .thenReturn(Optional.of(session));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(encounters.findByCampaignIdAndStatus(campaignId,
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(projection.projectTokens(eq(map), isNull())).thenReturn(List.of());

        service.restoreOnStartup();

        assertThat(service.getCurrentState().mode()).isEqualTo("MAP");
        assertThat(service.getCurrentState().map().mapId()).isEqualTo(map.getId().toString());
    }

    @Test
    void refreshFromAnotherCampaignCannotReplaceTheCurrentTable() {
        UUID otherCampaignId = UUID.randomUUID();
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));
        when(encounters.findByCampaignIdAndStatus(campaignId,
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(projection.projectTokens(eq(map), isNull())).thenReturn(List.of());
        service.presentMap(campaignId, map.getId());

        LiveTableState unchanged = service.broadcastCurrentState(otherCampaignId);

        assertThat(unchanged.map().mapId()).isEqualTo(map.getId().toString());
        verify(sessions, never()).findByCampaignId(otherCampaignId);
    }

    @Test
    void deletingThePresentedContentCurtainsAndBroadcastsImmediately() {
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));
        when(encounters.findByCampaignIdAndStatus(campaignId,
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(projection.projectTokens(eq(map), isNull())).thenReturn(List.of());
        service.presentMap(campaignId, map.getId());
        AtomicInteger broadcasts = new AtomicInteger();
        service.setOnStateChange(broadcasts::incrementAndGet);

        service.onPresentationInvalidated(
                new dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.PresentationInvalidated(
                        campaignId, map.getId(), false));

        assertThat(service.getCurrentState().mode()).isEqualTo("CURTAIN");
        assertThat(broadcasts).hasValue(1);
    }

    @Test
    void deletionInvalidationIsAppliedOnlyAfterTheDatabaseCommit() throws NoSuchMethodException {
        var listener = TablePresentationService.class.getMethod(
                "onPresentationInvalidated",
                dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.PresentationInvalidated.class);

        assertThat(listener.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void restoringDmOnlyHandoutLowersCurtainAndClearsPersistedPresentation() {
        Handout handout = new Handout();
        handout.setId(UUID.randomUUID());
        handout.setCampaign(session.getCampaign());
        handout.setDmOnly(true);
        handout.setSafetyClassification(Handout.SafetyClassification.DM_SOURCE);
        session.setPresentationMode(CampaignSession.PresentationMode.HANDOUT);
        session.setPresentedMap(null);
        session.setPresentedHandout(handout);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        LiveTableState restored = service.restorePresentation(campaignId);

        assertThat(restored.mode()).isEqualTo("CURTAIN");
        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(session.getPresentedHandout()).isNull();
        verify(sessions).save(session);
    }

    private Handout createHandout(UUID id, Handout.SafetyClassification classification) {
        Handout h = new Handout();
        h.setId(id);
        h.setCampaign(session.getCampaign());
        h.setTitle("Test " + classification.name());
        h.setContentType("image/png");
        h.setSafetyClassification(classification);
        h.setDmOnly(!classification.isPresentable());
        return h;
    }

    @Test
    void ordinaryPresentationRejectsDmSource() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presentHandout(campaignId, handout.getId(), false, null))
                .isInstanceOf(dev.hendrikhoemberg.dmhelper.common.NotFoundException.class);
    }

    @Test
    void ordinaryPresentationRejectsUnreviewed() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.UNREVIEWED);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presentHandout(campaignId, handout.getId(), false, null))
                .isInstanceOf(dev.hendrikhoemberg.dmhelper.common.NotFoundException.class);
    }

    @Test
    void ordinaryPresentationAcceptsPlayerSafe() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.PLAYER_SAFE);
        handout.setDmOnly(false);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        LiveTableState result = service.presentHandout(campaignId, handout.getId(), false, null);

        assertThat(result.mode()).isEqualTo("HANDOUT");
        assertThat(result.handout().id()).isEqualTo(handout.getId().toString());
        assertThat(result.handout().fileUrl()).isEqualTo("/player/files/" + handout.getId());
    }

    @Test
    void ordinaryPresentationAcceptsPlayerDerivative() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.PLAYER_DERIVATIVE);
        handout.setDmOnly(false);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        LiveTableState result = service.presentHandout(campaignId, handout.getId(), false, null);

        assertThat(result.mode()).isEqualTo("HANDOUT");
        assertThat(result.handout().id()).isEqualTo(handout.getId().toString());
    }

    @Test
    void overridePresentsUnsafeHandoutWithCorrectAcknowledgement() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        LiveTableState result = service.presentHandout(campaignId, handout.getId(),
                true, "I understand this may expose DM content");

        assertThat(result.mode()).isEqualTo("HANDOUT");
        assertThat(result.handout().id()).isEqualTo(handout.getId().toString());
        assertThat(result.handout().fileUrl()).isEqualTo("/player/files/" + handout.getId());
    }

    @Test
    void overrideRejectsWithoutEmergencyOverride() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presentHandout(campaignId, handout.getId(),
                false, "I understand this may expose DM content"))
                .isInstanceOf(dev.hendrikhoemberg.dmhelper.common.NotFoundException.class);
    }

    @Test
    void overrideRejectsWithWrongAcknowledgement() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presentHandout(campaignId, handout.getId(),
                true, "wrong acknowledgement"))
                .isInstanceOf(dev.hendrikhoemberg.dmhelper.common.NotFoundException.class);
    }

    @Test
    void overrideWritesAuditEntryWithCorrectDetails() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        service.presentHandout(campaignId, handout.getId(),
                true, "I understand this may expose DM content");

        ArgumentCaptor<SessionAuditEntry> captor = ArgumentCaptor.forClass(SessionAuditEntry.class);
        verify(auditEntryRepository).saveAndFlush(captor.capture());
        SessionAuditEntry entry = captor.getValue();

        assertThat(entry.getEntryType()).isEqualTo(SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE);
        assertThat(entry.getContentType()).isEqualTo("HANDOUT");
        assertThat(entry.getContentId()).isEqualTo(handout.getId());
        assertThat(entry.getDetails()).contains(handout.getTitle());
        assertThat(entry.getDetails()).contains(handout.getSafetyClassification().name());
    }

    @Test
    void overrideAuditDetailsRemainValidJsonForControlCharacters() throws Exception {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        handout.setTitle("Secret note\nsecond line\t\"quoted\"");
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        service.presentHandout(campaignId, handout.getId(),
                true, "I understand this may expose DM content");

        ArgumentCaptor<SessionAuditEntry> captor = ArgumentCaptor.forClass(SessionAuditEntry.class);
        verify(auditEntryRepository).saveAndFlush(captor.capture());
        var details = JsonMapper.builder().build().readTree(captor.getValue().getDetails());
        assertThat(details.get("title").asText()).isEqualTo(handout.getTitle());
        assertThat(details.get("classification").asText()).isEqualTo("DM_SOURCE");
    }

    @Test
    void overridePreservesClassification() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        service.presentHandout(campaignId, handout.getId(),
                true, "I understand this may expose DM content");

        assertThat(handout.getSafetyClassification()).isEqualTo(Handout.SafetyClassification.DM_SOURCE);
    }

    @Test
    void restoreLowersCurtainForReclassifiedHandout() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        session.setPresentationMode(CampaignSession.PresentationMode.HANDOUT);
        session.setPresentedMap(null);
        session.setPresentedHandout(handout);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        LiveTableState restored = service.restorePresentation(campaignId);

        assertThat(restored.mode()).isEqualTo("CURTAIN");
        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(session.getPresentedHandout()).isNull();
        verify(sessions).save(session);
    }

    @Test
    void previewHandoutReturnsStateWithoutMutation() {
        Handout handout = createHandout(UUID.randomUUID(), Handout.SafetyClassification.DM_SOURCE);
        when(handouts.findById(handout.getId())).thenReturn(Optional.of(handout));

        var preview = service.previewHandout(campaignId, handout.getId());

        assertThat(preview.state().mode()).isEqualTo("HANDOUT");
        assertThat(preview.state().handout().id()).isEqualTo(handout.getId().toString());
        assertThat(preview.state().handout().fileUrl())
                .isEqualTo("/api/v1/campaigns/" + campaignId + "/table/handouts/" + handout.getId() + "/preview-file");
        assertThat(preview.classification()).isEqualTo("DM_SOURCE");
        assertThat(preview.requiresOverride()).isTrue();
        verify(sessions, never()).findByCampaignId(any());
        verify(auditEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void broadcastIncludesCombatantSourcedTokensWhenEncounterIsActive() {
        var enc = new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
        enc.setId(UUID.randomUUID());
        enc.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE);
        enc.setActiveTurnIndex(0);

        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));
        when(encounters.findByCampaignIdAndStatus(campaignId,
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(enc));
        when(combatants.findByEncounterIdOrderBySortOrderAsc(enc.getId()))
                .thenReturn(List.of());
        when(projection.projectTokens(eq(map), eq(enc)))
                .thenReturn(List.of(new LiveTableState.TokenSnapshot(
                        "c1", "Goblin", "MONSTER", "#e74c3c",
                        0, 0, 1, 1, false, false, "COMBATANT", UUID.randomUUID())));

        service.presentMap(campaignId, map.getId());

        assertThat(service.getCurrentState().map().tokens()).hasSize(1);
        assertThat(service.getCurrentState().map().tokens().getFirst().source()).isEqualTo("COMBATANT");
    }

    @Test
    void broadcastExcludesHiddenCombatants() {
        var enc = new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
        enc.setId(UUID.randomUUID());
        enc.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE);
        enc.setActiveTurnIndex(0);

        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));
        when(encounters.findByCampaignIdAndStatus(campaignId,
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE))
                .thenReturn(Optional.of(enc));
        when(combatants.findByEncounterIdOrderBySortOrderAsc(enc.getId()))
                .thenReturn(List.of());
        when(projection.projectTokens(eq(map), eq(enc)))
                .thenReturn(List.of());

        service.presentMap(campaignId, map.getId());

        assertThat(service.getCurrentState().map().tokens()).isEmpty();
    }
}
