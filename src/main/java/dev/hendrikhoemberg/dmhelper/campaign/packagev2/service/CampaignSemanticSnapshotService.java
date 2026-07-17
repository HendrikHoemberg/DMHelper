package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransition;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKey;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyRepository;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotation;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChange;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Transactional
public class CampaignSemanticSnapshotService {

    private final CampaignExportCoordinator exporter;
    private final CampaignPackageKeyRepository keys;
    private final HandoutService handouts;
    private final EntityManager entityManager;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public CampaignSemanticSnapshotService(CampaignExportCoordinator exporter,
                                           CampaignPackageKeyRepository keys,
                                           HandoutService handouts,
                                           EntityManager entityManager) {
        this.exporter = exporter;
        this.keys = keys;
        this.handouts = handouts;
        this.entityManager = entityManager;
    }

    public CampaignSemanticSnapshot snapshot(UUID campaignId) {
        CampaignPackageArtifact artifact = exporter.export(campaignId);
        // Export assigns stable package keys. Flush them before the independent projection queries
        // database-owned rows so a newly exported campaign cannot produce an incomplete snapshot.
        entityManager.flush();
        return new CampaignSemanticSnapshot(
                artifact.manifest(), directPersistenceProjection(campaignId));
    }

    /**
     * Builds a second fidelity view directly from JPA state. It deliberately does not call a v1
     * or v2 DTO mapper. Local identifiers become immutable package keys, catalog relationships
     * become source keys, generated handout filenames become content digests, and only collections
     * with an explicit {@link OrderColumn} retain database order.
     */
    private ObjectNode directPersistenceProjection(UUID campaignId) {
        List<CampaignPackageKey> bindings = keys
                .findByCampaignIdOrderByEntityTypeAscPackageKeyAsc(campaignId);
        Map<UUID, String> stableIds = new LinkedHashMap<>();
        Map<EntityIdentity, CampaignPackageKey> bindingsByEntity = new LinkedHashMap<>();
        for (CampaignPackageKey binding : bindings) {
            stableIds.put(binding.getEntityId(),
                    binding.getEntityType() + ":" + binding.getPackageKey());
            bindingsByEntity.put(new EntityIdentity(
                    CampaignContentType.valueOf(binding.getEntityType()), binding.getEntityId()), binding);
        }

        ObjectNode root = mapper.createObjectNode();
        ObjectNode entities = mapper.createObjectNode();
        root.set("entities", entities);
        Map<String, JsonNode> projected = new TreeMap<>();
        for (OwnedEntity owned : campaignOwnedEntities(campaignId)) {
            CampaignPackageKey binding = bindingsByEntity.remove(
                    new EntityIdentity(owned.type(), owned.id()));
            if (binding == null) {
                throw new IllegalStateException("Campaign-owned entity has no package key: "
                        + owned.type() + ":" + owned.id());
            }
            String stableKey = binding.getEntityType() + ":" + binding.getPackageKey();
            projected.put(stableKey, projectEntity(owned.entity(), stableIds));
        }
        if (!bindingsByEntity.isEmpty()) {
            CampaignPackageKey stale = bindingsByEntity.values().iterator().next();
            throw new IllegalStateException("Package key does not resolve to a campaign-owned entity: "
                    + stale.getEntityType() + ":" + stale.getPackageKey());
        }
        projected.forEach(entities::set);
        return root;
    }

    /**
     * Enumerates campaign ownership from the domain model, independently of export adapters and
     * package-key rows. This is intentionally explicit: adding a new campaign-owned entity type
     * requires choosing its ownership path here and makes omissions visible to the fidelity gate.
     */
    private List<OwnedEntity> campaignOwnedEntities(UUID campaignId) {
        List<OwnedEntity> owned = new ArrayList<>();
        for (OwnershipQuery query : OWNERSHIP_QUERIES) {
            String jpql = "select e from " + query.entityClass().getSimpleName()
                    + " e where e." + query.campaignPath() + " = :campaignId";
            for (Object entity : entityManager.createQuery(jpql, query.entityClass())
                    .setParameter("campaignId", campaignId)
                    .getResultList()) {
                Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                        .getIdentifier(entity);
                if (!(id instanceof UUID entityId)) {
                    throw new IllegalStateException("Campaign entity has no UUID identifier: "
                            + query.entityClass().getName());
                }
                owned.add(new OwnedEntity(query.type(), entityId, entity));
            }
        }
        owned.sort(Comparator.comparing((OwnedEntity entity) -> entity.type().name())
                .thenComparing(entity -> entity.id().toString()));
        return owned;
    }

