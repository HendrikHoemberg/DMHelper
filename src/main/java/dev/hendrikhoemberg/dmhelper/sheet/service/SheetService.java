package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SheetService {

    private final CharacterSheetRepository sheetRepo;
    private final SheetResourceRepository resourceRepo;
    private final SheetSpellReferenceRepository spellRefRepo;
    private final PartyMemberRepository partyMemberRepo;
    private final SheetEngine sheetEngine;
    private final CharacterClassRepository classRepo;
    private final SpeciesRepository speciesRepo;
    private final BackgroundRepository backgroundRepo;
    private final SpellRepository spellRepo;
    private final FeatRepository featRepo;
    private final ObjectMapper mapper;

    private static final Logger log = LoggerFactory.getLogger(SheetService.class);

    public record CreateSheetRequest(
            UUID partyMemberId,
            Map<String, Integer> abilityScores,
            List<ClassLevelEntry> classLevels,
            Map<String, Object> proficiencies,
            UUID speciesId,
            UUID backgroundId,
            List<String> featRefs,
            int xp
    ) {}

    public record UpdateSheetRequest(
            Map<String, Integer> abilityScores,
            List<ClassLevelEntry> classLevels,
            Map<String, Object> proficiencies,
            UUID speciesId,
            UUID backgroundId,
            List<String> featRefs,
            Map<String, Object> overrides,
            int xp
    ) {}

    private static final int[] XP_THRESHOLDS = {
            0, 0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000,
            64000, 85000, 100000, 120000, 140000, 165000, 195000,
            225000, 265000, 305000, 355000
    };

    public record XpResult(
            UUID sheetId,
            int xp,
            int level,
            boolean levelUp
    ) {}

    public XpResult checkLevelUp(CharacterSheet sheet) {
        Campaign campaign = sheet.getPartyMember().getCampaign();
        if (campaign.isMilestoneLeveling()) {
            return new XpResult(sheet.getId(), sheet.getXp(), getTotalLevel(sheet), false);
        }
        int xp = sheet.getXp();
        int currentLevel = getTotalLevel(sheet);
        int newLevel = currentLevel;
        for (int i = currentLevel + 1; i < XP_THRESHOLDS.length; i++) {
            if (xp >= XP_THRESHOLDS[i]) {
                newLevel = i;
            } else {
                break;
            }
        }
        return new XpResult(sheet.getId(), xp, newLevel, newLevel > currentLevel);
    }

    private int getTotalLevel(CharacterSheet sheet) {
        try {
            List<Map<String, Object>> classLevels = mapper.readValue(sheet.getClassLevels(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            int total = 0;
            for (Map<String, Object> entry : classLevels) {
                total += ((Number) entry.get("level")).intValue();
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    public record ClassLevelEntry(
            String classSourceKey,
            int level,
            List<Integer> hitDieRolls,
            String subclassSourceKey
    ) {
        public ClassLevelEntry(String classSourceKey, int level, List<Integer> hitDieRolls) {
            this(classSourceKey, level, hitDieRolls, null);
        }
    }

    public record SheetDto(
            UUID id, UUID partyMemberId,
            Map<String, Integer> abilityScores,
            List<ClassLevelEntry> classLevels,
            String speciesName, UUID speciesId,
            String backgroundName, UUID backgroundId,
            List<String> featRefs,
            int xp,
            Map<String, Object> overrides,
            int hitDiceUsed,
            Map<String, Integer> spellSlotsUsed,
            DerivedValues derivedValues,
            List<SheetResourceDto> resources,
            List<SheetSpellDto> spells
    ) {}

    public record LevelUpRequest(
            String classSourceKey,
            int hpRoll,
            boolean isAverage
    ) {}

    public record SheetResourceDto(
            UUID id, String name, int maxUses, int currentUses, String resetRule
    ) {}

    public record SheetSpellDto(
            UUID id, String spellName, int spellLevel, boolean prepared, String sourceClass
    ) {}

    public SheetService(CharacterSheetRepository sheetRepo,
                        SheetResourceRepository resourceRepo,
                        SheetSpellReferenceRepository spellRefRepo,
                        PartyMemberRepository partyMemberRepo,
                        SheetEngine sheetEngine,
                        CharacterClassRepository classRepo,
                        SpeciesRepository speciesRepo,
                        BackgroundRepository backgroundRepo,
                        SpellRepository spellRepo,
                        FeatRepository featRepo) {
        this.sheetRepo = sheetRepo;
        this.resourceRepo = resourceRepo;
        this.spellRefRepo = spellRefRepo;
        this.partyMemberRepo = partyMemberRepo;
        this.sheetEngine = sheetEngine;
        this.classRepo = classRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.spellRepo = spellRepo;
        this.featRepo = featRepo;
        this.mapper = new ObjectMapper();
    }

    public boolean hasSheet(UUID partyMemberId) {
        return sheetRepo.findByPartyMemberId(partyMemberId).isPresent();
    }

    // ---- Sheet CRUD ----

    public SheetDto createSheet(CreateSheetRequest request) {
        PartyMember pm = partyMemberRepo.findById(request.partyMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Party member not found"));
        if (sheetRepo.findByPartyMemberId(pm.getId()).isPresent()) {
            throw new IllegalStateException("Party member already has a character sheet");
        }

        CharacterSheet sheet = new CharacterSheet();
        sheet.setPartyMember(pm);

        try {
            sheet.setAbilityScores(mapper.writeValueAsString(request.abilityScores()));
            sheet.setClassLevels(mapper.writeValueAsString(request.classLevels()));
            sheet.setProficiencies(mapper.writeValueAsString(
                    request.proficiencies() != null ? request.proficiencies() : Map.of(
                        "skills", List.of(), "tools", List.of(),
                        "languages", List.of(), "armor", List.of(),
                        "weapons", List.of(), "expertise", List.of()
                    )));
            sheet.setFeatRefs(mapper.writeValueAsString(request.featRefs() != null ? request.featRefs() : List.of()));
            sheet.setOverrides(mapper.writeValueAsString(Map.of()));
            sheet.setSpellSlotsUsed(mapper.writeValueAsString(Map.of()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize sheet data", e);
        }

        if (request.speciesId() != null) {
            sheet.setSpecies(speciesRepo.findById(request.speciesId()).orElse(null));
        }
        if (request.backgroundId() != null) {
            sheet.setBackground(backgroundRepo.findById(request.backgroundId()).orElse(null));
        }
        sheet.setXp(request.xp());

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    public SheetDto getSheetDto(UUID sheetId) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        return toDto(sheet);
    }

    public SheetDto getSheetDtoByPartyMemberId(UUID partyMemberId) {
        CharacterSheet sheet = sheetRepo.findByPartyMemberId(partyMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        return toDto(sheet);
    }

    public SheetDto updateSheet(UUID sheetId, UpdateSheetRequest request) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        try {
            if (request.abilityScores() != null) {
                sheet.setAbilityScores(mapper.writeValueAsString(request.abilityScores()));
            }
            if (request.classLevels() != null) {
                sheet.setClassLevels(mapper.writeValueAsString(request.classLevels()));
            }
            if (request.proficiencies() != null) {
                sheet.setProficiencies(mapper.writeValueAsString(request.proficiencies()));
            }
            if (request.featRefs() != null) {
                sheet.setFeatRefs(mapper.writeValueAsString(request.featRefs()));
            }
            if (request.overrides() != null) {
                sheet.setOverrides(mapper.writeValueAsString(request.overrides()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize sheet data", e);
        }

        if (request.speciesId() != null) {
            sheet.setSpecies(speciesRepo.findById(request.speciesId()).orElse(null));
        }
        if (request.backgroundId() != null) {
            sheet.setBackground(backgroundRepo.findById(request.backgroundId()).orElse(null));
        }
        if (request.xp() >= 0) {
            sheet.setXp(request.xp());
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    public void deleteSheet(UUID sheetId) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        resourceRepo.deleteBySheetId(sheetId);
        spellRefRepo.deleteBySheetId(sheetId);
        sheetRepo.delete(sheet);
    }

    // ---- Level-up ----

    public SheetDto levelUp(UUID sheetId, LevelUpRequest request) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        try {
            List<Map<String, Object>> classLevels = mapper.readValue(sheet.getClassLevels(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            Map<String, Object> entry = null;
            for (Map<String, Object> e : classLevels) {
                if (request.classSourceKey().equals(SheetClassLevelCodec.classSourceKeyOf(e))) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                entry = new HashMap<>();
                entry.put("classSourceKey", request.classSourceKey());
                entry.put("level", 0);
                entry.put("hitDieRolls", new ArrayList<Integer>());
                classLevels.add(entry);
            }

            int currentLevel = ((Number) entry.get("level")).intValue();
            entry.put("level", currentLevel + 1);

            List<Integer> rolls = getHitDieRolls(entry);
            int hpGain;
            if (request.isAverage()) {
                var cls = classRepo.findBySourceKey(request.classSourceKey());
                int dieType = 8;
                if (cls.isPresent() && cls.get().getHitDie() != null) {
                    dieType = Integer.parseInt(cls.get().getHitDie().substring(1));
                }
                hpGain = (dieType / 2) + 1;
            } else {
                hpGain = request.hpRoll();
            }
            rolls.add(hpGain);
            entry.put("hitDieRolls", rolls);

            sheet.setClassLevels(mapper.writeValueAsString(classLevels));
        } catch (Exception e) {
            throw new RuntimeException("Failed to process level-up", e);
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    // ---- Rests ----

    public SheetDto shortRest(UUID sheetId, int hitDiceSpent) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        DerivedValues derived = sheetEngine.derive(sheet);
        int maxSpendable = derived.remainingHitDice();
        int toSpend = Math.min(hitDiceSpent, maxSpendable);
        sheet.setHitDiceUsed(sheet.getHitDiceUsed() + toSpend);

        List<SheetResource> resources = resourceRepo.findBySheetId(sheetId);
        for (SheetResource r : resources) {
            if (r.getResetRule() == SheetResource.ResetRule.SHORT_REST) {
                r.setCurrentUses(r.getMaxUses());
                resourceRepo.save(r);
            }
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    public SheetDto longRest(UUID sheetId, int hitDiceSpent) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        DerivedValues derived = sheetEngine.derive(sheet);

        int recovered = Math.min(sheet.getHitDiceUsed(), Math.max(1, derived.totalHitDice() / 2));
        sheet.setHitDiceUsed(Math.max(0, sheet.getHitDiceUsed() - recovered));
        int toSpend = Math.min(hitDiceSpent, derived.remainingHitDice());
        sheet.setHitDiceUsed(sheet.getHitDiceUsed() + toSpend);

        try {
            sheet.setSpellSlotsUsed(mapper.writeValueAsString(Map.of()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset spell slots", e);
        }

        List<SheetResource> resources = resourceRepo.findBySheetId(sheetId);
        for (SheetResource r : resources) {
            if (r.getResetRule() == SheetResource.ResetRule.SHORT_REST ||
                    r.getResetRule() == SheetResource.ResetRule.LONG_REST) {
                r.setCurrentUses(r.getMaxUses());
                resourceRepo.save(r);
            }
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    // ---- XP ----

    public SheetDto awardXp(UUID sheetId, int amount) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        sheet.setXp(sheet.getXp() + amount);
        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    public SheetDto setLevel(UUID sheetId, String classSourceKey, int totalLevel) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));

        try {
            List<Map<String, Object>> classLevels = mapper.readValue(sheet.getClassLevels(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            Map<String, Object> entry = null;
            for (Map<String, Object> e : classLevels) {
                if (classSourceKey.equals(SheetClassLevelCodec.classSourceKeyOf(e))) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                throw new IllegalStateException("Class not found on sheet: " + classSourceKey);
            }

            int oldLevel = ((Number) entry.get("level")).intValue();
            entry.put("level", totalLevel);

            List<Integer> rolls = getHitDieRolls(entry);
            if (totalLevel > oldLevel) {
                int dieSize = getHitDieSize(SheetClassLevelCodec.classSourceKeyOf(entry));
                int avg = (dieSize / 2) + 1;
                for (int i = oldLevel; i < totalLevel; i++) {
                    rolls.add(avg);
                }
            } else if (totalLevel < oldLevel && totalLevel > 1) {
                while (rolls.size() > totalLevel - 1) {
                    rolls.remove(rolls.size() - 1);
                }
            }
            entry.put("hitDieRolls", rolls);

            sheet.setClassLevels(mapper.writeValueAsString(classLevels));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set level", e);
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    // ---- Resources ----

    public SheetResourceDto updateResource(UUID resourceId, int currentUses) {
        SheetResource resource = resourceRepo.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));
        resource.setCurrentUses(Math.max(0, Math.min(currentUses, resource.getMaxUses())));
        resource = resourceRepo.save(resource);
        return new SheetResourceDto(resource.getId(), resource.getName(),
                resource.getMaxUses(), resource.getCurrentUses(), resource.getResetRule().name());
    }

    public SheetResourceDto createResource(UUID sheetId, String name, int maxUses, String resetRule) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        SheetResource resource = new SheetResource();
        resource.setSheet(sheet);
        resource.setName(name);
        resource.setMaxUses(maxUses);
        resource.setCurrentUses(maxUses);
        resource.setResetRule(SheetResource.ResetRule.valueOf(resetRule));
        resource = resourceRepo.save(resource);
        return new SheetResourceDto(resource.getId(), resource.getName(),
                resource.getMaxUses(), resource.getCurrentUses(), resource.getResetRule().name());
    }

    // ---- Campaign-aware resolution ----

    public Optional<Spell> resolveSpellForCampaign(UUID campaignId, String sourceKey) {
        return spellRepo.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> spellRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> spellRepo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    public Optional<CharacterClass> resolveClassForCampaign(UUID campaignId, String sourceKey) {
        return classRepo.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> classRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> classRepo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    public Optional<Feat> resolveFeatForCampaign(UUID campaignId, String sourceKey) {
        return featRepo.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> featRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> featRepo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    public Optional<Species> resolveSpeciesForCampaign(UUID campaignId, String sourceKey) {
        return speciesRepo.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> speciesRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> speciesRepo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    public Optional<Background> resolveBackgroundForCampaign(UUID campaignId, String sourceKey) {
        return backgroundRepo.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> backgroundRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> backgroundRepo.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    // ---- Spells ----

    public SheetSpellDto addSpell(UUID sheetId, UUID spellId, boolean prepared, String sourceClass) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        Spell spell = spellRepo.findById(spellId)
                .orElseThrow(() -> new IllegalArgumentException("Spell not found"));
        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        ref.setPrepared(prepared);
        ref.setSourceClass(sourceClass);
        ref = spellRefRepo.save(ref);
        return new SheetSpellDto(ref.getId(), spell.getName(), spell.getLevel(), ref.isPrepared(), ref.getSourceClass());
    }

    public SheetSpellDto addSpellBySourceKey(UUID sheetId, String sourceKey, boolean prepared, String sourceClass) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        UUID campaignId = sheet.getPartyMember().getCampaign().getId();
        Spell spell = resolveSpellForCampaign(campaignId, sourceKey)
                .orElseThrow(() -> new IllegalArgumentException("Spell not found for sourceKey: " + sourceKey));
        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        ref.setPrepared(prepared);
        ref.setSourceClass(sourceClass);
        ref = spellRefRepo.save(ref);
        return new SheetSpellDto(ref.getId(), spell.getName(), spell.getLevel(), ref.isPrepared(), ref.getSourceClass());
    }

    public void removeSpell(UUID spellRefId) {
        spellRefRepo.deleteById(spellRefId);
    }

    public void togglePrepared(UUID spellRefId) {
        SheetSpellReference ref = spellRefRepo.findById(spellRefId)
                .orElseThrow(() -> new IllegalArgumentException("Spell reference not found"));
        ref.setPrepared(!ref.isPrepared());
        spellRefRepo.save(ref);
    }

    // ---- Sync ----

    void syncToPartyMember(CharacterSheet sheet) {
        DerivedValues derived = sheetEngine.derive(sheet);
        PartyMember pm = sheet.getPartyMember();
        pm.setAc(derived.armorClass());
        pm.setMaxHp(derived.maxHp());
        if (pm.getCurrentHp() > derived.maxHp()) {
            pm.setCurrentHp(derived.maxHp());
        }
        pm.setInitiativeBonus(derived.initiativeBonus());
        pm.setSpeed(derived.speed());
        pm.setPassivePerception(derived.passivePerception());
        pm.setPassiveInsight(derived.passiveInsight());
        pm.setPassiveInvestigation(derived.passiveInvestigation());
        pm.setClassAndLevel(derived.classAndLevel());
        partyMemberRepo.save(pm);
    }

    // ---- Helpers ----

    private SheetDto toDto(CharacterSheet sheet) {
        DerivedValues derived = sheetEngine.derive(sheet);

        Map<String, Integer> abilityScores = Map.of();
        List<ClassLevelEntry> classLevels = List.of();
        List<String> featRefs = List.of();
        Map<String, Object> overrides = Map.of();
        Map<String, Integer> spellsUsed = Map.of();

        try {
            if (sheet.getAbilityScores() != null) {
                abilityScores = mapper.readValue(sheet.getAbilityScores(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
            }
            if (sheet.getClassLevels() != null) {
                classLevels = SheetClassLevelCodec.read(sheet.getClassLevels());
            }
            if (sheet.getFeatRefs() != null) {
                featRefs = mapper.readValue(sheet.getFeatRefs(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
            if (sheet.getOverrides() != null) {
                overrides = mapper.readValue(sheet.getOverrides(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
            if (sheet.getSpellSlotsUsed() != null) {
                spellsUsed = mapper.readValue(sheet.getSpellSlotsUsed(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize sheet JSON fields", e);
        }

        List<SheetResourceDto> resources = resourceRepo.findBySheetId(sheet.getId()).stream()
                .map(r -> new SheetResourceDto(r.getId(), r.getName(), r.getMaxUses(), r.getCurrentUses(), r.getResetRule().name()))
                .collect(Collectors.toList());

        List<SheetSpellDto> spells = spellRefRepo.findBySheetId(sheet.getId()).stream()
                .map(s -> {
                    String spellName = s.getSpell() != null ? s.getSpell().getName() : "Unknown";
                    int spellLevel = s.getSpell() != null ? s.getSpell().getLevel() : 0;
                    return new SheetSpellDto(s.getId(), spellName, spellLevel, s.isPrepared(), s.getSourceClass());
                })
                .collect(Collectors.toList());

        String speciesName = sheet.getSpecies() != null ? sheet.getSpecies().getName() : null;
        UUID speciesId = sheet.getSpecies() != null ? sheet.getSpecies().getId() : null;
        String backgroundName = sheet.getBackground() != null ? sheet.getBackground().getName() : null;
        UUID backgroundId = sheet.getBackground() != null ? sheet.getBackground().getId() : null;

        return new SheetDto(
                sheet.getId(), sheet.getPartyMember().getId(),
                abilityScores, classLevels,
                speciesName, speciesId,
                backgroundName, backgroundId,
                featRefs, sheet.getXp(), overrides, sheet.getHitDiceUsed(),
                spellsUsed, derived, resources, spells
        );
    }

    @SuppressWarnings("unchecked")
    private List<Integer> getHitDieRolls(Map<String, Object> entry) {
        Object rolls = entry.get("hitDieRolls");
        if (rolls instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number n) result.add(n.intValue());
            }
            return result;
        }
        return new ArrayList<>();
    }

    private int getHitDieSize(String classSourceKey) {
        var cls = classRepo.findBySourceKey(classSourceKey);
        if (cls.isPresent() && cls.get().getHitDie() != null) {
            try {
                return Integer.parseInt(cls.get().getHitDie().substring(1));
            } catch (NumberFormatException e) {
                return 8;
            }
        }
        return 8;
    }
}
