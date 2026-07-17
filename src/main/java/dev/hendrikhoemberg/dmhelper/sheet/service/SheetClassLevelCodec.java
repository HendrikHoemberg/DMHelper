package dev.hendrikhoemberg.dmhelper.sheet.service;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SheetClassLevelCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SheetClassLevelCodec() {}

    public static String classSourceKeyOf(Map<String, Object> entry) {
        Object v = entry.get("classSourceKey");
        if (v == null) v = entry.get("classRef");
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<SheetService.ClassLevelEntry> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var rawList = (List<Map<String, Object>>) MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<SheetService.ClassLevelEntry> result = new ArrayList<>();
            for (var entry : rawList) {
                String classSourceKey = classSourceKeyOf(entry);
                int level = entry.get("level") instanceof Number n ? n.intValue() : 0;
                List<Integer> hitDieRolls = new ArrayList<>();
                if (entry.get("hitDieRolls") instanceof List<?> rolls) {
                    for (Object r : rolls) {
                        if (r instanceof Number n) hitDieRolls.add(n.intValue());
                    }
                }
                String subclassSourceKey = null;
                Object sub = entry.get("subclassSourceKey");
                if (sub != null) subclassSourceKey = sub.toString();
                result.add(new SheetService.ClassLevelEntry(classSourceKey, level, hitDieRolls, subclassSourceKey));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String write(List<SheetService.ClassLevelEntry> levels) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (var entry : levels) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("classSourceKey", entry.classSourceKey());
                map.put("level", entry.level());
                map.put("hitDieRolls", entry.hitDieRolls());
                if (entry.subclassSourceKey() != null) {
                    map.put("subclassSourceKey", entry.subclassSourceKey());
                }
                list.add(map);
            }
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize class levels", e);
        }
    }
}
