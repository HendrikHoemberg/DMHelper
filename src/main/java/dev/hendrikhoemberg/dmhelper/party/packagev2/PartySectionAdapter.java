package dev.hendrikhoemberg.dmhelper.party.packagev2;

import tools.jackson.core.type.TypeReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ClassLevelDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.PartyMemberDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ResourceDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SheetDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SpellRefDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetClassLevelCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PartySectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final PartyMemberRepository partyMemberRepository;
    private final CharacterSheetRepository characterSheetRepository;
    private final SheetResourceRepository sheetResourceRepository;
    private final SheetSpellReferenceRepository sheetSpellReferenceRepository;
    private final LibraryContentReferenceResolver libraryRefs;
    private final ObjectMapper objectMapper;

    public PartySectionAdapter(PartyMemberRepository partyMemberRepository,
                               CharacterSheetRepository characterSheetRepository,
                               SheetResourceRepository sheetResourceRepository,
                               SheetSpellReferenceRepository sheetSpellReferenceRepository,
                               LibraryContentReferenceResolver libraryRefs) {
        this.partyMemberRepository = partyMemberRepository;
        this.characterSheetRepository = characterSheetRepository;
        this.sheetResourceRepository = sheetResourceRepository;
        this.sheetSpellReferenceRepository = sheetSpellReferenceRepository;
        this.libraryRefs = libraryRefs;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sectionName() {
        return "Party";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var members = partyMemberRepository.findByCampaignIdOrderByCharacterNameAscIdAsc(context.campaignId());

        List<PartyMemberDto> dtos = members.stream()
                .map(pm -> exportPartyMember(pm, context))
                .toList();

        target.party(dtos);
    }

    private PartyMemberDto exportPartyMember(PartyMember pm, CampaignExportContext context) {
        String key = context.key(CampaignContentType.PARTY_MEMBER, pm.getId(), pm.getCharacterName());

        SheetDto sheetDto = null;
        var cs = pm.getCharacterSheet();
        if (cs != null) {
            sheetDto = exportSheet(cs, context);
        }

        return new PartyMemberDto(
                key, pm.getCharacterName(), pm.getPlayerName(), pm.getClassAndLevel(),
                pm.getAc(), pm.getMaxHp(), pm.getCurrentHp(), pm.getInitiativeBonus(),
                pm.getSpeed(), pm.getPassivePerception(), pm.getPassiveInsight(),
                pm.getPassiveInvestigation(), pm.getNotes(), pm.isActive(), sheetDto,
                pm.getTempHp(), pm.isInspiration(), pm.getExhaustion(),
                pm.getDeathSaveSuccesses(), pm.getDeathSaveFailures(),
                pm.getConcentratingOn(), pm.getConditionsJson()
        );
    }

    private SheetDto exportSheet(CharacterSheet cs, CampaignExportContext context) {
        String sheetKey = context.key(CampaignContentType.CHARACTER_SHEET, cs.getId(),
                cs.getPartyMember().getCharacterName() + " sheet");

        Map<String, Object> abilityScores = parseJsonMap(cs.getAbilityScores());
        Map<String, Object> proficiencies = parseJsonMap(cs.getProficiencies());
        Map<String, Object> overrides = parseJsonMap(cs.getOverrides());
        Map<String, Object> spellSlotsUsed = parseJsonMap(cs.getSpellSlotsUsed());

        List<ClassLevelDto> classLevels = exportClassLevels(cs, context);
        List<ContentReference> featRefs = exportFeatRefs(cs, context);
        List<ResourceDto> resources = exportResources(cs, context);
        List<SpellRefDto> spells = exportSpells(cs, context);
        List<CampaignManifestV2.AttackDto> attacks = exportAttacks(cs);
        List<CampaignManifestV2.FeatureDto> features = exportFeatures(cs);

        ContentReference speciesRef = null;
        if (cs.getSpecies() != null) {
            speciesRef = libraryRefs.referenceFor(cs.getSpecies(), context);
        }

        ContentReference backgroundRef = null;
        if (cs.getBackground() != null) {
            backgroundRef = libraryRefs.referenceFor(cs.getBackground(), context);
        }

        return new SheetDto(
                sheetKey, abilityScores, classLevels, proficiencies,
                speciesRef, backgroundRef, featRefs,
                cs.getXp(), overrides, cs.getHitDiceUsed(),
                resources, spells, spellSlotsUsed,
                attacks, features
        );
    }

    private List<CampaignManifestV2.AttackDto> exportAttacks(CharacterSheet cs) {
        String raw = cs.getAttacksJson();
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw,
                    new TypeReference<List<CampaignManifestV2.AttackDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<CampaignManifestV2.FeatureDto> exportFeatures(CharacterSheet cs) {
        String raw = cs.getFeaturesJson();
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw,
                    new TypeReference<List<CampaignManifestV2.FeatureDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ClassLevelDto> exportClassLevels(CharacterSheet cs, CampaignExportContext context) {
        String raw = cs.getClassLevels();
        if (raw == null || raw.isBlank()) return List.of();

        try {
            var list = objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
            List<ClassLevelDto> result = new ArrayList<>();
            for (var entry : list) {
                String classSourceKey = SheetClassLevelCodec.classSourceKeyOf(entry);
                int level = ((Number) entry.get("level")).intValue();
                @SuppressWarnings("unchecked")
                List<Integer> hitDieRolls = entry.containsKey("hitDieRolls")
                        ? ((List<Number>) entry.get("hitDieRolls")).stream().map(Number::intValue).toList()
                        : List.of();
                CharacterClass cls = libraryRefs.findClassForCampaign(context.campaignId(), classSourceKey);
                if (cls == null) {
                    throw new IllegalStateException(
                            "Sheet class level references unknown class sourceKey '" + classSourceKey + "'");
                }
                ContentReference classRef = libraryRefs.referenceFor(cls, context);
                ContentReference subclassRef = null;
                Object subclassKeyObj = entry.get("subclassSourceKey");
                if (subclassKeyObj != null && !subclassKeyObj.toString().isBlank()) {
                    String subclassKey = subclassKeyObj.toString();
                    CharacterClass subclass = libraryRefs.findClassForCampaign(context.campaignId(), subclassKey);
                    if (subclass == null) {
                        throw new IllegalStateException(
                                "Sheet class level references unknown subclass sourceKey '" + subclassKey + "'");
                    }
                    subclassRef = libraryRefs.referenceFor(subclass, context);
                }
                result.add(new ClassLevelDto(classRef, level, hitDieRolls, subclassRef));
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ContentReference> exportFeatRefs(CharacterSheet cs, CampaignExportContext context) {
        String raw = cs.getFeatRefs();
        if (raw == null || raw.isBlank()) return List.of();

        try {
            var sourceKeys = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            List<ContentReference> refs = new ArrayList<>();
            for (String sk : sourceKeys) {
                Feat feat = libraryRefs.findFeatForCampaign(context.campaignId(), sk);
                if (feat == null) {
                    throw new IllegalStateException(
                            "Sheet feat references unknown feat sourceKey '" + sk + "'");
                }
                refs.add(libraryRefs.referenceFor(feat, context));
            }
            return refs;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ResourceDto> exportResources(CharacterSheet cs, CampaignExportContext context) {
        return sheetResourceRepository.findBySheetIdOrderByIdAsc(cs.getId())
                .stream()
                .map(r -> {
                    String key = context.key(CampaignContentType.SHEET_RESOURCE, r.getId(), r.getName());
                    return new ResourceDto(key, r.getName(), r.getMaxUses(), r.getCurrentUses(),
                            r.getResetRule().name());
                })
                .toList();
    }

    private List<SpellRefDto> exportSpells(CharacterSheet cs, CampaignExportContext context) {
        return sheetSpellReferenceRepository.findBySheetIdOrderByIdAsc(cs.getId())
                .stream()
                .map(sr -> {
                    ContentReference spellRef = libraryRefs.referenceFor(sr.getSpell(), context);
                    ContentReference sourceClassRef = null;
                    if (sr.getSourceClass() != null && !sr.getSourceClass().isBlank()) {
                        CharacterClass sourceClass = libraryRefs.findClassForCampaign(
                                context.campaignId(), sr.getSourceClass());
                        if (sourceClass == null) {
                            throw new IllegalStateException(
                                    "Spell source class references unknown class sourceKey '"
                                            + sr.getSourceClass() + "'");
                        }
                        sourceClassRef = libraryRefs.referenceFor(sourceClass, context);
                    }
                    return new SpellRefDto(spellRef, sr.isPrepared(), sourceClassRef);
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<PartyMemberDto> dtos = source.party();
        if (dtos == null) return;

        var campaign = context.campaign();

        List<ImportedPartyMember> imported = new ArrayList<>();
        for (PartyMemberDto dto : dtos) {
            var pm = new PartyMember();
            pm.setCampaign(campaign);
            pm.setCharacterName(dto.characterName());
            pm.setPlayerName(dto.playerName());
            pm.setClassAndLevel(dto.classAndLevel());
            pm.setAc(dto.ac());
            pm.setMaxHp(dto.maxHp());
            pm.setCurrentHp(dto.currentHp());
            pm.setInitiativeBonus(dto.initiativeBonus());
            pm.setSpeed(dto.speed());
            pm.setPassivePerception(dto.passivePerception());
            pm.setPassiveInsight(dto.passiveInsight());
            pm.setPassiveInvestigation(dto.passiveInvestigation());
            pm.setNotes(dto.notes());
            pm.setActive(dto.active());
            pm.setTempHp(dto.tempHp());
            pm.setInspiration(dto.inspiration());
            pm.setExhaustion(dto.exhaustion());
            pm.setDeathSaveSuccesses(dto.deathSaveSuccesses());
            pm.setDeathSaveFailures(dto.deathSaveFailures());
            pm.setConcentratingOn(dto.concentratingOn());
            pm.setConditionsJson(dto.conditionsJson());
            partyMemberRepository.save(pm);
            context.register(CampaignContentType.PARTY_MEMBER, dto.key(), pm, pm.getId());
            imported.add(new ImportedPartyMember(pm, dto));
        }

        for (var entry : imported) {
            var sheetDto = entry.dto.sheet();
            if (sheetDto == null) continue;

            var cs = new CharacterSheet();
            cs.setPartyMember(entry.pm);
            cs.setAbilityScores(toJson(sheetDto.abilityScores()));
            cs.setClassLevels(importClassLevels(sheetDto, context));
            cs.setProficiencies(toJson(sheetDto.proficiencies()));
            cs.setSpecies(libraryRefs.resolveSpecies(sheetDto.speciesRef(), context));
            cs.setBackground(libraryRefs.resolveBackground(sheetDto.backgroundRef(), context));
            cs.setFeatRefs(importFeatRefs(sheetDto, context));
            cs.setXp(sheetDto.xp());
            cs.setOverrides(toJson(sheetDto.overrides()));
            cs.setHitDiceUsed(sheetDto.hitDiceUsed());
            cs.setSpellSlotsUsed(toJson(sheetDto.spellSlotsUsed()));
            cs.setAttacksJson(toAttacksJson(sheetDto.attacks()));
            cs.setFeaturesJson(toFeaturesJson(sheetDto.features()));
            characterSheetRepository.save(cs);
            context.register(CampaignContentType.CHARACTER_SHEET, sheetDto.key(), cs, cs.getId());

            for (ResourceDto resDto : sheetDto.resources()) {
                var res = new SheetResource();
                res.setSheet(cs);
                res.setName(resDto.name());
                res.setMaxUses(resDto.maxUses());
                res.setCurrentUses(resDto.currentUses());
                res.setResetRule(SheetResource.ResetRule.valueOf(resDto.resetRule()));
                sheetResourceRepository.save(res);
                context.register(CampaignContentType.SHEET_RESOURCE, resDto.key(), res, res.getId());
            }

            for (SpellRefDto spellDto : sheetDto.spells()) {
                Spell spell = libraryRefs.resolveSpell(spellDto.spellRef(), context);
                if (spell == null) {
                    throw new IllegalStateException(
                            "Unresolved spell reference on party member '" + entry.dto.characterName() + "'");
                }

                var sr = new SheetSpellReference();
                sr.setSheet(cs);
                sr.setSpell(spell);
                sr.setPrepared(spellDto.prepared());
                if (spellDto.sourceClassRef() != null) {
                    CharacterClass sourceClass = libraryRefs.resolveClass(spellDto.sourceClassRef(), context);
                    sr.setSourceClass(sourceClass.getSourceKey());
                }
                sheetSpellReferenceRepository.save(sr);
            }
        }
    }

    private String importClassLevels(SheetDto sheetDto, CampaignImportContext context) {
        if (sheetDto.classLevels() == null || sheetDto.classLevels().isEmpty()) return null;
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ClassLevelDto cl : sheetDto.classLevels()) {
                CharacterClass cls = libraryRefs.resolveClass(cl.classRef(), context);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("classSourceKey", cls.getSourceKey());
                entry.put("level", cl.level());
                if (cl.hitDieRolls() != null && !cl.hitDieRolls().isEmpty()) {
                    entry.put("hitDieRolls", cl.hitDieRolls());
                }
                if (cl.subclassRef() != null) {
                    CharacterClass subclass = libraryRefs.resolveClass(cl.subclassRef(), context);
                    if (subclass != null) {
                        entry.put("subclassSourceKey", subclass.getSourceKey());
                    }
                }
                list.add(entry);
            }
            return objectMapper.writeValueAsString(list);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import class levels", e);
        }
    }

    private String importFeatRefs(SheetDto sheetDto, CampaignImportContext context) {
        if (sheetDto.featRefs() == null || sheetDto.featRefs().isEmpty()) return null;
        try {
            List<String> sourceKeys = new ArrayList<>();
            for (ContentReference ref : sheetDto.featRefs()) {
                Feat feat = libraryRefs.resolveFeat(ref, context);
                sourceKeys.add(feat.getSourceKey());
            }
            return objectMapper.writeValueAsString(sourceKeys);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import feat refs", e);
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private String toAttacksJson(List<CampaignManifestV2.AttackDto> attacks) {
        if (attacks == null || attacks.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(attacks);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeaturesJson(List<CampaignManifestV2.FeatureDto> features) {
        if (features == null || features.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(features);
        } catch (Exception e) {
            return null;
        }
    }

    private record ImportedPartyMember(PartyMember pm, PartyMemberDto dto) {}
}
