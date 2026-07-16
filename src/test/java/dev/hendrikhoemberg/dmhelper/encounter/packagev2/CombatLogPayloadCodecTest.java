package dev.hendrikhoemberg.dmhelper.encounter.packagev2;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatLogPayloadCodecTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final UUID COMBATANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COMBATANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String KEY_A = "goblin-00000001";
    private static final String KEY_B = "orc-00000002";

    @Test
    void replacesTopLevelCombatantId() throws Exception {
        Map<String, String> idToKey = Map.of(
                COMBATANT_A.toString(), KEY_A,
                COMBATANT_B.toString(), KEY_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"combatantId": "00000000-0000-0000-0000-000000000001"}
                """);

        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, payload);

        assertThat(result.get("combatantId").asText()).isEqualTo(KEY_A);
    }

    @Test
    void replacesCombatantIdInReorderedPayload() throws Exception {
        Map<String, String> idToKey = Map.of(
                COMBATANT_A.toString(), KEY_A,
                COMBATANT_B.toString(), KEY_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"type":"COMBATANT_REORDERED","payload":{"orderedIds":[
                    "00000000-0000-0000-0000-000000000001",
                    "00000000-0000-0000-0000-000000000002"
                ]}}
                """);

        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, payload);

        JsonNode ordered = result.get("payload").get("orderedIds");
        assertThat(ordered.get(0).asText()).isEqualTo(KEY_A);
        assertThat(ordered.get(1).asText()).isEqualTo(KEY_B);
    }

    @Test
    void replacesUuidObjectKeysInSortOrder() throws Exception {
        Map<String, String> idToKey = Map.of(
                COMBATANT_A.toString(), KEY_A,
                COMBATANT_B.toString(), KEY_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"type":"SORT_ORDER","00000000-0000-0000-0000-000000000001":1,"00000000-0000-0000-0000-000000000002":2}
                """);

        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, payload);

        assertThat(result.get(KEY_A).asInt()).isEqualTo(1);
        assertThat(result.get(KEY_B).asInt()).isEqualTo(2);
        assertThat(result.get("type").asText()).isEqualTo("SORT_ORDER");
    }

    @Test
    void reversesTopLevelCombatantId() throws Exception {
        Map<String, UUID> keyToId = Map.of(
                KEY_A, COMBATANT_A,
                KEY_B, COMBATANT_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"combatantId": "goblin-00000001"}
                """);

        JsonNode result = CombatLogPayloadCodec.toLocal(keyToId, payload);

        assertThat(result.get("combatantId").asText()).isEqualTo(COMBATANT_A.toString());
    }

    @Test
    void reversesReorderedPayload() throws Exception {
        Map<String, UUID> keyToId = Map.of(
                KEY_A, COMBATANT_A,
                KEY_B, COMBATANT_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"type":"COMBATANT_REORDERED","payload":{"orderedIds":[
                    "goblin-00000001",
                    "orc-00000002"
                ]}}
                """);

        JsonNode result = CombatLogPayloadCodec.toLocal(keyToId, payload);

        JsonNode ordered = result.get("payload").get("orderedIds");
        assertThat(ordered.get(0).asText()).isEqualTo(COMBATANT_A.toString());
        assertThat(ordered.get(1).asText()).isEqualTo(COMBATANT_B.toString());
    }

    @Test
    void reversesSortOrderKeys() throws Exception {
        Map<String, UUID> keyToId = Map.of(
                KEY_A, COMBATANT_A,
                KEY_B, COMBATANT_B
        );
        JsonNode payload = MAPPER.readTree("""
                {"type":"SORT_ORDER","goblin-00000001":1,"orc-00000002":2}
                """);

        JsonNode result = CombatLogPayloadCodec.toLocal(keyToId, payload);

        assertThat(result.get(COMBATANT_A.toString()).asInt()).isEqualTo(1);
        assertThat(result.get(COMBATANT_B.toString()).asInt()).isEqualTo(2);
    }

    @Test
    void returnsOriginalPayloadIfNoUuidsFound() throws Exception {
        Map<String, String> idToKey = Map.of();
        JsonNode payload = MAPPER.readTree("""
                {"type":"DAMAGE","amount":15}
                """);

        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, payload);

        assertThat(result.get("type").asText()).isEqualTo("DAMAGE");
        assertThat(result.get("amount").asInt()).isEqualTo(15);
    }

    @Test
    void wrapsNonJsonPayloadAsStringNode() throws Exception {
        Map<String, String> idToKey = Map.of();
        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, null);

        assertThat(result.isTextual()).isTrue();
        assertThat(result.asText()).isEmpty();
    }

    @Test
    void wrapsBlankPayloadAsStringNode() throws Exception {
        Map<String, String> idToKey = Map.of();
        JsonNode result = CombatLogPayloadCodec.toPackage(idToKey, MAPPER.readTree("\"\""));

        assertThat(result.isTextual()).isTrue();
    }

    @Test
    void handlesEmptyUuidMaps() throws Exception {
        Map<String, String> idToKey = new LinkedHashMap<>();
        Map<String, UUID> keyToId = new LinkedHashMap<>();

        JsonNode payload = MAPPER.readTree("""
                {"combatantId": "00000000-0000-0000-0000-000000000001"}
                """);

        JsonNode toPkg = CombatLogPayloadCodec.toPackage(idToKey, payload);
        assertThat(toPkg.get("combatantId").asText())
                .isEqualTo("00000000-0000-0000-0000-000000000001");

        JsonNode toLoc = CombatLogPayloadCodec.toLocal(keyToId, payload);
        assertThat(toLoc.get("combatantId").asText())
                .isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}
