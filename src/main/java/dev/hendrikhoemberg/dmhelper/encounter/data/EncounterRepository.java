package dev.hendrikhoemberg.dmhelper.encounter.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> findByIdForUpdate(@Param("id") UUID id);

    List<Encounter> findByCampaignIdOrderByNameAsc(UUID campaignId);

    Optional<Encounter> findByCampaignIdAndStatus(UUID campaignId, Encounter.Status status);

    Optional<Encounter> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<Encounter> findByMapIdOrderByNameAsc(UUID mapId);

    List<Encounter> findByCombatAudioCueId(UUID cueId);

    List<Encounter> findByVictoryAudioCueId(UUID cueId);
}
