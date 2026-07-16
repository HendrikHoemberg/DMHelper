package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CombatLogEntryDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CombatantDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.EncounterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.packagev2.EncounterSectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterSectionAdapterTest {

    @Mock EncounterRepository encounterRepository;
    @Mock CombatantRepository combatantRepository;
    @Mock CombatLogEntryRepository combatLogEntryRepository;

    private EncounterSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new EncounterSectionAdapter(encounterRepository, combatantRepository, combatLogEntryRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder600() {
        assertThat(adapter.order()).isEqualTo(600);
    }

    @Test
    void hasSectionNameEncounter() {
        assertThat(adapter.sectionName()).isEqualTo("Encounter");
    }

    @Test
    void exportsEncounterFields() {
        var encounter = createEncounter("Test Encounter", Encounter.Status.ACTIVE, 3, 1);
        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of());

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.encounters()).hasSize(1);
        var dto = manifest.encounters().get(0);
        assertThat(dto.name()).isEqualTo("Test Encounter");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.round()).isEqualTo(3);
        assertThat(dto.activeTurnIndex()).isEqualTo(1);
    }

    @Test
    void exportsCombatants() {
        var encounter = createEncounter("Encounter", Encounter.Status.PLANNED, 0, -1);
        var combatant = new Combatant();
        combatant.setId(UUID.randomUUID());
        combatant.setEncounter(encounter);
        combatant.setName("Goblin");
        combatant.setInitiative(15);
        combatant.setSortOrder(0);
        combatant.setMaxHp(7);
        combatant.setCurrentHp(7);
        combatant.setKind("MONSTER");

        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(combatant));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        var dto = manifest.encounters().get(0);
        assertThat(dto.combatants()).hasSize(1);
        assertThat(dto.combatants().get(0).name()).isEqualTo("Goblin");
    }

    @Test
    void exportsCombatLogWhenIncluded() {
        var encounter = createEncounter("Encounter", Encounter.Status.DONE, 5, 2);
        var combatantId = UUID.randomUUID();
        var combatant = new Combatant();
        combatant.setId(combatantId);
        combatant.setName("Hero");
        combatant.setInitiative(20);
        combatant.setSortOrder(0);
        combatant.setMaxHp(30);
        combatant.setCurrentHp(30);
        combatant.setKind("PC");

        var logEntry = new CombatLogEntry();
        logEntry.setId(UUID.randomUUID());
        logEntry.setEncounter(encounter);
        logEntry.setRound(1);
        logEntry.setSequence(1);
        logEntry.setType(CombatLogEntry.EntryType.TURN_START);
        logEntry.setCombatantId(combatantId.toString());
        logEntry.setPayload("{\"combatantId\":\"" + combatantId + "\"}");
        logEntry.setCreatedAt(Instant.now());

        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(combatant));
        when(combatLogEntryRepository.findByEncounterIdOrderBySequenceAsc(encounter.getId()))
                .thenReturn(List.of(logEntry));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        var dto = manifest.encounters().get(0);
        assertThat(dto.combatLog()).hasSize(1);
        assertThat(dto.combatLog().get(0).type()).isEqualTo("TURN_START");
        assertThat(dto.combatLog().get(0).round()).isEqualTo(1);
    }

    @Test
    void excludesCombatLogWhenOptionIsFalse() {
        var encounter = createEncounter("Encounter", Encounter.Status.DONE, 1, 0);
        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of());

        var ctx = exportContext(new CampaignExportOptions(false, true));
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.encounters().get(0).combatLog()).isEmpty();
    }

    private Encounter createEncounter(String name, Encounter.Status status, int round, int activeTurnIndex) {
        var e = new Encounter();
        e.setId(UUID.randomUUID());
        e.setCampaign(campaign);
        e.setName(name);
        e.setStatus(status);
        e.setRound(round);
        e.setActiveTurnIndex(activeTurnIndex);
        e.setLogSequence(0);
        return e;
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
        a.party(List.of());
        a.customStatBlocks(List.of());
        a.handouts(List.of());
        a.maps(List.of());
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.diceRolls(List.of());
        a.campaign(new CampaignManifestV2.CampaignDto("key", "test", null, null, null, null));
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
