package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReadinessFixtureShapeTest {

    @Autowired
    CampaignReadinessFacade facade;

    @Autowired
    CampaignFixtures fixtures;

    @Test
    void notReadyFixtureHasBlockers() {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThat(facade.reportForCampaign(id).sessionReady()).isFalse();
    }

    @Test
    void readyAfterRepairFixtureIsSessionReady() {
        UUID id = fixtures.operationalFixtureReadyAfterRepair();
        assertThat(facade.reportForCampaign(id).sessionReady()).isTrue();
    }
}
