package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomSpellDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomConditionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomRuleDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomEquipmentDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomMagicItemDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomClassDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomSpeciesDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomBackgroundDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CustomFeatDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ProvenanceDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.StatBlockDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class LibrarySectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final StatBlockRepository statBlockRepository;
    private final SpellRepository spellRepository;
    private final ConditionRepository conditionRepository;
    private final RuleSectionRepository ruleSectionRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;
    private final CharacterClassRepository characterClassRepository;
    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final FeatRepository featRepository;

    public LibrarySectionAdapter(StatBlockRepository statBlockRepository,
                                  SpellRepository spellRepository,
                                  ConditionRepository conditionRepository,
                                  RuleSectionRepository ruleSectionRepository,
                                  EquipmentItemRepository equipmentItemRepository,
                                  MagicItemRepository magicItemRepository,
                                  CharacterClassRepository characterClassRepository,
                                  SpeciesRepository speciesRepository,
                                  BackgroundRepository backgroundRepository,
                                  FeatRepository featRepository) {
        this.statBlockRepository = statBlockRepository;
        this.spellRepository = spellRepository;
        this.conditionRepository = conditionRepository;
        this.ruleSectionRepository = ruleSectionRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
        this.characterClassRepository = characterClassRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.featRepository = featRepository;
    }

    @Override
    public String sectionName() {
        return "Library";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        exportStatBlocks(context, target);
        exportSpells(context, target);
        exportConditions(context, target);
        exportRules(context, target);
        exportEquipment(context, target);
        exportMagicItems(context, target);
        exportClasses(context, target);
        exportSpecies(context, target);
        exportBackgrounds(context, target);
        exportFeats(context, target);
    }

    private void exportStatBlocks(CampaignExportContext context, CampaignManifestAssembler target) {
        List<StatBlock> customBlocks = statBlockRepository.findByCampaignIdOrderByNameAscIdAsc(context.campaignId())
                .stream()
                .filter(sb -> sb.getSource() == ContentSource.CUSTOM)
                .toList();

        List<StatBlockDto> dtos = customBlocks.stream()
                .map(sb -> {
                    String key = context.key(CampaignContentType.STATBLOCK, sb.getId(), sb.getName());
                    return new StatBlockDto(
                            key, sb.getSourceKey(), sb.getName(), sb.getCr(), sb.getType(),
                            sb.getSize(), sb.getAlignment(), sb.getAc(), sb.getHp(), sb.getSpeed(),
                            sb.getStrScore(), sb.getDexScore(), sb.getConScore(), sb.getIntScore(),
                            sb.getWisScore(), sb.getChaScore(),
                            sb.getStrSave(), sb.getDexSave(), sb.getConSave(), sb.getIntSave(),
                            sb.getWisSave(), sb.getChaSave(),
                            sb.getSkills(), sb.getDamageVulnerabilities(), sb.getDamageResistances(),
                            sb.getDamageImmunities(), sb.getConditionImmunities(),
                            sb.getSenses(), sb.getLanguages(),
                            sb.getTraits(), sb.getActions(), sb.getBonusActions(), sb.getReactions(),
                            sb.getLegendaryActions(), sb.getLegendaryDescription(), sb.getLairActions(),
                            sb.getXp(), sb.getCreatedAt()
                    );
                })
                .toList();

        target.customStatBlocks(dtos);
    }

    private void exportSpells(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomSpellDto> dtos = spellRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(s -> s.getSource() == ContentSource.CUSTOM)
                .map(s -> {
                    String key = context.key(CampaignContentType.SPELL, s.getId(), s.getName());
                    return new CustomSpellDto(
                            key, s.getSourceKey(), s.getName(), s.getLevel(), s.getSchool(),
                            s.getCastingTime(), s.getRange(), s.getComponents(), s.getDuration(),
                            s.getDescription(), s.getHigherLevel(), s.isRitual(), s.isConcentration(),
                            toProvenanceDto(s.getProvenance())
                    );
                })
                .toList();

        target.customSpells(dtos);
    }

    private void exportConditions(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomConditionDto> dtos = conditionRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(c -> c.getSource() == ContentSource.CUSTOM)
                .map(c -> {
                    String key = context.key(CampaignContentType.CONDITION, c.getId(), c.getName());
                    return new CustomConditionDto(
                            key, c.getSourceKey(), c.getName(), c.getDescription(),
                            toProvenanceDto(c.getProvenance())
                    );
                })
                .toList();

        target.customConditions(dtos);
    }

    private void exportRules(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomRuleDto> dtos = ruleSectionRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(r -> r.getSource() == ContentSource.CUSTOM)
                .map(r -> {
                    String key = context.key(CampaignContentType.RULE, r.getId(), r.getName());
                    return new CustomRuleDto(
                            key, r.getSourceKey(), r.getName(), r.getBody(), r.getParentKey(),
                            r.getSortOrder(), r.getRuleset(), toProvenanceDto(r.getProvenance())
                    );
                })
                .toList();

        target.customRules(dtos);
    }

    private void exportEquipment(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomEquipmentDto> dtos = equipmentItemRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(e -> e.getSource() == ContentSource.CUSTOM)
                .map(e -> {
                    String key = context.key(CampaignContentType.EQUIPMENT_ITEM, e.getId(), e.getName());
                    String category = e.getCategory() != null ? e.getCategory().name() : null;
                    return new CustomEquipmentDto(
                            key, e.getSourceKey(), e.getName(), category, e.getCost(), e.getWeight(),
                            e.getProperties(), e.getDescription(), toProvenanceDto(e.getProvenance())
                    );
                })
                .toList();

        target.customEquipment(dtos);
    }

    private void exportMagicItems(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomMagicItemDto> dtos = magicItemRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(m -> m.getSource() == ContentSource.CUSTOM)
                .map(m -> {
                    String key = context.key(CampaignContentType.MAGIC_ITEM, m.getId(), m.getName());
                    return new CustomMagicItemDto(
                            key, m.getSourceKey(), m.getName(), m.getRarity(), m.getCategory(),
                            m.getType(), m.getDescription(), m.getWeight(), m.getCost(),
                            m.isRequiresAttunement(), m.getAttunementDetail(),
                            toProvenanceDto(m.getProvenance())
                    );
                })
                .toList();

        target.customMagicItems(dtos);
    }

    private void exportClasses(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomClassDto> dtos = characterClassRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(c -> c.getSource() == ContentSource.CUSTOM)
                .map(c -> {
                    String key = context.key(CampaignContentType.CLASS, c.getId(), c.getName());
                    return new CustomClassDto(
                            key, c.getSourceKey(), c.getName(), c.getHitDie(), c.getSubclassOf(),
                            c.getDescription(), c.getSavingThrows(), c.getFeatures(),
                            c.getSpellcasting(), c.getProficiencies(), toProvenanceDto(c.getProvenance())
                    );
                })
                .toList();

        target.customClasses(dtos);
    }

    private void exportSpecies(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomSpeciesDto> dtos = speciesRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(s -> s.getSource() == ContentSource.CUSTOM)
                .map(s -> {
                    String key = context.key(CampaignContentType.SPECIES, s.getId(), s.getName());
                    return new CustomSpeciesDto(
                            key, s.getSourceKey(), s.getName(), s.getSize(), s.getSpeed(),
                            s.getTraits(), s.getDescription(), toProvenanceDto(s.getProvenance())
                    );
                })
                .toList();

        target.customSpecies(dtos);
    }

    private void exportBackgrounds(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomBackgroundDto> dtos = backgroundRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(b -> b.getSource() == ContentSource.CUSTOM)
                .map(b -> {
                    String key = context.key(CampaignContentType.BACKGROUND, b.getId(), b.getName());
                    return new CustomBackgroundDto(
                            key, b.getSourceKey(), b.getName(), b.getAbilityScores(), b.getFeatRef(),
                            b.getSkills(), b.getTools(), b.getDescription(), b.getEquipment(),
                            toProvenanceDto(b.getProvenance())
                    );
                })
                .toList();

        target.customBackgrounds(dtos);
    }

    private void exportFeats(CampaignExportContext context, CampaignManifestAssembler target) {
        List<CustomFeatDto> dtos = featRepository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(f -> f.getSource() == ContentSource.CUSTOM)
                .map(f -> {
                    String key = context.key(CampaignContentType.FEAT, f.getId(), f.getName());
                    return new CustomFeatDto(
                            key, f.getSourceKey(), f.getName(), f.getCategory(),
                            f.getPrerequisite(), f.getBenefit(), toProvenanceDto(f.getProvenance())
                    );
                })
                .toList();

        target.customFeats(dtos);
    }

    private static ProvenanceDto toProvenanceDto(ContentProvenance p) {
        if (p == null) return null;
        return new ProvenanceDto(
                p.getSourceTitle(), p.getEditionVersion(), p.getSourceLocator(),
                p.getLicenseClassification(), p.getImportedAt(), p.getConverterId(),
                p.getConverterVersion(), p.getSourceHash(), p.getExtractionConfidence()
        );
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        importStatBlocks(source.customStatBlocks(), context);
        importSpells(source.customSpells(), context);
        importConditions(source.customConditions(), context);
        importRules(source.customRules(), context);
        importEquipment(source.customEquipment(), context);
        importMagicItems(source.customMagicItems(), context);
        importClasses(source.customClasses(), context);
        importSpecies(source.customSpecies(), context);
        importBackgrounds(source.customBackgrounds(), context);
        importFeats(source.customFeats(), context);
    }

    private void importStatBlocks(List<StatBlockDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (StatBlockDto dto : dtos) {
            var sb = new StatBlock();
            sb.setSource(ContentSource.CUSTOM);
            sb.setCampaign(campaign);
            sb.setName(dto.name());
            sb.setCr(dto.cr());
            sb.setType(dto.type());
            sb.setSize(dto.size());
            sb.setAlignment(dto.alignment());
            sb.setAc(dto.ac());
            sb.setHp(dto.hp());
            sb.setSpeed(dto.speed());
            sb.setStrScore(dto.strScore());
            sb.setDexScore(dto.dexScore());
            sb.setConScore(dto.conScore());
            sb.setIntScore(dto.intScore());
            sb.setWisScore(dto.wisScore());
            sb.setChaScore(dto.chaScore());
            sb.setStrSave(dto.strSave());
            sb.setDexSave(dto.dexSave());
            sb.setConSave(dto.conSave());
            sb.setIntSave(dto.intSave());
            sb.setWisSave(dto.wisSave());
            sb.setChaSave(dto.chaSave());
            sb.setSkills(dto.skills());
            sb.setDamageVulnerabilities(dto.damageVulnerabilities());
            sb.setDamageResistances(dto.damageResistances());
            sb.setDamageImmunities(dto.damageImmunities());
            sb.setConditionImmunities(dto.conditionImmunities());
            sb.setSenses(dto.senses());
            sb.setLanguages(dto.languages());
            sb.setTraits(dto.traits());
            sb.setActions(dto.actions());
            sb.setBonusActions(dto.bonusActions());
            sb.setReactions(dto.reactions());
            sb.setLegendaryActions(dto.legendaryActions());
            sb.setLegendaryDescription(dto.legendaryDescription());
            sb.setLairActions(dto.lairActions());
            sb.setXp(dto.xp());
            sb.setCreatedAt(dto.createdAt());
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                sb.setSourceKey(dto.sourceKey());
            }
            statBlockRepository.save(sb);
            context.register(CampaignContentType.STATBLOCK, dto.key(), sb, sb.getId());
        }
    }

    private void importSpells(List<CustomSpellDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomSpellDto dto : dtos) {
            var entity = new Spell();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setLevel(dto.level());
            entity.setSchool(dto.school());
            entity.setCastingTime(dto.castingTime());
            entity.setRange(dto.range());
            entity.setComponents(dto.components());
            entity.setDuration(dto.duration());
            entity.setDescription(dto.description());
            entity.setHigherLevel(dto.higherLevel());
            entity.setRitual(dto.ritual());
            entity.setConcentration(dto.concentration());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            spellRepository.save(entity);
            context.register(CampaignContentType.SPELL, dto.key(), entity, entity.getId());
        }
    }

    private void importConditions(List<CustomConditionDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomConditionDto dto : dtos) {
            var entity = new Condition();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setDescription(dto.description());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            conditionRepository.save(entity);
            context.register(CampaignContentType.CONDITION, dto.key(), entity, entity.getId());
        }
    }

    private void importRules(List<CustomRuleDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomRuleDto dto : dtos) {
            var entity = new RuleSection();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setBody(dto.body());
            entity.setParentKey(dto.parentKey());
            entity.setSortOrder(dto.sortOrder());
            entity.setRuleset(dto.ruleset());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            ruleSectionRepository.save(entity);
            context.register(CampaignContentType.RULE, dto.key(), entity, entity.getId());
        }
    }

    private void importEquipment(List<CustomEquipmentDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomEquipmentDto dto : dtos) {
            var entity = new EquipmentItem();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            if (dto.category() != null) {
                entity.setCategory(EquipmentItem.Category.valueOf(dto.category()));
            }
            entity.setCost(dto.cost());
            entity.setWeight(dto.weight());
            entity.setProperties(dto.properties());
            entity.setDescription(dto.description());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            equipmentItemRepository.save(entity);
            context.register(CampaignContentType.EQUIPMENT_ITEM, dto.key(), entity, entity.getId());
        }
    }

    private void importMagicItems(List<CustomMagicItemDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomMagicItemDto dto : dtos) {
            var entity = new MagicItem();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setRarity(dto.rarity());
            entity.setCategory(dto.category());
            entity.setType(dto.type());
            entity.setDescription(dto.description());
            entity.setWeight(dto.weight());
            entity.setCost(dto.cost());
            entity.setRequiresAttunement(dto.requiresAttunement());
            entity.setAttunementDetail(dto.attunementDetail());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            magicItemRepository.save(entity);
            context.register(CampaignContentType.MAGIC_ITEM, dto.key(), entity, entity.getId());
        }
    }

    private void importClasses(List<CustomClassDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomClassDto dto : dtos) {
            var entity = new CharacterClass();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setHitDie(dto.hitDie());
            entity.setSubclassOf(dto.subclassOf());
            entity.setDescription(dto.description());
            entity.setSavingThrows(dto.savingThrows());
            entity.setFeatures(dto.features());
            entity.setSpellcasting(dto.spellcasting());
            entity.setProficiencies(dto.proficiencies());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            characterClassRepository.save(entity);
            context.register(CampaignContentType.CLASS, dto.key(), entity, entity.getId());
        }
    }

    private void importSpecies(List<CustomSpeciesDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomSpeciesDto dto : dtos) {
            var entity = new Species();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setSize(dto.size());
            entity.setSpeed(dto.speed());
            entity.setTraits(dto.traits());
            entity.setDescription(dto.description());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            speciesRepository.save(entity);
            context.register(CampaignContentType.SPECIES, dto.key(), entity, entity.getId());
        }
    }

    private void importBackgrounds(List<CustomBackgroundDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomBackgroundDto dto : dtos) {
            var entity = new Background();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setAbilityScores(dto.abilityScores());
            entity.setFeatRef(dto.featRef());
            entity.setSkills(dto.skills());
            entity.setTools(dto.tools());
            entity.setDescription(dto.description());
            entity.setEquipment(dto.equipment());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            backgroundRepository.save(entity);
            context.register(CampaignContentType.BACKGROUND, dto.key(), entity, entity.getId());
        }
    }

    private void importFeats(List<CustomFeatDto> dtos, CampaignImportContext context) {
        if (dtos == null) return;
        var campaign = context.campaign();
        for (CustomFeatDto dto : dtos) {
            var entity = new Feat();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setName(dto.name());
            entity.setCategory(dto.category());
            entity.setPrerequisite(dto.prerequisite());
            entity.setBenefit(dto.benefit());
            entity.setProvenance(toContentProvenance(dto.provenance()));
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                entity.setSourceKey(dto.sourceKey());
            }
            featRepository.save(entity);
            context.register(CampaignContentType.FEAT, dto.key(), entity, entity.getId());
        }
    }

    private static ContentProvenance toContentProvenance(ProvenanceDto dto) {
        if (dto == null) {
            var p = new ContentProvenance();
            p.setImportedAt(Instant.now());
            return p;
        }
        var p = new ContentProvenance();
        p.setSourceTitle(dto.sourceTitle());
        p.setEditionVersion(dto.editionVersion());
        p.setSourceLocator(dto.sourceLocator());
        p.setLicenseClassification(dto.licenseClassification());
        p.setImportedAt(dto.importedAt() != null ? dto.importedAt() : Instant.now());
        p.setConverterId(dto.converterId());
        p.setConverterVersion(dto.converterVersion());
        p.setSourceHash(dto.sourceHash());
        p.setExtractionConfidence(dto.extractionConfidence());
        return p;
    }
}
