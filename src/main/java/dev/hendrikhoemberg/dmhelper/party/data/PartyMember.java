package dev.hendrikhoemberg.dmhelper.party.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "party_member")
public class PartyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String characterName;

    @Column(length = 255)
    private String playerName;

    @Column(length = 255)
    private String classAndLevel;

    @Column(nullable = false)
    private int ac;

    @Column(nullable = false)
    private int maxHp;

    private int currentHp;

    @Column(nullable = false)
    private int initiativeBonus;

    @Column(nullable = false)
    private int speed;

    @Column(nullable = false)
    private int passivePerception;

    @Column(nullable = false)
    private int passiveInsight;

    @Column(nullable = false)
    private int passiveInvestigation;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int tempHp;

    @Column(nullable = false)
    private boolean inspiration;

    @Column(nullable = false)
    private int exhaustion;

    @Column(nullable = false)
    private int deathSaveSuccesses;

    @Column(nullable = false)
    private int deathSaveFailures;

    @Column(length = 255)
    private String concentratingOn;

    @Column(columnDefinition = "CLOB")
    private String conditionsJson;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getClassAndLevel() { return classAndLevel; }
    public void setClassAndLevel(String classAndLevel) { this.classAndLevel = classAndLevel; }

    public int getAc() { return ac; }
    public void setAc(int ac) { this.ac = ac; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }

    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int initiativeBonus) { this.initiativeBonus = initiativeBonus; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public int getPassivePerception() { return passivePerception; }
    public void setPassivePerception(int passivePerception) { this.passivePerception = passivePerception; }

    public int getPassiveInsight() { return passiveInsight; }
    public void setPassiveInsight(int passiveInsight) { this.passiveInsight = passiveInsight; }

    public int getPassiveInvestigation() { return passiveInvestigation; }
    public void setPassiveInvestigation(int passiveInvestigation) { this.passiveInvestigation = passiveInvestigation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getTempHp() { return tempHp; }
    public void setTempHp(int tempHp) { this.tempHp = tempHp; }

    public boolean isInspiration() { return inspiration; }
    public void setInspiration(boolean inspiration) { this.inspiration = inspiration; }

    public int getExhaustion() { return exhaustion; }
    public void setExhaustion(int exhaustion) { this.exhaustion = exhaustion; }

    public int getDeathSaveSuccesses() { return deathSaveSuccesses; }
    public void setDeathSaveSuccesses(int deathSaveSuccesses) { this.deathSaveSuccesses = deathSaveSuccesses; }

    public int getDeathSaveFailures() { return deathSaveFailures; }
    public void setDeathSaveFailures(int deathSaveFailures) { this.deathSaveFailures = deathSaveFailures; }

    public String getConcentratingOn() { return concentratingOn; }
    public void setConcentratingOn(String concentratingOn) { this.concentratingOn = concentratingOn; }

    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }

    @OneToOne(mappedBy = "partyMember", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private CharacterSheet characterSheet;

    public CharacterSheet getCharacterSheet() { return characterSheet; }
    public void setCharacterSheet(CharacterSheet characterSheet) { this.characterSheet = characterSheet; }
}
