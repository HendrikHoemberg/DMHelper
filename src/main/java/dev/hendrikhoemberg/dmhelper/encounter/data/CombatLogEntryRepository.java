package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CombatLogEntryRepository extends JpaRepository<CombatLogEntry, UUID> {

    List<CombatLogEntry> findByEncounterIdOrderBySequenceAsc(UUID encounterId);

    void deleteByEncounterId(UUID encounterId);

    @Query("select log from CombatLogEntry log where log.encounter.campaign.id = :campaignId " +
           "and log.createdAt >= :from and log.createdAt <= :to order by log.createdAt, log.sequence, log.id")
    List<CombatLogEntry> findSessionEvidence(UUID campaignId, Instant from, Instant to);
}
