package dev.hendrikhoemberg.dmhelper.threat.data;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hazard", indexes = {
    @Index(name = "idx_hazard_campaign", columnList = "campaign_id_fk"),
    @Index(name = "idx_hazard_name", columnList = "name")
})
public class Hazard extends AbstractThreat {

    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_mode", nullable = false, length = 20)
    private HazardExposureMode exposureMode;

    @Column(name = "exposure_text", columnDefinition = "CLOB")
    private String exposureText;

    @Column(name = "area_hint", length = 1000)
    private String areaHint;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "mode", column = @Column(name = "check_mode", length = 10)),
        @AttributeOverride(name = "ability", column = @Column(name = "check_ability", length = 50)),
        @AttributeOverride(name = "skill", column = @Column(name = "check_skill", length = 50)),
        @AttributeOverride(name = "dc", column = @Column(name = "check_dc"))
    })
    private ThreatCheck check;

    @Column(name = "damage_expression", length = 255)
    private String damageExpression;

    @ElementCollection
    @CollectionTable(name = "hazard_damage_type", joinColumns = @JoinColumn(name = "hazard_id"))
    @Column(name = "damage_type", nullable = false, length = 20)
    @OrderColumn(name = "sort_order")
    @Enumerated(EnumType.STRING)
    @Fetch(FetchMode.SUBSELECT)
    private List<DamageType> damageTypes = new ArrayList<>();

    @Column(name = "escalation_text", columnDefinition = "CLOB")
    private String escalationText;

    @Column(name = "ending_conditions", columnDefinition = "CLOB")
    private String endingConditions;

    @OneToMany(mappedBy = "hazard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<ThreatReference> references = new ArrayList<>();

    public HazardExposureMode getExposureMode() { return exposureMode; }
    public void setExposureMode(HazardExposureMode exposureMode) { this.exposureMode = exposureMode; }

    public String getExposureText() { return exposureText; }
    public void setExposureText(String exposureText) { this.exposureText = exposureText; }

    public String getAreaHint() { return areaHint; }
    public void setAreaHint(String areaHint) { this.areaHint = areaHint; }

    public ThreatCheck getCheck() { return check; }
    public void setCheck(ThreatCheck check) { this.check = check; }

    public String getDamageExpression() { return damageExpression; }
    public void setDamageExpression(String damageExpression) { this.damageExpression = damageExpression; }

    public List<DamageType> getDamageTypes() { return damageTypes; }
    public void setDamageTypes(List<DamageType> damageTypes) { this.damageTypes = damageTypes; }

    public String getEscalationText() { return escalationText; }
    public void setEscalationText(String escalationText) { this.escalationText = escalationText; }

    public String getEndingConditions() { return endingConditions; }
    public void setEndingConditions(String endingConditions) { this.endingConditions = endingConditions; }

    public List<ThreatReference> getReferences() { return references; }
    public void setReferences(List<ThreatReference> references) { this.references = references; }
}
