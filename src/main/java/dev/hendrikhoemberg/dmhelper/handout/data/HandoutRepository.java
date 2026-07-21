package dev.hendrikhoemberg.dmhelper.handout.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HandoutRepository extends JpaRepository<Handout, UUID> {
    List<Handout> findByCampaignIdOrderByTitleAsc(UUID campaignId);
    long countByCampaignId(UUID campaignId);
}
