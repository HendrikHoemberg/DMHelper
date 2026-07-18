package dev.hendrikhoemberg.dmhelper.threat.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ThreatCheck {

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 10)
    private ThreatCheckMode mode;

    @Column(name = "ability", length = 50)
    private String ability;

    @Column(name = "skill", length = 50)
    private String skill;

    @Column(name = "dc")
    private Integer dc;

    public ThreatCheckMode getMode() { return mode; }
    public void setMode(ThreatCheckMode mode) { this.mode = mode; }

    public String getAbility() { return ability; }
    public void setAbility(String ability) { this.ability = ability; }

    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }

    public Integer getDc() { return dc; }
    public void setDc(Integer dc) { this.dc = dc; }
}
