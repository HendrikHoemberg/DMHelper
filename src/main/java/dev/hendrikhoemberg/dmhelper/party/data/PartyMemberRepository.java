package dev.hendrikhoemberg.dmhelper.party.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface PartyMemberRepository extends JpaRepository<PartyMember, UUID> {

    List<PartyMember> findByCampaignIdOrderByCharacterNameAsc(UUID campaignId);

    /** Party sizes for many campaigns at once, for the campaign index's readiness badges. */
    List<PartyMember> findByCampaignIdIn(java.util.Collection<UUID> campaignIds);

    List<PartyMember> findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(UUID campaignId);

    @Query("SELECT pm FROM PartyMember pm WHERE pm.campaign.id = :campaignId ORDER BY pm.characterName ASC, pm.id ASC")
    List<PartyMember> findByCampaignIdOrderByCharacterNameAscIdAsc(@Param("campaignId") UUID campaignId);
}
