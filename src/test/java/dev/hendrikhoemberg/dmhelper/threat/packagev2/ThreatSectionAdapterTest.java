package dev.hendrikhoemberg.dmhelper.threat.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.TrapDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreatSectionAdapterTest {

    @Mock TrapRepository trapRepo;
    @Mock HazardRepository hazardRepo;
    @Mock ThreatExportClosureService closureService;
    @Mock ConditionRepository conditionRepo;
    @Mock EquipmentItemRepository equipmentRepo;
    @Mock MagicItemRepository magicItemRepo;
    @Mock StatBlockRepository statBlockRepo;

    private ThreatSectionAdapter adapter() {
        var libraryRefs = new LibraryContentReferenceResolver(
                mockSpell(), mockSpecies(), mockBg(), mockClass(), mockFeat(),
                magicItemRepo, equipmentRepo, statBlockRepo);
        return new ThreatSectionAdapter(
                trapRepo, hazardRepo, closureService,
                new StatBlockReferenceResolver(libraryRefs),
                conditionRepo, equipmentRepo, magicItemRepo);
    }

    @Test
    void hasOrder250() {
        assertThat(adapter().order()).isEqualTo(250);
    }

    @Test
    void sectionNameIsThreats() {
        assertThat(adapter().sectionName()).isEqualTo("Threats");
    }

    @Test
    void exportSectionWritesTrapsAndHazards() {
        UUID campaignId = UUID.randomUUID();
        UUID trapId = UUID.randomUUID();
        var trap = new Trap();
        trap.setId(trapId);
        trap.setName("Spike Pit");
        trap.setSourceKey("spike-pit");
        trap.setDescription("A pit.");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        trap.setDamageExpression("2d10");
        trap.getDamageTypes().add(DamageType.PIERCING);

        when(closureService.forCampaign(campaignId)).thenReturn(
                new ThreatExportClosureService.ClosureResult(
                        List.of(trapId), List.of(), java.util.Map.of()));
        when(trapRepo.findDetailedById(trapId)).thenReturn(Optional.of(trap));

        var campaign = new Campaign();
        campaign.setId(campaignId);
        var ctx = new CampaignExportContext(campaignId, campaign,
                CampaignExportOptions.complete(),
                new dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter.CampaignSectionAdapterTest.FakeKeyService(),
                new CampaignAssetCollector());
        var assembler = primedAssembler();
        adapter().exportSection(ctx, assembler);

        var manifest = assembler.build(new CampaignManifestV2.Metadata(
                "pkg", null, "test", null, null, List.of()));
        assertThat(manifest.traps()).hasSize(1);
        assertThat(manifest.traps().getFirst().name()).isEqualTo("Spike Pit");
        assertThat(manifest.traps().getFirst().damage()).isNotNull();
        assertThat(manifest.traps().getFirst().damage().expression()).isEqualTo("2d10");
        assertThat(manifest.traps().getFirst().damage().types()).containsExactly("PIERCING");
        assertThat(manifest.hazards()).isEmpty();
    }

    @Test
    void importSectionCreatesTraps() {
        when(trapRepo.save(any(Trap.class))).thenAnswer(inv -> {
            Trap t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        var campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        var trapDto = new TrapDto(
                "trap-spike", "trap-spike", "Spike Pit", "A pit.",
                "SETBACK", null, null, null, null, null, null, List.of(),
                null, null, null, null, "NONE", null, null, null,
                List.of(), List.of(), null, null);
        var manifest = emptyManifestWithTraps(List.of(trapDto));

        var ctx = new CampaignImportContext(campaign.getId(),
                new dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter.CampaignSectionAdapterTest.FakeKeyService(),
                new PendingCampaignImport(null, null, null, null));
        ctx.setCampaign(campaign);
        adapter().importSection(manifest, ctx);
        ctx.runDeferred();

        verify(trapRepo, org.mockito.Mockito.atLeastOnce()).save(any(Trap.class));
    }

    private static CampaignManifestAssembler primedAssembler() {
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
        return assembler;
    }

    private static CampaignManifestV2 emptyManifestWithTraps(List<TrapDto> traps) {
        return new CampaignManifestV2(
                2,
                new CampaignManifestV2.Metadata("pkg", null, "test", null, null, List.of()),
                new CampaignManifestV2.CampaignDto("key", "name", null, null, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                traps, List.of());
    }

    private static dev.hendrikhoemberg.dmhelper.library.data.SpellRepository mockSpell() {
        return org.mockito.Mockito.mock(dev.hendrikhoemberg.dmhelper.library.data.SpellRepository.class);
    }
    private static dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository mockSpecies() {
        return org.mockito.Mockito.mock(dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository.class);
    }
    private static dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository mockBg() {
        return org.mockito.Mockito.mock(dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository.class);
    }
    private static dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository mockClass() {
        return org.mockito.Mockito.mock(dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository.class);
    }
    private static dev.hendrikhoemberg.dmhelper.library.data.FeatRepository mockFeat() {
        return org.mockito.Mockito.mock(dev.hendrikhoemberg.dmhelper.library.data.FeatRepository.class);
    }
}
