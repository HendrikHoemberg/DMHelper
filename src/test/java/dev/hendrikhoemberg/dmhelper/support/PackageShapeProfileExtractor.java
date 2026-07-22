package dev.hendrikhoemberg.dmhelper.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackageShapeProfileExtractor {

    public static final Path REAL_PACKAGE =
            Path.of(System.getProperty("user.home"), "Documents/DnDCampaigns/lmop-de.dmcampaign");

    public static final Path COMMITTED_PROFILE =
            Path.of("src/test/resources/campaigns/shape-profiles/lmop-de.profile.json");

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private static final Set<String> STRUCTURAL = Set.of(
            "key", "sortOrder", "sourceKey", "provenance", "createdAt", "assetRef",
            "contentType", "ownerRef", "entries", "objectives", "links", "chapters",
            "sections", "checks", "participants", "transitions", "conditionRefs",
            "salvageItemRefs", "disarmMethods");

    private PackageShapeProfileExtractor() {}

    public static PackageShapeProfile extract(Path dmcampaignZip) throws IOException {
        JsonNode manifest = readManifest(dmcampaignZip);

        List<JsonNode> adventures = children(manifest, "adventures");
        List<JsonNode> chapters = flatten(adventures, "chapters");
        List<JsonNode> scenes = flatten(chapters, "scenes");
        List<JsonNode> sections = flatten(scenes, "sections");
        List<JsonNode> participants = flatten(scenes, "participants");
        List<JsonNode> transitions = flatten(scenes, "transitions");
        List<JsonNode> checks = flatten(scenes, "checks");
        List<JsonNode> links = flatten(scenes, "links");

        SortedMap<String, Integer> counts = new TreeMap<>();
        counts.put("adventures", adventures.size());
        counts.put("chapters", chapters.size());
        counts.put("scenes", scenes.size());
        counts.put("sceneSections", sections.size());
        counts.put("sceneParticipants", participants.size());
        counts.put("sceneTransitions", transitions.size());
        counts.put("sceneChecks", checks.size());
        counts.put("sceneLinks", links.size());
        for (String top : List.of("worldNpcs", "worldLocations", "factions", "worldRelationships",
                "quests", "handouts", "customStatBlocks", "customMagicItems", "traps", "hazards",
                "rollableTables", "notes", "annotations", "encounters", "maps", "party",
                "audioCues", "factionClocks")) {
            counts.put(top, children(manifest, top).size());
        }

        SortedMap<String, Integer> populated = new TreeMap<>();
        tally(populated, "scene", scenes);
        tally(populated, "sceneSection", sections);
        tally(populated, "sceneParticipant", participants);
        tally(populated, "sceneTransition", transitions);
        tally(populated, "sceneCheck", checks);
        tally(populated, "sceneLink", links);
        tally(populated, "worldNpc", children(manifest, "worldNpcs"));
        tally(populated, "worldLocation", children(manifest, "worldLocations"));
        tally(populated, "faction", children(manifest, "factions"));
        tally(populated, "quest", children(manifest, "quests"));
        tally(populated, "handout", children(manifest, "handouts"));
        tally(populated, "trap", children(manifest, "traps"));
        tally(populated, "hazard", children(manifest, "hazards"));
        tally(populated, "rollableTable", children(manifest, "rollableTables"));

        return new PackageShapeProfile(
                dmcampaignZip.getFileName().toString(),
                manifest.path("formatVersion").asInt(),
                distinct(sections, "kind"),
                distinct(transitions, "kind"),
                counts,
                populated);
    }

    public static PackageShapeProfile load(Path profileJson) throws IOException {
        return JSON.readValue(Files.readString(profileJson), PackageShapeProfile.class);
    }

    public static void write(PackageShapeProfile profile, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, JSON.writeValueAsString(profile) + "\n");
    }

    private static JsonNode readManifest(Path zip) throws IOException {
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            ZipEntry entry = archive.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("No manifest.json in " + zip);
            }
            try (InputStream in = archive.getInputStream(entry)) {
                return JSON.readTree(in);
            }
        }
    }

    private static List<JsonNode> children(JsonNode parent, String field) {
        List<JsonNode> out = new ArrayList<>();
        parent.path(field).forEach(out::add);
        return out;
    }

    private static List<JsonNode> flatten(List<JsonNode> parents, String field) {
        List<JsonNode> out = new ArrayList<>();
        parents.forEach(p -> out.addAll(children(p, field)));
        return out;
    }

    private static List<String> distinct(List<JsonNode> rows, String field) {
        Set<String> values = new TreeSet<>();
        for (JsonNode row : rows) {
            if (row.hasNonNull(field)) {
                values.add(row.get(field).asText());
            }
        }
        return List.copyOf(values);
    }

    private static void tally(SortedMap<String, Integer> into, String prefix, List<JsonNode> rows) {
        Set<String> fields = new TreeSet<>();
        for (JsonNode row : rows) {
            row.properties().forEach(e -> fields.add(e.getKey()));
        }
        for (String field : fields) {
            if (STRUCTURAL.contains(field)) {
                continue;
            }
            int count = 0;
            for (JsonNode row : rows) {
                if (isPopulated(row.get(field))) {
                    count++;
                }
            }
            if (count > 0) {
                into.put(prefix + "." + field, count);
            }
        }
    }

    private static boolean isPopulated(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isObject() || value.isArray()) {
            return !value.isEmpty();
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
        Path source = args.length > 0 ? Path.of(args[0]) : REAL_PACKAGE;
        write(extract(source), COMMITTED_PROFILE);
        System.out.println("Wrote " + COMMITTED_PROFILE + " from " + source);
    }
}
