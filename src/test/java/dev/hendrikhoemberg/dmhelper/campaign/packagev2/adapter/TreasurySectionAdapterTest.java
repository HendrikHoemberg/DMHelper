package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import dev.hendrikhoemberg.dmhelper.treasury.packagev2.TreasurySectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class TreasurySectionAdapterTest {

    @Mock ItemAssignmentRepository assignmentRepository;
    @Mock MagicItemRepository magicItemRepository;
    @Mock EquipmentItemRepository equipmentItemRepository;

    private TreasurySectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        var libraryRefs = new LibraryContentReferenceResolver(
                mock(SpellRepository.class),
                mock(SpeciesRepository.class),
                mock(BackgroundRepository.class),
                mock(CharacterClassRepository.class),
                mock(FeatRepository.class),
                magicItemRepository,
                equipmentItemRepository,
                mock(StatBlockRepository.class));
        adapter = new TreasurySectionAdapter(assignmentRepository, libraryRefs);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder700() {
        assertThat(adapter.order()).isEqualTo(700);
    }

    @Test
    void hasSectionNameTreasury() {
        assertThat(adapter.sectionName()).isEqualTo("Treasury");
    }

    @Test
    void exportsAssignments() {
        var pm = new PartyMember();
        pm.setId(UUID.randomUUID());
        pm.setCharacterName("Thia");

        var mi = new MagicItem();
        mi.setSource(ContentSource.SRD);
        mi.setSourceKey("srd-2024_bag-of-holding");
        mi.setName("Bag of Holding");

        var assignment = new ItemAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setCampaign(campaign);
        assignment.setPartyMember(pm);
        assignment.setMagicItem(mi);
        assignment.setCustomText("Dagger +1");
        assignment.setQuantity(2);
        assignment.setAttuned(true);

        when(assignmentRepository.findByCampaignIdOrderByPartyMemberAscIdAsc(campaignId))
                .thenReturn(List.of(assignment));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.assignments()).hasSize(1);
        var dto = manifest.assignments().get(0);
        assertThat(dto.customText()).isEqualTo("Dagger +1");
        assertThat(dto.quantity()).isEqualTo(2);
        assertThat(dto.attuned()).isTrue();
        assertThat(dto.holderRef()).isNotNull();
        assertThat(dto.holderRef().type().name()).isEqualTo("PARTY_MEMBER");
        assertThat(dto.magicItemRef()).isNotNull();
        assertThat(dto.magicItemRef().sourceKey()).isEqualTo("srd-2024_bag-of-holding");
        assertThat(dto.equipmentItemRef()).isNull();
    }

    @Test
    void exportsAssignmentWithEquipmentItem() {
        var ei = new EquipmentItem();
        ei.setSource(ContentSource.SRD);
        ei.setSourceKey("srd-2024_chain-mail");
        ei.setName("Chain Mail");

        var assignment = new ItemAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setCampaign(campaign);
        assignment.setEquipmentItem(ei);
        assignment.setQuantity(1);
        assignment.setAttuned(false);

        when(assignmentRepository.findByCampaignIdOrderByPartyMemberAscIdAsc(campaignId))
                .thenReturn(List.of(assignment));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.assignments()).hasSize(1);
        var dto = manifest.assignments().get(0);
        assertThat(dto.equipmentItemRef()).isNotNull();
        assertThat(dto.equipmentItemRef().sourceKey()).isEqualTo("srd-2024_chain-mail");
        assertThat(dto.magicItemRef()).isNull();
        assertThat(dto.holderRef()).isNull();
    }

    @Test
    void exportsEmptyListWhenNoAssignments() {
        when(assignmentRepository.findByCampaignIdOrderByPartyMemberAscIdAsc(campaignId))
                .thenReturn(List.of());

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.assignments()).isEmpty();
    }

    @Test
    void importsCatalogItemReferences() {
        var magicItem = new MagicItem();
        magicItem.setSource(ContentSource.SRD);
        magicItem.setId(UUID.randomUUID());
        magicItem.setSourceKey("srd-2024_bag-of-holding");
        var equipmentItem = new EquipmentItem();
        equipmentItem.setSource(ContentSource.SRD);
        equipmentItem.setId(UUID.randomUUID());
        equipmentItem.setSourceKey("srd-2024_chain-mail");
        when(magicItemRepository.findBySourceAndSourceKey(ContentSource.SRD, "srd-2024_bag-of-holding"))
                .thenReturn(Optional.of(magicItem));
        when(equipmentItemRepository.findBySourceAndSourceKey(ContentSource.SRD, "srd-2024_chain-mail"))
                .thenReturn(Optional.of(equipmentItem));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            var assignment = invocation.getArgument(0, ItemAssignment.class);
            assignment.setId(UUID.randomUUID());
            return assignment;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                List.of(
                        new CampaignManifestV2.AssignmentDto(
                                "bag", null,
                                ContentReference.catalogRef(CampaignContentType.MAGIC_ITEM, "SRD_5_2", "srd-2024_bag-of-holding"),
                                null, null, 1, false, "CARRIED"),
                        new CampaignManifestV2.AssignmentDto(
                                "armor", null, null,
                                ContentReference.catalogRef(CampaignContentType.EQUIPMENT_ITEM, "SRD_5_2", "srd-2024_chain-mail"),
                                null, 1, false, "CARRIED")),
                null, null, null, null, null, List.of(), List.of());
        var context = new CampaignImportContext(
                campaignId, new CampaignSectionAdapterTest.FakeKeyService(),
                new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);

        adapter.importSection(manifest, context);

        verify(assignmentRepository).save(argThat(a -> a.getMagicItem() == magicItem));
        verify(assignmentRepository).save(argThat(a -> a.getEquipmentItem() == equipmentItem));
    }

    private CampaignExportContext exportContext() {
        return new CampaignExportContext(
                campaignId, campaign, CampaignExportOptions.complete(),
                new CampaignSectionAdapterTest.FakeKeyService(),
                new CampaignAssetCollector());
    }

    private CampaignManifestAssembler assembler() {
        var a = new CampaignManifestAssembler();
        a.assets(List.of());
        a.campaign(new CampaignManifestV2.CampaignDto("key", "test", null, null, null, null));
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
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.diceRolls(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
