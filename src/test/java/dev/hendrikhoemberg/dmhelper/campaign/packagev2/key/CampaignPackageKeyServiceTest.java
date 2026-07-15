package dev.hendrikhoemberg.dmhelper.campaign.packagev2.key;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CampaignPackageKeyService.class)
class CampaignPackageKeyServiceTest {

    @Autowired
    private CampaignPackageKeyService keys;

    @Autowired
    private CampaignRepository campaigns;

    private Campaign campaign() {
        Campaign c = new Campaign();
        c.setName("Test Campaign");
        return campaigns.save(c);
    }

    @Test
    void generatesDeterministicKeyForEmptyName() {
        Campaign c = campaign();
        String key = keys.getOrCreate(c.getId(), CampaignContentType.MAP, UUID.randomUUID(), "");
        assertThat(key).matches("map-[0-9a-f]{12}");
    }

    @Test
    void buildsKeyFromDisplayName() {
        Campaign c = campaign();
        String key = keys.getOrCreate(c.getId(), CampaignContentType.MAP, UUID.randomUUID(), "The Crypt");
        assertThat(key).startsWith("the-crypt-").endsWith(key.substring(key.length() - 12));
        assertThat(key.substring(key.length() - 12)).matches("[0-9a-f]{12}");
    }

    @Test
    void getOrCreateReturnsSameKeyOnSecondCall() {
        Campaign c = campaign();
        UUID entityId = UUID.randomUUID();
        String first = keys.getOrCreate(c.getId(), CampaignContentType.MAP, entityId, "The Crypt");
        String second = keys.getOrCreate(c.getId(), CampaignContentType.MAP, entityId, "Renamed Crypt");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void bindImportedStoresKnownKey() {
        Campaign c = campaign();
        UUID sceneId = UUID.randomUUID();
        keys.bindImported(c.getId(), CampaignContentType.SCENE, sceneId, "crypt-entry");
        assertThat(keys.find(c.getId(), CampaignContentType.SCENE, sceneId)).contains("crypt-entry");
    }

    @Test
    void bindImportedRejectsInvalidKeyFormat() {
        Campaign c = campaign();
        assertThatThrownBy(() -> keys.bindImported(c.getId(), CampaignContentType.SCENE, UUID.randomUUID(), "Bad Key!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid package key format");
    }

    @Test
    void bindImportedRejectsRebindToDifferentKey() {
        Campaign c = campaign();
        UUID sceneId = UUID.randomUUID();
        keys.bindImported(c.getId(), CampaignContentType.SCENE, sceneId, "crypt-entry");
        assertThatThrownBy(() -> keys.bindImported(c.getId(), CampaignContentType.SCENE, sceneId, "different-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void bindImportedRejectsDuplicateKeyAcrossEntities() {
        Campaign c = campaign();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        keys.bindImported(c.getId(), CampaignContentType.SCENE, firstId, "crypt-entry");
        assertThatThrownBy(() -> keys.bindImported(c.getId(), CampaignContentType.SCENE, secondId, "crypt-entry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void bindImportedAllowsSameKeyForDifferentTypes() {
        Campaign c = campaign();
        keys.bindImported(c.getId(), CampaignContentType.SCENE, UUID.randomUUID(), "crypt-entry");
        keys.bindImported(c.getId(), CampaignContentType.MAP, UUID.randomUUID(), "crypt-entry");
    }

    @Test
    void bindImportedIsIdempotent() {
        Campaign c = campaign();
        UUID sceneId = UUID.randomUUID();
        keys.bindImported(c.getId(), CampaignContentType.SCENE, sceneId, "crypt-entry");
        keys.bindImported(c.getId(), CampaignContentType.SCENE, sceneId, "crypt-entry");
        assertThat(keys.find(c.getId(), CampaignContentType.SCENE, sceneId)).contains("crypt-entry");
    }

    @Test
    void findReturnsEmptyForUnknownBinding() {
        Campaign c = campaign();
        assertThat(keys.find(c.getId(), CampaignContentType.SCENE, UUID.randomUUID())).isEmpty();
    }

    @Test
    void generatedKeyDoesNotExceedMaxLength() {
        Campaign c = campaign();
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            longName.append('x');
        }
        String key = keys.getOrCreate(c.getId(), CampaignContentType.MAP, UUID.randomUUID(), longName.toString());
        assertThat(key.length()).isLessThanOrEqualTo(100);
    }

    @Test
    void keysScopedToCampaign() {
        Campaign c1 = campaign();
        Campaign c2 = campaign();
        UUID entityId = UUID.randomUUID();
        keys.bindImported(c1.getId(), CampaignContentType.NOTE, entityId, "my-note");
        keys.bindImported(c2.getId(), CampaignContentType.NOTE, entityId, "my-note");
        assertThat(keys.find(c1.getId(), CampaignContentType.NOTE, entityId)).contains("my-note");
        assertThat(keys.find(c2.getId(), CampaignContentType.NOTE, entityId)).contains("my-note");
    }

    @Test
    void generatedKeyContainsValidPrefixAndSuffix() {
        Campaign c = campaign();
        String key = keys.getOrCreate(c.getId(), CampaignContentType.ENCOUNTER, UUID.randomUUID(), "Goblin Ambush");
        assertThat(key).matches("^[a-z0-9][a-z0-9._-]{0,99}$");
        assertThat(key).contains("goblin-ambush");
        assertThat(key).containsPattern("-[0-9a-f]{12}$");
    }

    @Test
    void stripCombiningMarksFromDisplayName() {
        Campaign c = campaign();
        String key = keys.getOrCreate(c.getId(), CampaignContentType.NOTE, UUID.randomUUID(), "S\u00F6ren the \u00DCber");
        assertThat(key).doesNotContain("\u00F6", "\u00DC");
        assertThat(key).startsWith("soren-the-uber-");
    }
}
