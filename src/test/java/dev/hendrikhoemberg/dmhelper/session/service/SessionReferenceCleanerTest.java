package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;

@ExtendWith(MockitoExtension.class)
class SessionReferenceCleanerTest {

    @Mock private CampaignSessionRepository sessions;
    @Mock private SessionSceneVisitRepository visits;
    @Mock private CampaignPackageKeyRepository packageKeys;
    @Mock private ApplicationEventPublisher events;
    @Mock private SessionObjectiveChangeRepository objectiveChanges;

    @InjectMocks private SessionReferenceCleaner cleaner;

    private UUID campaignId;
    private Campaign campaign;
    private CampaignSession session;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        session = CampaignSession.idle(campaign);
        session.setId(UUID.randomUUID());
    }

    @Test
    void detachMapClearsWorkspaceMap() {
        var map = new dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap();
        map.setId(UUID.randomUUID());
        session.setWorkspaceMap(map);
        when(sessions.findAll()).thenReturn(List.of(session));

        cleaner.detachMap(map.getId());

        assertThat(session.getWorkspaceMap()).isNull();
        verify(sessions).save(session);
    }

    @Test
    void detachMapLowersCurtainForPresentedMap() {
        var map = new dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap();
        map.setId(UUID.randomUUID());
        session.setPresentedMap(map);
        session.setPresentationMode(CampaignSession.PresentationMode.MAP);
        when(sessions.findAll()).thenReturn(List.of(session));

        cleaner.detachMap(map.getId());

        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(session.getPresentedMap()).isNull();
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void detachHandoutLowersCurtain() {
        var handout = new dev.hendrikhoemberg.dmhelper.handout.data.Handout();
        handout.setId(UUID.randomUUID());
        session.setPresentedHandout(handout);
        session.setPresentationMode(CampaignSession.PresentationMode.HANDOUT);
        when(sessions.findAll()).thenReturn(List.of(session));

        cleaner.detachHandout(handout.getId());

        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(session.getPresentedHandout()).isNull();
    }

    @Test
    void detachPlanNoteClearsNote() {
        var note = new dev.hendrikhoemberg.dmhelper.notes.data.Note();
        note.setId(UUID.randomUUID());
        session.setPlanNote(note);
        when(sessions.findAll()).thenReturn(List.of(session));

        cleaner.detachPlanNote(note.getId());

        assertThat(session.getPlanNote()).isNull();
    }

    @Test
    void detachAttendeeRemovesPartyMember() {
        var member = new dev.hendrikhoemberg.dmhelper.party.data.PartyMember();
        member.setId(UUID.randomUUID());
        session.setAttendees(new ArrayList<>(List.of(member)));
        when(sessions.findAll()).thenReturn(List.of(session));

        cleaner.detachAttendee(member.getId());

        assertThat(session.getAttendees()).isEmpty();
    }

    @Test
    void detachSceneRemovesVisitsAndPackageKeys() {
        var scene = new dev.hendrikhoemberg.dmhelper.adventure.data.Scene();
        scene.setId(UUID.randomUUID());
        SessionSceneVisit visit = new SessionSceneVisit();
        visit.setId(UUID.randomUUID());
        visit.setSession(session);
        when(visits.findBySceneId(scene.getId())).thenReturn(List.of(visit));

        cleaner.detachScene(scene.getId());

        verify(packageKeys).deleteByCampaignIdAndEntityTypeAndEntityIdIn(
                campaignId, CampaignContentType.SESSION_SCENE_VISIT.name(), List.of(visit.getId()));
        verify(visits).deleteBySceneId(scene.getId());
    }

    @Test
    void detachCampaignPublishesInvalidation() {
        cleaner.detachCampaign(campaignId);
        verify(events).publishEvent(any(Object.class));
    }

    // ---- Objective change cleanup ----

    @Test
    void detachObjectiveDeletesChangesAndPackageKeys() {
        UUID objectiveId = UUID.randomUUID();
        SessionObjectiveChange change = new SessionObjectiveChange();
        change.setId(UUID.randomUUID());
        change.setSession(session);
        when(objectiveChanges.findByObjectiveId(objectiveId)).thenReturn(List.of(change));

        cleaner.detachObjective(objectiveId);

        verify(objectiveChanges).deleteByObjectiveId(objectiveId);
        verify(packageKeys).deleteByCampaignIdAndEntityTypeAndEntityIdIn(
                campaignId, CampaignContentType.SESSION_OBJECTIVE_CHANGE.name(), List.of(change.getId()));
    }

    @Test
    void detachObjectiveDoesNothingWhenNoChanges() {
        UUID objectiveId = UUID.randomUUID();
        when(objectiveChanges.findByObjectiveId(objectiveId)).thenReturn(List.of());

        cleaner.detachObjective(objectiveId);

        verify(objectiveChanges, never()).deleteByObjectiveId(any());
        verify(packageKeys, never()).deleteByCampaignIdAndEntityTypeAndEntityIdIn(any(), any(), any());
    }

    @Test
    void cleansUpObjectiveChangesOnSessionCompletion() {
        UUID objectiveId = UUID.randomUUID();
        SessionObjectiveChange change1 = new SessionObjectiveChange();
        change1.setId(UUID.randomUUID());
        change1.setSession(session);
        SessionObjectiveChange change2 = new SessionObjectiveChange();
        change2.setId(UUID.randomUUID());
        change2.setSession(session);
        when(objectiveChanges.findBySessionIdOrderByChangedAtAscIdAsc(session.getId()))
                .thenReturn(List.of(change1, change2));

        cleaner.detachSessionObjectiveChanges(session.getId(), campaignId);

        verify(objectiveChanges).deleteBySessionId(session.getId());
        verify(packageKeys).deleteByCampaignIdAndEntityTypeAndEntityIdIn(
                campaignId, CampaignContentType.SESSION_OBJECTIVE_CHANGE.name(),
                List.of(change1.getId(), change2.getId()));
    }
}
