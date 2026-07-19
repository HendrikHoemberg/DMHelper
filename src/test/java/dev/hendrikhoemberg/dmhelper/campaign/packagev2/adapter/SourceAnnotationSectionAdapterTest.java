package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotation;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.SourceAnnotationSectionAdapter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceAnnotationSectionAdapterTest {

    @Mock SourceAnnotationRepository repo;

    private SourceAnnotationSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new SourceAnnotationSectionAdapter(repo);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder980() {
        assertThat(adapter.order()).isEqualTo(980);
    }

    @Test
    void sectionNameIsSourceAnnotation() {
        assertThat(adapter.sectionName()).isEqualTo("SourceAnnotation");
    }

    @Test
    void importsSourceAnnotationWithDeferredPackageOwner() {
        var sceneId = UUID.randomUUID();
        var scene = new Scene();
        scene.setId(sceneId);
        scene.setTitle("Village");

        var dto = new CampaignManifestV2.SourceAnnotationDto(
                "ann-1",
                ContentReference.packageRef(CampaignContentType.SCENE, "sc-village"),
                "/adventures/0/chapters/0/scenes/0/checks/0/dc", "DC inferred", "MEDIUM",
                "book:1", "OPEN", null, Instant.parse("2025-01-01T00:00:00Z"));

        when(repo.save(any())).thenAnswer(inv -> {
            SourceAnnotation a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(dto), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);
        context.register(CampaignContentType.SCENE, "sc-village", scene, sceneId);

        adapter.importSection(manifest, context);
        context.runDeferred();

        ArgumentCaptor<SourceAnnotation> captor = ArgumentCaptor.forClass(SourceAnnotation.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        SourceAnnotation saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getOwnerType()).isEqualTo("SCENE");
        assertThat(saved.getOwnerId()).isEqualTo(sceneId);
        assertThat(saved.getConfidence().name()).isEqualTo("MEDIUM");
        assertThat(keys.bindings).containsValue(dto.key());
    }

    @Test
    void exportsSourceAnnotationsWithPackageKeysNotUuids() {
        UUID ownerId = UUID.randomUUID();
        SourceAnnotation a = new SourceAnnotation();
        a.setId(UUID.randomUUID());
        a.setCampaign(campaign);
        a.setOwnerType("SCENE");
        a.setOwnerId(ownerId);
        a.setFieldPath("/adventures/0/chapters/0/scenes/0/checks/0/dc");
        a.setMessage("DC inferred");
        a.setConfidence(dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence.HIGH);
        a.setStatus(dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationStatus.OPEN);
        a.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));

        when(repo.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId)).thenReturn(List.of(a));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        // Pre-bind a stable package key for the owner so export does not emit a UUID
        keyService.bindImported(campaignId, CampaignContentType.SCENE, ownerId, "sc-village");
        var ctx = new CampaignExportContext(
                campaignId, campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.annotations()).hasSize(1);
        var exported = manifest.annotations().get(0);
        assertThat(exported.ownerRef().scope()).isEqualTo(ContentReference.Scope.PACKAGE);
        assertThat(exported.ownerRef().type()).isEqualTo(CampaignContentType.SCENE);
        assertThat(exported.ownerRef().key()).isEqualTo("sc-village");
        assertThat(exported.confidence()).isEqualTo("HIGH");
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null, null));
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
        a.handouts(List.of());
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
        a.quests(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
