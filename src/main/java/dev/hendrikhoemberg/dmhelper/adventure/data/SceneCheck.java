package dev.hendrikhoemberg.dmhelper.adventure.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "scene_check", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scene_check_scene_sort", columnNames = {"scene_id", "sort_order"})
})
public class SceneCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(length = 500)
    private String label;

    @Column(length = 50)
    private String ability;

    @Column(length = 50)
    private String skill;

    private Integer dc;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SceneCheckVisibility visibility;

    @Column(columnDefinition = "CLOB")
    private String success;

    @Column(columnDefinition = "CLOB")
    private String failure;

    @Column(columnDefinition = "CLOB")
    private String partial;

    @Column(length = 20)
    private String ruleScope;

    @Column(length = 100)
    private String ruleRuleset;

    @Column(length = 100)
    private String ruleSourceKey;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false)
    private int sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getAbility() { return ability; }
    public void setAbility(String ability) { this.ability = ability; }

    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }

    public Integer getDc() { return dc; }
    public void setDc(Integer dc) { this.dc = dc; }

    public SceneCheckVisibility getVisibility() { return visibility; }
    public void setVisibility(SceneCheckVisibility visibility) { this.visibility = visibility; }

    public String getSuccess() { return success; }
    public void setSuccess(String success) { this.success = success; }

    public String getFailure() { return failure; }
    public void setFailure(String failure) { this.failure = failure; }

    public String getPartial() { return partial; }
    public void setPartial(String partial) { this.partial = partial; }

    public String getRuleScope() { return ruleScope; }
    public void setRuleScope(String ruleScope) { this.ruleScope = ruleScope; }

    public String getRuleRuleset() { return ruleRuleset; }
    public void setRuleRuleset(String ruleRuleset) { this.ruleRuleset = ruleRuleset; }

    public String getRuleSourceKey() { return ruleSourceKey; }
    public void setRuleSourceKey(String ruleSourceKey) { this.ruleSourceKey = ruleSourceKey; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
