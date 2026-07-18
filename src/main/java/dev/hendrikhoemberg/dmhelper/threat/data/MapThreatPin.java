package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "map_threat_pin", uniqueConstraints = {
    @UniqueConstraint(name = "uq_map_threat_pin_key", columnNames = {"map_id", "pin_key"})
}, indexes = {
    @Index(name = "idx_map_threat_pin_map", columnList = "map_id"),
    @Index(name = "idx_map_threat_pin_threat", columnList = "threat_kind, threat_id")
})
public class MapThreatPin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private GameMap map;

    @Column(name = "pin_key", nullable = false, length = 255)
    private String pinKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_kind", nullable = false, length = 10)
    private ThreatKind threatKind;

    @Column(name = "threat_id", nullable = false)
    private UUID threatId;

    @Column(name = "x_px", nullable = false)
    private int xPx;

    @Column(name = "y_px", nullable = false)
    private int yPx;

    @Column(length = 500)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getPinKey() { return pinKey; }
    public void setPinKey(String pinKey) { this.pinKey = pinKey; }

    public ThreatKind getThreatKind() { return threatKind; }
    public void setThreatKind(ThreatKind threatKind) { this.threatKind = threatKind; }

    public UUID getThreatId() { return threatId; }
    public void setThreatId(UUID threatId) { this.threatId = threatId; }

    public int getXPx() { return xPx; }
    public void setXPx(int xPx) { this.xPx = xPx; }

    public int getYPx() { return yPx; }
    public void setYPx(int yPx) { this.yPx = yPx; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
