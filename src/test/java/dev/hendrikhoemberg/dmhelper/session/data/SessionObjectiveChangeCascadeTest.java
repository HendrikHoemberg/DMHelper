package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SessionObjectiveChangeCascadeTest {

    @Autowired private EntityManager em;
    @Autowired private CampaignSessionRepository sessionRepo;
    @Autowired private SessionObjectiveChangeRepository changeRepo;

    @Test
    void deletingSessionCascadesObjectiveChanges() {
        Campaign c = new Campaign();
        c.setName("CascadeTest");
        em.persist(c);
        em.flush();

        CampaignSession s = CampaignSession.idle(c);
        em.persist(s);
        em.flush();

        Quest q = new Quest();
        q.setCampaign(c);
        q.setTitle("Q");
        em.persist(q);
        em.flush();

        QuestObjective obj = new QuestObjective();
        obj.setQuest(q);
        obj.setTitle("O");
        obj.setSortOrder(0);
        em.persist(obj);
        em.flush();

        SessionObjectiveChange change = new SessionObjectiveChange();
        change.setSession(s);
        change.setObjective(obj);
        change.setNewStatus(QuestObjectiveStatus.COMPLETED);
        change.setChangedAt(Instant.now());
        em.persist(change);
        em.flush();

        UUID sId = s.getId();
        em.clear();
        sessionRepo.deleteById(sId);
        sessionRepo.flush();

        assertThat(sessionRepo.findById(sId)).isEmpty();
        assertThat(changeRepo.findBySessionIdOrderByChangedAtAscIdAsc(sId)).isEmpty();
    }
}
