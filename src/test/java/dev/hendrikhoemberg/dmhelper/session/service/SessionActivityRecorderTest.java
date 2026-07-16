package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionActivityRecorderTest {

    @Mock private Clock clock;
    @Mock private CampaignSessionRepository sessions;
    @Mock private SessionObjectiveChangeRepository objectiveChanges;

    @InjectMocks private SessionActivityRecorder recorder;

    private UUID campaignId;
    private CampaignSession runningSession;
    private CampaignSession idleSession;
    private QuestObjective objective;

    @Captor private ArgumentCaptor<SessionObjectiveChange> changeCaptor;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        var campaign = new Campaign();
        campaign.setId(campaignId);

        runningSession = CampaignSession.idle(campaign);
        runningSession.setId(UUID.randomUUID());
        runningSession.setStatus(CampaignSession.Status.RUNNING);

        idleSession = CampaignSession.idle(campaign);
        idleSession.setId(UUID.randomUUID());
        idleSession.setStatus(CampaignSession.Status.IDLE);

        objective = new QuestObjective();
        objective.setId(UUID.randomUUID());
    }

    @Test
    void recordsOneRowPerStatusChange() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T12:00:00Z"));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(runningSession));
        when(objectiveChanges.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recorder.recordObjectiveChange(campaignId, objective.getId(),
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveStatus.ACTIVE);

        verify(objectiveChanges).save(changeCaptor.capture());
        SessionObjectiveChange saved = changeCaptor.getValue();
        assertThat(saved.getSession().getId()).isEqualTo(runningSession.getId());
        assertThat(saved.getObjective().getId()).isEqualTo(objective.getId());
        assertThat(saved.getPreviousStatus()).isEqualTo(QuestObjectiveStatus.NOT_STARTED);
        assertThat(saved.getNewStatus()).isEqualTo(QuestObjectiveStatus.ACTIVE);
        assertThat(saved.getChangedAt()).isEqualTo(Instant.parse("2026-07-16T12:00:00Z"));
    }

    @Test
    void doesNotRecordWhenStatusIsUnchanged() {
        recorder.recordObjectiveChange(campaignId, objective.getId(),
                QuestObjectiveStatus.ACTIVE, QuestObjectiveStatus.ACTIVE);

        verify(objectiveChanges, never()).save(any());
    }

    @Test
    void doesNotRecordWhenNoSessionIsRunning() {
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(idleSession));

        recorder.recordObjectiveChange(campaignId, objective.getId(),
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveStatus.ACTIVE);

        verify(objectiveChanges, never()).save(any());
    }

    @Test
    void allowsNullablePreviousStatusOnFirstChange() {
        when(clock.instant()).thenReturn(Instant.now());
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(runningSession));
        when(objectiveChanges.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recorder.recordObjectiveChange(campaignId, objective.getId(), null, QuestObjectiveStatus.NOT_STARTED);

        verify(objectiveChanges).save(changeCaptor.capture());
        assertThat(changeCaptor.getValue().getPreviousStatus()).isNull();
        assertThat(changeCaptor.getValue().getNewStatus()).isEqualTo(QuestObjectiveStatus.NOT_STARTED);
    }

    @Test
    void doesNotRecordWhenNoSessionExists() {
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.empty());

        recorder.recordObjectiveChange(campaignId, objective.getId(),
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveStatus.ACTIVE);

        verify(objectiveChanges, never()).save(any());
    }
}
