package dev.hendrikhoemberg.dmhelper.audio.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAudioStateRepository extends JpaRepository<SessionAudioState, UUID> {

    Optional<SessionAudioState> findBySessionId(UUID sessionId);

    List<SessionAudioState> findByManualOverrideCueId(UUID cueId);

    List<SessionAudioState> findByAcceptedAutomaticCueId(UUID cueId);

    List<SessionAudioState> findByPendingCueId(UUID cueId);

    List<SessionAudioState> findByDismissedCandidateCueId(UUID cueId);

    List<SessionAudioState> findByTemporaryVictoryCueId(UUID cueId);

    void deleteBySessionId(UUID sessionId);
}
