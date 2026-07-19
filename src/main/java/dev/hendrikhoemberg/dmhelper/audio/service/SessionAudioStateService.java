package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SessionAudioStateService {

    private final SessionAudioStateRepository stateRepository;
    private final CampaignSessionRepository sessionRepository;
    private final AudioCueResolver resolver;

    public SessionAudioStateService(SessionAudioStateRepository stateRepository,
                                     CampaignSessionRepository sessionRepository,
                                     AudioCueResolver resolver) {
        this.stateRepository = stateRepository;
        this.sessionRepository = sessionRepository;
        this.resolver = resolver;
    }

    public SessionAudioState createOrReset(UUID sessionId) {
        CampaignSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (!session.isOpen()) {
            throw new IllegalStateException("Cannot create audio state for an idle session.");
        }
        SessionAudioState state = stateRepository.findBySessionId(sessionId)
                .orElseGet(SessionAudioState::new);
        state.setSession(session);
        state.setSwitchMode(AudioSwitchMode.AUTOMATIC);
        state.setMuted(false);
        state.setManualOverrideCue(null);
        state.setAcceptedAutomaticCue(null);
        state.setPendingCue(null);
        state.setDismissedCandidateCue(null);
        state.setTemporaryVictoryCue(null);
        state.setVictoryUntil(null);
        return stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public SessionAudioState getState(UUID sessionId) {
        return stateRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("No audio state for session: " + sessionId));
    }

    public SessionAudioState mute(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setMuted(true);
        return stateRepository.save(state);
    }

    public SessionAudioState unmute(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setMuted(false);
        return stateRepository.save(state);
    }

    public SessionAudioState setManualOverride(UUID sessionId, AudioCue cue) {
        SessionAudioState state = getState(sessionId);
        state.setManualOverrideCue(cue);
        return stateRepository.save(state);
    }

    public SessionAudioState clearManualOverride(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setManualOverrideCue(null);
        return stateRepository.save(state);
    }

    public SessionAudioState confirm(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setAcceptedAutomaticCue(state.getPendingCue());
        state.setPendingCue(null);
        state.setDismissedCandidateCue(null);
        return stateRepository.save(state);
    }

    public SessionAudioState decline(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setDismissedCandidateCue(state.getPendingCue());
        state.setPendingCue(null);
        return stateRepository.save(state);
    }

    public SessionAudioState expireVictory(UUID sessionId) {
        SessionAudioState state = getState(sessionId);
        state.setTemporaryVictoryCue(null);
        state.setVictoryUntil(null);
        return stateRepository.save(state);
    }

    public void acknowledgePlaybackResult(UUID sessionId, String result) {
    }

    public void deleteBySessionId(UUID sessionId) {
        stateRepository.deleteBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public AudioRuntimeView getRuntimeView(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId);
        ResolvedAudioCue resolved = resolver.resolve(state, campaignId);
        return new AudioRuntimeView(
                resolved.cue(),
                resolved.source().kind().name(),
                resolved.source().sourceId(),
                resolved.source().sourceLabel(),
                state.isMuted(),
                state.getSwitchMode(),
                state.getPendingCue() != null,
                resolved.cue() != null
        );
    }
}
