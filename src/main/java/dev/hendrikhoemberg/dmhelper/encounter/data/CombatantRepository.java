package dev.hendrikhoemberg.dmhelper.encounter.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CombatantRepository extends JpaRepository<Combatant, UUID> {

    List<Combatant> findByEncounterIdOrderBySortOrderAsc(UUID encounterId);

    List<Combatant> findByEncounterIdAndGroupId(UUID encounterId, String groupId);

    void deleteByEncounterId(UUID encounterId);

    Optional<Combatant> findByEncounterIdAndTokenId(UUID encounterId, UUID tokenId);

    List<Combatant> findByTokenId(UUID tokenId);

    Optional<Combatant> findByEncounterIdAndPartyMemberId(UUID encounterId, UUID partyMemberId);

    List<Combatant> findByPartyMemberId(UUID partyMemberId);
}
