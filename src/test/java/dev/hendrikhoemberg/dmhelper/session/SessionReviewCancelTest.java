package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.hendrikhoemberg.dmhelper.session.data.CampaignSession.Status.PAUSED;
import static dev.hendrikhoemberg.dmhelper.session.data.CampaignSession.Status.REVIEW;
import static dev.hendrikhoemberg.dmhelper.session.data.CampaignSession.Status.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionReviewCancelTest {

    @Autowired private SessionLifecycleService lifecycle;
    @Autowired private CampaignSessionRepository sessions;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void cancellingAReviewEnteredWhileRunningReturnsToRunning() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.beginReview(campaignId);
        assertThat(status(campaignId)).isEqualTo(REVIEW);
        lifecycle.cancelReview(campaignId);
        assertThat(status(campaignId)).isEqualTo(RUNNING);
    }

    @Test
    void cancellingAReviewEnteredWhilePausedReturnsToPaused() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.pause(campaignId);
        lifecycle.beginReview(campaignId);
        lifecycle.cancelReview(campaignId);
        assertThat(status(campaignId)).isEqualTo(PAUSED);
    }

    @Test
    void cancellingAReviewKeepsTheDraft() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.beginReview(campaignId);
        String draft = draft(campaignId);
        assertThat(draft).isNotBlank();
        lifecycle.cancelReview(campaignId);
        assertThat(draft(campaignId)).isEqualTo(draft);
    }

    @Test
    void reopeningTheReviewKeepsTheDmsEdits() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.beginReview(campaignId);
        setDraft(campaignId, "## Recap\nThe goblins broke.\n");
        lifecycle.cancelReview(campaignId);
        lifecycle.beginReview(campaignId);
        assertThat(draft(campaignId)).contains("broke");
    }

    private CampaignSession.Status status(UUID campaignId) {
        return sessions.findByCampaignId(campaignId).orElseThrow().getStatus();
    }

    private String draft(UUID campaignId) {
        return sessions.findByCampaignId(campaignId).orElseThrow().getDraftBody();
    }

    private void setDraft(UUID campaignId, String body) {
        CampaignSession s = sessions.findByCampaignId(campaignId).orElseThrow();
        s.setDraftBody(body);
        sessions.save(s);
    }
}
