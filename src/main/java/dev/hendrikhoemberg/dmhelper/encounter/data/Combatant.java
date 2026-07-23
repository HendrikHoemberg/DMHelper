package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "combatant", indexes = {
    @Index(name = "idx_combatant_encounter", columnList = "encounter_id"),
    @Index(name = "idx_combatant_group", columnList = "group_id"),
})
public class Combatant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private Integer initiative;

    private int tieBreaker;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int maxHp;

    private int currentHp;

    private int tempHp;

    @Column(nullable = false, length = 16)
    private String kind = "NPC";

    @Column(length = 36)
    private String groupId;

    private boolean groupLeader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id")
    private Token token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_member_id")
    private PartyMember partyMember;

    private boolean defeated = false;

    private boolean hidden = false;

    @Column(columnDefinition = "CLOB")
    private String conditionsJson = "[]";

    @Column(length = 255)
    private String concentratingOn;

    private boolean concentrationCheckPending = false;

    private int legendaryActionsUsed;

    private int legendaryResistancesUsed;

    private int legendaryActionsMax;

    private int legendaryResistancesMax;

    @Column(columnDefinition = "CLOB")
    private String rechargedAbilities = "[]";

    @Column(length = 255)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wave_id")
    private EncounterWave wave;

    @Column(name = "start_x")
    private Integer startX;

    @Column(name = "start_y")
    private Integer startY;

    @Column(name = "placement_region_key", length = 100)
    private String placementRegionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_kind", length = 10)
    private ThreatKind threatKind;

    @Column(name = "threat_id")
    private UUID threatId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getInitiative() { return initiative; }
    public void setInitiative(Integer initiative) { this.initiative = initiative; }

    public int getTieBreaker() { return tieBreaker; }
    public void setTieBreaker(int tieBreaker) { this.tieBreaker = tieBreaker; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }

    public int getTempHp() { return tempHp; }
    public void setTempHp(int tempHp) { this.tempHp = tempHp; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public boolean isGroupLeader() { return groupLeader; }
    public void setGroupLeader(boolean groupLeader) { this.groupLeader = groupLeader; }

    public Token getToken() { return token; }
    public void setToken(Token token) { this.token = token; }

    public StatBlock getStatBlock() { return statBlock; }
    public void setStatBlock(StatBlock statBlock) { this.statBlock = statBlock; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public boolean isDefeated() { return defeated; }
    public void setDefeated(boolean defeated) { this.defeated = defeated; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }

    public String getConcentratingOn() { return concentratingOn; }
    public void setConcentratingOn(String concentratingOn) { this.concentratingOn = concentratingOn; }

    public boolean isConcentrationCheckPending() { return concentrationCheckPending; }
    public void setConcentrationCheckPending(boolean concentrationCheckPending) { this.concentrationCheckPending = concentrationCheckPending; }

    public int getLegendaryActionsUsed() { return legendaryActionsUsed; }
    public void setLegendaryActionsUsed(int legendaryActionsUsed) { this.legendaryActionsUsed = legendaryActionsUsed; }

    public int getLegendaryResistancesUsed() { return legendaryResistancesUsed; }
    public void setLegendaryResistancesUsed(int legendaryResistancesUsed) { this.legendaryResistancesUsed = legendaryResistancesUsed; }

    public int getLegendaryActionsMax() { return legendaryActionsMax; }
    public void setLegendaryActionsMax(int legendaryActionsMax) { this.legendaryActionsMax = legendaryActionsMax; }

    public int getLegendaryResistancesMax() { return legendaryResistancesMax; }
    public void setLegendaryResistancesMax(int legendaryResistancesMax) { this.legendaryResistancesMax = legendaryResistancesMax; }

    public String getRechargedAbilities() { return rechargedAbilities; }
    public void setRechargedAbilities(String rechargedAbilities) { this.rechargedAbilities = rechargedAbilities; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public EncounterWave getWave() { return wave; }
    public void setWave(EncounterWave wave) { this.wave = wave; }

    public Integer getStartX() { return startX; }
    public void setStartX(Integer startX) { this.startX = startX; }

    public Integer getStartY() { return startY; }
    public void setStartY(Integer startY) { this.startY = startY; }

    public String getPlacementRegionKey() { return placementRegionKey; }
    public void setPlacementRegionKey(String placementRegionKey) { this.placementRegionKey = placementRegionKey; }

    public ThreatKind getThreatKind() { return threatKind; }
    public void setThreatKind(ThreatKind threatKind) { this.threatKind = threatKind; }

    public UUID getThreatId() { return threatId; }
    public void setThreatId(UUID threatId) { this.threatId = threatId; }
}
