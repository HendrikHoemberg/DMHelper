package dev.hendrikhoemberg.dmhelper.threat.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "trap_disarm_method", uniqueConstraints = {
    @UniqueConstraint(name = "uq_trap_disarm_method_key", columnNames = {"trap_id", "method_key"})
}, indexes = {
    @Index(name = "idx_trap_disarm_method_trap", columnList = "trap_id")
})
public class TrapDisarmMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trap_id", nullable = false)
    private Trap trap;

    @Column(name = "method_key", nullable = false, length = 255)
    private String methodKey;

    @Column(nullable = false, length = 500)
    private String label;

    @Column(length = 50)
    private String ability;

    @Column(length = 50)
    private String skill;

    @Column(length = 100)
    private String tool;

    private Integer dc;

    @Column(name = "failure_consequence", columnDefinition = "CLOB")
    private String failureConsequence;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Trap getTrap() { return trap; }
    public void setTrap(Trap trap) { this.trap = trap; }

    public String getMethodKey() { return methodKey; }
    public void setMethodKey(String methodKey) { this.methodKey = methodKey; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getAbility() { return ability; }
    public void setAbility(String ability) { this.ability = ability; }

    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }

    public Integer getDc() { return dc; }
    public void setDc(Integer dc) { this.dc = dc; }

    public String getFailureConsequence() { return failureConsequence; }
    public void setFailureConsequence(String failureConsequence) { this.failureConsequence = failureConsequence; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
