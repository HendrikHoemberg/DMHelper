package dev.hendrikhoemberg.dmhelper.handout.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntry;
import dev.hendrikhoemberg.dmhelper.session.data.SessionAuditEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:handoutsafety;DB_CLOSE_DELAY=-1")
@Transactional
class HandoutSafetyPersistenceTest {

    @Autowired
    private CampaignRepository campaigns;

    @Autowired
    private HandoutRepository handouts;

    @Autowired
    private CampaignSessionRepository sessions;

    @Autowired
    private SessionAuditEntryRepository auditEntries;

    @Autowired
    private EntityManager em;

    @Test
    void mapsSafetyClassificationAsAnEnum() throws Exception {
        Field field = Handout.class.getDeclaredField("safetyClassification");
        assertThat(field.getType()).isEqualTo(Handout.SafetyClassification.class);
        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    }

    @Test
    void persistsSafetyClassificationAndAuditEntry() {
        Campaign campaign = new Campaign();
        campaign.setName("Test campaign");
        campaign = campaigns.save(campaign);

        Handout source = new Handout();
        source.setCampaign(campaign);
        source.setTitle("Source map");
        source.setFileName("source.png");
        source.setSafetyClassification(Handout.SafetyClassification.DM_SOURCE);
        source = handouts.save(source);

        Handout derivative = new Handout();
        derivative.setCampaign(campaign);
        derivative.setTitle("Derivative");
        derivative.setFileName("derivative.png");
        derivative.setSafetyClassification(Handout.SafetyClassification.PLAYER_DERIVATIVE);
        derivative.setSourceHandout(source);
        derivative.setDerivativeRecipe("{\"crop\":{\"x\":0,\"y\":0,\"w\":800,\"h\":600}}");
        derivative = handouts.save(derivative);

        CampaignSession session = CampaignSession.idle(campaign);
        session = sessions.save(session);

        SessionAuditEntry audit = new SessionAuditEntry();
        audit.setSession(session);
        audit.setEntryType(SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE);
        audit.setContentType("HANDOUT");
        audit.setContentId(derivative.getId());
        audit.setDetails("{\"title\":\"Scanned page 12\",\"classification\":\"DM_SOURCE\"}");
        audit = auditEntries.save(audit);

        em.flush();
        em.clear();

        Handout loadedSource = handouts.findById(source.getId()).orElseThrow();
        Handout loadedDerivative = handouts.findById(derivative.getId()).orElseThrow();

        assertThat(loadedSource.getSafetyClassification()).isEqualTo(Handout.SafetyClassification.DM_SOURCE);
        assertThat(loadedSource.isPresentable()).isFalse();
        assertThat(loadedSource.isDerivative()).isFalse();

        assertThat(loadedDerivative.getSafetyClassification()).isEqualTo(Handout.SafetyClassification.PLAYER_DERIVATIVE);
        assertThat(loadedDerivative.isPresentable()).isTrue();
        assertThat(loadedDerivative.isDerivative()).isTrue();
        assertThat(loadedDerivative.getSourceHandout().getId()).isEqualTo(source.getId());
        assertThat(loadedDerivative.getDerivativeRecipe()).isEqualTo("{\"crop\":{\"x\":0,\"y\":0,\"w\":800,\"h\":600}}");

        SessionAuditEntry loadedAudit = auditEntries.findById(audit.getId()).orElseThrow();
        assertThat(loadedAudit.getEntryType()).isEqualTo(SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE);
        assertThat(loadedAudit.getContentId()).isEqualTo(derivative.getId());
        assertThat(loadedAudit.getDetails()).isEqualTo("{\"title\":\"Scanned page 12\",\"classification\":\"DM_SOURCE\"}");

        List<SessionAuditEntry> entries = auditEntries
                .findBySession_IdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
                        session.getId(),
                        Instant.EPOCH,
                        Instant.parse("2100-01-01T00:00:00Z"));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getId()).isEqualTo(audit.getId());
    }
}
