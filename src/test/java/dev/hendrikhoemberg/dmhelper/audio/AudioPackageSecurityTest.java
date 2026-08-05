package dev.hendrikhoemberg.dmhelper.audio;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPackageSecurityTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CampaignManifestV2SchemaValidator schemaValidator = new CampaignManifestV2SchemaValidator();

    @Test
    void schemaRejectsAccessTokenField() throws Exception {
        var json = (String) createAdversarialManifest("accessToken", "secret-token-123");
        var violations = schemaValidator.validate(json);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void schemaRejectsRefreshTokenField() throws Exception {
        var json = (String) createAdversarialManifest("refreshToken", "refresh-token-456");
        var violations = schemaValidator.validate(json);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void schemaRejectsAuthorizationCodeField() throws Exception {
        var json = (String) createAdversarialManifest("authorizationCode", "auth-code-789");
        var violations = schemaValidator.validate(json);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void schemaRejectsDeviceIdField() throws Exception {
        var json = (String) createAdversarialManifest("deviceId", "device-abc-123");
        var violations = schemaValidator.validate(json);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void exportNeverContainsRuntimeStateFields() {
        var manifest = createMinimalManifest();
        var json = mapper.valueToTree(manifest);
        assertThat(json.has("accessToken")).isFalse();
        assertThat(json.has("refreshToken")).isFalse();
        assertThat(json.has("authorizationCode")).isFalse();
        assertThat(json.has("deviceId")).isFalse();
    }

    @Test
    void exportNeverContainsSessionAudioStateValues() {
        var manifest = createMinimalManifest();
        var json = mapper.valueToTree(manifest);
        assertThat(json.has("sessionAudioState")).isFalse();
        assertThat(json.has("manualOverrideCue")).isFalse();
        assertThat(json.has("acceptedAutomaticCue")).isFalse();
        assertThat(json.has("pendingCue")).isFalse();
        assertThat(json.has("dismissedCandidateCue")).isFalse();
        assertThat(json.has("temporaryVictoryCue")).isFalse();
        assertThat(json.has("victoryUntil")).isFalse();
    }

    private Object createAdversarialManifest(String extraField, String extraValue) throws Exception {
        var baseJson = mapper.writeValueAsString(createMinimalManifest());
        tools.jackson.databind.JsonNode node = mapper.readTree(baseJson);
        ((tools.jackson.databind.node.ObjectNode) node).put(extraField, extraValue);
        return mapper.writeValueAsString(node);
    }

    private CampaignManifestV2 createMinimalManifest() {
        return new CampaignManifestV2(
                2,
                new CampaignManifestV2.Metadata("pkg", null, "test", null, null, List.of()),
                new CampaignManifestV2.CampaignDto("campaign-key", "Test", "desc",
                        java.time.Instant.now(), null, null, null),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
