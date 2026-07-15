package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class CampaignCatalogResolver {
    private final StatBlockRepository statBlocks;
    private final SpeciesRepository species;
    private final BackgroundRepository backgrounds;
    private final CharacterClassRepository classes;
    private final FeatRepository feats;
    private final SpellRepository spells;
    private final MagicItemRepository magicItems;
    private final EquipmentItemRepository equipmentItems;

    public CampaignCatalogResolver(StatBlockRepository statBlocks,
                                   SpeciesRepository species,
                                   BackgroundRepository backgrounds,
                                   CharacterClassRepository classes,
                                   FeatRepository feats,
                                   SpellRepository spells,
                                   MagicItemRepository magicItems,
                                   EquipmentItemRepository equipmentItems) {
        this.statBlocks = statBlocks;
        this.species = species;
        this.backgrounds = backgrounds;
        this.classes = classes;
        this.feats = feats;
        this.spells = spells;
        this.magicItems = magicItems;
        this.equipmentItems = equipmentItems;
    }

    public boolean hasStatBlock(String sourceKey) {
        return sourceKey != null && statBlocks.findBySourceKey(sourceKey).isPresent();
    }

    public boolean hasSpecies(String sourceKey) {
        return sourceKey != null && species.findBySourceKey(sourceKey) != null;
    }

    public boolean hasBackground(String sourceKey) {
        return sourceKey != null && backgrounds.findBySourceKey(sourceKey) != null;
    }

    public boolean hasCharacterClass(String sourceKey) {
        return sourceKey != null && classes.findBySourceKey(sourceKey).isPresent();
    }

    public boolean hasFeat(String sourceKey) {
        return sourceKey != null && feats.findBySourceKeyIn(List.of(sourceKey)).stream()
                .anyMatch(feat -> sourceKey.equals(feat.getSourceKey()));
    }

    public boolean hasSpell(String sourceKey) {
        return sourceKey != null && spells.existsBySourceKey(sourceKey);
    }

    public boolean hasMagicItem(String sourceKey) {
        return sourceKey != null && magicItems.findBySourceKey(sourceKey).isPresent();
    }

    public boolean hasEquipmentItem(String sourceKey) {
        return sourceKey != null && equipmentItems.findBySourceKey(sourceKey).isPresent();
    }
}
