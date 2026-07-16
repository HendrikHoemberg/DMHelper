package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SessionObjectiveChangeRepositoryTest {

    @Autowired private EntityManager em;
    @Autowired private CampaignSessionRepository sessionRepository;
    @Autowired private SessionObjectiveChangeRepository repository;
    @Autowired private QuestRepository questRepository;
    @Autowired private QuestObjectiveRepository objectiveRepository;

    private Campaign campaign;
    private CampaignSession session;
    private QuestObjective objective;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Session Test");
        em.persist(campaign);

        session = CampaignSession.idle(campaign);
        em.persist(session);

        Quest q = new Quest();
        q.setCampaign(campaign);
        q.setTitle("Test Quest");
        em.persist(q);

        objective = new QuestObjective();
        objective.setQuest(q);
        objective.setTitle("Do the thing");
        objective.setSortOrder(0);
        em.persist(objective);
    }

    @Test
    void persistsAndFindsBySession() {
        var change = new SessionObjectiveChange();
        change.setSession(session);
        change.setObjective(objective);
        change.setPreviousStatus(QuestObjectiveStatus.NOT_STARTED);
        change.setNewStatus(QuestObjectiveStatus.COMPLETED);
        change.setChangedAt(Instant.now());
        em.persist(change);
        em.flush();
        em.clear();

        var changes = repository.findBySessionIdOrderByChangedAtAscIdAsc(session.getId());
        assertThat(changes).hasSize(1);
        var loaded = changes.get(0);
        assertThat(loaded.getObjective().getId()).isEqualTo(objective.getId());
        assertThat(loaded.getPreviousStatus()).isEqualTo(QuestObjectiveStatus.NOT_STARTED);
        assertThat(loaded.getNewStatus()).isEqualTo(QuestObjectiveStatus.COMPLETED);
        assertThat(loaded.getChangedAt()).isNotNull();
    }

    @Test
    void allowsNullPreviousStatus() {
        var change = new SessionObjectiveChange();
        change.setSession(session);
        change.setObjective(objective);
        change.setNewStatus(QuestObjectiveStatus.ACTIVE);
        change.setChangedAt(Instant.now());
        em.persist(change);
        em.flush();
        em.clear();

        var changes = repository.findBySessionIdOrderByChangedAtAscIdAsc(session.getId());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getPreviousStatus()).isNull();
    }
}
