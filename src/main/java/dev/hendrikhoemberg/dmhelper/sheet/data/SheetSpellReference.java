package dev.hendrikhoemberg.dmhelper.sheet.data;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sheet_spell_ref", indexes = {
    @Index(name = "idx_spellref_sheet", columnList = "sheet_id"),
    @Index(name = "idx_spellref_spell", columnList = "spell_id")
})
public class SheetSpellReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private CharacterSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spell_id", nullable = false)
    private Spell spell;

    @Column(nullable = false)
    private boolean prepared = false;

    @Column(length = 100)
    private String sourceClass; // sourceKey of the class providing this spell (for multiclass)

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CharacterSheet getSheet() { return sheet; }
    public void setSheet(CharacterSheet sheet) { this.sheet = sheet; }
    public Spell getSpell() { return spell; }
    public void setSpell(Spell spell) { this.spell = spell; }
    public boolean isPrepared() { return prepared; }
    public void setPrepared(boolean prepared) { this.prepared = prepared; }
    public String getSourceClass() { return sourceClass; }
    public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }
}
