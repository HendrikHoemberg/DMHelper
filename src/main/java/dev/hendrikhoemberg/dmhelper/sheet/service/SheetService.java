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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public record CreationOptionsDto(
            String classSourceKey,
            String hitDie,
            List<String> savingThrows,
            int skillChoiceCount,
            List<String> skillOptions,
            List<SubclassOption> subclasses,
            List<String> armorProficiencies,
            List<String> weaponProficiencies
    ) {}

    public record SubclassOption(String sourceKey, String name) {}

    static final int SUBCLASS_LEVEL = 3;

    private static final Map<String, String> SKILL_ABILITY_MAP = Map.ofEntries(
        Map.entry("athletics", "str"),
        Map.entry("acrobatics", "dex"),
        Map.entry("sleight_of_hand", "dex"),
        Map.entry("stealth", "dex"),
        Map.entry("arcana", "int"),
        Map.entry("history", "int"),
        Map.entry("investigation", "int"),
        Map.entry("nature", "int"),
        Map.entry("religion", "int"),
        Map.entry("animal_handling", "wis"),
        Map.entry("insight", "wis"),
        Map.entry("medicine", "wis"),
        Map.entry("perception", "wis"),
        Map.entry("survival", "wis"),
        Map.entry("deception", "cha"),
        Map.entry("intimidation", "cha"),
        Map.entry("performance", "cha"),
        Map.entry("persuasion", "cha")
    );

    private static final Pattern CHOOSE_FROM_PATTERN = Pattern.compile(
        "choose\\s+(\\d+)\\s+from\\s+([A-Za-z_\\s,]+)", Pattern.CASE_INSENSITIVE
    );

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

    public CreationOptionsDto creationOptions(String classSourceKey) {
        var clsOpt = classRepo.findBySourceKey(classSourceKey);
        if (clsOpt.isEmpty()) {
            return new CreationOptionsDto(classSourceKey, "d8", List.of(), 0, List.of(),
                    List.of(), List.of(), List.of());
        }
        var cls = clsOpt.get();

        int skillChoiceCount = 0;
        List<String> skillOptions = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> features = mapper.readValue(cls.getFeatures(), List.class);
            for (Map<String, Object> f : features) {
                if ("CORE_TRAITS_TABLE".equals(f.get("feature_type"))) {
                    String desc = (String) f.get("description");
                    if (desc != null) {
                        Matcher m = CHOOSE_FROM_PATTERN.matcher(desc);
                        while (m.find()) {
                            skillChoiceCount = Integer.parseInt(m.group(1));
                            String skillsPart = m.group(2);
                            for (String s : skillsPart.split("[;,/]")) {
                                String skill = s.trim().toLowerCase(Locale.ROOT)
                                        .replace(" ", "_").replace("-", "_");
                                if (SKILL_ABILITY_MAP.containsKey(skill) && !skillOptions.contains(skill)) {
                                    skillOptions.add(skill);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse skill choices for {}", classSourceKey, e);
        }

        List<String> savingThrows = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<Object> raw = mapper.readValue(cls.getSavingThrows(), List.class);
            for (Object item : raw) {
                if (item instanceof String s) {
                    String normal = s.toLowerCase(Locale.ROOT);
                    if (normal.length() <= 3) savingThrows.add(normal);
                } else if (item instanceof Map<?, ?> m) {
                    String name = (String) m.get("name");
                    if (name != null) {
                        savingThrows.add(switch (name.toLowerCase(Locale.ROOT)) {
                            case "strength" -> "str"; case "dexterity" -> "dex";
                            case "constitution" -> "con"; case "intelligence" -> "int";
                            case "wisdom" -> "wis"; case "charisma" -> "cha";
                            default -> name.toLowerCase(Locale.ROOT);
                        });
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse saving throws for {}", classSourceKey, e);
        }

        List<SubclassOption> subclasses = classRepo.findBySubclassOfOrderByNameAsc(classSourceKey).stream()
                .map(sc -> new SubclassOption(sc.getSourceKey(), sc.getName()))
                .collect(Collectors.toList());

        List<String> armorProfs = new ArrayList<>();
        List<String> weaponProfs = new ArrayList<>();
        if (cls.getProficiencies() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> profMap = mapper.readValue(cls.getProficiencies(), Map.class);
                Object armor = profMap.get("armor");
                if (armor instanceof List<?> a) {
                    for (Object item : a) armorProfs.add(item.toString());
                }
                Object weapons = profMap.get("weapons");
                if (weapons instanceof List<?> w) {
                    for (Object item : w) weaponProfs.add(item.toString());
                }
            } catch (Exception e) {
                log.debug("Failed to parse proficiencies for {}", classSourceKey, e);
            }
        }

        return new CreationOptionsDto(
                classSourceKey,
                cls.getHitDie() != null ? cls.getHitDie() : "d8",
                savingThrows,
                skillChoiceCount,
                skillOptions,
                subclasses,
                armorProfs,
                weaponProfs
        );
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
            List<SheetSpellDto> spells,
            List<AttackDto> attacks,
            List<FeatureDto> features
    ) {}

    public record LevelUpRequest(
            String classSourceKey,
            int hpRoll,
            boolean isAverage,
            String subclassSourceKey,
            String asiType,
            Map<String, Integer> asiScores
    ) {
        public LevelUpRequest(String classSourceKey, int hpRoll, boolean isAverage) {
            this(classSourceKey, hpRoll, isAverage, null, null, Map.of());
        }
    }

    public record SheetResourceDto(
            UUID id, String name, int maxUses, int currentUses, String resetRule
    ) {}

    public record SheetSpellDto(
            UUID id, String spellName, int spellLevel, boolean prepared, String sourceClass, String description
    ) {}

    // ---- Attack / Feature DTOs ----

    public record AttackDto(
            String key,
            String name,
            int attackBonus,
            String damageExpression,
            String damageType,
            String range,
            String properties,
            String ammunition,
            String notes
    ) {}

    public record FeatureDto(
            String key,
            String name,
            String actionType,
            String source,
            String body,
            String resourceName
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
            sheet.setAttacksJson(mapper.writeValueAsString(List.of()));
            sheet.setFeaturesJson(mapper.writeValueAsString(List.of()));
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
            boolean isNewClass = false;
            if (entry == null) {
                entry = new HashMap<>();
                entry.put("classSourceKey", request.classSourceKey());
                entry.put("level", 0);
                entry.put("hitDieRolls", new ArrayList<Integer>());
                classLevels.add(entry);
                isNewClass = true;
            }

            int currentLevel = ((Number) entry.get("level")).intValue();
            int newLevel = currentLevel + 1;
            entry.put("level", newLevel);

            // Handle subclass at level 3
            if (request.subclassSourceKey() != null && !request.subclassSourceKey().isBlank()) {
                entry.put("subclassSourceKey", request.subclassSourceKey());
            }

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

            // Handle ASI
            if (request.asiType() != null && !request.asiType().isBlank()) {
                Map<String, Integer> scores = mapper.readValue(sheet.getAbilityScores(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));

                if ("single".equals(request.asiType()) && request.asiScores() != null) {
                    for (var adjust : request.asiScores().entrySet()) {
                        scores.merge(adjust.getKey(), adjust.getValue(), Integer::sum);
                    }
                } else if ("double".equals(request.asiType()) && request.asiScores() != null) {
                    for (var adjust : request.asiScores().entrySet()) {
                        scores.merge(adjust.getKey(), adjust.getValue(), Integer::sum);
                    }
                } else if ("feat".equals(request.asiType())) {
                    List<String> currentFeats = sheet.getFeatRefs() != null ?
                            mapper.readValue(sheet.getFeatRefs(),
                                    mapper.getTypeFactory().constructCollectionType(List.class, String.class))
                            : new ArrayList<>();
                    String featKey = request.asiScores() != null && request.asiScores().containsKey("featSourceKey")
                            ? request.asiScores().get("featSourceKey").toString() : null;
                    if (featKey != null && !currentFeats.contains(featKey)) {
                        currentFeats.add(featKey);
                    }
                    sheet.setFeatRefs(mapper.writeValueAsString(currentFeats));
                }

                sheet.setAbilityScores(mapper.writeValueAsString(scores));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process level-up", e);
        }

        sheet = sheetRepo.save(sheet);
        syncToPartyMember(sheet);
        return toDto(sheet);
    }

    public record RestPreviewDto(
            UUID sheetId,
            String restType,
            int hitDiceAvailable,
            int hitDiceToSpend,
            int estimatedHpRecovered,
            List<String> resourcesToReset,
            Map<String, Integer> spellSlotsToRecover,
            boolean clearExhaustionOneLevel,
            List<String> notes
    ) {}

    @Transactional(readOnly = true)
    public RestPreviewDto previewRest(UUID sheetId, String restType, int hitDiceToSpend) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        DerivedValues derived = sheetEngine.derive(sheet);

        int hitDiceAvailable = derived.remainingHitDice();
        int hitDiceToSpendActual = Math.min(hitDiceToSpend, hitDiceAvailable);
        int estimatedHpRecovered = 0;
        List<String> resourcesToReset = new ArrayList<>();
        Map<String, Integer> spellSlotsToRecover = new HashMap<>();
        boolean clearExhaustionOneLevel = false;
        List<String> notes = new ArrayList<>();

        List<SheetResource> resources = resourceRepo.findBySheetId(sheetId);

        if ("SHORT".equalsIgnoreCase(restType)) {
            estimatedHpRecovered = estimateHitDiceHp(sheet, hitDiceToSpendActual);
            for (SheetResource r : resources) {
                if (r.getResetRule() == SheetResource.ResetRule.SHORT_REST) {
                    resourcesToReset.add(r.getName());
                }
            }
            if (hitDiceToSpendActual > 0) {
                notes.add("Spending " + hitDiceToSpendActual + " hit "
                        + (hitDiceToSpendActual == 1 ? "die" : "dice")
                        + " (avg ~" + estimatedHpRecovered + " HP)");
            }
        } else {
            estimatedHpRecovered = derived.maxHp();
            clearExhaustionOneLevel = true;

            int recoveredHd = Math.min(sheet.getHitDiceUsed(), Math.max(1, derived.totalHitDice() / 2));
            if (recoveredHd > 0) {
                notes.add("Recover " + recoveredHd + " hit "
                        + (recoveredHd == 1 ? "die" : "dice"));
            }

            for (SheetResource r : resources) {
                if (r.getResetRule() == SheetResource.ResetRule.SHORT_REST
                        || r.getResetRule() == SheetResource.ResetRule.LONG_REST) {
                    resourcesToReset.add(r.getName());
                }
            }

            Map<String, Integer> used = getSpellSlotsUsed(sheet);
            for (int i = 1; i < derived.spellSlots().length; i++) {
                int max = derived.spellSlots()[i];
                if (max > 0) {
                    String key = String.valueOf(i);
                    int usedCount = used.getOrDefault(key, 0);
                    if (usedCount > 0) {
                        spellSlotsToRecover.put(key, usedCount);
                    }
                }
            }
            int pactLevel = 0;
            for (int i = 1; i < derived.pactSlots().length; i++) {
                if (derived.pactSlots()[i] > 0) {
                    pactLevel = i;
                    break;
                }
            }
            if (pactLevel > 0) {
                int pactUsed = used.getOrDefault("pact", 0);
                if (pactUsed > 0) {
                    spellSlotsToRecover.put("pact", pactUsed);
                }
            }
            if (!spellSlotsToRecover.isEmpty()) {
                notes.add("All spell slots recovered");
            }
            notes.add("Full HP restored");
        }

        return new RestPreviewDto(
                sheetId,
                restType.toUpperCase(),
                hitDiceAvailable,
                hitDiceToSpendActual,
                estimatedHpRecovered,
                resourcesToReset,
                spellSlotsToRecover,
                clearExhaustionOneLevel,
                notes
        );
    }

    private int estimateHitDiceHp(CharacterSheet sheet, int hitDiceCount) {
        if (hitDiceCount <= 0) return 0;
        try {
            List<Map<String, Object>> classLevels = mapper.readValue(sheet.getClassLevels(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            int weightedSum = 0;
            int totalLevels = 0;
            for (Map<String, Object> entry : classLevels) {
                String classKey = SheetClassLevelCodec.classSourceKeyOf(entry);
                int level = ((Number) entry.get("level")).intValue();
                int dieSize = getHitDieSize(classKey);
                weightedSum += dieSize * level;
                totalLevels += level;
            }
            if (totalLevels == 0) return 0;
            int avgDieSize = weightedSum / totalLevels;
            int avgPerDie = (avgDieSize / 2) + 1;
            return avgPerDie * hitDiceCount;
        } catch (Exception e) {
            log.warn("Failed to estimate hit dice HP", e);
            return 0;
        }
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
        return new SheetSpellDto(ref.getId(), spell.getName(), spell.getLevel(), ref.isPrepared(), ref.getSourceClass(), spell.getDescription());
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
        return new SheetSpellDto(ref.getId(), spell.getName(), spell.getLevel(), ref.isPrepared(), ref.getSourceClass(), spell.getDescription());
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

    // ---- Spell Slots ----

    public Map<String, Integer> getSpellSlotsUsed(CharacterSheet sheet) {
        try {
            if (sheet.getSpellSlotsUsed() != null) {
                return mapper.readValue(sheet.getSpellSlotsUsed(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize spellSlotsUsed", e);
        }
        return new HashMap<>();
    }

    private void saveSpellSlotsUsed(CharacterSheet sheet, Map<String, Integer> used) {
        try {
            sheet.setSpellSlotsUsed(mapper.writeValueAsString(used));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize spell slots", e);
        }
    }

    public SheetDto spendSpellSlot(UUID sheetId, String level) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        DerivedValues derived = sheetEngine.derive(sheet);
        Map<String, Integer> used = getSpellSlotsUsed(sheet);
        int current = used.getOrDefault(level, 0);

        int max;
        if ("pact".equals(level)) {
            int pactLevel = 0;
            for (int i = 1; i < derived.pactSlots().length; i++) {
                if (derived.pactSlots()[i] > 0) { pactLevel = i; break; }
            }
            max = pactLevel > 0 ? derived.pactSlots()[pactLevel] : 0;
        } else {
            try {
                int lvl = Integer.parseInt(level);
                max = lvl >= 0 && lvl < derived.spellSlots().length ? derived.spellSlots()[lvl] : 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid spell level: " + level);
            }
        }

        used.put(level, Math.min(current + 1, max));
        saveSpellSlotsUsed(sheet, used);
        return toDto(sheet);
    }

    public SheetDto recoverSpellSlot(UUID sheetId, String level) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        Map<String, Integer> used = getSpellSlotsUsed(sheet);
        int current = used.getOrDefault(level, 0);
        used.put(level, Math.max(0, current - 1));
        saveSpellSlotsUsed(sheet, used);
        return toDto(sheet);
    }

    public Map<String, Integer> getRemainingSlots(UUID sheetId) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        DerivedValues derived = sheetEngine.derive(sheet);
        Map<String, Integer> used = getSpellSlotsUsed(sheet);
        Map<String, Integer> remaining = new HashMap<>();

        for (int i = 1; i < derived.spellSlots().length; i++) {
            int max = derived.spellSlots()[i];
            if (max > 0) {
                String key = String.valueOf(i);
                remaining.put(key, max - used.getOrDefault(key, 0));
            }
        }

        int pactMax = 0;
        for (int i = 1; i < derived.pactSlots().length; i++) {
            if (derived.pactSlots()[i] > 0) {
                pactMax = derived.pactSlots()[i];
                break;
            }
        }
        if (pactMax > 0) {
            remaining.put("pact", pactMax - used.getOrDefault("pact", 0));
        }

        return remaining;
    }

    // ---- Attacks ----

    public List<AttackDto> getAttacks(UUID sheetId) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        return parseAttacks(sheet.getAttacksJson());
    }

    public List<AttackDto> setAttacks(UUID sheetId, List<AttackDto> attacks) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        try {
            sheet.setAttacksJson(mapper.writeValueAsString(attacks));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize attacks", e);
        }
        return getAttacks(sheetId);
    }

    public AttackDto addAttack(UUID sheetId, AttackDto request) {
        List<AttackDto> attacks = new ArrayList<>(getAttacks(sheetId));
        String key = request.key() != null && !request.key().isBlank()
                ? request.key() : generateKey(request.name());
        AttackDto attack = new AttackDto(key, request.name(), request.attackBonus(),
                request.damageExpression(), request.damageType(), request.range(),
                request.properties(), request.ammunition(), request.notes());
        attacks.add(attack);
        try {
            sheetRepo.findById(sheetId).ifPresent(s -> {
                try {
                    s.setAttacksJson(mapper.writeValueAsString(attacks));
                    sheetRepo.save(s);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize attacks", e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to add attack", e);
        }
        return attack;
    }

    public AttackDto updateAttack(UUID sheetId, AttackDto request) {
        List<AttackDto> attacks = new ArrayList<>(getAttacks(sheetId));
        boolean found = false;
        for (int i = 0; i < attacks.size(); i++) {
            if (attacks.get(i).key().equals(request.key())) {
                attacks.set(i, request);
                found = true;
                break;
            }
        }
        if (!found) throw new IllegalArgumentException("Attack not found: " + request.key());
        try {
            CharacterSheet sheet = sheetRepo.findById(sheetId).orElseThrow();
            sheet.setAttacksJson(mapper.writeValueAsString(attacks));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize attacks", e);
        }
        return request;
    }

    public void deleteAttack(UUID sheetId, String key) {
        List<AttackDto> attacks = new ArrayList<>(getAttacks(sheetId));
        boolean removed = attacks.removeIf(a -> a.key().equals(key));
        if (!removed) throw new IllegalArgumentException("Attack not found: " + key);
        try {
            CharacterSheet sheet = sheetRepo.findById(sheetId).orElseThrow();
            sheet.setAttacksJson(mapper.writeValueAsString(attacks));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize attacks", e);
        }
    }

    // ---- Features ----

    public List<FeatureDto> getFeatures(UUID sheetId) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        return parseFeatures(sheet.getFeaturesJson());
    }

    public List<FeatureDto> setFeatures(UUID sheetId, List<FeatureDto> features) {
        CharacterSheet sheet = sheetRepo.findById(sheetId)
                .orElseThrow(() -> new IllegalArgumentException("Sheet not found"));
        try {
            sheet.setFeaturesJson(mapper.writeValueAsString(features));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize features", e);
        }
        return getFeatures(sheetId);
    }

    public FeatureDto addFeature(UUID sheetId, FeatureDto request) {
        List<FeatureDto> features = new ArrayList<>(getFeatures(sheetId));
        String key = request.key() != null && !request.key().isBlank()
                ? request.key() : generateKey(request.name());
        FeatureDto feature = new FeatureDto(key, request.name(), request.actionType(),
                request.source(), request.body(), request.resourceName());
        features.add(feature);
        try {
            CharacterSheet sheet = sheetRepo.findById(sheetId).orElseThrow();
            sheet.setFeaturesJson(mapper.writeValueAsString(features));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize features", e);
        }
        return feature;
    }

    public FeatureDto updateFeature(UUID sheetId, FeatureDto request) {
        List<FeatureDto> features = new ArrayList<>(getFeatures(sheetId));
        boolean found = false;
        for (int i = 0; i < features.size(); i++) {
            if (features.get(i).key().equals(request.key())) {
                features.set(i, request);
                found = true;
                break;
            }
        }
        if (!found) throw new IllegalArgumentException("Feature not found: " + request.key());
        try {
            CharacterSheet sheet = sheetRepo.findById(sheetId).orElseThrow();
            sheet.setFeaturesJson(mapper.writeValueAsString(features));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize features", e);
        }
        return request;
    }

    public void deleteFeature(UUID sheetId, String key) {
        List<FeatureDto> features = new ArrayList<>(getFeatures(sheetId));
        boolean removed = features.removeIf(f -> f.key().equals(key));
        if (!removed) throw new IllegalArgumentException("Feature not found: " + key);
        try {
            CharacterSheet sheet = sheetRepo.findById(sheetId).orElseThrow();
            sheet.setFeaturesJson(mapper.writeValueAsString(features));
            sheetRepo.save(sheet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize features", e);
        }
    }

    private List<AttackDto> parseAttacks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, AttackDto.class));
        } catch (Exception e) {
            log.warn("Failed to parse attacks JSON", e);
            return List.of();
        }
    }

    private List<FeatureDto> parseFeatures(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, FeatureDto.class));
        } catch (Exception e) {
            log.warn("Failed to parse features JSON", e);
            return List.of();
        }
    }

    static String generateKey(String name) {
        if (name == null || name.isBlank()) return "";
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
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
                    String description = s.getSpell() != null ? s.getSpell().getDescription() : null;
                    return new SheetSpellDto(s.getId(), spellName, spellLevel, s.isPrepared(), s.getSourceClass(), description);
                })
                .collect(Collectors.toList());

        String speciesName = sheet.getSpecies() != null ? sheet.getSpecies().getName() : null;
        UUID speciesId = sheet.getSpecies() != null ? sheet.getSpecies().getId() : null;
        String backgroundName = sheet.getBackground() != null ? sheet.getBackground().getName() : null;
        UUID backgroundId = sheet.getBackground() != null ? sheet.getBackground().getId() : null;

        List<AttackDto> attacks = parseAttacks(sheet.getAttacksJson());
        List<FeatureDto> features = parseFeatures(sheet.getFeaturesJson());

        return new SheetDto(
                sheet.getId(), sheet.getPartyMember().getId(),
                abilityScores, classLevels,
                speciesName, speciesId,
                backgroundName, backgroundId,
                featRefs, sheet.getXp(), overrides, sheet.getHitDiceUsed(),
                spellsUsed, derived, resources, spells,
                attacks, features
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

    public boolean isAsiLevel(String classSourceKey, int level) {
        var clsOpt = classRepo.findBySourceKey(classSourceKey);
        if (clsOpt.isEmpty()) {
            return level == 4 || level == 8 || level == 12 || level == 16 || level == 19;
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> features = mapper.readValue(clsOpt.get().getFeatures(), List.class);
            for (Map<String, Object> f : features) {
                Object tableData = f.get("data_for_class_table");
                if (tableData instanceof List<?> rows) {
                    for (Object row : rows) {
                        if (row instanceof Map<?, ?> r) {
                            Object lvl = r.get("level");
                            if (lvl instanceof Number ln && ln.intValue() == level) {
                                String name = (String) f.get("name");
                                String featureType = (String) f.get("feature_type");
                                if ((name != null && (name.toLowerCase(Locale.ROOT).contains("asi")
                                        || name.toLowerCase(Locale.ROOT).contains("ability score improvement")))
                                        || (featureType != null && featureType.contains("ASI"))) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            // Fallback: check standard ASI levels
            return level == 4 || level == 8 || level == 12 || level == 16 || level == 19;
        } catch (Exception e) {
            log.debug("Failed to parse features for ASI check: {}", classSourceKey, e);
            return level == 4 || level == 8 || level == 12 || level == 16 || level == 19;
        }
    }

    public boolean levelHasSubclass(String classSourceKey, int level) {
        return level >= SUBCLASS_LEVEL;
    }
}
