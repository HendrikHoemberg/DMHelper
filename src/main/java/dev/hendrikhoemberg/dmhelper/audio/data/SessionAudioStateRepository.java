package dev.hendrikhoemberg.dmhelper.audio.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAudioStateRepository extends JpaRepository<SessionAudioState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessionAudioState> findBySessionId(UUID sessionId);

    List<SessionAudioState> findByManualOverrideCueId(UUID cueId);

    List<SessionAudioState> findByAcceptedAutomaticCueId(UUID cueId);

    List<SessionAudioState> findByPendingCueId(UUID cueId);

    List<SessionAudioState> findByDismissedCandidateCueId(UUID cueId);

    List<SessionAudioState> findByTemporaryVictoryCueId(UUID cueId);

    void deleteBySessionId(UUID sessionId);
}
