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

    List<EncounterTokenPlacement> findByEncounterIdOrderByCombatant_SortOrderAsc(UUID encounterId);

    List<EncounterTokenPlacement> findByMapIdAndEncounterIdOrderByCombatant_SortOrderAsc(
            UUID mapId, UUID encounterId);

    long countByEncounterId(UUID encounterId);

    void deleteByCombatantId(UUID combatantId);
}
