package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionAuditEntryRepository extends JpaRepository<SessionAuditEntry, UUID> {

    List<SessionAuditEntry> findBySession_IdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
            UUID sessionId, Instant from, Instant to);
}
