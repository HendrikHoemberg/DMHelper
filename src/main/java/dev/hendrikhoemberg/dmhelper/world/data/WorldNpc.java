package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "world_npc")
public class WorldNpc {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 500)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WorldDisposition disposition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faction_id")
    private Faction faction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private WorldLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statblock;

    @Column(columnDefinition = "CLOB")
    private String appearance;

    @Column(columnDefinition = "CLOB")
    private String voice;

    @Column(columnDefinition = "CLOB")
    private String motivation;

    @Column(columnDefinition = "CLOB")
    private String secret;

    @Column(columnDefinition = "CLOB")
    private String inventoryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorldNpcStatus status = WorldNpcStatus.UNKNOWN;

    @Column(length = 1000)
    private String tags;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public WorldDisposition getDisposition() { return disposition; }
    public void setDisposition(WorldDisposition disposition) { this.disposition = disposition; }

    public Faction getFaction() { return faction; }
    public void setFaction(Faction faction) { this.faction = faction; }

    public WorldLocation getLocation() { return location; }
    public void setLocation(WorldLocation location) { this.location = location; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }

    public StatBlock getStatblock() { return statblock; }
    public void setStatblock(StatBlock statblock) { this.statblock = statblock; }

    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }

    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }

    public String getMotivation() { return motivation; }
    public void setMotivation(String motivation) { this.motivation = motivation; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getInventoryText() { return inventoryText; }
    public void setInventoryText(String inventoryText) { this.inventoryText = inventoryText; }

    public WorldNpcStatus getStatus() { return status; }
    public void setStatus(WorldNpcStatus status) { this.status = status; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
