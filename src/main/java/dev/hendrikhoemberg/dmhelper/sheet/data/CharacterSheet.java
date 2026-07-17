package dev.hendrikhoemberg.dmhelper.sheet.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
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

    @Column(columnDefinition = "CLOB")
    private String attacksJson;

    @Column(columnDefinition = "CLOB")
    private String featuresJson;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference> spells = new ArrayList<>();

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

    public List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource> getResources() { return resources; }
    public void setResources(List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource> resources) { this.resources = resources; }

    public List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference> getSpells() { return spells; }
    public void setSpells(List<dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference> spells) { this.spells = spells; }

    public String getAttacksJson() { return attacksJson; }
    public void setAttacksJson(String attacksJson) { this.attacksJson = attacksJson; }

    public String getFeaturesJson() { return featuresJson; }
    public void setFeaturesJson(String featuresJson) { this.featuresJson = featuresJson; }
}
