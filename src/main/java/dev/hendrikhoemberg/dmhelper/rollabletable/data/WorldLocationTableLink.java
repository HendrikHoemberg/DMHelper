package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "world_location_table_link")
public class WorldLocationTableLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private WorldLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RollableTable table;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RollableTableLinkRole role;

    @Column(nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorldLocation getLocation() { return location; }
    public void setLocation(WorldLocation location) { this.location = location; }

    public RollableTable getTable() { return table; }
    public void setTable(RollableTable table) { this.table = table; }

    public RollableTableLinkRole getRole() { return role; }
    public void setRole(RollableTableLinkRole role) { this.role = role; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
