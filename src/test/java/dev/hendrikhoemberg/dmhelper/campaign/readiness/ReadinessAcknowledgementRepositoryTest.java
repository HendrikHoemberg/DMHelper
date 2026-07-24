package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReadinessAcknowledgementRepositoryTest {

    @Autowired ReadinessAcknowledgementRepository repo;

    @Test
    void persistsAndQueriesByCampaignAndKey() {
        UUID campaignId = UUID.randomUUID();
        var ack = new ReadinessAcknowledgement();
        ack.setCampaignId(campaignId);
        ack.setItemKey("omission:maps");
        ack.setAcceptedAt(Instant.now());
        repo.save(ack);

        assertThat(repo.existsByCampaignIdAndItemKey(campaignId, "omission:maps")).isTrue();
        assertThat(repo.findByCampaignId(campaignId)).hasSize(1);
        assertThat(repo.existsByCampaignIdAndItemKey(campaignId, "other")).isFalse();
    }
}
