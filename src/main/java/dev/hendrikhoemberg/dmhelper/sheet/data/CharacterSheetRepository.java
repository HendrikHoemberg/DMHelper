package dev.hendrikhoemberg.dmhelper.sheet.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CharacterSheetRepository extends JpaRepository<CharacterSheet, UUID> {
    Optional<CharacterSheet> findByPartyMemberId(UUID partyMemberId);
    List<CharacterSheet> findByPartyMember_CampaignId(UUID campaignId);
}
