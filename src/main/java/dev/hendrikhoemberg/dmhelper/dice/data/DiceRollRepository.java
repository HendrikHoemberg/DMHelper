package dev.hendrikhoemberg.dmhelper.dice.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiceRollRepository extends JpaRepository<DiceRoll, UUID> {
    List<DiceRoll> findTop20ByOrderByCreatedAtDesc();

    List<DiceRoll> findTop20ByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<DiceRoll> findByCampaignId(UUID campaignId);

    List<DiceRoll> findByCampaignIdOrderByCreatedAtAscIdAsc(UUID campaignId);
}
