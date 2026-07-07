package dev.hendrikhoemberg.dmhelper.party.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartyMemberRepository extends JpaRepository<PartyMember, UUID> {

    List<PartyMember> findByCampaignIdOrderByCharacterNameAsc(UUID campaignId);

    List<PartyMember> findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(UUID campaignId);
}
