package dev.hendrikhoemberg.dmhelper.treasury.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemAssignmentRepository extends JpaRepository<ItemAssignment, UUID> {
    List<ItemAssignment> findByCampaignIdOrderByPartyMemberAsc(UUID campaignId);
    List<ItemAssignment> findByPartyMemberId(UUID partyMemberId);
    List<ItemAssignment> findByCampaignIdAndPartyMemberIsNull(UUID campaignId);
    int countByPartyMemberIdAndAttunedTrue(UUID partyMemberId);
}
