package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StatBlockRuntimeProjection(
        UUID id, String name, String size, String type, String alignment, String cr,
        int ac, String hp, int xp, String speed, String senses, String languages,
        String skills, String damageResistances, String damageImmunities, String conditionImmunities,
        Map<String, Integer> abilityScores, List<Save> savingThrows,
        List<Entry> traits, List<Entry> actions, List<Entry> bonusActions,
        List<Entry> reactions, List<Entry> legendaryActions) {

    public record Save(String ability, int modifier) {}

    public record Entry(String name, String description, Integer attackBonus, String damageExpression) {}

    private static final Pattern ATTACK = Pattern.compile("\\+(\\d+) to hit");
    private static final Pattern DAMAGE = Pattern.compile("\\((\\d+d\\d+[-+]?\\d*)\\)");

    public static StatBlockRuntimeProjection from(StatBlock sb, ObjectMapper objectMapper) {
        Map<String, Integer> abilityScores = new LinkedHashMap<>();
        abilityScores.put("str", sb.getStrScore());
        abilityScores.put("dex", sb.getDexScore());
        abilityScores.put("con", sb.getConScore());
        abilityScores.put("int", sb.getIntScore());
        abilityScores.put("wis", sb.getWisScore());
        abilityScores.put("cha", sb.getChaScore());

        List<Save> savingThrows = new ArrayList<>();
        if (sb.getStrSave() != null) savingThrows.add(new Save("str", sb.getStrSave()));
        if (sb.getDexSave() != null) savingThrows.add(new Save("dex", sb.getDexSave()));
        if (sb.getConSave() != null) savingThrows.add(new Save("con", sb.getConSave()));
        if (sb.getIntSave() != null) savingThrows.add(new Save("int", sb.getIntSave()));
        if (sb.getWisSave() != null) savingThrows.add(new Save("wis", sb.getWisSave()));
        if (sb.getChaSave() != null) savingThrows.add(new Save("cha", sb.getChaSave()));

        return new StatBlockRuntimeProjection(
                sb.getId(), sb.getName(), sb.getSize(), sb.getType(), sb.getAlignment(),
                sb.getCr(), sb.getAc(), sb.getHp(), sb.getXp(), sb.getSpeed(),
                sb.getSenses(), sb.getLanguages(), sb.getSkills(),
                sb.getDamageResistances(), sb.getDamageImmunities(), sb.getConditionImmunities(),
                abilityScores, savingThrows,
                parseEntries(sb.getTraits(), objectMapper),
                parseEntries(sb.getActions(), objectMapper),
                parseEntries(sb.getBonusActions(), objectMapper),
                parseEntries(sb.getReactions(), objectMapper),
                parseEntries(sb.getLegendaryActions(), objectMapper)
        );
    }

    @SuppressWarnings("unchecked")
    static List<Entry> parseEntries(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class);
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) objectMapper.readValue(json, type);
            List<Entry> entries = new ArrayList<>();
            for (Map<String, Object> map : rawList) {
                String name = stringVal(map.get("name"));
                String description = stringVal(map.get("description"));
                Integer attackBonus = extractAttackBonus(name);
                String damageExpression = extractDamageExpression(name);
                entries.add(new Entry(name, description, attackBonus, damageExpression));
            }
            return entries;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Integer extractAttackBonus(String name) {
        if (name == null) return null;
        Matcher m = ATTACK.matcher(name);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static String extractDamageExpression(String name) {
        if (name == null) return null;
        Matcher m = DAMAGE.matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String stringVal(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        return value.toString();
    }
}
