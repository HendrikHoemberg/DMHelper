package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAudioStateServiceTest {

    @Mock private Clock clock;
    @Mock private SessionAudioStateRepository stateRepository;
    @Mock private CampaignSessionRepository sessionRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private AudioCueResolver resolver;

    @InjectMocks private SessionAudioStateService service;

    @Captor private ArgumentCaptor<SessionAudioState> stateCaptor;

    private final Instant now = Instant.parse("2026-07-19T12:00:00Z");
    private final UUID campaignId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private Campaign campaign;
    private CampaignSession session;
    private SessionAudioState state;
    private AudioCue cue;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");

        session = new CampaignSession();
        session.setId(sessionId);
        session.setCampaign(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);

        state = new SessionAudioState();
        state.setSession(session);

        cue = new AudioCue();
        cue.setId(UUID.randomUUID());
        cue.setName("Test Cue");
    }

    @Test
    void createOrResetCreatesNewState() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.createOrReset(sessionId);

        verify(stateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getSession().getId()).isEqualTo(sessionId);
        assertThat(stateCaptor.getValue().getSwitchMode()).isEqualTo(AudioSwitchMode.AUTOMATIC);
        assertThat(stateCaptor.getValue().isMuted()).isFalse();
    }

    @Test
    void createOrResetResetsExistingState() {
        SessionAudioState existing = new SessionAudioState();
        existing.setSession(session);
        existing.setManualOverrideCue(cue);
        existing.setMuted(true);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.createOrReset(sessionId);

        verify(stateRepository).save(stateCaptor.capture());
        SessionAudioState saved = stateCaptor.getValue();
        assertThat(saved.getManualOverrideCue()).isNull();
        assertThat(saved.isMuted()).isFalse();
        assertThat(saved.getAcceptedAutomaticCue()).isNull();
        assertThat(saved.getPendingCue()).isNull();
        assertThat(saved.getDismissedCandidateCue()).isNull();
        assertThat(saved.getTemporaryVictoryCue()).isNull();
        assertThat(saved.getVictoryUntil()).isNull();
    }

    @Test
    void getStateReturnsExisting() {
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));

        SessionAudioState result = service.getState(sessionId);

        assertThat(result).isSameAs(state);
    }

    @Test
    void getStateThrowsWhenNotFound() {
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getState(sessionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No audio state");
    }

    @Test
    void muteSetsFlag() {
        state.setMuted(false);
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.mute(sessionId);

        assertThat(result.isMuted()).isTrue();
    }

    @Test
    void unmuteClearsFlag() {
        state.setMuted(true);
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.unmute(sessionId);

        assertThat(result.isMuted()).isFalse();
    }

    @Test
    void setOverrideSavesCue() {
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.setManualOverride(sessionId, cue);

        verify(stateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getManualOverrideCue().getId()).isEqualTo(cue.getId());
        assertThat(result.getManualOverrideCue().getId()).isEqualTo(cue.getId());
    }

    @Test
    void clearOverrideRemovesCue() {
        state.setManualOverrideCue(cue);
        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.clearManualOverride(sessionId);

        verify(stateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getManualOverrideCue()).isNull();
    }

    @Test
    void confirmAcceptsPendingAndClearsState() {
        SessionAudioState existing = new SessionAudioState();
        existing.setSession(session);
        existing.setAcceptedAutomaticCue(null);
        existing.setPendingCue(cue);
        existing.setDismissedCandidateCue(null);

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.confirm(sessionId);

        assertThat(result.getAcceptedAutomaticCue().getId()).isEqualTo(cue.getId());
        assertThat(result.getPendingCue()).isNull();
        assertThat(result.getDismissedCandidateCue()).isNull();
    }

    @Test
    void declineDismissesPending() {
        SessionAudioState existing = new SessionAudioState();
        existing.setSession(session);
        existing.setAcceptedAutomaticCue(cue);
        existing.setPendingCue(cue);

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.decline(sessionId);

        assertThat(result.getDismissedCandidateCue().getId()).isEqualTo(cue.getId());
        assertThat(result.getPendingCue()).isNull();
        assertThat(result.getAcceptedAutomaticCue().getId()).isEqualTo(cue.getId());
    }

    @Test
    void expireVictoryClearsTemporaryCue() {
        SessionAudioState existing = new SessionAudioState();
        existing.setSession(session);
        existing.setTemporaryVictoryCue(cue);
        existing.setVictoryUntil(now.plusSeconds(30));

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(existing));
        when(stateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionAudioState result = service.expireVictory(sessionId);

        assertThat(result.getTemporaryVictoryCue()).isNull();
        assertThat(result.getVictoryUntil()).isNull();
    }

    @Test
    void acknowledgePlaybackDoesNothing() {
        service.acknowledgePlaybackResult(sessionId, "completed");
    }

    @Test
    void deleteBySessionIdDelegates() {
        service.deleteBySessionId(sessionId);

        verify(stateRepository).deleteBySessionId(sessionId);
    }

    @Test
    void getRuntimeViewReturnsView() {
        state.setMuted(true);
        state.setSwitchMode(AudioSwitchMode.CONFIRM);
        state.setPendingCue(cue);

        ResolvedAudioCue resolved = new ResolvedAudioCue(
                cue,
                new AudioCueSource(AudioCueSource.SourceKind.SCENE, null, null));

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(resolver.resolve(state, campaignId)).thenReturn(resolved);

        AudioRuntimeView view = service.getRuntimeView(sessionId, campaignId);

        assertThat(view.muted()).isTrue();
        assertThat(view.switchMode()).isEqualTo(AudioSwitchMode.CONFIRM);
        assertThat(view.hasPendingConfirmation()).isTrue();
    }

    @Test
    void getRuntimeViewReturnsSilenceWhenResolved() {
        ResolvedAudioCue silence = new ResolvedAudioCue(
                null,
                new AudioCueSource(AudioCueSource.SourceKind.CAMPAIGN_DEFAULT, null, null));

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(resolver.resolve(state, campaignId)).thenReturn(silence);

        AudioRuntimeView view = service.getRuntimeView(sessionId, campaignId);

        assertThat(view.cue()).isNull();
    }

    @Test
    void getRuntimeViewReportsActionableAvailability() {
        ResolvedAudioCue resolved = new ResolvedAudioCue(
                cue,
                new AudioCueSource(AudioCueSource.SourceKind.COMBAT, null, null));

        when(stateRepository.findBySessionId(sessionId)).thenReturn(Optional.of(state));
        when(resolver.resolve(state, campaignId)).thenReturn(resolved);

        AudioRuntimeView view = service.getRuntimeView(sessionId, campaignId);

        assertThat(view.cue()).isNotNull();
        assertThat(view.sourceKind()).isEqualTo("COMBAT");
    }

    @Test
    void rejectsIdleSessionForCreate() {
        session.setStatus(CampaignSession.Status.IDLE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.createOrReset(sessionId))
                .isInstanceOf(IllegalStateException.class);
    }
}
