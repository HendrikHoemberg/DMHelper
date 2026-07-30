package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "campaign_session", uniqueConstraints =
        @UniqueConstraint(name = "uq_campaign_session_campaign", columnNames = "campaign_id"))
public class CampaignSession {

    public enum Status { IDLE, RUNNING, PAUSED, REVIEW }
    public enum PresentationMode { CURTAIN, MAP, HANDOUT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.IDLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_review_status", length = 16)
    private Status preReviewStatus;

    private Instant startedAt;

    private Instant pausedAt;

    private Instant reviewStartedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Integer startInGameYear;

    private Integer startInGameMonth;

    private Integer startInGameDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_note_id")
    private Note planNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_map_id")
    private GameMap workspaceMap;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PresentationMode presentationMode = PresentationMode.CURTAIN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presented_map_id")
    private GameMap presentedMap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presented_handout_id")
    private Handout presentedHandout;

    @Column(columnDefinition = "CLOB")
    private String draftBody;

    @ManyToMany
    @JoinTable(name = "campaign_session_attendee",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "party_member_id"))
    @OrderBy("characterName asc")
    private List<PartyMember> attendees = new ArrayList<>();

    @Version
    private long version;

    public static CampaignSession idle(Campaign campaign) {
        CampaignSession value = new CampaignSession();
        value.campaign = campaign;
        value.status = Status.IDLE;
        value.presentationMode = PresentationMode.CURTAIN;
        value.updatedAt = Instant.EPOCH;
        return value;
    }

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public boolean isOpen() { return status != Status.IDLE; }
}
