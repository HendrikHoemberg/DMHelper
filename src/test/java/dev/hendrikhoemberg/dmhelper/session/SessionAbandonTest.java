package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SessionAbandonTest {

    @Autowired private SessionLifecycleService lifecycle;
    @Autowired private NoteRepository notes;
    @Autowired private CampaignSessionRepository sessions;
    @Autowired private SessionAudioStateRepository audioStates;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void abandoningASessionReturnsToIdleWithoutWritingASessionLog() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        int logsBefore = notes
                .findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_LOG)
                .size();
        CampaignSession before = sessions.findByCampaignId(campaignId).orElseThrow();
        UUID sessionId = before.getId();
        before.setDraftBody("Throw-away draft");
        before.setPresentationMode(CampaignSession.PresentationMode.MAP);
        before.setPresentedMap(before.getWorkspaceMap());
        sessions.saveAndFlush(before);
        assertThat(audioStates.findBySessionId(sessionId)).isPresent();

        CampaignSession session = lifecycle.abandon(campaignId);

        assertThat(session.getStatus()).isEqualTo(CampaignSession.Status.IDLE);
        assertThat(session.getStartedAt()).isNull();
        assertThat(session.getPausedAt()).isNull();
        assertThat(session.getReviewStartedAt()).isNull();
        assertThat(session.getStartInGameYear()).isNull();
        assertThat(session.getStartInGameMonth()).isNull();
        assertThat(session.getStartInGameDay()).isNull();
        assertThat(session.getPlanNote()).isNull();
        assertThat(session.getWorkspaceMap()).isNull();
        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(session.getPresentedMap()).isNull();
        assertThat(session.getPresentedHandout()).isNull();
        assertThat(session.getDraftBody()).isNull();
        assertThat(session.getAttendees()).isEmpty();
        assertThat(audioStates.findBySessionId(sessionId))
                .as("session-only audio runtime state must be removed")
                .isEmpty();
        assertThat(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_LOG))
                .as("abandoning must not create a session log note")
                .hasSize(logsBefore);
    }

    @Test
    void abandoningAnIdleSessionIsRejected() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.abandon(campaignId);

        assertThatThrownBy(() -> lifecycle.abandon(campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already idle");
    }
}
