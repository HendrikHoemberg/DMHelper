package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.audio.service.SessionAudioStateService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduces the production open-in-view=false failure: lifecycle methods return a session
 * whose {@code attendees} collection the controller's state mapper reads after the transaction
 * commits and the entity is detached. Clearing the persistence context simulates that boundary.
 */
@DataJpaTest
@Import(SessionLifecycleService.class)
class SessionLifecycleAttendeeHydrationTest {

    @MockitoBean private Clock clock;
    @MockitoBean private CalendarService calendar;
    @MockitoBean private SessionDraftService drafts;
    @MockitoBean private NoteService noteService;
    @MockitoBean private CampaignPackageKeyService packageKeys;
    @MockitoBean private SessionReferenceCleaner sessionRefCleaner;
    @MockitoBean private SessionAudioStateService audioStateService;

    @Autowired private SessionLifecycleService service;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private PartyMember aria;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Ashes of Dawn");
        em.persist(campaign);

        aria = new PartyMember();
        aria.setCampaign(campaign);
        aria.setCharacterName("Aria");
        em.persist(aria);
    }

    private CampaignSession persistRunningSessionWithAttendee() {
        CampaignSession session = CampaignSession.idle(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.getAttendees().add(aria);
        em.persist(session);
        em.flush();
        em.clear();
        return session;
    }

    @Test
    void setWorkspaceMapReturnsSessionWhoseAttendeesSurviveDetachment() {
        persistRunningSessionWithAttendee();

        CampaignSession result = service.setWorkspaceMap(campaign.getId(), null);
        em.clear(); // simulate open-in-view=false: transaction committed, entity detached

        assertThatCode(() -> {
            List<UUID> ids = result.getAttendees().stream().map(PartyMember::getId).toList();
            assertThat(ids).containsExactly(aria.getId());
        }).doesNotThrowAnyException();
    }

    @Test
    void pauseReturnsSessionWhoseAttendeesSurviveDetachment() {
        persistRunningSessionWithAttendee();

        CampaignSession result = service.pause(campaign.getId());
        em.clear(); // simulate open-in-view=false: transaction committed, entity detached

        assertThatCode(() -> {
            List<UUID> ids = result.getAttendees().stream().map(PartyMember::getId).toList();
            assertThat(ids).containsExactly(aria.getId());
        }).doesNotThrowAnyException();
    }
}
