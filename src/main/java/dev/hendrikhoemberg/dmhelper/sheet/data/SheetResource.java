package dev.hendrikhoemberg.dmhelper.sheet.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sheet_resource", indexes = {
    @Index(name = "idx_resource_sheet", columnList = "sheet_id")
})
public class SheetResource {

    public enum ResetRule { SHORT_REST, LONG_REST, NEVER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int currentUses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResetRule resetRule = ResetRule.LONG_REST;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CharacterSheet getSheet() { return sheet; }
    public void setSheet(CharacterSheet sheet) { this.sheet = sheet; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public int getCurrentUses() { return currentUses; }
    public void setCurrentUses(int currentUses) { this.currentUses = currentUses; }
    public ResetRule getResetRule() { return resetRule; }
    public void setResetRule(ResetRule resetRule) { this.resetRule = resetRule; }
}
