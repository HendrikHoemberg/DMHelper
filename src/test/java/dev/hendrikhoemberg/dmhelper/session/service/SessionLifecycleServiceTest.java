package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionLifecycleServiceTest {

    @Mock private Clock clock;
    @Mock private CampaignRepository campaigns;
    @Mock private CampaignSessionRepository sessions;
    @Mock private SessionSceneVisitRepository visits;
    @Mock private GameMapRepository maps;
    @Mock private PartyMemberRepository party;
    @Mock private NoteRepository notes;
    @Mock private CalendarService calendar;

    @InjectMocks private SessionLifecycleService service;

    private UUID campaignId;
    private UUID mapId;
    private Campaign campaign;
    private GameMap map;
    private PartyMember aria;
    private PartyMember borin;
    private Note plan;
    private CampaignSession session;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        mapId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        map = new GameMap();
        map.setId(mapId);
        map.setCampaign(campaign);
        aria = new PartyMember();
        aria.setId(UUID.randomUUID());
        aria.setCharacterName("Aria");
        aria.setCampaign(campaign);
        borin = new PartyMember();
        borin.setId(UUID.randomUUID());
        borin.setCharacterName("Borin");
        borin.setCampaign(campaign);
        plan = new Note();
        plan.setId(UUID.randomUUID());
        plan.setType(NoteType.SESSION_PLAN);
        session = CampaignSession.idle(campaign);
    }

    @Test
    void startSnapshotsActiveAttendanceDatePlanAndSelectedMap() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T18:00:00Z"));
        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId))
                .thenReturn(List.of(aria, borin));
        when(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN))
                .thenReturn(List.of(plan));
        when(campaigns.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(mapId)).thenReturn(Optional.of(map));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.start(campaignId, mapId);

        assertThat(result.getStatus()).isEqualTo(CampaignSession.Status.RUNNING);
        assertThat(result.getStartedAt()).isEqualTo(Instant.parse("2026-07-16T18:00:00Z"));
        assertThat(result.getAttendees()).containsExactly(aria, borin);
        assertThat(result.getStartInGameYear()).isEqualTo(1492);
        assertThat(result.getWorkspaceMap()).isEqualTo(map);
        assertThat(result.getPlanNote()).isEqualTo(plan);
        assertThat(result.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
    }

    @Test
    void startSucceedsWithoutMapAndWithoutPlan() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T18:00:00Z"));
        when(calendar.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 6, 12));
        when(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId))
                .thenReturn(List.of(aria));
        when(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN))
                .thenReturn(List.of());
        when(campaigns.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.start(campaignId, null);

        assertThat(result.getStatus()).isEqualTo(CampaignSession.Status.RUNNING);
        assertThat(result.getWorkspaceMap()).isNull();
        assertThat(result.getPlanNote()).isNull();
    }

    @Test
    void rejectsIllegalLifecycleTransitionsWithoutMutatingState() {
        session.setStatus(CampaignSession.Status.IDLE);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.pause(campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a running session can be paused.");
        assertThat(session.getStatus()).isEqualTo(CampaignSession.Status.IDLE);
    }

    @Test
    void pauseSetsStatusAndPausedAt() {
        Instant freeze = Instant.parse("2026-07-16T20:00:00Z");
        when(clock.instant()).thenReturn(freeze);
        session.setStatus(CampaignSession.Status.RUNNING);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.pause(campaignId);

        assertThat(result.getStatus()).isEqualTo(CampaignSession.Status.PAUSED);
        assertThat(result.getPausedAt()).isEqualTo(freeze);
    }

    @Test
    void resumeOnlyFromPaused() {
        session.setStatus(CampaignSession.Status.PAUSED);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.resume(campaignId);
        assertThat(result.getStatus()).isEqualTo(CampaignSession.Status.RUNNING);
    }

    @Test
    void setAttendeesReplacesListAndRejectsForeignMembers() {
        session.setStatus(CampaignSession.Status.RUNNING);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(party.findAllById(List.of(aria.getId(), borin.getId())))
                .thenReturn(List.of(aria, borin));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.setAttendees(campaignId, List.of(aria.getId(), borin.getId()));

        assertThat(result.getAttendees()).containsExactly(aria, borin);
    }

    @Test
    void setAttendeesRejectsForeignCampaignMembers() {
        session.setStatus(CampaignSession.Status.RUNNING);
        PartyMember foreign = new PartyMember();
        foreign.setId(UUID.randomUUID());
        foreign.setCharacterName("Spy");
        Campaign otherCampaign = new Campaign();
        otherCampaign.setId(UUID.randomUUID());
        foreign.setCampaign(otherCampaign);

        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(party.findAllById(List.of(foreign.getId())))
                .thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.setAttendees(campaignId, List.of(foreign.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belong to this campaign");
    }

    @Test
    void setWorkspaceMapUpdatesSession() {
        session.setStatus(CampaignSession.Status.RUNNING);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(maps.findById(mapId)).thenReturn(Optional.of(map));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignSession result = service.setWorkspaceMap(campaignId, mapId);

        assertThat(result.getWorkspaceMap()).isEqualTo(map);
    }

    @Test
    void onlyRunningSessionCanBePaused() {
        session.setStatus(CampaignSession.Status.REVIEW);
        when(sessions.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.pause(campaignId))
                .isInstanceOf(IllegalStateException.class);
    }
}
