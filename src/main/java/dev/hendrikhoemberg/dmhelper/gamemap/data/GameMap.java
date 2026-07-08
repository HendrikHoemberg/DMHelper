package dev.hendrikhoemberg.dmhelper.gamemap.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "game_map", indexes = {
    @Index(name = "idx_gamemap_campaign", columnList = "campaign_id"),
})
public class GameMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int gridWidth = 30;

    @Column(nullable = false)
    private int gridHeight = 20;

    @Column(nullable = false)
    private int cellSizePx = 48;

    @Column(nullable = false)
    private int sortOrder;

    /** Reserved for post-v1 hex support (SPEC §9); always "SQUARE" in v1. */
    @Column(nullable = false, length = 16)
    private String gridType = "SQUARE";

    /** Movement mode: GRID (default, token movement snaps to cells) or FREEFORM (pixel movement). */
    @Column(nullable = false, length = 16)
    private String movementMode = "GRID";

    /** Whether to show grid lines on the battle map (independent of movement mode). */
    @Column(nullable = false)
    private boolean showGrid = true;

    /** Optimistic-lock counter for whole-document replaces (SPEC §5). */
    @Version
    private long version;

    @Column(columnDefinition = "CLOB")
    private String document;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getGridWidth() { return gridWidth; }
    public void setGridWidth(int gridWidth) { this.gridWidth = gridWidth; }

    public int getGridHeight() { return gridHeight; }
    public void setGridHeight(int gridHeight) { this.gridHeight = gridHeight; }

    public int getCellSizePx() { return cellSizePx; }
    public void setCellSizePx(int cellSizePx) { this.cellSizePx = cellSizePx; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getGridType() { return gridType; }
    public void setGridType(String gridType) { this.gridType = gridType; }

    public String getMovementMode() { return movementMode; }
    public void setMovementMode(String movementMode) { this.movementMode = movementMode; }

    public boolean isShowGrid() { return showGrid; }
    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public String getDocument() { return document; }
    public void setDocument(String document) { this.document = document; }
}
