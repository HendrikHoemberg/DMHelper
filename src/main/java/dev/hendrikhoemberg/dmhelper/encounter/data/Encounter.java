package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "encounter", indexes = {
    @Index(name = "idx_encounter_campaign", columnList = "campaign_id"),
    @Index(name = "idx_encounter_map", columnList = "map_id"),
})
public class Encounter {

    public enum Status { PLANNED, ACTIVE, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private GameMap map;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PLANNED;

    @Column(nullable = false)
    private int round = 0;

    @Column(nullable = false)
    private int activeTurnIndex = -1;

    @Column(nullable = false)
    private long logSequence = 0;

    @Column(length = 255)
    private String lairActionName;

    @Column(columnDefinition = "CLOB")
    private String lairActionDescription;

    @Column(nullable = false)
    private boolean lairActionTriggered = false;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getActiveTurnIndex() { return activeTurnIndex; }
    public void setActiveTurnIndex(int activeTurnIndex) { this.activeTurnIndex = activeTurnIndex; }

    public long getLogSequence() { return logSequence; }
    public void setLogSequence(long logSequence) { this.logSequence = logSequence; }

    public String getLairActionName() { return lairActionName; }
    public void setLairActionName(String lairActionName) { this.lairActionName = lairActionName; }

    public String getLairActionDescription() { return lairActionDescription; }
    public void setLairActionDescription(String lairActionDescription) { this.lairActionDescription = lairActionDescription; }

    public boolean isLairActionTriggered() { return lairActionTriggered; }
    public void setLairActionTriggered(boolean lairActionTriggered) { this.lairActionTriggered = lairActionTriggered; }
}