    private ObjectNode projectEntity(Object entity, Map<UUID, String> stableIds) {
        ObjectNode projected = mapper.createObjectNode();
        var fields = new ArrayList<>(List.of(entity.getClass().getDeclaredFields()));
        fields.sort(Comparator.comparing(Field::getName));
        for (Field field : fields) {
            if (!persistent(field)) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (entity instanceof Handout handout && field.getName().equals("fileName")) {
                    byte[] bytes = handouts.getFileContent(handout.getId());
                    projected.put("contentSize", bytes.length);
                    projected.put("contentSha256", sha256(bytes));
                    continue;
                }
                if (entity instanceof dev.hendrikhoemberg.dmhelper.adventure.data.Scene
                        && field.getName().equals("tags")) {
                    continue;
                }
                JsonNode node = projectValue(value, field, stableIds);
                if (node != null) projected.set(field.getName(), node);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot project persistent field "
                        + entity.getClass().getSimpleName() + "." + field.getName(), e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read handout content for semantic projection", e);
            }
        }
        return projected;
    }

    private JsonNode projectValue(Object value, Field field, Map<UUID, String> stableIds) {
        if (value == null) return mapper.nullNode();
        if (relation(field)) return mapper.valueToTree(stableReference(value, stableIds));
        if (value instanceof Iterable<?> values) {
            ArrayNode array = mapper.createArrayNode();
            for (Object element : values) {
                String reference = stableReferenceOrNull(element, stableIds);
                array.add(reference != null ? mapper.valueToTree(reference) : projectEntity(element, stableIds));
            }
            if (field.getAnnotation(OrderColumn.class) == null) sortByCanonicalValue(array);
            return array;
        }
        if (value instanceof UUID id) return mapper.valueToTree(stableIds.getOrDefault(id, id.toString()));
        if (value instanceof String text) return projectString(text, stableIds);
        if (value instanceof Enum<?> || value instanceof TemporalAccessor
                || value instanceof Number || value instanceof Boolean) {
            return mapper.valueToTree(value);
        }
        return normalizeLocalIds(mapper.valueToTree(value), stableIds);
    }

    private JsonNode projectString(String text, Map<UUID, String> stableIds) {
        String stable = stableText(text, stableIds);
        String trimmed = stable.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return normalizeLocalIds(mapper.readTree(stable), stableIds);
            } catch (Exception ignored) {
                // A normal text field may begin with a brace; preserve it as text when it is not JSON.
            }
        }
        return mapper.valueToTree(stable);
    }

    private JsonNode normalizeLocalIds(JsonNode node, Map<UUID, String> stableIds) {
        if (node == null) return null;
        if (node.isTextual()) return mapper.valueToTree(stableText(node.asText(), stableIds));
        if (node instanceof ObjectNode object) {
            var names = new ArrayList<String>();
            object.propertyNames().forEach(names::add);
            for (String name : names) object.set(name, normalizeLocalIds(object.get(name), stableIds));
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) array.set(i, normalizeLocalIds(array.get(i), stableIds));
        }
        return node;
    }

    private String stableText(String text, Map<UUID, String> stableIds) {
        try {
            UUID id = UUID.fromString(text);
            return stableIds.getOrDefault(id, text);
        } catch (IllegalArgumentException ignored) {
            return text;
        }
    }

    private String stableReference(Object entity, Map<UUID, String> stableIds) {
        String reference = stableReferenceOrNull(entity, stableIds);
        if (reference == null) {
            throw new IllegalStateException("Cannot create stable reference for " + entity.getClass().getName());
        }
        return reference;
    }

    private String stableReferenceOrNull(Object entity, Map<UUID, String> stableIds) {
        if (entity == null) return null;
        PersistenceUnitUtil persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        Object identifier = persistence.getIdentifier(entity);
        if (identifier instanceof UUID id && stableIds.containsKey(id)) return stableIds.get(id);
        try {
            Object sourceKey = entity.getClass().getMethod("getSourceKey").invoke(entity);
            if (sourceKey instanceof String key && !key.isBlank()) {
                return "CATALOG:" + entity.getClass().getSimpleName() + ":" + key;
            }
        } catch (ReflectiveOperationException ignored) {
            // Not every campaign relationship points to catalog content.
        }
        return null;
    }

    private static boolean persistent(Field field) {
        return !Modifier.isStatic(field.getModifiers())
                && !Modifier.isTransient(field.getModifiers())
                && field.getAnnotation(Id.class) == null
                && field.getAnnotation(Version.class) == null
                && field.getAnnotation(Transient.class) == null
                && !"updatedAt".equals(field.getName())
;
    }

    private static boolean relation(Field field) {
        return field.getAnnotation(ManyToOne.class) != null
                || field.getAnnotation(OneToOne.class) != null;
    }

    private void sortByCanonicalValue(ArrayNode array) {
        var sorted = new ArrayList<JsonNode>();
        array.forEach(sorted::add);
        sorted.sort(Comparator.comparing(JsonNode::toString));
        array.removeAll();
        sorted.forEach(array::add);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record EntityIdentity(CampaignContentType type, UUID id) {}

    private record OwnedEntity(CampaignContentType type, UUID id, Object entity) {}

    private record OwnershipQuery(CampaignContentType type, Class<?> entityClass, String campaignPath) {}

    private static final List<OwnershipQuery> OWNERSHIP_QUERIES = List.of(
            new OwnershipQuery(CampaignContentType.CAMPAIGN, Campaign.class, "id"),
            new OwnershipQuery(CampaignContentType.ADVENTURE, Adventure.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.CHAPTER, Chapter.class, "adventure.campaign.id"),
            new OwnershipQuery(CampaignContentType.SCENE, Scene.class, "chapter.adventure.campaign.id"),
            new OwnershipQuery(CampaignContentType.MAP, GameMap.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.TOKEN, Token.class, "map.campaign.id"),
            new OwnershipQuery(CampaignContentType.ENCOUNTER, Encounter.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.COMBATANT, Combatant.class, "encounter.campaign.id"),
            new OwnershipQuery(CampaignContentType.COMBAT_LOG_ENTRY, CombatLogEntry.class,
                    "encounter.campaign.id"),
            new OwnershipQuery(CampaignContentType.PARTY_MEMBER, PartyMember.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.CHARACTER_SHEET, CharacterSheet.class,
                    "partyMember.campaign.id"),
            new OwnershipQuery(CampaignContentType.SHEET_RESOURCE, SheetResource.class,
                    "sheet.partyMember.campaign.id"),
            new OwnershipQuery(CampaignContentType.NOTE, Note.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.QUICK_NOTE, QuickNote.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.HANDOUT, Handout.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.ASSIGNMENT, ItemAssignment.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.LEDGER_ENTRY, LedgerEntry.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.TIMELINE_EVENT, TimelineEvent.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.STATBLOCK, StatBlock.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.DICE_ROLL, DiceRoll.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.SESSION, CampaignSession.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.SESSION_SCENE_VISIT, SessionSceneVisit.class,
                    "session.campaign.id"),
            new OwnershipQuery(CampaignContentType.TRANSITION, SceneTransition.class, "scene.chapter.adventure.campaign.id"),
            new OwnershipQuery(CampaignContentType.QUEST, Quest.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.OBJECTIVE, QuestObjective.class, "quest.campaign.id"),
            new OwnershipQuery(CampaignContentType.SOURCE_ANNOTATION, SourceAnnotation.class, "campaign.id"),
            new OwnershipQuery(CampaignContentType.SESSION_OBJECTIVE_CHANGE, SessionObjectiveChange.class,
                    "session.campaign.id"));
}
