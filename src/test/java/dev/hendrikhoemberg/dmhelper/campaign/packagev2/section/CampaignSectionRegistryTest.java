package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CampaignSectionRegistryTest {

    @Nested
    class RegistryTests {

        @Test
        void returnsExportersInOrder() {
            var registry = new CampaignSectionRegistry(
                    List.of(exporter("b", 20), exporter("a", 10), exporter("c", 30)),
                    List.of());
            assertThat(registry.exporters()).extracting(CampaignSectionExporter::order)
                    .containsExactly(10, 20, 30);
        }

        @Test
        void returnsImportersInOrder() {
            var registry = new CampaignSectionRegistry(
                    List.of(),
                    List.of(importer("b", 20), importer("a", 10), importer("c", 30)));
            assertThat(registry.importers()).extracting(CampaignSectionImporter::order)
                    .containsExactly(10, 20, 30);
        }

        @Test
        void rejectsDuplicateOrderAmongExporters() {
            assertThatThrownBy(() -> new CampaignSectionRegistry(
                    List.of(exporter("a", 10), exporter("b", 10)),
                    List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate")
                    .hasMessageContaining("order");
        }

        @Test
        void rejectsDuplicateNameAmongExporters() {
            assertThatThrownBy(() -> new CampaignSectionRegistry(
                    List.of(exporter("notes", 10), exporter("notes", 20)),
                    List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate")
                    .hasMessageContaining("name");
        }

        @Test
        void rejectsDuplicateOrderAmongImporters() {
            assertThatThrownBy(() -> new CampaignSectionRegistry(
                    List.of(),
                    List.of(importer("a", 5), importer("b", 5))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        void rejectsDuplicateNameAmongImporters() {
            assertThatThrownBy(() -> new CampaignSectionRegistry(
                    List.of(),
                    List.of(importer("maps", 10), importer("maps", 20))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate")
                    .hasMessageContaining("name");
        }

        @Test
        void returnsImmutableExporterList() {
            var registry = new CampaignSectionRegistry(List.of(exporter("a", 10)), List.of());
            assertThatThrownBy(() -> registry.exporters().add(exporter("b", 20)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnsImmutableImporterList() {
            var registry = new CampaignSectionRegistry(List.of(), List.of(importer("a", 10)));
            assertThatThrownBy(() -> registry.importers().add(importer("b", 20)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void emptyListsAreValid() {
            var registry = new CampaignSectionRegistry(List.of(), List.of());
            assertThat(registry.exporters()).isEmpty();
            assertThat(registry.importers()).isEmpty();
        }

        @Test
        void orderOnNameConflictBetweenExporterAndImporterIsAllowed() {
            var registry = new CampaignSectionRegistry(
                    List.of(exporter("notes", 10)),
                    List.of(importer("notes", 10)));
            assertThat(registry.exporters()).hasSize(1);
            assertThat(registry.importers()).hasSize(1);
        }
    }

    @Nested
    class AssemblerTests {

        @Test
        void buildsManifestWithAllSections() {
            var assembler = new CampaignManifestAssembler();
            var campaign = new CampaignManifestV2.CampaignDto(
                    "test-key", "Test", "desc", null, null, null);
            assembler.campaign(campaign);
            assembler.assets(List.of());
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
            assembler.diceRolls(List.of());

            var metadata = new CampaignManifestV2.Metadata(
                    "pkg-key", null, "test", null, null, List.of());
            var manifest = assembler.build(metadata);

            assertThat(manifest.formatVersion()).isEqualTo(CampaignManifestV2.CURRENT_FORMAT_VERSION);
            assertThat(manifest.metadata()).isEqualTo(metadata);
            assertThat(manifest.campaign()).isEqualTo(campaign);
        }

        @Test
        void rejectsDoubleWrite() {
            var assembler = new CampaignManifestAssembler();
            assembler.notes(List.of());
            assertThatThrownBy(() -> assembler.notes(List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already");
        }

        @Test
        void rejectsMissingSectionOnBuild() {
            var assembler = new CampaignManifestAssembler();
            var campaign = new CampaignManifestV2.CampaignDto(
                    "test-key", "Test", "desc", null, null, null);
            assembler.campaign(campaign);
            assembler.assets(List.of());
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
            // diceRolls omitted

            var metadata = new CampaignManifestV2.Metadata(
                    "pkg-key", null, "test", null, null, List.of());
            assertThatThrownBy(() -> assembler.build(metadata))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("diceRolls");
        }
    }

    @Nested
    class ExportOptionsTests {

        @Test
        void completeReturnsBothIncluded() {
            var opts = CampaignExportOptions.complete();
            assertThat(opts.includeCombatLog()).isTrue();
            assertThat(opts.includeDiceHistory()).isTrue();
        }

        @Test
        void exclusionsEmptyWhenBothIncluded() {
            var opts = new CampaignExportOptions(true, true);
            assertThat(opts.exclusions()).isEmpty();
        }

        @Test
        void exclusionsContainsCombatLogWhenExcluded() {
            var opts = new CampaignExportOptions(false, true);
            assertThat(opts.exclusions()).containsExactly(CampaignExportExclusion.COMBAT_LOG);
        }

        @Test
        void exclusionsContainsDiceHistoryWhenExcluded() {
            var opts = new CampaignExportOptions(true, false);
            assertThat(opts.exclusions()).containsExactly(CampaignExportExclusion.DICE_HISTORY);
        }

        @Test
        void exclusionsContainsBothWhenBothExcluded() {
            var opts = new CampaignExportOptions(false, false);
            assertThat(opts.exclusions()).containsExactlyInAnyOrder(
                    CampaignExportExclusion.COMBAT_LOG,
                    CampaignExportExclusion.DICE_HISTORY);
        }
    }

    @Nested
    class AssetCollectorTests {

        @Test
        void collectsAssetsByKey() {
            var collector = new CampaignAssetCollector();
            var descriptor = new AssetDescriptor("img-1", "maps/img.png", "image/png", 100, "abc", "img.png");
            collector.add(descriptor, new byte[]{1, 2, 3});
            assertThat(collector.assetDescriptors()).containsExactly(descriptor);
            assertThat(collector.assetSources()).containsOnlyKeys("img-1");
        }

        @Test
        void rejectsDuplicateAssetKey() {
            var collector = new CampaignAssetCollector();
            var d1 = new AssetDescriptor("img-1", "maps/img.png", "image/png", 100, "abc", "img.png");
            var d2 = new AssetDescriptor("img-1", "maps/other.png", "image/png", 200, "def", "other.png");
            collector.add(d1, new byte[]{1, 2, 3});
            assertThatThrownBy(() -> collector.add(d2, new byte[]{4, 5, 6}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("img-1");
        }

        @Test
        void returnsUnmodifiableViews() {
            var collector = new CampaignAssetCollector();
            assertThatThrownBy(() -> collector.assetDescriptors().add(
                    new AssetDescriptor("x", "x", "x", 0, "x", "x")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> collector.assetSources().put("x", new byte[0]))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void isEmptyInitially() {
            var collector = new CampaignAssetCollector();
            assertThat(collector.assetDescriptors()).isEmpty();
            assertThat(collector.assetSources()).isEmpty();
        }
    }

    @Nested
    class ImportContextTests {

        @Test
        void registerBindsKeyViaKeyService() {
            var keyService = new FakeKeyService();
            var context = new CampaignImportContext(
                    UUID.randomUUID(), keyService, pendingImport());
            var campaign = new Campaign();
            campaign.setName("Test");
            context.setCampaign(campaign);

            UUID entityId = UUID.randomUUID();
            context.register(CampaignContentType.SCENE, "my-scene", "entity-instance", entityId);

            assertThat(keyService.bindings)
                    .containsEntry("SCENE:" + entityId, "my-scene");
        }

        @Test
        void requireReturnsRegisteredEntity() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());
            UUID entityId = UUID.randomUUID();
            String entity = "the-entity";
            context.register(CampaignContentType.NOTE, "note-1", entity, entityId);

            String result = context.require(
                    ContentReference.packageRef(CampaignContentType.NOTE, "note-1"),
                    CampaignContentType.NOTE, String.class);
            assertThat(result).isEqualTo("the-entity");
        }

        @Test
        void requireWithWrongTypeFails() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());
            UUID entityId = UUID.randomUUID();
            context.register(CampaignContentType.NOTE, "note-1", "entity", entityId);

            assertThatThrownBy(() -> context.require(
                    ContentReference.packageRef(CampaignContentType.NOTE, "note-1"),
                    CampaignContentType.MAP, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MAP");
        }

        @Test
        void requireWithMissingReferenceFails() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());

            assertThatThrownBy(() -> context.require(
                    ContentReference.packageRef(CampaignContentType.NOTE, "nonexistent"),
                    CampaignContentType.NOTE, String.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nonexistent");
        }

        @Test
        void requireRejectsCatalogReferences() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());

            var catalogRef = new ContentReference(
                    ContentReference.Scope.CATALOG, CampaignContentType.SPELL, null, "5e", "fireball");
            assertThatThrownBy(() -> context.require(catalogRef, CampaignContentType.SPELL, String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Catalog");
        }

        @Test
        void setCampaignCanBeCalledOnce() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            var campaign = new Campaign();
            context.setCampaign(campaign);
            assertThat(context.campaign()).isSameAs(campaign);
            assertThatThrownBy(() -> context.setCampaign(new Campaign()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already");
        }

        @Test
        void campaignFailsBeforeSetCampaign() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            assertThatThrownBy(context::campaign)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not yet");
        }

        @Test
        void deferredExecutionRunsOnRunDeferred() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());
            var holder = new int[]{0};
            context.defer("increment", () -> holder[0]++);
            context.defer("increment-again", () -> holder[0]++);
            assertThat(holder[0]).isZero();
            context.runDeferred();
            assertThat(holder[0]).isEqualTo(2);
        }

        @Test
        void deferredRunsOnlyOnce() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            context.setCampaign(new Campaign());
            var holder = new int[]{0};
            context.defer("increment", () -> holder[0]++);
            context.runDeferred();
            assertThat(holder[0]).isEqualTo(1);
            context.runDeferred();
            assertThat(holder[0]).isEqualTo(1);
        }

        @Test
        void requireAssetResolvesFromValidationResult() {
            var result = new CampaignPackageValidationResult(
                    null, null, 0, Map.of("img-1", Path.of("maps/img.png")), List.of(), List.of());
            var pending = new PendingCampaignImport(
                    UUID.randomUUID(), result, null, null);
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pending);
            context.setCampaign(new Campaign());

            Path path = context.requireAsset("img-1");
            assertThat(path).isEqualTo(Path.of("maps/img.png"));
        }

        @Test
        void requireAssetFailsWhenResultNotAvailable() {
            var context = new CampaignImportContext(
                    UUID.randomUUID(), new FakeKeyService(), pendingImport());
            assertThatThrownBy(() -> context.requireAsset("img-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("validation result is not available");
        }
    }

    @Nested
    class ExportContextTests {

        @Test
        void keyGeneratesViaKeyService() {
            var keyService = new FakeKeyService();
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            campaign.setName("Test");
            var options = CampaignExportOptions.complete();
            var assets = new CampaignAssetCollector();
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, options, keyService, assets);

            UUID entityId = UUID.randomUUID();
            String key = ctx.key(CampaignContentType.MAP, entityId, "Dungeon");
            assertThat(key).startsWith("dungeon-");
        }

        @Test
        void packageRefUsesGeneratedKey() {
            var keyService = new FakeKeyService();
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            campaign.setName("Test");
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, CampaignExportOptions.complete(),
                    keyService, new CampaignAssetCollector());

            UUID entityId = UUID.randomUUID();
            ContentReference ref = ctx.packageRef(CampaignContentType.SCENE, entityId, "Entry");
            assertThat(ref.scope()).isEqualTo(ContentReference.Scope.PACKAGE);
            assertThat(ref.type()).isEqualTo(CampaignContentType.SCENE);
            assertThat(ref.key()).startsWith("entry-");
            assertThat(ref.ruleset()).isNull();
            assertThat(ref.sourceKey()).isNull();
        }

        @Test
        void catalogRefUsesSourceKey() {
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, CampaignExportOptions.complete(),
                    new FakeKeyService(), new CampaignAssetCollector());

            ContentReference ref = ctx.catalogRef(CampaignContentType.SPELL, "fireball");
            assertThat(ref.scope()).isEqualTo(ContentReference.Scope.CATALOG);
            assertThat(ref.type()).isEqualTo(CampaignContentType.SPELL);
            assertThat(ref.key()).isNull();
            assertThat(ref.sourceKey()).isEqualTo("fireball");
        }

        @Test
        void assetsDelegatesToCollector() {
            var collector = new CampaignAssetCollector();
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, CampaignExportOptions.complete(),
                    new FakeKeyService(), collector);
            assertThat(ctx.assets()).isSameAs(collector);
        }

        @Test
        void campaignExposesEntity() {
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, CampaignExportOptions.complete(),
                    new FakeKeyService(), new CampaignAssetCollector());
            assertThat(ctx.campaign()).isSameAs(campaign);
            assertThat(ctx.campaignId()).isEqualTo(campaign.getId());
        }

        @Test
        void optionsExposed() {
            var options = new CampaignExportOptions(false, true);
            var campaign = new Campaign();
            campaign.setId(UUID.randomUUID());
            var ctx = new CampaignExportContext(
                    campaign.getId(), campaign, options,
                    new FakeKeyService(), new CampaignAssetCollector());
            assertThat(ctx.options()).isSameAs(options);
        }
    }

    /* --- helpers --- */

    private static CampaignSectionExporter exporter(String name, int order) {
        return new CampaignSectionExporter() {
            @Override public String sectionName() { return name; }
            @Override public int order() { return order; }
            @Override
            public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
            }
        };
    }

    private static CampaignSectionImporter importer(String name, int order) {
        return new CampaignSectionImporter() {
            @Override public String sectionName() { return name; }
            @Override public int order() { return order; }
            @Override
            public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
            }
        };
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(
                UUID.randomUUID(), null, null, null);
    }

    static class FakeKeyService extends CampaignPackageKeyService {
        final Map<String, String> bindings = new LinkedHashMap<>();

        FakeKeyService() {
            super(null);
        }

        @Override
        public String getOrCreate(UUID campaignId, CampaignContentType type, UUID entityId, String displayName) {
            String slug = displayName.isBlank() ? "item"
                    : displayName.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
            return slug + "-" + entityId.toString().substring(0, 8);
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
