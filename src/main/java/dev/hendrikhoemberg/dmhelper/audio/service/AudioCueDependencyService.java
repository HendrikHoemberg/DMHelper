package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AudioCueDependencyService {

    public static final String DEP_KIND_SCENE = "SCENE";
    public static final String DEP_KIND_COMBATANT = "COMBATANT";
    public static final String DEP_KIND_SESSION_OVERRIDE = "SESSION_OVERRIDE";
    public static final String DEP_KIND_SESSION_ACCEPTED = "SESSION_ACCEPTED";
    public static final String DEP_KIND_SESSION_PENDING = "SESSION_PENDING";
    public static final String DEP_KIND_SESSION_DISMISSED = "SESSION_DISMISSED";
    public static final String DEP_KIND_SESSION_VICTORY = "SESSION_VICTORY";

    private final SessionAudioStateRepository sessionAudioStateRepository;

    public AudioCueDependencyService(SessionAudioStateRepository sessionAudioStateRepository) {
        this.sessionAudioStateRepository = sessionAudioStateRepository;
    }

    public AudioCueDeletionImpact computeDeletionImpact(AudioCue cue) {
        List<AudioCueDependency> deps = new ArrayList<>();
        UUID cueId = cue.getId();

        var allStates = sessionAudioStateRepository.findAll();
        for (var state : allStates) {
            UUID sessionId = state.getSession().getId();
            if (state.getManualOverrideCue() != null && cueId.equals(state.getManualOverrideCue().getId())) {
                deps.add(new AudioCueDependency(DEP_KIND_SESSION_OVERRIDE, sessionId,
                        "Session manual override", "/sessions/" + sessionId));
            }
            if (state.getAcceptedAutomaticCue() != null && cueId.equals(state.getAcceptedAutomaticCue().getId())) {
                deps.add(new AudioCueDependency(DEP_KIND_SESSION_ACCEPTED, sessionId,
                        "Session accepted cue", "/sessions/" + sessionId));
            }
            if (state.getPendingCue() != null && cueId.equals(state.getPendingCue().getId())) {
                deps.add(new AudioCueDependency(DEP_KIND_SESSION_PENDING, sessionId,
                        "Session pending cue", "/sessions/" + sessionId));
            }
            if (state.getDismissedCandidateCue() != null && cueId.equals(state.getDismissedCandidateCue().getId())) {
                deps.add(new AudioCueDependency(DEP_KIND_SESSION_DISMISSED, sessionId,
                        "Session dismissed cue", "/sessions/" + sessionId));
            }
            if (state.getTemporaryVictoryCue() != null && cueId.equals(state.getTemporaryVictoryCue().getId())) {
                deps.add(new AudioCueDependency(DEP_KIND_SESSION_VICTORY, sessionId,
                        "Session victory cue", "/sessions/" + sessionId));
            }
        }

        return new AudioCueDeletionImpact(cue.getId(), cue.getName(), List.copyOf(deps));
    }
}
