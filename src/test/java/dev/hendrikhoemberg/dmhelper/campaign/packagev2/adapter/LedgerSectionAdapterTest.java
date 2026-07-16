package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.ledger.packagev2.LedgerSectionAdapter;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerSectionAdapterTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;

    private LedgerSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new LedgerSectionAdapter(ledgerEntryRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder800() {
        assertThat(adapter.order()).isEqualTo(800);
    }

    @Test
    void hasSectionNameLedger() {
        assertThat(adapter.sectionName()).isEqualTo("Ledger");
    }

    @Test
    void exportsLedgerEntries() {
        var entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setCampaign(campaign);
        entry.setTimestamp(Instant.parse("2025-06-01T12:00:00Z"));
        entry.setInGameYear(1492);
        entry.setInGameMonth(5);
        entry.setInGameDay(1);
        entry.setKind(LedgerEntry.Kind.GOLD);
        entry.setDirection(LedgerEntry.Direction.GAIN);
        entry.setAmount(new BigDecimal("500"));
        entry.setCurrency("GP");
        entry.setHolder("Party Stash");
        entry.setNote("Dragon hoard");

        when(ledgerEntryRepository.findByCampaignIdOrderByTimestampAscIdAsc(campaignId))
                .thenReturn(List.of(entry));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.ledgerEntries()).hasSize(1);
        var dto = manifest.ledgerEntries().get(0);
        assertThat(dto.timestamp()).isEqualTo(entry.getTimestamp());
        assertThat(dto.inGameYear()).isEqualTo(1492);
        assertThat(dto.inGameMonth()).isEqualTo(5);
        assertThat(dto.inGameDay()).isEqualTo(1);
        assertThat(dto.kind()).isEqualTo("GOLD");
        assertThat(dto.direction()).isEqualTo("GAIN");
        assertThat(dto.amount()).isEqualByComparingTo("500");
        assertThat(dto.currency()).isEqualTo("GP");
        assertThat(dto.holder()).isEqualTo("Party Stash");
        assertThat(dto.note()).isEqualTo("Dragon hoard");
    }

    @Test
    void exportsLedgerEntryWithItemAssignmentRef() {
        var assignment = new ItemAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setCustomText("Dagger +1");

        var entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setCampaign(campaign);
        entry.setTimestamp(Instant.parse("2025-06-01T12:00:00Z"));
        entry.setKind(LedgerEntry.Kind.ITEM);
        entry.setDirection(LedgerEntry.Direction.SPEND);
        entry.setItemAssignmentRef(assignment);
        entry.setNote("Bought item");

        when(ledgerEntryRepository.findByCampaignIdOrderByTimestampAscIdAsc(campaignId))
                .thenReturn(List.of(entry));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.ledgerEntries()).hasSize(1);
        var dto = manifest.ledgerEntries().get(0);
        assertThat(dto.itemAssignmentRef()).isNotNull();
        assertThat(dto.kind()).isEqualTo("ITEM");
        assertThat(dto.direction()).isEqualTo("SPEND");
    }

    @Test
    void exportsEmptyListWhenNoEntries() {
        when(ledgerEntryRepository.findByCampaignIdOrderByTimestampAscIdAsc(campaignId))
                .thenReturn(List.of());

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.ledgerEntries()).isEmpty();
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
        a.assignments(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.diceRolls(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
