package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SheetEngine {

    private final CharacterClassRepository classRepo;
    private final RuleSectionRepository ruleSectionRepo;
    private final FeatRepository featRepo;
    private final ObjectMapper mapper;

    private Map<Integer, Integer> proficiencyBonusTable;
    private Map<Integer, int[]> multiclassSlotTable;
    private Map<String, String> classSpellcastingAbilities;
    private Map<String, Map<Integer, int[]>> classSlotTables;
    private Map<String, Integer> classHitDies;
    private Map<String, Set<String>> classSavingThrows;
    private Map<String, List<String>> classSkillProficiencies;
    private Map<String, String> classNames;

    private static final Logger log = LoggerFactory.getLogger(SheetEngine.class);

    private static final Map<String, String> CASTER_TYPES = Map.of(
        "wizard", "FULL", "sorcerer", "FULL", "cleric", "FULL", "druid", "FULL",
        "bard", "FULL", "warlock", "PACT",
        "paladin", "HALF", "ranger", "HALF",
        "fighter", "THIRD", "rogue", "THIRD"
    );

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

    private static final Map<String, String> SAVE_NAME_NORMALIZE = Map.of(
        "strength", "str", "dexterity", "dex", "constitution", "con",
        "intelligence", "int", "wisdom", "wis", "charisma", "cha"
    );

    public SheetEngine(CharacterClassRepository classRepo, RuleSectionRepository ruleSectionRepo, FeatRepository featRepo) {
        this.classRepo = classRepo;
        this.ruleSectionRepo = ruleSectionRepo;
        this.featRepo = featRepo;
        this.mapper = new ObjectMapper();
    }

    public record DerivedValues(
        int strMod, int dexMod, int conMod, int intMod, int wisMod, int chaMod,
        int proficiencyBonus,
        int totalLevel,
        int saveStr, int saveDex, int saveCon, int saveInt, int saveWis, int saveCha,
        Map<String, Integer> skillBonuses,
        int passivePerception, int passiveInsight, int passiveInvestigation,
        int maxHp,
        int totalHitDice,
        int remainingHitDice,
        int[] spellSlots,
        int[] pactSlots,
        Map<String, String> classSpellcastingAbilities,
        int spellSaveDC,
        int spellAttackBonus,
        String classAndLevel,
        int speed,
        int initiativeBonus,
        int armorClass,
        List<String> skillChoices,
        List<String> featsRequiringManualAssignment
    ) {}

    @PostConstruct
    public void initialize() {
        this.proficiencyBonusTable = buildProficiencyBonusTable();
        this.multiclassSlotTable = parseMulticlassSlotTable();
        this.classSpellcastingAbilities = buildSpellcastingAbilities();
        this.classSlotTables = buildClassSlotTables();
        this.classHitDies = buildClassHitDies();
        this.classSavingThrows = buildClassSavingThrows();
        this.classSkillProficiencies = buildClassSkillProficiencies();
        this.classNames = buildClassNames();
    }

    private Map<String, Integer> buildClassHitDies() {
        Map<String, Integer> map = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            String die = cls.getHitDie();
            if (die != null && die.startsWith("d")) {
                map.put(cls.getSourceKey(), Integer.parseInt(die.substring(1)));
            }
        }
        return map;
    }

    private Map<String, Set<String>> buildClassSavingThrows() {
        Map<String, Set<String>> map = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> raw = mapper.readValue(cls.getSavingThrows(), List.class);
                Set<String> saves = new HashSet<>();
                for (Object item : raw) {
                    if (item instanceof String s) {
                        saves.add(s);
                    } else if (item instanceof Map<?, ?> m) {
                        String name = (String) m.get("name");
                        if (name != null) {
                            String normal = SAVE_NAME_NORMALIZE.getOrDefault(name.toLowerCase(), name.toLowerCase());
                            saves.add(normal);
                        }
                    }
                }
                map.put(cls.getSourceKey(), saves);
            } catch (Exception e) {
                log.debug("Failed to parse saving throws for {}", cls.getSourceKey(), e);
                map.put(cls.getSourceKey(), Set.of());
            }
        }
        return map;
    }

    private static final Pattern CHOOSE_FROM_PATTERN = Pattern.compile(
        "choose\\s+(\\d+)\\s+from\\s+([A-Za-z_\\s,]+)", Pattern.CASE_INSENSITIVE
    );

    private Map<String, List<String>> buildClassSkillProficiencies() {
        Map<String, List<String>> map = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> features = mapper.readValue(cls.getFeatures(), List.class);
                List<String> unconditionalSkills = new ArrayList<>();
                for (Map<String, Object> f : features) {
                    if ("CORE_TRAITS_TABLE".equals(f.get("feature_type"))) {
                        String desc = (String) f.get("description");
                        if (desc != null) {
                            Set<String> chooseSkills = new HashSet<>();
                            Matcher matcher = CHOOSE_FROM_PATTERN.matcher(desc);
                            while (matcher.find()) {
                                String skillsPart = matcher.group(2);
                                for (String s : skillsPart.split("[;,/]")) {
                                    String skill = s.trim().toLowerCase().replace(" ", "_").replace("-", "_");
                                    if (SKILL_ABILITY_MAP.containsKey(skill)) {
                                        chooseSkills.add(skill);
                                    }
                                }
                            }
                            for (String line : desc.split("\\n")) {
                                if (line.toLowerCase().contains("skill proficiencies")) {
                                    String[] cells = line.split("\\|");
                                    if (cells.length >= 3) {
                                        for (String s : cells[2].trim().split("[;,/]")) {
                                            String skill = s.trim().toLowerCase().replace(" ", "_").replace("-", "_");
                                            if (SKILL_ABILITY_MAP.containsKey(skill) && !chooseSkills.contains(skill)) {
                                                unconditionalSkills.add(skill);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                map.put(cls.getSourceKey(), unconditionalSkills);
            } catch (Exception e) {
                log.debug("Failed to parse skill proficiencies for {}", cls.getSourceKey(), e);
                map.put(cls.getSourceKey(), List.of());
            }
        }
        return map;
    }

    private Map<String, String> buildClassNames() {
        Map<String, String> map = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            map.put(cls.getSourceKey(), cls.getName());
        }
        return map;
    }

    private Map<Integer, Integer> buildProficiencyBonusTable() {
        Map<Integer, Integer> table = new LinkedHashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> features = mapper.readValue(cls.getFeatures(), List.class);
                for (Map<String, Object> feature : features) {
                    if ("PROFICIENCY_BONUS".equals(feature.get("feature_type"))) {
                        Object tableData = feature.get("data_for_class_table");
                        if (tableData instanceof List<?> rows) {
                            for (Object row : rows) {
                                if (row instanceof Map<?, ?> r) {
                                    Object levelObj = r.get("level");
                                    Object valueObj = r.get("column_value");
                                    if (levelObj instanceof Number lvl && valueObj instanceof String val) {
                                        table.put(lvl.intValue(), Integer.parseInt(val.replace("+", "")));
                                    }
                                }
                            }
                        }
                        if (!table.isEmpty()) return table;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse proficiency bonus features for {}", cls.getSourceKey(), e);
            }
        }
        if (table.isEmpty()) {
            log.warn("Could not build proficiency bonus table from class data, using fallback table");
            int[] pb = {0, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6};
            for (int i = 1; i <= 20; i++) table.put(i, pb[i]);
        }
        return table;
    }

    private Map<Integer, int[]> parseMulticlassSlotTable() {
        Map<Integer, int[]> table = new LinkedHashMap<>();

        var sections = ruleSectionRepo.findAllByOrderBySortOrderAsc();
        RuleSection targetSection = null;
        for (var section : sections) {
            if ("srd-2024_multiclassing_spellcasting".equals(section.getSourceKey())) {
                targetSection = section;
                break;
            }
        }

        if (targetSection == null) return buildFallbackMulticlassTable();

        String desc = targetSection.getBody();
        if (desc == null) return buildFallbackMulticlassTable();

        String[] lines = desc.split("\\n");
        boolean inTable = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("|") && line.contains("Level") && line.contains("1")) {
                inTable = true;
                continue;
            }
            if (inTable && line.startsWith("|") && !line.startsWith("|-")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 10) {
                    try {
                        int level = Integer.parseInt(cells[1].trim());
                        int[] slots = new int[10];
                        for (int i = 2; i <= 10 && i < cells.length; i++) {
                            String val = cells[i].trim();
                            if (val.equals("\u2014") || val.equals("—") || val.equals("-") || val.isEmpty()) {
                                slots[i - 1] = 0;
                            } else {
                                slots[i - 1] = Integer.parseInt(val);
                            }
                        }
                        table.put(level, slots);
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse slot table row: {}", line, e);
                    }
                }
            }
        }
        if (table.isEmpty()) {
            log.warn("Could not parse multiclass slot table from rules section, using fallback table");
            return buildFallbackMulticlassTable();
        }
        return table;
    }

    private Map<Integer, int[]> buildFallbackMulticlassTable() {
        Map<Integer, int[]> table = new LinkedHashMap<>();
        table.put(1,  new int[]{0, 2, 0, 0, 0, 0, 0, 0, 0, 0});
        table.put(2,  new int[]{0, 3, 0, 0, 0, 0, 0, 0, 0, 0});
        table.put(3,  new int[]{0, 4, 2, 0, 0, 0, 0, 0, 0, 0});
        table.put(4,  new int[]{0, 4, 3, 0, 0, 0, 0, 0, 0, 0});
        table.put(5,  new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0});
        table.put(6,  new int[]{0, 4, 3, 3, 0, 0, 0, 0, 0, 0});
        table.put(7,  new int[]{0, 4, 3, 3, 1, 0, 0, 0, 0, 0});
        table.put(8,  new int[]{0, 4, 3, 3, 2, 0, 0, 0, 0, 0});
        table.put(9,  new int[]{0, 4, 3, 3, 3, 1, 0, 0, 0, 0});
        table.put(10, new int[]{0, 4, 3, 3, 3, 2, 0, 0, 0, 0});
        table.put(11, new int[]{0, 4, 3, 3, 3, 2, 1, 0, 0, 0});
        table.put(12, new int[]{0, 4, 3, 3, 3, 2, 1, 0, 0, 0});
        table.put(13, new int[]{0, 4, 3, 3, 3, 2, 1, 1, 0, 0});
        table.put(14, new int[]{0, 4, 3, 3, 3, 2, 1, 1, 0, 0});
        table.put(15, new int[]{0, 4, 3, 3, 3, 2, 1, 1, 1, 0});
        table.put(16, new int[]{0, 4, 3, 3, 3, 2, 1, 1, 1, 0});
        table.put(17, new int[]{0, 4, 3, 3, 3, 2, 1, 1, 1, 1});
        table.put(18, new int[]{0, 4, 3, 3, 3, 3, 1, 1, 1, 1});
        table.put(19, new int[]{0, 4, 3, 3, 3, 3, 2, 1, 1, 1});
        table.put(20, new int[]{0, 4, 3, 3, 3, 3, 2, 2, 1, 1});
        return table;
    }

    private Map<String, String> buildSpellcastingAbilities() {
        Map<String, String> abilities = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> features = mapper.readValue(cls.getFeatures(), List.class);
                boolean hasSpellSlots = false;
                for (Map<String, Object> f : features) {
                    if ("SPELL_SLOTS".equals(f.get("feature_type"))) {
                        hasSpellSlots = true;
                        break;
                    }
                }
                if (!hasSpellSlots) continue;

                for (Map<String, Object> f : features) {
                    if ("CORE_TRAITS_TABLE".equals(f.get("feature_type"))) {
                        String desc = (String) f.get("description");
                        if (desc != null) {
                            String ability = parsePrimaryAbility(desc);
                            if (ability != null) {
                                abilities.put(cls.getSourceKey(), ability);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse spellcasting abilities for {}", cls.getSourceKey(), e);
            }
        }
        return abilities;
    }

    private String parsePrimaryAbility(String markdown) {
        for (String line : markdown.split("\\n")) {
            if (line.toLowerCase().contains("primary ability")) {
                String[] cells = line.split("\\|");
                if (cells.length >= 3) {
                    String ability = cells[2].trim().toLowerCase();
                    if (ability.contains("strength")) return "str";
                    if (ability.contains("dexterity")) return "dex";
                    if (ability.contains("constitution")) return "con";
                    if (ability.contains("intelligence")) return "int";
                    if (ability.contains("wisdom")) return "wis";
                    if (ability.contains("charisma")) return "cha";
                }
            }
        }
        return null;
    }

    private Map<String, Map<Integer, int[]>> buildClassSlotTables() {
        Map<String, Map<Integer, int[]>> tables = new HashMap<>();
        var classes = classRepo.findAllByOrderByNameAsc();
        for (var cls : classes) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> features = mapper.readValue(cls.getFeatures(), List.class);
                Map<Integer, int[]> classTable = new HashMap<>();
                for (Map<String, Object> f : features) {
                    if ("SPELL_SLOTS".equals(f.get("feature_type"))) {
                        String key = (String) f.get("key");
                        if (key != null) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("_slots-(\\d+)");
                            java.util.regex.Matcher matcher = pattern.matcher(key);
                            int spellLevel;
                            if (matcher.find()) {
                                spellLevel = Integer.parseInt(matcher.group(1));
                            } else {
                                continue;
                            }
                            Object tableData = f.get("data_for_class_table");
                            if (tableData instanceof List<?> rows) {
                                for (Object row : rows) {
                                    if (row instanceof Map<?, ?> r) {
                                        Object lvl = r.get("level");
                                        Object val = r.get("column_value");
                                        if (lvl instanceof Number cl && val instanceof String vs) {
                                            int classLevel = cl.intValue();
                                            int slots = Integer.parseInt(vs);
                                            classTable.computeIfAbsent(classLevel, k -> new int[10]);
                                            classTable.get(classLevel)[spellLevel] = slots;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!classTable.isEmpty()) {
                    tables.put(cls.getSourceKey(), classTable);
                }
            } catch (Exception e) {
                log.debug("Failed to parse class slot tables for {}", cls.getSourceKey(), e);
            }
        }
        return tables;
    }

    @SuppressWarnings("unchecked")
    public DerivedValues derive(CharacterSheet sheet) {
        try {
            Map<String, Object> scores = sheet.getAbilityScores() != null ?
                mapper.readValue(sheet.getAbilityScores(), Map.class) : Map.of();
            List<Map<String, Object>> classLevels = sheet.getClassLevels() != null ?
                mapper.readValue(sheet.getClassLevels(), List.class) : List.of();
            Map<String, Object> prof = sheet.getProficiencies() != null ?
                mapper.readValue(sheet.getProficiencies(), Map.class) : Map.of();
            Map<String, Object> overrides = sheet.getOverrides() != null ?
                mapper.readValue(sheet.getOverrides(), Map.class) : Map.of();
            List<String> featRefs = sheet.getFeatRefs() != null ?
                mapper.readValue(sheet.getFeatRefs(), List.class) : List.of();

            int str = getInt(scores, "str");
            int dex = getInt(scores, "dex");
            int con = getInt(scores, "con");
            int intel = getInt(scores, "int");
            int wis = getInt(scores, "wis");
            int cha = getInt(scores, "cha");

            List<String> featsRequiringManualAssignment = new ArrayList<>();

            if (!featRefs.isEmpty()) {
                var feats = featRepo.findBySourceKeyIn(featRefs);
                for (Feat feat : feats) {
                    if ("ability-score-improvement".equals(feat.getSourceKey())) {
                        featsRequiringManualAssignment.add(feat.getName());
                        continue;
                    }
                    String benefit = feat.getBenefit();
                    if (benefit != null) {
                        String lower = benefit.toLowerCase();
                        Matcher m = Pattern.compile("\\+(\\d+)\\s+to\\s+(\\w+)").matcher(lower);
                        while (m.find()) {
                            int bonus = Integer.parseInt(m.group(1));
                            String target = m.group(2);
                            switch (target) {
                                case "strength" -> str += bonus;
                                case "dexterity" -> dex += bonus;
                                case "constitution" -> con += bonus;
                                case "intelligence" -> intel += bonus;
                                case "wisdom" -> wis += bonus;
                                case "charisma" -> cha += bonus;
                                default -> {
                                    if (target.startsWith("any")) {
                                        str += bonus;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            int strMod = mod(str);
            int dexMod = mod(dex);
            int conMod = mod(con);
            int intMod = mod(intel);
            int wisMod = mod(wis);
            int chaMod = mod(cha);

            Map<String, Integer> abilityMods = Map.of(
                "str", strMod, "dex", dexMod, "con", conMod,
                "int", intMod, "wis", wisMod, "cha", chaMod
            );

            int totalLevel = 0;
            for (Map<String, Object> entry : classLevels) {
                totalLevel += getInt(entry, "level");
            }
            int profBonus = proficiencyBonusTable.getOrDefault(totalLevel, 2);

            int maxHp = 0;
            int totalHitDice = 0;
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                int level = getInt(entry, "level");
                totalHitDice += level;
                int dieType = classHitDies.getOrDefault(classKey, 8);
                List<Number> rolls = entry.get("hitDieRolls") instanceof List<?> r ? (List<Number>) r : List.of();
                int hpFromClass = dieType;
                for (int i = 0; i < level - 1 && i < rolls.size(); i++) {
                    hpFromClass += rolls.get(i).intValue();
                }
                hpFromClass += conMod * level;
                maxHp += hpFromClass;
            }
            int remainingHitDice = Math.max(0, totalHitDice - sheet.getHitDiceUsed());

            Set<String> proficientSaves = new HashSet<>();
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                Set<String> saves = classSavingThrows.getOrDefault(classKey, Set.of());
                proficientSaves.addAll(saves);
            }
            Object saveProfs = prof.get("saving_throws");
            if (saveProfs instanceof List<?> sl) {
                for (Object s : sl) proficientSaves.add(s.toString());
            }

            int saveStr = strMod + (proficientSaves.contains("str") ? profBonus : 0);
            int saveDex = dexMod + (proficientSaves.contains("dex") ? profBonus : 0);
            int saveCon = conMod + (proficientSaves.contains("con") ? profBonus : 0);
            int saveInt = intMod + (proficientSaves.contains("int") ? profBonus : 0);
            int saveWis = wisMod + (proficientSaves.contains("wis") ? profBonus : 0);
            int saveCha = chaMod + (proficientSaves.contains("cha") ? profBonus : 0);

            List<String> profSkills = new ArrayList<>();
            List<String> expertise = new ArrayList<>();
            Object skillsObj = prof.get("skills");
            if (skillsObj instanceof List<?> sl) {
                for (Object s : sl) profSkills.add(s.toString());
            }
            if (sheet.getBackground() != null && sheet.getBackground().getSkills() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> bgSkills = mapper.readValue(sheet.getBackground().getSkills(), List.class);
                    if (bgSkills != null) profSkills.addAll(bgSkills);
                } catch (Exception e) {
                    log.debug("Failed to parse background skills", e);
                }
            }
            Object expObj = prof.get("expertise");
            if (expObj instanceof List<?> el) {
                for (Object s : el) expertise.add(s.toString());
            }

            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                List<String> skills = classSkillProficiencies.getOrDefault(classKey, List.of());
                for (String skill : skills) {
                    if (!profSkills.contains(skill)) {
                        profSkills.add(skill);
                    }
                }
            }

            Map<String, Integer> skillBonuses = new LinkedHashMap<>();
            for (String skill : SKILL_ABILITY_MAP.keySet()) {
                String ability = SKILL_ABILITY_MAP.get(skill);
                int mod = abilityMods.getOrDefault(ability, 0);
                boolean isProficient = profSkills.contains(skill);
                boolean isExpertise = expertise.contains(skill);
                int bonus = mod;
                if (isProficient) bonus += (isExpertise ? 2 * profBonus : profBonus);
                skillBonuses.put(skill, bonus);
            }

            int passivePerception = 10 + skillBonuses.getOrDefault("perception", 0);
            int passiveInsight = 10 + skillBonuses.getOrDefault("insight", 0);
            int passiveInvestigation = 10 + skillBonuses.getOrDefault("investigation", 0);

            int[] spellSlots = new int[10];
            int[] pactSlots = new int[10];
            List<Map<String, Object>> castingClasses = new ArrayList<>();
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                if (classSlotTables.containsKey(classKey)) {
                    castingClasses.add(entry);
                }
            }

            if (castingClasses.size() == 1) {
                Map<String, Object> entry = castingClasses.get(0);
                String classKey = (String) entry.get("classSourceKey");
                int classLevel = getInt(entry, "level");
                Map<Integer, int[]> classTable = classSlotTables.get(classKey);
                if (classTable != null) {
                    int[] slots = classTable.get(classLevel);
                    if (slots != null) spellSlots = slots;
                }
                if (classKey != null && classKey.contains("warlock")) {
                    pactSlots = spellSlots;
                    spellSlots = new int[10];
                }
            } else if (castingClasses.size() > 1) {
                int combinedLevel = 0;
                for (Map<String, Object> entry : castingClasses) {
                    String classKey = (String) entry.get("classSourceKey");
                    int classLevel = getInt(entry, "level");
                    if (classKey != null && classKey.contains("warlock")) {
                        Map<Integer, int[]> lockTable = classSlotTables.get(classKey);
                        if (lockTable != null) {
                            int[] lockSlots = lockTable.get(classLevel);
                            if (lockSlots != null) pactSlots = lockSlots;
                        }
                    } else {
                        String casterType = getCasterType(classKey);
                        if ("FULL".equals(casterType)) {
                            combinedLevel += classLevel;
                        } else if ("HALF".equals(casterType)) {
                            combinedLevel += (int) Math.ceil(classLevel / 2.0);
                        } else if ("THIRD".equals(casterType)) {
                            combinedLevel += Math.floorDiv(classLevel, 3);
                        }
                    }
                }
                if (combinedLevel > 0) {
                    int[] slots = multiclassSlotTable.get(combinedLevel);
                    if (slots != null) spellSlots = slots;
                }
            }

            int bestDC = 0;
            int bestAtk = 0;
            Map<String, String> classCasting = new LinkedHashMap<>();
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                String ability = classSpellcastingAbilities.get(classKey);
                if (ability != null) {
                    classCasting.put(classKey, ability);
                    int mod = abilityMods.getOrDefault(ability, 0);
                    int dc = 8 + profBonus + mod;
                    int atk = profBonus + mod;
                    if (dc > bestDC) bestDC = dc;
                    if (atk > bestAtk) bestAtk = atk;
                }
            }

            bestDC = getOverrideInt(overrides, "spellSaveDc", bestDC);
            bestAtk = getOverrideInt(overrides, "spellAttackBonus", bestAtk);

            List<String> skillChoices = new ArrayList<>();
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                if (classKey == null) continue;
                var clsOpt = classRepo.findBySourceKey(classKey);
                if (clsOpt.isEmpty()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> features = mapper.readValue(clsOpt.get().getFeatures(), List.class);
                    for (Map<String, Object> f : features) {
                        if ("CORE_TRAITS_TABLE".equals(f.get("feature_type"))) {
                            String desc = (String) f.get("description");
                            if (desc != null) {
                                Matcher m = CHOOSE_FROM_PATTERN.matcher(desc);
                                while (m.find()) {
                                    String skillsPart = m.group(2);
                                    for (String s : skillsPart.split("[;,/]")) {
                                        String skill = s.trim().toLowerCase().replace(" ", "_").replace("-", "_");
                                        if (SKILL_ABILITY_MAP.containsKey(skill) && !skillChoices.contains(skill)) {
                                            skillChoices.add(skill);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse features for skill choices: {}", classKey, e);
                }
            }

            List<String> classStrings = new ArrayList<>();
            for (Map<String, Object> entry : classLevels) {
                String classKey = (String) entry.get("classSourceKey");
                int level = getInt(entry, "level");
                String name = classNames.getOrDefault(classKey, classKey);
                classStrings.add(name + " " + level);
            }
            String classAndLevel = String.join(" / ", classStrings);

            int speed = 30;
            if (sheet.getSpecies() != null && sheet.getSpecies().getSpeed() != null) {
                String speedStr = sheet.getSpecies().getSpeed();
                try {
                    speed = Integer.parseInt(speedStr.replaceAll("[^\\d]", ""));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse speed: {}", speedStr, e);
                }
            }

            int armorClass = 10 + dexMod;

            armorClass = getOverrideInt(overrides, "ac", armorClass);
            maxHp = getOverrideInt(overrides, "maxHp", maxHp);
            speed = getOverrideInt(overrides, "speed", speed);
            int initiativeBonus = getOverrideInt(overrides, "initiativeBonus", dexMod);
            passivePerception = getOverrideInt(overrides, "passivePerception", passivePerception);
            passiveInsight = getOverrideInt(overrides, "passiveInsight", passiveInsight);
            passiveInvestigation = getOverrideInt(overrides, "passiveInvestigation", passiveInvestigation);
            saveStr = getOverrideInt(overrides, "save_str", saveStr);
            saveDex = getOverrideInt(overrides, "save_dex", saveDex);
            saveCon = getOverrideInt(overrides, "save_con", saveCon);
            saveInt = getOverrideInt(overrides, "save_int", saveInt);
            saveWis = getOverrideInt(overrides, "save_wis", saveWis);
            saveCha = getOverrideInt(overrides, "save_cha", saveCha);

            Map<String, Integer> finalSkillBonuses = new LinkedHashMap<>(skillBonuses);
            for (Map.Entry<String, Object> override : overrides.entrySet()) {
                String key = override.getKey();
                if (key.startsWith("skill_") && !key.equals("skill_")) {
                    String skill = key.substring(6);
                    if (SKILL_ABILITY_MAP.containsKey(skill) && override.getValue() instanceof Number n) {
                        finalSkillBonuses.put(skill, n.intValue());
                    }
                }
            }

            return new DerivedValues(
                strMod, dexMod, conMod, intMod, wisMod, chaMod,
                profBonus, totalLevel,
                saveStr, saveDex, saveCon, saveInt, saveWis, saveCha,
                finalSkillBonuses,
                passivePerception, passiveInsight, passiveInvestigation,
                maxHp, totalHitDice, remainingHitDice,
                spellSlots, pactSlots,
                classCasting,
                bestDC, bestAtk,
                classAndLevel,
                speed, initiativeBonus, armorClass,
                skillChoices,
                featsRequiringManualAssignment
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive sheet values", e);
        }
    }

    private static int mod(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private static int getOverrideInt(Map<String, Object> overrides, String key, int fallback) {
        Object val = overrides.get(key);
        if (val instanceof Number n) return n.intValue();
        return fallback;
    }

    private String getCasterType(String classKey) {
        if (classKey == null) return "NONE";
        for (var entry : CASTER_TYPES.entrySet()) {
            String prefix = "srd-2024_" + entry.getKey();
            if (classKey.equals(prefix) || classKey.startsWith(prefix + "_")) {
                return entry.getValue();
            }
        }
        if (classSlotTables.containsKey(classKey)) return "FULL";
        return "NONE";
    }
}
