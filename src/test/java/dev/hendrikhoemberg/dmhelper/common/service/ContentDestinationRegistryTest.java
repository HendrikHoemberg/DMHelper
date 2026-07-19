package dev.hendrikhoemberg.dmhelper.common.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDestinationRegistryTest {
    private final ContentDestinationRegistry registry = new ContentDestinationRegistry();
    private final UUID campaignId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID entityId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adventureId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void campaignDestinationsMatchControllerRoutes() {
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.NOTE,
                campaignId, entityId, null)).isEqualTo(
                "/campaigns/11111111-1111-1111-1111-111111111111/notes/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.MAP,
                campaignId, entityId, null)).endsWith("/maps/22222222-2222-2222-2222-222222222222/play");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.ENCOUNTER,
                campaignId, entityId, null)).endsWith("/encounters/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.HANDOUT,
                campaignId, entityId, null)).endsWith("/handouts#handout-22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.PARTY_MEMBER,
                campaignId, entityId, null)).endsWith("/party#pm-card-22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.PARTY_MEMBER_SHEET,
                campaignId, entityId, null)).endsWith("/party/22222222-2222-2222-2222-222222222222/sheet");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.SCENE,
                campaignId, entityId, adventureId)).endsWith(
                "/adventures/33333333-3333-3333-3333-333333333333/scenes/22222222-2222-2222-2222-222222222222");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.QUICK_NOTE,
                campaignId, entityId, null)).endsWith("/notes");
        assertThat(registry.campaign(ContentDestinationRegistry.CampaignType.AUDIO_CUE,
                campaignId, entityId, null)).endsWith("/audio/cues/" + entityId);
    }

    @Test
    void libraryDestinationsUseDetailsOrFilteredTabs() {
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.STATBLOCK,
                entityId, "goblin", "Goblin")).isEqualTo("/library/statblocks/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CLASS,
                entityId, "srd-2024_fighter", "Fighter")).isEqualTo("/library/classes/id/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.SPELL,
                entityId, "fireball", "Fireball")).isEqualTo("/library/spells/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CONDITION,
                entityId, "poisoned", "Poisoned")).isEqualTo("/library/conditions/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.RULE,
                entityId, "combat", "Combat")).isEqualTo("/library/rules/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.EQUIPMENT,
                entityId, "longsword", "Longsword")).isEqualTo("/library/equipment/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.MAGIC_ITEM,
                entityId, "bag-of-holding", "Bag of Holding")).isEqualTo(
                "/library/magic-items/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.SPECIES,
                entityId, "elf", "Elf")).isEqualTo("/library/species/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.BACKGROUND,
                entityId, "acolyte", "Acolyte")).isEqualTo("/library/backgrounds/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.FEAT,
                entityId, "alert", "Alert")).isEqualTo("/library/feats/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.ROLLABLE_TABLE,
                entityId, "my-table", "My Table")).isEqualTo("/library/tables/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.TRAP,
                entityId, "spike-pit", "Spike Pit")).isEqualTo("/library/traps/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.HAZARD,
                entityId, "lava-field", "Lava Field")).isEqualTo("/library/hazards/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.AUDIO_CUE,
                entityId, "cave-ambient", "Cave Ambient")).isEqualTo("/library?tab=audio-cues&search=Cave%20Ambient");
    }

    @Test
    void libraryDestinationsFallBackToFilteredTabsWhenNoEntityId() {
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.SPELL,
                null, "fireball", "Fireball")).isEqualTo("/library?tab=spells&search=Fireball");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.MAGIC_ITEM,
                null, "bag-of-holding", "Bag of Holding")).isEqualTo(
                "/library?tab=magic-items&search=Bag%20of%20Holding");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CLASS,
                null, "srd-2024_fighter", "Fighter")).isEqualTo("/library/classes/srd-2024_fighter");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CLASS,
                null, null, "Fighter")).isEqualTo("/library?tab=classes&search=Fighter");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.TRAP,
                null, "spike-pit", "Spike Pit")).isEqualTo("/library?tab=traps&search=Spike%20Pit");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.HAZARD,
                null, "lava-field", "Lava Field")).isEqualTo("/library?tab=hazards&search=Lava%20Field");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.AUDIO_CUE,
                null, "cave-ambient", "Cave Ambient")).isEqualTo("/library?tab=audio-cues&search=Cave%20Ambient");
    }

    @Test
    void everyEnumValueHasADestination() {
        assertThat(Arrays.stream(ContentDestinationRegistry.CampaignType.values())
                .map(type -> registry.campaign(type, campaignId, entityId, adventureId)))
                .allMatch(url -> url.startsWith("/"));
        assertThat(Arrays.stream(ContentDestinationRegistry.LibraryType.values())
                .map(type -> registry.library(type, entityId, "source-key", "Display Name")))
                .allMatch(url -> url.startsWith("/library"));
    }
}
