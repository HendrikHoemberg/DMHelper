package dev.hendrikhoemberg.dmhelper.calendar.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "timeline_event")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    private int inGameYear;
    private int inGameMonth; // 0-indexed
    private int inGameDay;   // 1-indexed

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note noteRef;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public int getInGameYear() { return inGameYear; }
    public void setInGameYear(int inGameYear) { this.inGameYear = inGameYear; }
    public int getInGameMonth() { return inGameMonth; }
    public void setInGameMonth(int inGameMonth) { this.inGameMonth = inGameMonth; }
    public int getInGameDay() { return inGameDay; }
    public void setInGameDay(int inGameDay) { this.inGameDay = inGameDay; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Note getNoteRef() { return noteRef; }
    public void setNoteRef(Note noteRef) { this.noteRef = noteRef; }
}
