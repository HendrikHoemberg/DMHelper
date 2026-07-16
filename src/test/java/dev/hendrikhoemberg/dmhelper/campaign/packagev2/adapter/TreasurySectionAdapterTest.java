package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasurySectionAdapterTest {

    @Mock ItemAssignmentRepository assignmentRepository;

    private TreasurySectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new TreasurySectionAdapter(assignmentRepository);
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
        a.handouts(List.of());
        a.maps(List.of());
        a.encounters(List.of());
        a.notes(List.of());
        a.quickNotes(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.diceRolls(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
