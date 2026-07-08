package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CombatLogEntryRepository extends JpaRepository<CombatLogEntry, UUID> {

    List<CombatLogEntry> findByEncounterIdOrderBySequenceAsc(UUID encounterId);

    void deleteByEncounterId(UUID encounterId);
}
