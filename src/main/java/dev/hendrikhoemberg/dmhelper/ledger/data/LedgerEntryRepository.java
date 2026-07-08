package dev.hendrikhoemberg.dmhelper.ledger.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByCampaignIdOrderByTimestampDesc(UUID campaignId);

    List<LedgerEntry> findByCampaignIdAndHolderOrderByTimestampDesc(UUID campaignId, String holder);

    @Query("SELECT COALESCE(SUM(CASE WHEN le.direction = 'GAIN' THEN le.amount ELSE le.amount * -1 END), 0) " +
           "FROM LedgerEntry le WHERE le.campaign.id = :campaignId AND le.kind = 'GOLD' AND le.holder = :holder " +
           "AND le.currency = :currency")
    BigDecimal computeGoldBalance(UUID campaignId, String holder, String currency);
}
