package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    List<Encounter> findByCampaignIdOrderByNameAsc(UUID campaignId);

    Optional<Encounter> findByCampaignIdAndStatus(UUID campaignId, Encounter.Status status);

    List<Encounter> findByMapIdOrderByNameAsc(UUID mapId);

    List<Encounter> findByCombatAudioCueId(UUID cueId);

    List<Encounter> findByVictoryAudioCueId(UUID cueId);
}
