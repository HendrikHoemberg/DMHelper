package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rollable_table_entry", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rollable_table_entry_key", columnNames = {"table_id", "entry_key"})
})
public class RollableTableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RollableTable table;

    @Column(name = "entry_key", nullable = false, length = 255)
    private String entryKey;

    private Integer rangeStart;

    private Integer rangeEnd;

    private Integer weight;

    @Column(columnDefinition = "CLOB")
    private String resultText;

    @Column(length = 255)
    private String quantityExpression;

    @Column(nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RollableTableEntryReference> references = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public RollableTable getTable() { return table; }
    public void setTable(RollableTable table) { this.table = table; }
    public String getEntryKey() { return entryKey; }
    public void setEntryKey(String entryKey) { this.entryKey = entryKey; }
    public Integer getRangeStart() { return rangeStart; }
    public void setRangeStart(Integer rangeStart) { this.rangeStart = rangeStart; }
    public Integer getRangeEnd() { return rangeEnd; }
    public void setRangeEnd(Integer rangeEnd) { this.rangeEnd = rangeEnd; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getQuantityExpression() { return quantityExpression; }
    public void setQuantityExpression(String quantityExpression) { this.quantityExpression = quantityExpression; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<RollableTableEntryReference> getReferences() { return references; }
    public void setReferences(List<RollableTableEntryReference> references) { this.references = references; }
}
