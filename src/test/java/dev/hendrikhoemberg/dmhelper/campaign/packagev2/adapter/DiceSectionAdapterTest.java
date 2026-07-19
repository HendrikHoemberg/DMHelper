package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.dice.packagev2.DiceSectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DiceSectionAdapterTest {

    @Mock DiceRollRepository diceRollRepository;

    private DiceSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new DiceSectionAdapter(diceRollRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder1200() {
        assertThat(adapter.order()).isEqualTo(1200);
    }

    @Test
    void hasSectionNameDice() {
        assertThat(adapter.sectionName()).isEqualTo("Dice");
    }

    @Test
    void exportsDiceRollsWhenIncluded() {
        var roll = new DiceRoll();
        roll.setId(UUID.randomUUID());
        roll.setExpression("1d20+5");
        roll.setRolls("[{\"die\":\"d20\",\"values\":[15]}]");
        roll.setModifier(5);
        roll.setTotal(20);
        roll.setAdvantage(false);
        roll.setDisadvantage(false);
        roll.setCampaign(campaign);
        roll.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));

        when(diceRollRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId))
                .thenReturn(List.of(roll));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.diceRolls()).hasSize(1);
        assertThat(manifest.diceRolls().get(0).expression()).isEqualTo("1d20+5");
        assertThat(manifest.diceRolls().get(0).total()).isEqualTo(20);
    }

    @Test
    void exportsEmptyListWhenDiceHistoryExcluded() {
        var ctx = exportContext(new CampaignExportOptions(true, false));
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.diceRolls()).isEmpty();
    }

    @Test
    void importBindsThePackageKeyForThePersistedDiceRoll() {
        var rollId = UUID.randomUUID();
        when(diceRollRepository.save(any())).thenAnswer(invocation -> {
            var roll = invocation.getArgument(0, DiceRoll.class);
            roll.setId(rollId);
            return roll;
        });
        var dto = new CampaignManifestV2.DiceRollDto(
                "roll-important", "1d20+5", List.of(), 5, 20,
                false, false, null, Instant.parse("2025-06-01T12:00:00Z"));
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, List.of(dto), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);

        adapter.importSection(manifest, context);

        assertThat(keys.bindings)
                .containsEntry(CampaignContentType.DICE_ROLL.name() + ":" + rollId, "roll-important");
    }

    private CampaignExportContext exportContext(CampaignExportOptions options) {
        return new CampaignExportContext(
                campaignId, campaign, options,
                new CampaignSectionAdapterTest.FakeKeyService(),
                new CampaignAssetCollector());
    }

    private CampaignManifestAssembler assembler() {
        var a = new CampaignManifestAssembler();
        a.assets(List.of());
        a.campaign(new CampaignManifestV2.CampaignDto("key", "test", null, null, null, null, null));
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
        a.audioCues(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
