package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Map;

public final class CampaignSemanticComparator {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> UNORDERED_VALUE_COLLECTIONS = Set.of(
            "links", "spells", "featRefs");
    private static final Set<String> ORDERED_KEYED_COLLECTIONS = Set.of(
            "handoutRefs", "statblockRefs");

    private CampaignSemanticComparator() {
    }

    public static void assertEquivalent(CampaignSemanticSnapshot expected,
                                        CampaignSemanticSnapshot actual) {
        JsonNode expectedNode = canonical(expected);
        JsonNode actualNode = canonical(actual);
        String mismatch = firstMismatch(expectedNode, actualNode, "");
        if (mismatch != null) {
            throw new AssertionError("Campaign semantic mismatch at " + mismatch);
        }
        if (expected.persistenceProjection() != null && actual.persistenceProjection() != null) {
            mismatch = firstMismatch(
                    expected.persistenceProjection(), actual.persistenceProjection(), "/persistence");
            if (mismatch != null) {
                throw new AssertionError("Campaign semantic mismatch at " + mismatch);
            }
        }
    }

    private static JsonNode canonical(CampaignSemanticSnapshot snapshot) {
        ObjectNode root = (ObjectNode) MAPPER.valueToTree(snapshot.manifest());
        JsonNode metadata = root.get("metadata");
        if (metadata instanceof ObjectNode object) {
            object.remove("createdAt");
            object.remove("generator");
            object.remove("catalogVersion");
            object.remove("catalogSha256");
        }
        replaceAssetReferences(root);
        sortIdentityCollections(root, null);
        return root;
    }

    private static void sortIdentityCollections(JsonNode node, String fieldName) {
        if (node == null) return;
        if (node instanceof ObjectNode object) {
            var names = new java.util.ArrayList<String>();
            object.propertyNames().forEach(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                sortIdentityCollections(child, name);
                if (UNORDERED_VALUE_COLLECTIONS.contains(name) && child instanceof ArrayNode array) {
                    sortByCanonicalValue(array);
                }
            }
            return;
        }
        if (!(node instanceof ArrayNode array)) return;
        array.forEach(child -> sortIdentityCollections(child, null));
        if (ORDERED_KEYED_COLLECTIONS.contains(fieldName)) return;
        boolean keyedObjects = !array.isEmpty();
        for (JsonNode child : array) {
            if (!child.isObject() || !child.path("key").isTextual()) {
                keyedObjects = false;
                break;
            }
        }
        if (!keyedObjects) return;
        var sorted = new java.util.ArrayList<JsonNode>();
        array.forEach(sorted::add);
        sorted.sort(java.util.Comparator.comparing(child -> child.path("key").asText()));
        array.removeAll();
        sorted.forEach(array::add);
    }

    private static void sortByCanonicalValue(ArrayNode array) {
        var sorted = new java.util.ArrayList<JsonNode>();
        array.forEach(sorted::add);
        sorted.sort(java.util.Comparator.comparing(JsonNode::toString));
        array.removeAll();
        sorted.forEach(array::add);
    }

    private static void replaceAssetReferences(ObjectNode root) {
        Map<String, JsonNode> semanticsByKey = new HashMap<>();
        JsonNode assets = root.get("assets");
        if (assets != null && assets.isArray()) {
            for (JsonNode asset : assets) {
                ObjectNode semantic = MAPPER.createObjectNode();
                semantic.set("mediaType", asset.get("mediaType"));
                semantic.set("sizeBytes", asset.get("sizeBytes"));
                semantic.set("sha256", asset.get("sha256"));
                semanticsByKey.put(asset.get("key").asText(), semantic);
            }
        }
        replaceAssetReferences(root, semanticsByKey);
        root.remove("assets");
    }

    private static void replaceAssetReferences(JsonNode node, Map<String, JsonNode> semanticsByKey) {
        if (node == null) return;
        if (node instanceof ObjectNode object) {
            JsonNode assetRef = object.get("assetRef");
            if (assetRef != null && assetRef.isTextual()) {
                JsonNode semantic = semanticsByKey.get(assetRef.asText());
                if (semantic != null) object.set("assetRef", semantic.deepCopy());
            }
            var names = new java.util.ArrayList<String>();
            object.propertyNames().forEach(names::add);
            for (String name : names) replaceAssetReferences(object.get(name), semanticsByKey);
        } else if (node.isArray()) {
            node.forEach(child -> replaceAssetReferences(child, semanticsByKey));
        }
    }

    private static String firstMismatch(JsonNode expected, JsonNode actual, String path) {
        if (expected == null || actual == null) {
            JsonNode present = expected != null ? expected : actual;
            if (present != null && present.isArray() && present.isEmpty()) return null;
            return expected == actual ? null : display(path);
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return display(path);
        }
        if (expected.isObject()) {
            Set<String> names = new TreeSet<>();
            expected.propertyNames().forEach(names::add);
            actual.propertyNames().forEach(names::add);
            for (String name : names) {
                String mismatch = firstMismatch(
                        expected.get(name), actual.get(name), path + "/" + escape(name));
                if (mismatch != null) return mismatch;
            }
            return null;
        }
        if (expected.isArray()) {
            if (expected.size() != actual.size()) return display(path + "/size");
            for (int i = 0; i < expected.size(); i++) {
                String mismatch = firstMismatch(
                        expected.get(i), actual.get(i), path + "/" + i);
                if (mismatch != null) return mismatch;
            }
            return null;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0
                    ? null : display(path);
        }
        return expected.equals(actual) ? null : display(path);
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static String display(String path) {
        return path.isEmpty() ? "/" : path;
    }
}
