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
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWave;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.encounter.packagev2.EncounterSectionAdapter;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterSectionAdapterTest {

    @Mock EncounterRepository encounterRepository;
    @Mock CombatantRepository combatantRepository;
    @Mock CombatLogEntryRepository combatLogEntryRepository;
    @Mock EncounterWaveRepository waveRepository;
    @Mock StatBlockRepository statBlockRepository;
    StatBlockReferenceResolver statBlockResolver;

    private EncounterSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        statBlockResolver = new StatBlockReferenceResolver(
                new dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver(
                        mock(dev.hendrikhoemberg.dmhelper.library.data.SpellRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.FeatRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository.class),
                        mock(dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository.class),
                        statBlockRepository));
        adapter = new EncounterSectionAdapter(
                encounterRepository, combatantRepository, combatLogEntryRepository,
                waveRepository, statBlockResolver);
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
        var srd = new StatBlock();
        srd.setId(UUID.randomUUID());
        srd.setSource(ContentSource.SRD);
        srd.setSourceKey("srd-2024_goblin");
        srd.setName("Goblin");
        combatant.setStatBlock(srd);

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
        assertThat(dto.combatants().get(0).statBlockRef()).isEqualTo(
                dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference.catalogRef(
                        CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin"));
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
    void exportsWavesAndPrepAndRewards() {
        var encounter = createEncounter("Wave Encounter", Encounter.Status.ACTIVE, 1, 0);
        encounter.setPrepJson("{\"tactics\":\"flank\",\"morale\":\"flee at half\",\"sceneKey\":\"goblin-ambush\"}");
        encounter.setRewardsJson("{\"xpTotal\":500,\"xpPerPc\":100,\"notes\":\"Test rewards\"}");
        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of());

        var wave = new EncounterWave();
        wave.setId(UUID.randomUUID());
        wave.setEncounter(encounter);
        wave.setWaveKey("wave-1");
        wave.setName("First Wave");
        wave.setSortOrder(0);
        wave.setStatus(WaveStatus.PENDING);
        wave.setTriggerKind(WaveTriggerKind.MANUAL);
        wave.setNotes("Wait for signal");
        when(waveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(wave));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.encounters()).hasSize(1);
        var dto = manifest.encounters().get(0);
        assertThat(dto.waves()).hasSize(1);
        assertThat(dto.waves().get(0).name()).isEqualTo("First Wave");
        assertThat(dto.waves().get(0).status()).isEqualTo("PENDING");
        assertThat(dto.waves().get(0).triggerKind()).isEqualTo("MANUAL");
        assertThat(dto.prep()).isNotNull();
        assertThat(dto.prep().tactics()).isEqualTo("flank");
        assertThat(dto.prep().sceneKey()).isEqualTo("goblin-ambush");
        assertThat(dto.rewards()).isNotNull();
        assertThat(dto.rewards().xpTotal()).isEqualTo(500);
        assertThat(dto.rewards().xpPerPc()).isEqualTo(100);
    }

    @Test
    void exportsCombatantWaveKeyAndPlacementFields() {
        var encounter = createEncounter("Placed Encounter", Encounter.Status.ACTIVE, 1, 0);
        var wave = new EncounterWave();
        wave.setId(UUID.randomUUID());
        wave.setEncounter(encounter);
        wave.setWaveKey("main-wave");
        wave.setName("Main");
        wave.setSortOrder(0);
        wave.setStatus(WaveStatus.ACTIVE);
        wave.setTriggerKind(WaveTriggerKind.MANUAL);
        when(waveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(wave));

        var combatant = new Combatant();
        combatant.setId(UUID.randomUUID());
        combatant.setEncounter(encounter);
        combatant.setName("Placed Goblin");
        combatant.setInitiative(10);
        combatant.setSortOrder(0);
        combatant.setMaxHp(7);
        combatant.setCurrentHp(7);
        combatant.setKind("MONSTER");
        combatant.setWave(wave);
        combatant.setStartX(5);
        combatant.setStartY(8);
        combatant.setPlacementRegionKey("room-1");

        when(encounterRepository.findByCampaignIdOrderByNameAsc(campaignId))
                .thenReturn(List.of(encounter));
        when(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .thenReturn(List.of(combatant));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        var dto = manifest.encounters().get(0);
        assertThat(dto.waves()).hasSize(1);
        assertThat(dto.combatants()).hasSize(1);
        var cDto = dto.combatants().get(0);
        assertThat(cDto.waveKey()).isNotNull();
        assertThat(cDto.startX()).isEqualTo(5);
        assertThat(cDto.startY()).isEqualTo(8);
        assertThat(cDto.placementRegionKey()).isEqualTo("room-1");
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

    @Test
    void importBindsThePackageKeyForThePersistedCombatLogEntry() {
        var logDto = new CombatLogEntryDto(
                "opening-turn", 1, 1, "TURN_START", null,
                tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode(),
                Instant.parse("2025-06-01T12:00:00Z"));
        var encounterDto = new EncounterDto(
                "ambush", "Ambush", List.of(), "ACTIVE",
                1, 0, 1, null, null, null, false, List.of(logDto),
                null, null, null);
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null,
                List.of(encounterDto),
                null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        when(encounterRepository.save(any())).thenAnswer(invocation -> {
            var encounter = invocation.getArgument(0, Encounter.class);
            encounter.setId(UUID.randomUUID());
            return encounter;
        });
        when(combatLogEntryRepository.save(any())).thenAnswer(invocation -> {
            var log = invocation.getArgument(0, CombatLogEntry.class);
            log.setId(UUID.randomUUID());
            return log;
        });

        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(campaignId, keys, pendingImport());
        context.setCampaign(campaign);
        adapter.importSection(manifest, context);

        assertThat(keys.bindings).containsValue("opening-turn");
    }

    @Test
    void importsCatalogStatBlockReference() {
        var srdRef = dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference.catalogRef(
                CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin");
        var srd = new StatBlock();
        srd.setId(UUID.randomUUID());
        srd.setSource(ContentSource.SRD);
        srd.setSourceKey("srd-2024_goblin");

        var combatantDto = new CombatantDto(
                "goblin", "Goblin", 12, 0, 0, 7, 7, 0,
                "MONSTER", null, false, null, srdRef, null,
                false, false, null, null, false,
                0, 0, 0, 0, null, null,
                null, null, null, null, null);
        var encounterDto = new EncounterDto(
                "ambush", "Ambush", List.of(combatantDto), "PLANNED",
                0, -1, 0, null, null, null, false, List.of(),
                null, null, null);
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null,
                List.of(encounterDto),
                null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        when(encounterRepository.save(any())).thenAnswer(invocation -> {
            var encounter = invocation.getArgument(0, Encounter.class);
            encounter.setId(UUID.randomUUID());
            return encounter;
        });
        when(combatantRepository.save(any())).thenAnswer(invocation -> {
            var combatant = invocation.getArgument(0, Combatant.class);
            combatant.setId(UUID.randomUUID());
            return combatant;
        });
        when(statBlockRepository.findBySourceAndSourceKey(
                ContentSource.SRD, "srd-2024_goblin")).thenReturn(java.util.Optional.of(srd));

        var context = new CampaignImportContext(
                campaignId, new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        context.setCampaign(campaign);
        adapter.importSection(manifest, context);
        context.runDeferred();

        org.mockito.Mockito.verify(combatantRepository).save(org.mockito.ArgumentMatchers.argThat(
                combatant -> combatant.getStatBlock() == srd));
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
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
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
