package dev.hendrikhoemberg.dmhelper.encounter.packagev2;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatLogPayloadCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static JsonNode toPackage(Map<String, String> idToKey, JsonNode payload) {
        if (payload == null) {
            return StringNode.valueOf("");
        }
        if (!payload.isObject()) {
            return payload;
        }
        ObjectNode root = (ObjectNode) payload.deepCopy();
        replaceCombatantIdField(root, idToKey);
        replaceReorderedIds(root, idToKey);
        replaceSortOrderKeys(root, idToKey);
        return root;
    }

    public static JsonNode toLocal(Map<String, UUID> keyToId, JsonNode payload) {
        if (payload == null) {
            return StringNode.valueOf("");
        }
        if (!payload.isObject()) {
            return payload;
        }
        ObjectNode root = (ObjectNode) payload.deepCopy();
        reverseCombatantIdField(root, keyToId);
        reverseReorderedIds(root, keyToId);
        reverseSortOrderKeys(root, keyToId);
        return root;
    }

    private static void replaceCombatantIdField(ObjectNode node, Map<String, String> idToKey) {
        JsonNode combatantId = node.get("combatantId");
        if (combatantId != null && combatantId.isTextual()) {
            String key = idToKey.get(combatantId.asText());
            if (key != null) {
                node.put("combatantId", key);
            }
        }
    }

    private static void replaceReorderedIds(ObjectNode node, Map<String, String> idToKey) {
        JsonNode type = node.get("type");
        if (type == null || !type.isTextual()) return;
        if (!"COMBATANT_REORDERED".equals(type.asText())) return;

        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) return;

        JsonNode orderedIds = payload.get("orderedIds");
        if (orderedIds == null || !orderedIds.isArray()) return;

        ArrayNode replaced = MAPPER.createArrayNode();
        for (JsonNode idNode : orderedIds) {
            if (idNode.isTextual()) {
                String key = idToKey.get(idNode.asText());
                replaced.add(key != null ? key : idNode.asText());
            } else {
                replaced.add(idNode);
            }
        }
        ((ObjectNode) payload).set("orderedIds", replaced);
    }

    private static void replaceSortOrderKeys(ObjectNode node, Map<String, String> idToKey) {
        JsonNode type = node.get("type");
        if (type == null || !type.isTextual()) return;
        if (!"SORT_ORDER".equals(type.asText())) return;

        ObjectNode replaced = MAPPER.createObjectNode();
        replaced.put("type", "SORT_ORDER");
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String fieldName = entry.getKey();
            if ("type".equals(fieldName)) continue;
            String key = idToKey.get(fieldName);
            replaced.set(key != null ? key : fieldName, entry.getValue());
        }
        removeAllExcept(node, Set.of());
        for (Map.Entry<String, JsonNode> entry : replaced.properties()) {
            node.set(entry.getKey(), entry.getValue());
        }
    }

    private static void reverseCombatantIdField(ObjectNode node, Map<String, UUID> keyToId) {
        JsonNode combatantId = node.get("combatantId");
        if (combatantId != null && combatantId.isTextual()) {
            UUID id = keyToId.get(combatantId.asText());
            if (id != null) {
                node.put("combatantId", id.toString());
            }
        }
    }

    private static void reverseReorderedIds(ObjectNode node, Map<String, UUID> keyToId) {
        JsonNode type = node.get("type");
        if (type == null || !type.isTextual()) return;
        if (!"COMBATANT_REORDERED".equals(type.asText())) return;

        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) return;

        JsonNode orderedIds = payload.get("orderedIds");
        if (orderedIds == null || !orderedIds.isArray()) return;

        ArrayNode replaced = MAPPER.createArrayNode();
        for (JsonNode idNode : orderedIds) {
            if (idNode.isTextual()) {
                UUID id = keyToId.get(idNode.asText());
                replaced.add(id != null ? id.toString() : idNode.asText());
            } else {
                replaced.add(idNode);
            }
        }
        ((ObjectNode) payload).set("orderedIds", replaced);
    }

    private static void reverseSortOrderKeys(ObjectNode node, Map<String, UUID> keyToId) {
        JsonNode type = node.get("type");
        if (type == null || !type.isTextual()) return;
        if (!"SORT_ORDER".equals(type.asText())) return;

        ObjectNode replaced = MAPPER.createObjectNode();
        replaced.put("type", "SORT_ORDER");
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String fieldName = entry.getKey();
            if ("type".equals(fieldName)) continue;
            UUID id = keyToId.get(fieldName);
            replaced.set(id != null ? id.toString() : fieldName, entry.getValue());
        }
        removeAllExcept(node, Set.of());
        for (Map.Entry<String, JsonNode> entry : replaced.properties()) {
            node.set(entry.getKey(), entry.getValue());
        }
    }

    private static void removeAllExcept(ObjectNode node, Set<String> keep) {
        var names = new java.util.ArrayList<>(node.propertyNames());
        for (String name : names) {
            if (!keep.contains(name)) {
                node.remove(name);
            }
        }
    }
}
