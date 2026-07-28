package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterTokenPlacementRepository
        extends JpaRepository<EncounterTokenPlacement, UUID> {

    Optional<EncounterTokenPlacement> findByCombatantId(UUID combatantId);

    List<EncounterTokenPlacement> findByEncounterIdOrderByCombatantSortOrderAsc(UUID encounterId);

    List<EncounterTokenPlacement> findByMapIdAndEncounterIdOrderByCombatantSortOrderAsc(
            UUID mapId, UUID encounterId);

    long countByEncounterId(UUID encounterId);

    void deleteByCombatantId(UUID combatantId);
}
