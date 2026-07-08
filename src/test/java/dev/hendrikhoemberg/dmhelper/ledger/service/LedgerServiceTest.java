package dev.hendrikhoemberg.dmhelper.ledger.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(LedgerService.class)
class LedgerServiceTest {

    @Autowired private LedgerService service;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreateLedgerEntry() {
        var result = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("100"), "GP",
                "Thia", "Quest reward", 1492, 2, 15));
        assertThat(result.id()).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("100");
        assertThat(result.holder()).isEqualTo("Thia");
        assertThat(result.currency()).isEqualTo("GP");
    }

    @Test
    void shouldComputeGoldBalance() {
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("100"), "GP",
                "Thia", "Quest", null, null, null));
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "SPEND", new BigDecimal("32"), "GP",
                "Thia", "Inn stay", null, null, null));

        BigDecimal balance = service.computeGoldBalance(campaign.getId(), "Thia", "GP");
        assertThat(balance).isEqualByComparingTo("68.00");
    }

    @Test
    void shouldComputeBalancePerHolder() {
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("200"), "GP",
                "Party Stash", "Hoard", null, null, null));
        service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("50"), "GP",
                "Bruenor", "Share", null, null, null));

        var balances = service.computeAllGoldBalances(campaign.getId());
        assertThat(balances).hasSize(2);
    }

    @Test
    void shouldOrderByTimestampDesc() {
        var first = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("10"), "GP",
                "Thia", "First", null, null, null));
        var second = service.create(new LedgerService.CreateLedgerEntryRequest(
                campaign.getId(), "GOLD", "GAIN", new BigDecimal("20"), "GP",
                "Thia", "Second", null, null, null));

        var list = service.findByCampaignId(campaign.getId());
        assertThat(list.get(0).id()).isEqualTo(second.id());
    }
}
