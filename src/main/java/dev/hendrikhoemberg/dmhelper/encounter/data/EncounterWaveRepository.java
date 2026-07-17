package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterWaveRepository extends JpaRepository<EncounterWave, UUID> {

    List<EncounterWave> findByEncounterIdOrderBySortOrderAsc(UUID encounterId);

    Optional<EncounterWave> findByEncounterIdAndWaveKey(UUID encounterId, String waveKey);
}
