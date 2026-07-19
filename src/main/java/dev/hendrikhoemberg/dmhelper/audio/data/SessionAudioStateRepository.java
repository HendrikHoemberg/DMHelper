package dev.hendrikhoemberg.dmhelper.audio.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionAudioStateRepository extends JpaRepository<SessionAudioState, UUID> {

    Optional<SessionAudioState> findBySessionId(UUID sessionId);
}
