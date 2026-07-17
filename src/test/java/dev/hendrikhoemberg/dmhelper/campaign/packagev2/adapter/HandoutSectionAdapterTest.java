package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.packagev2.HandoutSectionAdapter;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandoutSectionAdapterTest {

    private HandoutService handoutService;
    private HandoutRepository handoutRepo;
    private HandoutSectionAdapter adapter;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        handoutService = mock(HandoutService.class);
        handoutRepo = mock(HandoutRepository.class);
        adapter = new HandoutSectionAdapter(handoutService, handoutRepo);
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder500() {
        assertThat(adapter.order()).isEqualTo(500);
    }

    @Test
    void sectionNameIsHandout() {
        assertThat(adapter.sectionName()).isEqualTo("Handout");
    }

    @Test
    void exportsHandoutsWithAssetRef() throws Exception {
        UUID handoutId = UUID.randomUUID();
        Handout handout = handout(handoutId, "Map", "quest,important", "image/png", "stored.png", true, false);
        byte[] fileData = "fake-image-data".getBytes();

        when(handoutRepo.findByCampaignIdOrderByTitleAsc(campaign.getId()))
                .thenReturn(List.of(handout));
        when(handoutService.getFileContent(handoutId)).thenReturn(fileData);

        var collector = new CampaignAssetCollector();
        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, collector);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var handoutDtos = manifest.handouts();
        assertThat(handoutDtos).hasSize(1);
        var dto = handoutDtos.get(0);
        assertThat(dto.title()).isEqualTo("Map");
        assertThat(dto.tags()).containsExactly("quest", "important");
        assertThat(dto.contentType()).isEqualTo("image/png");
        assertThat(dto.dmOnly()).isTrue();
        assertThat(dto.presented()).isFalse();
        assertThat(dto.assetRef()).isNotNull();

        var descriptors = collector.assetDescriptors();
        assertThat(descriptors).hasSize(1);
        AssetDescriptor desc = descriptors.get(0);
        assertThat(desc.key()).isEqualTo(dto.assetRef());
        assertThat(desc.mediaType()).isEqualTo("image/png");
        assertThat(desc.sizeBytes()).isEqualTo(fileData.length);
        assertThat(desc.sha256()).isNotNull();
        assertThat(desc.path()).startsWith("assets/handouts/");
    }

    @Test
    void exportsPresentedAndDmOnlyFlags() throws Exception {
        UUID h1id = UUID.randomUUID();
        UUID h2id = UUID.randomUUID();
        Handout dmOnly = handout(h1id, "Secret", "", "image/png", "s.png", true, false);
        Handout presented = handout(h2id, "Revealed", "", "image/png", "r.png", false, true);

        when(handoutRepo.findByCampaignIdOrderByTitleAsc(campaign.getId()))
                .thenReturn(List.of(dmOnly, presented));
        when(handoutService.getFileContent(any())).thenReturn("data".getBytes());

        var collector = new CampaignAssetCollector();
        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, collector);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.handouts()).hasSize(2);
        assertThat(manifest.handouts().get(0).dmOnly()).isTrue();
        assertThat(manifest.handouts().get(0).presented()).isFalse();
        assertThat(manifest.handouts().get(1).dmOnly()).isFalse();
        assertThat(manifest.handouts().get(1).presented()).isTrue();
    }

    @Test
    void importsHandoutsPreservingDmOnlyAndPresented(@TempDir Path tempDir) throws Exception {
        byte[] imageBytes = "handout-content".getBytes();
        String assetKey = "handout-key";
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(imageBytes));

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(new HandoutDto(
                        "handout-map", "Map", List.of("quest", "important"),
                        assetKey, "image/png", false, true
                )),
                null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        Path assetFile = tempDir.resolve(assetKey + ".png");
        Files.write(assetFile, imageBytes);

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var validationResult = new CampaignPackageValidationResult(
                null, manifest, 2, Map.of(assetKey, assetFile), List.of(), List.of());
        var pendingImport = new PendingCampaignImport(
                UUID.randomUUID(), validationResult, null, null);

        Handout saved = new Handout();
        saved.setId(UUID.randomUUID());
        saved.setCampaign(freshCampaign);
        saved.setTitle("Map");
        saved.setTags("quest, important");
        saved.setContentType("image/png");
        saved.setFileName("stored-" + UUID.randomUUID() + ".png");
        saved.setDmOnly(true);
        saved.setPresented(false);

        when(handoutService.createImported(eq(freshCampaign.getId()), eq("Map"), eq("quest, important"),
                anyString(), eq("image/png"), eq(imageBytes)))
                .thenReturn(saved);
        when(handoutRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport);
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(handoutService).createImported(eq(freshCampaign.getId()), eq("Map"), eq("quest, important"),
                anyString(), eq("image/png"), eq(imageBytes));

        verify(handoutRepo, atLeastOnce()).save(argThat(h ->
                h == saved && !h.isDmOnly() && h.isPresented()
        ));
    }

    @Test
    void importsSetsFlagsExactlyWithoutDerivation(@TempDir Path tempDir) throws Exception {
        byte[] imageBytes = "more-data".getBytes();
        String assetKey = "handout-key-2";
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(imageBytes));

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(
                        new HandoutDto("h1", "DM Only", List.of(), assetKey, "image/png", true, false),
                        new HandoutDto("h2", "Presented", List.of(), assetKey, "image/png", false, true),
                        new HandoutDto("h3", "Both False", List.of(), assetKey, "image/png", false, false),
                        new HandoutDto("h4", "Both True", List.of(), assetKey, "image/png", true, true)

                ),
                null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        Path assetFile = tempDir.resolve(assetKey + ".png");
        Files.write(assetFile, imageBytes);

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var validationResult = new CampaignPackageValidationResult(
                null, manifest, 2, Map.of(assetKey, assetFile), List.of(), List.of());
        var pendingImport = new PendingCampaignImport(
                UUID.randomUUID(), validationResult, null, null);

        when(handoutService.createImported(any(), anyString(), anyString(), anyString(), anyString(), any(byte[].class)))
                .thenAnswer(inv -> {
                    Handout h = new Handout();
                    h.setId(UUID.randomUUID());
                    h.setCampaign(freshCampaign);
                    h.setTitle(inv.getArgument(1));
                    h.setContentType("image/png");
                    return h;
                });
        when(handoutRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport);
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        // Verify each handout was saved with the exact dmOnly/presented values
        verify(handoutRepo).save(argThat(h ->
                h.getTitle().equals("DM Only") && h.isDmOnly() && !h.isPresented()
        ));
        verify(handoutRepo).save(argThat(h ->
                h.getTitle().equals("Presented") && !h.isDmOnly() && h.isPresented()
        ));
        verify(handoutRepo).save(argThat(h ->
                h.getTitle().equals("Both False") && !h.isDmOnly() && !h.isPresented()
        ));
        verify(handoutRepo).save(argThat(h ->
                h.getTitle().equals("Both True") && h.isDmOnly() && h.isPresented()
        ));
    }

    @Test
    void generatesDistinctStorageNames() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Handout h1 = handout(id1, "A", "", "image/png", "a.png", true, false);
        Handout h2 = handout(id2, "B", "", "image/png", "b.png", true, false);

        when(handoutRepo.findByCampaignIdOrderByTitleAsc(campaign.getId()))
                .thenReturn(List.of(h1, h2));
        when(handoutService.getFileContent(any())).thenReturn("data".getBytes());

        var collector = new CampaignAssetCollector();
        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, collector);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.handouts()).hasSize(2);
        // Asset paths must be different since they use different keys
        var descriptors = collector.assetDescriptors();
        assertThat(descriptors).hasSize(2);
        assertThat(descriptors.get(0).path()).isNotEqualTo(descriptors.get(1).path());
    }

    private Handout handout(UUID id, String title, String tags, String contentType,
                            String fileName, boolean dmOnly, boolean presented) {
        Handout h = new Handout();
        h.setId(id);
        h.setCampaign(campaign);
        h.setTitle(title);
        h.setTags(tags);
        h.setContentType(contentType);
        h.setFileName(fileName);
        h.setDmOnly(dmOnly);
        h.setPresented(presented);
        return h;
    }

    private CampaignExportContext exportContext(CampaignSectionAdapterTest.FakeKeyService keyService) {
        return new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
        a.party(List.of());
        a.customStatBlocks(List.of());
        a.customSpells(List.of());
        a.customConditions(List.of());
        a.customRules(List.of());
        a.customEquipment(List.of());
        a.customMagicItems(List.of());
        a.customClasses(List.of());
        a.customSpecies(List.of());
        a.customBackgrounds(List.of());
        a.customFeats(List.of());
        a.maps(List.of());
        a.encounters(List.of());
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.diceRolls(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
