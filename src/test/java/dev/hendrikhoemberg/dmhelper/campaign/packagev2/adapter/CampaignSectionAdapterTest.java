package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CampaignDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class CampaignSectionAdapterTest {

    private CampaignSectionAdapter adapter;
    private CampaignSettingsCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CampaignSettingsCodec(new ObjectMapper());
        adapter = new CampaignSectionAdapter(codec);
    }

    @Test
    void hasOrder100() {
        assertThat(adapter.order()).isEqualTo(100);
    }

    @Test
    void exportsCampaignFields() {
        var campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        campaign.setDescription("A test description");
        var settings = new CampaignSettings(LevelingMode.MILESTONE,
                new CalendarConfig(new int[]{30}, new String[]{"A"}, new String[]{"X"}),
                new InGameDate(1500, 1, 15),
                AudioSwitchMode.AUTOMATIC);
        codec.write(campaign, settings);
        campaign.setCurrentSceneId(UUID.randomUUID());
        campaign.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));

        var keyService = new FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);

        assembler.party(List.of());
        assembler.customStatBlocks(List.of());
        assembler.customSpells(List.of());
        assembler.customConditions(List.of());
        assembler.customRules(List.of());
        assembler.customEquipment(List.of());
        assembler.customMagicItems(List.of());
        assembler.customClasses(List.of());
        assembler.customSpecies(List.of());
        assembler.customBackgrounds(List.of());
        assembler.customFeats(List.of());
        assembler.handouts(List.of());
        assembler.maps(List.of());
        assembler.encounters(List.of());
        assembler.notes(List.of());
        assembler.quickNotes(List.of());
        assembler.assignments(List.of());
        assembler.ledgerEntries(List.of());
        assembler.timelineEvents(List.of());
        assembler.adventures(List.of());
        assembler.session(null);
        assembler.diceRolls(List.of());
        assembler.audioCues(List.of());

        var metadata = new Metadata("pkg-key", null, "test", null, null, List.of());
        var manifest = assembler.build(metadata);

        assertThat(manifest.campaign().name()).isEqualTo("Test Campaign");
        assertThat(manifest.campaign().description()).isEqualTo("A test description");
        assertThat(manifest.campaign().createdAt()).isEqualTo(campaign.getCreatedAt());
        assertThat(manifest.campaign().currentSceneRef()).isNotNull();
        assertThat(manifest.campaign().currentSceneRef().type()).isEqualTo(CampaignContentType.SCENE);
    }

    @Test
    void importsCampaignFields() {
        var manifest = new CampaignManifestV2(
                2, null,
                new CampaignDto("campaign-key", "Imported Campaign", "Imported description",
                        Instant.parse("2025-01-01T00:00:00Z"), null, null, null),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var context = new CampaignImportContext(
                UUID.randomUUID(), new FakeKeyService(), pendingImport());

        adapter.importSection(manifest, context);

        var campaign = context.campaign();
        assertThat(campaign.getName()).isEqualTo("Imported Campaign");
        assertThat(campaign.getDescription()).isEqualTo("Imported description");
        assertThat(campaign.getCreatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void deferredCurrentSceneIdSetsSceneOnCampaign() {
        var sceneId = UUID.randomUUID();
        var scene = new Scene();
        scene.setId(sceneId);
        scene.setTitle("Test Scene");

        var sceneRef = ContentReference.packageRef(CampaignContentType.SCENE, "my-scene");
        var manifest = new CampaignManifestV2(
                2, null,
                new CampaignDto("campaign-key", "Test", "desc", Instant.now(), null, sceneRef, null),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var context = new CampaignImportContext(
                UUID.randomUUID(), new FakeKeyService(), pendingImport());

        adapter.importSection(manifest, context);
        var campaign = context.campaign();

        assertThat(campaign.getCurrentSceneId()).isNull();

        context.register(CampaignContentType.SCENE, "my-scene", scene, sceneId);

        context.runDeferred();

        assertThat(campaign.getCurrentSceneId()).isEqualTo(sceneId);
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }

    public static class FakeKeyService extends CampaignPackageKeyService {
        final Map<String, String> bindings = new LinkedHashMap<>();

        public FakeKeyService() {
            super(null);
        }

        @Override
        public String getOrCreate(UUID campaignId, CampaignContentType type, UUID entityId, String displayName) {
            String mapKey = type.name() + ":" + entityId;
            if (bindings.containsKey(mapKey)) {
                return bindings.get(mapKey);
            }
            String slug = displayName.isBlank() ? "item"
                    : displayName.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
            String key = slug + "-" + entityId.toString().substring(0, 8);
            bindings.put(mapKey, key);
            return key;
        }

        @Override
        public void bindImported(UUID campaignId, CampaignContentType type, UUID entityId, String packageKey) {
            String mapKey = type.name() + ":" + entityId;
            if (bindings.containsKey(mapKey)) {
                throw new IllegalArgumentException("Entity already bound");
            }
            bindings.put(mapKey, packageKey);
        }

        @Override
        public java.util.Optional<String> find(UUID campaignId, CampaignContentType type, UUID entityId) {
            return java.util.Optional.empty();
        }
    }
}
