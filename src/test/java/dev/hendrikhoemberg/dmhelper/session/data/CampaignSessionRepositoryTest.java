package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CampaignSessionRepositoryTest {

    @Autowired
    private CampaignRepository campaigns;
    @Autowired
    private CampaignSessionRepository sessions;
    @Autowired
    private SessionSceneVisitRepository visits;

    private Campaign campaign(String name) {
        Campaign c = new Campaign();
        c.setName(name);
        return campaigns.save(c);
    }

    @Test
    void storesOneSessionPerCampaignPerformsUniqueConstraintCheck() {
        Campaign campaign = campaign("Ashes of Dawn");
        CampaignSession session = sessions.save(CampaignSession.idle(campaign));

        assertThatThrownBy(() -> sessions.saveAndFlush(CampaignSession.idle(campaign)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(session.getStatus()).isEqualTo(CampaignSession.Status.IDLE);
        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
    }
}
