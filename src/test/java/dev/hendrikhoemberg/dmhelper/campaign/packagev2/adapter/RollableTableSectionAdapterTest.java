package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.RollableTableDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.packagev2.RollableTableExportClosureService;
import dev.hendrikhoemberg.dmhelper.rollabletable.packagev2.RollableTableSectionAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollableTableSectionAdapterTest {

    @Mock RollableTableRepository tableRepo;
    @Mock RollableTableExportClosureService closureService;

    @Test
    void hasOrder150() {
        var adapter = new RollableTableSectionAdapter(tableRepo, closureService);
        assertThat(adapter.order()).isEqualTo(150);
    }

    @Test
    void sectionNameIsRollableTables() {
        var adapter = new RollableTableSectionAdapter(tableRepo, closureService);
        assertThat(adapter.sectionName()).isEqualTo("RollableTables");
    }

    @Test
    void exportSectionWritesTables() {
        UUID campaignId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();

        var table = new RollableTable();
        table.setId(tableId);
        table.setName("Test Table");
        table.setAddressMode(dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode.RANGE);
        table.setCategory(dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory.GENERIC);

        var result = new RollableTableExportClosureService.ClosureResult(
                List.of(tableId), java.util.Map.of());

        when(closureService.forCampaign(campaignId)).thenReturn(result);
        when(tableRepo.findWithEntriesById(tableId)).thenReturn(Optional.of(table));

        var adapter = new RollableTableSectionAdapter(tableRepo, closureService);
        var campaign = new Campaign();
        campaign.setId(campaignId);
        var ctx = new CampaignExportContext(campaignId, campaign,
                CampaignExportOptions.complete(),
                new CampaignSectionAdapterTest.FakeKeyService(),
                new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.campaign(new CampaignManifestV2.CampaignDto("key", "name", null, null, null, null));
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

        adapter.exportSection(ctx, assembler);

        var metadata = new CampaignManifestV2.Metadata("pkg", null, "test", null, null, List.of());
        var manifest = assembler.build(metadata);
        assertThat(manifest.rollableTables()).hasSize(1);
    }

    @Test
    void importSectionCreatesTables() {
        var adapter = new RollableTableSectionAdapter(tableRepo, closureService);
        var campaign = new Campaign();
        List<CampaignManifestV2.AssignmentDto> noAssign = List.of();
        List<CampaignManifestV2.LedgerEntryDto> noLedger = List.of();
        List<CampaignManifestV2.TimelineEventDto> noTimeline = List.of();
        List<CampaignManifestV2.DiceRollDto> noDice = List.of();
        List<AssetDescriptor> noAssets = List.of();
        List<CampaignManifestV2.PartyMemberDto> noParty = List.of();
        List<CampaignManifestV2.StatBlockDto> noSb = List.of();
        List<CampaignManifestV2.CustomSpellDto> noSpell = List.of();
        List<CampaignManifestV2.CustomConditionDto> noCond = List.of();
        List<CampaignManifestV2.CustomRuleDto> noRule = List.of();
        List<CampaignManifestV2.CustomEquipmentDto> noEquip = List.of();
        List<CampaignManifestV2.CustomMagicItemDto> noMagic = List.of();
        List<CampaignManifestV2.CustomClassDto> noClass = List.of();
        List<CampaignManifestV2.CustomSpeciesDto> noSpecies = List.of();
        List<CampaignManifestV2.CustomBackgroundDto> noBg = List.of();
        List<CampaignManifestV2.CustomFeatDto> noFeat = List.of();
        List<CampaignManifestV2.HandoutDto> noHand = List.of();
        List<CampaignManifestV2.MapDto> noMap = List.of();
        List<CampaignManifestV2.EncounterDto> noEnc = List.of();
        List<CampaignManifestV2.NoteDto> noNote = List.of();
        List<CampaignManifestV2.QuickNoteDto> noQn = List.of();
        List<CampaignManifestV2.AdventureDto> noAdv = List.of();
        List<CampaignManifestV2.QuestDto> noQ = List.of();
        List<CampaignManifestV2.SourceAnnotationDto> noAnn = List.of();
        List<CampaignManifestV2.WorldNpcDto> noNpcs = List.of();
        List<CampaignManifestV2.WorldLocationDto> noLocations = List.of();
        List<CampaignManifestV2.FactionDto> noFactions = List.of();
        List<CampaignManifestV2.WorldRelationshipDto> noRelationships = List.of();
        List<CampaignManifestV2.FactionClockDto> noClocks = List.of();

        var manifest = new CampaignManifestV2(
                2,
                new CampaignManifestV2.Metadata("pkg", null, "test", null, null, List.of()),
                new CampaignManifestV2.CampaignDto("key", "name", null, null, null, null),
                noAssets, noParty, noSb, noSpell, noCond, noRule, noEquip, noMagic,
                noClass, noSpecies, noBg, noFeat, noHand, noMap, noEnc, noNote, noQn,
                noAssign, noLedger, noTimeline, noAdv, null, noDice, noQ, noAnn,
                noNpcs, noLocations, noFactions, noRelationships, noClocks,
                List.of(new RollableTableDto("table-key", null, "Test Table", null,
                        "RANGE", "1d6", "GENERIC", null, List.of(), null)));

        when(tableRepo.save(any())).thenAnswer(inv -> {
            var t = (RollableTable) inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        var context = new CampaignImportContext(UUID.randomUUID(),
                new CampaignSectionAdapterTest.FakeKeyService(),
                new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);

        adapter.importSection(manifest, context);
        context.runDeferred();

        assertThat(manifest.rollableTables()).hasSize(1);
    }

    @Test
    void nullRollableTablesDoesNotFail() {
        var adapter = new RollableTableSectionAdapter(tableRepo, closureService);
        List<CampaignManifestV2.AssignmentDto> noAssign = List.of();
        List<CampaignManifestV2.LedgerEntryDto> noLedger = List.of();
        List<CampaignManifestV2.TimelineEventDto> noTimeline = List.of();
        List<CampaignManifestV2.DiceRollDto> noDice = List.of();
        List<AssetDescriptor> noAssets = List.of();
        List<CampaignManifestV2.PartyMemberDto> noParty = List.of();
        List<CampaignManifestV2.StatBlockDto> noSb = List.of();
        List<CampaignManifestV2.CustomSpellDto> noSpell = List.of();
        List<CampaignManifestV2.CustomConditionDto> noCond = List.of();
        List<CampaignManifestV2.CustomRuleDto> noRule = List.of();
        List<CampaignManifestV2.CustomEquipmentDto> noEquip = List.of();
        List<CampaignManifestV2.CustomMagicItemDto> noMagic = List.of();
        List<CampaignManifestV2.CustomClassDto> noClass = List.of();
        List<CampaignManifestV2.CustomSpeciesDto> noSpecies = List.of();
        List<CampaignManifestV2.CustomBackgroundDto> noBg = List.of();
        List<CampaignManifestV2.CustomFeatDto> noFeat = List.of();
        List<CampaignManifestV2.HandoutDto> noHand = List.of();
        List<CampaignManifestV2.MapDto> noMap = List.of();
        List<CampaignManifestV2.EncounterDto> noEnc = List.of();
        List<CampaignManifestV2.NoteDto> noNote = List.of();
        List<CampaignManifestV2.QuickNoteDto> noQn = List.of();
        List<CampaignManifestV2.AdventureDto> noAdv = List.of();
        List<CampaignManifestV2.QuestDto> noQ = List.of();
        List<CampaignManifestV2.SourceAnnotationDto> noAnn = List.of();
        List<CampaignManifestV2.WorldNpcDto> noNpcs = List.of();
        List<CampaignManifestV2.WorldLocationDto> noLocations = List.of();
        List<CampaignManifestV2.FactionDto> noFactions = List.of();
        List<CampaignManifestV2.WorldRelationshipDto> noRelationships = List.of();
        List<CampaignManifestV2.FactionClockDto> noClocks = List.of();

        var manifest = new CampaignManifestV2(
                2,
                new CampaignManifestV2.Metadata("pkg", null, "test", null, null, List.of()),
                new CampaignManifestV2.CampaignDto("key", "name", null, null, null, null),
                noAssets, noParty, noSb, noSpell, noCond, noRule, noEquip, noMagic,
                noClass, noSpecies, noBg, noFeat, noHand, noMap, noEnc, noNote, noQn,
                noAssign, noLedger, noTimeline, noAdv, null, noDice, noQ, noAnn,
                noNpcs, noLocations, noFactions, noRelationships, noClocks,
                null);

        var context = new CampaignImportContext(UUID.randomUUID(),
                new CampaignSectionAdapterTest.FakeKeyService(),
                new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(new Campaign());

        adapter.importSection(manifest, context);
        context.runDeferred();
    }
}
