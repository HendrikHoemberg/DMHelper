package dev.hendrikhoemberg.dmhelper.sheet.data;

import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "character_sheet")
public class CharacterSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_member_id", nullable = false, unique = true)
    private PartyMember partyMember;

    @Column(columnDefinition = "CLOB")
    private String abilityScores;

    @Column(columnDefinition = "CLOB")
    private String classLevels;

    @Column(columnDefinition = "CLOB")
    private String proficiencies;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id")
    private Species species;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_id")
    private Background background;

    @Column(columnDefinition = "CLOB")
    private String featRefs;

    @Column(nullable = false)
    private int xp = 0;

    @Column(columnDefinition = "CLOB")
    private String overrides;

    @Column(nullable = false)
    private int hitDiceUsed = 0;

    @Column(columnDefinition = "CLOB")
    private String spellSlotsUsed;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public String getAbilityScores() { return abilityScores; }
    public void setAbilityScores(String abilityScores) { this.abilityScores = abilityScores; }

    public String getClassLevels() { return classLevels; }
    public void setClassLevels(String classLevels) { this.classLevels = classLevels; }

    public String getProficiencies() { return proficiencies; }
    public void setProficiencies(String proficiencies) { this.proficiencies = proficiencies; }

    public Species getSpecies() { return species; }
    public void setSpecies(Species species) { this.species = species; }

    public Background getBackground() { return background; }
    public void setBackground(Background background) { this.background = background; }

    public String getFeatRefs() { return featRefs; }
    public void setFeatRefs(String featRefs) { this.featRefs = featRefs; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public String getOverrides() { return overrides; }
    public void setOverrides(String overrides) { this.overrides = overrides; }

    public int getHitDiceUsed() { return hitDiceUsed; }
    public void setHitDiceUsed(int hitDiceUsed) { this.hitDiceUsed = hitDiceUsed; }

    public String getSpellSlotsUsed() { return spellSlotsUsed; }
    public void setSpellSlotsUsed(String spellSlotsUsed) { this.spellSlotsUsed = spellSlotsUsed; }
}
