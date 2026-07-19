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

        for (var state : sessionAudioStateRepository.findByManualOverrideCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_OVERRIDE, state.getSession().getId(),
                    "Session manual override", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByAcceptedAutomaticCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_ACCEPTED, state.getSession().getId(),
                    "Session accepted cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByPendingCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_PENDING, state.getSession().getId(),
                    "Session pending cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByDismissedCandidateCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_DISMISSED, state.getSession().getId(),
                    "Session dismissed cue", "/sessions/" + state.getSession().getId()));
        }
        for (var state : sessionAudioStateRepository.findByTemporaryVictoryCueId(cueId)) {
            deps.add(new AudioCueDependency(DEP_KIND_SESSION_VICTORY, state.getSession().getId(),
                    "Session victory cue", "/sessions/" + state.getSession().getId()));
        }

        return new AudioCueDeletionImpact(cue.getId(), cue.getName(), List.copyOf(deps));
    }
}
