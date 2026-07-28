package dev.hendrikhoemberg.dmhelper.gamemap.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "token", indexes = {
    @Index(name = "idx_token_map", columnList = "map_id"),
})
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    @JsonIgnore
    private GameMap map;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 16)
    private String kind = "NPC";  // PC, NPC, MONSTER, OBJECT

    @Column(nullable = false)
    private int positionX = 0;

    @Column(nullable = false)
    private int positionY = 0;

    @Column(nullable = false)
    private int sizeCols = 1;

    @Column(nullable = false)
    private int sizeRows = 1;

    @Column(nullable = false, length = 7)
    private String color = "#7b68ee";

    @Column(nullable = false)
    private boolean hidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_member_id")
    private PartyMember partyMember;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(length = 100)
    private String icon;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public int getPositionX() { return positionX; }
    public void setPositionX(int positionX) { this.positionX = positionX; }

    public int getPositionY() { return positionY; }
    public void setPositionY(int positionY) { this.positionY = positionY; }

    public int getSizeCols() { return sizeCols; }
    public void setSizeCols(int sizeCols) { this.sizeCols = sizeCols; }

    public int getSizeRows() { return sizeRows; }
    public void setSizeRows(int sizeRows) { this.sizeRows = sizeRows; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public StatBlock getStatBlock() { return statBlock; }
    public void setStatBlock(StatBlock statBlock) { this.statBlock = statBlock; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
