package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trap", indexes = {
    @Index(name = "idx_trap_campaign", columnList = "campaign_id_fk"),
    @Index(name = "idx_trap_name", columnList = "name"),
    @Index(name = "idx_trap_statblock", columnList = "statblock_id")
})
public class Trap extends AbstractThreat {

    @Column(name = "trigger_description", columnDefinition = "CLOB")
    private String triggerDescription;

    @Column(name = "trigger_area_hint", length = 1000)
    private String triggerAreaHint;

    @Column(name = "detection_passive_threshold")
    private Integer detectionPassiveThreshold;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "mode", column = @Column(name = "detection_mode", length = 10)),
        @AttributeOverride(name = "ability", column = @Column(name = "detection_ability", length = 50)),
        @AttributeOverride(name = "skill", column = @Column(name = "detection_skill", length = 50)),
        @AttributeOverride(name = "dc", column = @Column(name = "detection_dc"))
    })
    private ThreatCheck detectionCheck;

    @OneToMany(mappedBy = "trap", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<TrapDisarmMethod> disarmMethods = new ArrayList<>();

    @Column(name = "attack_bonus")
    private Integer attackBonus;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "mode", column = @Column(name = "save_mode", length = 10)),
        @AttributeOverride(name = "ability", column = @Column(name = "save_ability", length = 50)),
        @AttributeOverride(name = "skill", column = @Column(name = "save_skill", length = 50)),
        @AttributeOverride(name = "dc", column = @Column(name = "save_dc"))
    })
    private ThreatCheck save;

    @Column(name = "damage_expression", length = 255)
    private String damageExpression;

    @ElementCollection
    @CollectionTable(name = "trap_damage_type", joinColumns = @JoinColumn(name = "trap_id"))
    @Column(name = "damage_type", nullable = false, length = 20)
    @OrderColumn(name = "sort_order")
    @Enumerated(EnumType.STRING)
    @Fetch(FetchMode.SUBSELECT)
    private List<DamageType> damageTypes = new ArrayList<>();

    @Column(name = "additional_effect", columnDefinition = "CLOB")
    private String additionalEffect;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_mode", nullable = false, length = 16)
    private ThreatResetMode resetMode = ThreatResetMode.NONE;

    @Column(name = "reset_timing", length = 500)
    private String resetTiming;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statBlock;

    @Column(name = "countermeasure_notes", columnDefinition = "CLOB")
    private String countermeasureNotes;

    @OneToMany(mappedBy = "trap", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<ThreatReference> references = new ArrayList<>();

    public String getTriggerDescription() { return triggerDescription; }
    public void setTriggerDescription(String triggerDescription) { this.triggerDescription = triggerDescription; }

    public String getTriggerAreaHint() { return triggerAreaHint; }
    public void setTriggerAreaHint(String triggerAreaHint) { this.triggerAreaHint = triggerAreaHint; }

    public Integer getDetectionPassiveThreshold() { return detectionPassiveThreshold; }
    public void setDetectionPassiveThreshold(Integer detectionPassiveThreshold) {
        this.detectionPassiveThreshold = detectionPassiveThreshold;
    }

    public ThreatCheck getDetectionCheck() { return detectionCheck; }
    public void setDetectionCheck(ThreatCheck detectionCheck) { this.detectionCheck = detectionCheck; }

    public List<TrapDisarmMethod> getDisarmMethods() { return disarmMethods; }
    public void setDisarmMethods(List<TrapDisarmMethod> disarmMethods) { this.disarmMethods = disarmMethods; }

    public Integer getAttackBonus() { return attackBonus; }
    public void setAttackBonus(Integer attackBonus) { this.attackBonus = attackBonus; }

    public ThreatCheck getSave() { return save; }
    public void setSave(ThreatCheck save) { this.save = save; }

    public String getDamageExpression() { return damageExpression; }
    public void setDamageExpression(String damageExpression) { this.damageExpression = damageExpression; }

    public List<DamageType> getDamageTypes() { return damageTypes; }
    public void setDamageTypes(List<DamageType> damageTypes) { this.damageTypes = damageTypes; }

    public String getAdditionalEffect() { return additionalEffect; }
    public void setAdditionalEffect(String additionalEffect) { this.additionalEffect = additionalEffect; }

    public ThreatResetMode getResetMode() { return resetMode; }
    public void setResetMode(ThreatResetMode resetMode) { this.resetMode = resetMode; }

    public String getResetTiming() { return resetTiming; }
    public void setResetTiming(String resetTiming) { this.resetTiming = resetTiming; }

    public StatBlock getStatBlock() { return statBlock; }
    public void setStatBlock(StatBlock statBlock) { this.statBlock = statBlock; }

    public String getCountermeasureNotes() { return countermeasureNotes; }
    public void setCountermeasureNotes(String countermeasureNotes) { this.countermeasureNotes = countermeasureNotes; }

    public List<ThreatReference> getReferences() { return references; }
    public void setReferences(List<ThreatReference> references) { this.references = references; }
}
