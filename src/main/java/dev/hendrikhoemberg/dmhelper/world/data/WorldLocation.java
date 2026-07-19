package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "world_location")
public class WorldLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 500)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationKind kind = LocationKind.SITE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_location_id")
    private WorldLocation parentLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private GameMap map;

    @Column(length = 100)
    private String mapRegionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_audio_cue_id")
    private AudioCue locationAudioCue;

    @Column(columnDefinition = "CLOB")
    private String summary;

    @Column(columnDefinition = "CLOB")
    private String services;

    @Column(columnDefinition = "CLOB")
    private String secrets;

    @Column(length = 1000)
    private String tags;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "world_location_encounter",
            joinColumns = @JoinColumn(name = "location_id"),
            inverseJoinColumns = @JoinColumn(name = "encounter_id"))
    @OrderColumn(name = "sort_order")
    private List<Encounter> encounters = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "world_location_travel",
            joinColumns = @JoinColumn(name = "location_id"),
            inverseJoinColumns = @JoinColumn(name = "target_location_id"))
    @OrderColumn(name = "sort_order")
    private List<WorldLocation> travelLocations = new ArrayList<>();

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

    public LocationKind getKind() { return kind; }
    public void setKind(LocationKind kind) { this.kind = kind; }

    public WorldLocation getParentLocation() { return parentLocation; }
    public void setParentLocation(WorldLocation parentLocation) { this.parentLocation = parentLocation; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getMapRegionKey() { return mapRegionKey; }
    public void setMapRegionKey(String mapRegionKey) { this.mapRegionKey = mapRegionKey; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }

    public AudioCue getLocationAudioCue() { return locationAudioCue; }
    public void setLocationAudioCue(AudioCue locationAudioCue) { this.locationAudioCue = locationAudioCue; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getServices() { return services; }
    public void setServices(String services) { this.services = services; }

    public String getSecrets() { return secrets; }
    public void setSecrets(String secrets) { this.secrets = secrets; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<Encounter> getEncounters() { return encounters; }
    public void setEncounters(List<Encounter> encounters) {
        this.encounters = encounters != null ? encounters : new ArrayList<>();
    }

    public List<WorldLocation> getTravelLocations() { return travelLocations; }
    public void setTravelLocations(List<WorldLocation> travelLocations) {
        this.travelLocations = travelLocations != null ? travelLocations : new ArrayList<>();
    }
}
