package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ReadinessAcceptanceTest {

    @Autowired MockMvc mvc;
    @Autowired CampaignReadinessFacade facade;
    @Autowired CampaignFixtures fixtures;

    @Test
    void resolvingAndAcceptingEveryBlockerReachesSessionReady() throws Exception {
        UUID id = fixtures.operationalFixtureNotReady();
        assertThat(facade.reportForCampaign(id).sessionReady()).isFalse();

        for (ReadinessItem blocker : facade.reportForCampaign(id).byState(ReadinessState.BLOCKER)) {
            mvc.perform(post("/campaigns/{c}/readiness/accept", id).param("itemKey", blocker.key()));
        }
        assertThat(facade.reportForCampaign(id).sessionReady()).isTrue();
    }
}
