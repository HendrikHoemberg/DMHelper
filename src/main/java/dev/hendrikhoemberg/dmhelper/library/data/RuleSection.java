package dev.hendrikhoemberg.dmhelper.library.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "rule_section", indexes = {
    @Index(name = "idx_rule_name", columnList = "name"),
    @Index(name = "idx_rule_ruleset", columnList = "ruleset"),
})
public class RuleSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String sourceKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(length = 100)
    private String parentKey;

    private int sortOrder;

    @Column(length = 100)
    private String ruleset;

    private int initialHeaderLevel;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParentKey() { return parentKey; }
    public void setParentKey(String parentKey) { this.parentKey = parentKey; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getRuleset() { return ruleset; }
    public void setRuleset(String ruleset) { this.ruleset = ruleset; }
    public int getInitialHeaderLevel() { return initialHeaderLevel; }
    public void setInitialHeaderLevel(int initialHeaderLevel) { this.initialHeaderLevel = initialHeaderLevel; }
}
