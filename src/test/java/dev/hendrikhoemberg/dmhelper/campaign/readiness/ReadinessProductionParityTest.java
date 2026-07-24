package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class ReadinessProductionParityTest {

    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures;

    @Test
    void reportAssemblesWithoutLazyAccessOutsideTransaction() {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThatCode(() -> facade.reportForCampaign(id)).doesNotThrowAnyException();
    }
}
