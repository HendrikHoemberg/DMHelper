package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class SessionAudioStateService {

    private static final Set<String> ACK_RESULTS = Set.of(
            "PLAYING", "PAUSED", "COMPLETED", "AUTOPLAY_BLOCKED",
            "CONTENT_UNAVAILABLE", "PROVIDER_OFFLINE", "POLICY_DISABLED",
            "UNSUPPORTED_CONTROL");

    private final SessionAudioStateRepository stateRepository;
    private final CampaignSessionRepository sessionRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignSettingsCodec settingsCodec;
    private final AudioCueResolver resolver;
    private final Clock clock;

    public SessionAudioStateService(SessionAudioStateRepository stateRepository,
                                    CampaignSessionRepository sessionRepository,
                                    CampaignRepository campaignRepository,
                                    CampaignSettingsCodec settingsCodec,
                                    AudioCueResolver resolver,
                                    Clock clock) {
        this.stateRepository = stateRepository;
        this.sessionRepository = sessionRepository;
        this.campaignRepository = campaignRepository;
        this.settingsCodec = settingsCodec;
        this.resolver = resolver;
        this.clock = clock;
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
        state.setSwitchMode(settingsCodec.read(session.getCampaign()).audioSwitchMode());
        state.setMuted(false);
        state.setManualOverrideCue(null);
        state.setAcceptedAutomaticCue(null);
        AudioCueResolver.clearAcceptedSource(state);
        AudioCueResolver.clearPending(state);
        state.setDismissedCandidateCue(null);
        AudioCueResolver.clearVictory(state);
        return stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public SessionAudioState getState(UUID sessionId) {
        return stateRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("No audio state for session: " + sessionId));
    }

    @Transactional(readOnly = true)
    public SessionAudioState getState(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId);
        if (!campaignId.equals(state.getSession().getCampaign().getId())) {
            throw new IllegalStateException("No audio state for campaign session");
        }
        return state;
    }

    public SessionAudioState mute(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        state.setMuted(true);
        return stateRepository.save(state);
    }

    public SessionAudioState mute(UUID sessionId) {
        return mute(sessionId, campaignIdFor(sessionId));
    }

    public SessionAudioState unmute(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        state.setMuted(false);
        return stateRepository.save(state);
    }

    public SessionAudioState unmute(UUID sessionId) {
        return unmute(sessionId, campaignIdFor(sessionId));
    }

    public SessionAudioState setManualOverride(UUID sessionId, UUID campaignId, AudioCue cue) {
        SessionAudioState state = getState(sessionId, campaignId);
        if (cue == null || !campaignId.equals(cue.getCampaign().getId())) {
            throw new IllegalArgumentException("Audio cue not found in campaign");
        }
        state.setManualOverrideCue(cue);
        return stateRepository.save(state);
    }

    public SessionAudioState setManualOverride(UUID sessionId, AudioCue cue) {
        return setManualOverride(sessionId, campaignIdFor(sessionId), cue);
    }

    public SessionAudioState clearManualOverride(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        state.setManualOverrideCue(null);
        return stateRepository.save(state);
    }

    public SessionAudioState clearManualOverride(UUID sessionId) {
        return clearManualOverride(sessionId, campaignIdFor(sessionId));
    }

    public SessionAudioState confirm(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        if (state.getPendingCue() == null) {
            throw new IllegalStateException("No pending audio cue to confirm");
        }
        state.setAcceptedAutomaticCue(state.getPendingCue());
        state.setAcceptedSourceKind(state.getPendingSourceKind());
        state.setAcceptedSourceId(state.getPendingSourceId());
        state.setAcceptedSourceLabel(state.getPendingSourceLabel());
        AudioCueResolver.clearPending(state);
        state.setDismissedCandidateCue(null);
        return stateRepository.save(state);
    }

    public SessionAudioState confirm(UUID sessionId) {
        return confirm(sessionId, campaignIdFor(sessionId));
    }

    public SessionAudioState decline(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        if (state.getPendingCue() == null) {
            throw new IllegalStateException("No pending audio cue to decline");
        }
        state.setDismissedCandidateCue(state.getPendingCue());
        AudioCueResolver.clearPending(state);
        return stateRepository.save(state);
    }

    public SessionAudioState decline(UUID sessionId) {
        return decline(sessionId, campaignIdFor(sessionId));
    }

    public SessionAudioState expireVictory(UUID sessionId, UUID campaignId) {
        SessionAudioState state = getState(sessionId, campaignId);
        AudioCueResolver.clearVictory(state);
        return stateRepository.save(state);
    }

    public SessionAudioState expireVictory(UUID sessionId) {
        return expireVictory(sessionId, campaignIdFor(sessionId));
    }

    public void acknowledgePlaybackResult(UUID sessionId, UUID campaignId, String result) {
        SessionAudioState state = getState(sessionId, campaignId);
        String normalized = result == null ? "" : result.toUpperCase();
        if (!ACK_RESULTS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported playback result");
        }
        // Touch the owned state so acknowledgement is observable without persisting provider errors.
        stateRepository.save(state);
    }

    public void acknowledgePlaybackResult(UUID sessionId, String result) {
        acknowledgePlaybackResult(sessionId, campaignIdFor(sessionId), result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startVictory(UUID campaignId, UUID encounterId, String encounterName,
                             AudioCue cue, Integer durationSeconds) {
        if (cue == null || durationSeconds == null || durationSeconds <= 0) return;
        if (cue.getCampaign() == null || !campaignId.equals(cue.getCampaign().getId())) return;
        CampaignSession session = sessionRepository.findByCampaignId(campaignId)
                .filter(CampaignSession::isOpen).orElse(null);
        if (session == null) return;
        SessionAudioState state = getState(session.getId(), campaignId);
        state.setTemporaryVictoryCue(cue);
        state.setVictoryUntil(clock.instant().plusSeconds(durationSeconds));
        state.setVictorySourceId(encounterId);
        state.setVictorySourceLabel("Victory: " + encounterName);
        stateRepository.save(state);
    }

    public void deleteBySessionId(UUID sessionId) {
        stateRepository.deleteBySessionId(sessionId);
    }

    public AudioRuntimeView getRuntimeView(UUID sessionId, UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalStateException("Campaign not found"));
        SessionAudioState state = getState(sessionId, campaignId);
        AudioSwitchMode configured = settingsCodec.read(campaign).audioSwitchMode();
        if (configured != state.getSwitchMode()) state.setSwitchMode(configured);

        ResolvedAudioCue resolved = resolver.resolve(state, campaignId);
        stateRepository.save(state);
        return new AudioRuntimeView(
                AudioRuntimeCue.from(resolved.cue()),
                AudioRuntimeCue.from(state.getPendingCue()),
                resolved.source().kind().name(),
                resolved.source().sourceId(),
                resolved.source().sourceLabel(),
                state.isMuted(),
                state.getSwitchMode(),
                state.getPendingCue() != null,
                resolved.cue() != null || state.getPendingCue() != null,
                state.getVictoryUntil()
        );
    }

    private UUID campaignIdFor(UUID sessionId) {
        return getState(sessionId).getSession().getCampaign().getId();
    }
}
