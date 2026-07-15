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
    }

    @Test
    void libraryDestinationsUseDetailsOrFilteredTabs() {
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.STATBLOCK,
                entityId, "goblin", "Goblin")).isEqualTo("/library/statblocks/" + entityId);
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.CLASS,
                entityId, "srd-2024_fighter", "Fighter")).isEqualTo("/library/classes/srd-2024_fighter");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.SPELL,
                entityId, "fireball", "Fireball")).isEqualTo("/library?tab=spells&search=Fireball");
        assertThat(registry.library(ContentDestinationRegistry.LibraryType.MAGIC_ITEM,
                entityId, "bag-of-holding", "Bag of Holding")).isEqualTo(
                "/library?tab=magic-items&search=Bag%20of%20Holding");
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
