package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import dev.hendrikhoemberg.dmhelper.world.packagev2.WorldSectionAdapter;
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
class WorldSectionAdapterTest {

    @Mock WorldNpcRepository npcRepo;
    @Mock WorldLocationRepository locationRepo;
    @Mock FactionRepository factionRepo;
    @Mock WorldRelationshipRepository relationshipRepo;
    @Mock FactionClockRepository clockRepo;
    @Mock StatBlockReferenceResolver statBlockResolver;

    private WorldSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new WorldSectionAdapter(npcRepo, locationRepo, factionRepo, relationshipRepo, clockRepo,
                statBlockResolver);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder850() {
        assertThat(adapter.order()).isEqualTo(850);
    }

    @Test
    void sectionNameIsWorld() {
        assertThat(adapter.sectionName()).isEqualTo("World");
    }

    @Test
    void handlesEmptyWorldData() {
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.factions()).isEmpty();
        assertThat(manifest.worldLocations()).isEmpty();
        assertThat(manifest.worldNpcs()).isEmpty();
        assertThat(manifest.worldRelationships()).isEmpty();
        assertThat(manifest.factionClocks()).isEmpty();
    }

    @Test
    void exportsFactions() {
        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());
        faction.setCampaign(campaign);
        faction.setName("The Syndicate");
        faction.setGoals("Control the underworld");

        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(faction));
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.factions()).hasSize(1);
        var dto = manifest.factions().get(0);
        assertThat(dto.name()).isEqualTo("The Syndicate");
        assertThat(dto.goals()).isEqualTo("Control the underworld");
    }

    @Test
    void exportsLocations() {
        WorldLocation location = new WorldLocation();
        location.setId(UUID.randomUUID());
        location.setCampaign(campaign);
        location.setName("Dark Alley");
        location.setKind(LocationKind.SITE);
        location.setSecrets("Hidden treasure");

        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(location));
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.worldLocations()).hasSize(1);
        var dto = manifest.worldLocations().get(0);
        assertThat(dto.name()).isEqualTo("Dark Alley");
        assertThat(dto.kind()).isEqualTo("SITE");
        assertThat(dto.secrets()).isEqualTo("Hidden treasure");
        assertThat(dto.occupantNpcRefs()).isEmpty();
        assertThat(dto.encounterRefs()).isEmpty();
        assertThat(dto.travelLocationRefs()).isEmpty();
    }

    @Test
    void exportsLocationOccupantsEncountersAndTravel() {
        WorldLocation harbor = new WorldLocation();
        harbor.setId(UUID.randomUUID());
        harbor.setCampaign(campaign);
        harbor.setName("Harbor");
        harbor.setKind(LocationKind.SETTLEMENT);

        WorldLocation road = new WorldLocation();
        road.setId(UUID.randomUUID());
        road.setCampaign(campaign);
        road.setName("Coast Road");
        road.setKind(LocationKind.SITE);
        harbor.getTravelLocations().add(road);

        WorldNpc mira = new WorldNpc();
        mira.setId(UUID.randomUUID());
        mira.setCampaign(campaign);
        mira.setName("Mira");
        mira.setStatus(WorldNpcStatus.ALIVE);
        mira.setLocation(harbor);

        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(harbor, road));
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(mira));
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var harborDto = manifest.worldLocations().stream()
                .filter(l -> "Harbor".equals(l.name())).findFirst().orElseThrow();
        assertThat(harborDto.occupantNpcRefs()).hasSize(1);
        assertThat(harborDto.occupantNpcRefs().getFirst().type()).isEqualTo(CampaignContentType.WORLD_NPC);
        assertThat(harborDto.travelLocationRefs()).hasSize(1);
        assertThat(harborDto.travelLocationRefs().getFirst().type()).isEqualTo(CampaignContentType.WORLD_LOCATION);
    }

    @Test
    void exportsNpcsWithRefs() {
        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());
        faction.setCampaign(campaign);
        faction.setName("The Syndicate");

        WorldLocation location = new WorldLocation();
        location.setId(UUID.randomUUID());
        location.setCampaign(campaign);
        location.setName("Dark Alley");

        WorldNpc npc = new WorldNpc();
        npc.setId(UUID.randomUUID());
        npc.setCampaign(campaign);
        npc.setName("Shadowy Figure");
        npc.setRole("Informant");
        npc.setDisposition(WorldDisposition.NEUTRAL);
        npc.setSecret("I am the Guild Master");
        npc.setStatus(WorldNpcStatus.ALIVE);
        npc.setFaction(faction);
        npc.setLocation(location);
        npc.setTags("criminal,informant");

        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(npc));
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.worldNpcs()).hasSize(1);
        var dto = manifest.worldNpcs().get(0);
        assertThat(dto.name()).isEqualTo("Shadowy Figure");
        assertThat(dto.secret()).isEqualTo("I am the Guild Master");
        assertThat(dto.factionRef()).isNotNull();
        assertThat(dto.locationRef()).isNotNull();
    }

    @Test
    void exportsRelationships() {
        WorldRelationship rel = new WorldRelationship();
        rel.setId(UUID.randomUUID());
        rel.setCampaign(campaign);
        rel.setKind(RelationshipKind.ALLY);
        rel.setFromType("FACTION");
        rel.setFromId(UUID.randomUUID());
        rel.setToType("FACTION");
        rel.setToId(UUID.randomUUID());

        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of(rel));
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.worldRelationships()).hasSize(1);
        var dto = manifest.worldRelationships().get(0);
        assertThat(dto.kind()).isEqualTo("ALLY");
        assertThat(dto.fromRef()).isNotNull();
        assertThat(dto.toRef()).isNotNull();
    }

    @Test
    void exportsClocks() {
        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());
        faction.setCampaign(campaign);
        faction.setName("The Syndicate");

        FactionClock clock = new FactionClock();
        clock.setId(UUID.randomUUID());
        clock.setCampaign(campaign);
        clock.setFaction(faction);
        clock.setTitle("Rival Rise");
        clock.setSegments(6);
        clock.setFilled(3);

        when(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of(clock));
        when(factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of(faction));
        when(locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(npcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId)).thenReturn(List.of());
        when(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = exportContext(keyService);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.factionClocks()).hasSize(1);
        var dto = manifest.factionClocks().get(0);
        assertThat(dto.title()).isEqualTo("Rival Rise");
        assertThat(dto.segments()).isEqualTo(6);
        assertThat(dto.filled()).isEqualTo(3);
        assertThat(dto.factionRef()).isNotNull();
    }

    @Test
    void importsFactionAndBindsKey() {
        var factionDto = new CampaignManifestV2.FactionDto(
                "faction-key", "Test Faction", "Goals", "Resources", "Notes", null, List.of("tag1"), null, null);

        when(factionRepo.save(any())).thenAnswer(inv -> {
            Faction f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        var manifest = manifestWith(factionDto);
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = importContext(keys);

        adapter.importSection(manifest, context);

        assertThat(keys.bindings).containsValue("faction-key");
    }

    @Test
    void importsLocationAndBindsKey() {
        var locationDto = new CampaignManifestV2.WorldLocationDto(
                "loc-key", "Test Location", "SITE", null, null, null, null,
                "Summary", "Services", "Secrets", null, null, null, null, null, null);

        when(locationRepo.save(any())).thenAnswer(inv -> {
            WorldLocation l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        var manifest = manifestWith(locationDto);
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = importContext(keys);

        adapter.importSection(manifest, context);

        assertThat(keys.bindings).containsValue("loc-key");
    }

    @Test
    void importsNpcAndResolvesFactionRef() {
        var factionRef = ContentReference.packageRef(CampaignContentType.FACTION, "faction-key");
        var npcDto = new CampaignManifestV2.WorldNpcDto(
                "npc-key", "Test NPC", null, null, factionRef, null, null, null,
                null, null, null, null, null, null, null, null, null);

        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());

        when(npcRepo.save(any())).thenAnswer(inv -> {
            WorldNpc n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        var manifest = manifestWith(npcDto);
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = importContext(keys);

        adapter.importSection(manifest, context);

        context.register(CampaignContentType.FACTION, "faction-key", faction, faction.getId());
        context.runDeferred();

        assertThat(keys.bindings).containsValue("npc-key");
    }

    @Test
    void importsRelationshipAndResolvesRefs() {
        var fromRef = ContentReference.packageRef(CampaignContentType.WORLD_NPC, "npc-key");
        var toRef = ContentReference.packageRef(CampaignContentType.FACTION, "faction-key");
        var relDto = new CampaignManifestV2.WorldRelationshipDto(
                "rel-key", "ALLY", fromRef, toRef, true, "PUBLIC", "ACTIVE", null, null, 0);

        WorldNpc npc = new WorldNpc();
        npc.setId(UUID.randomUUID());
        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());

        when(relationshipRepo.save(any())).thenAnswer(inv -> {
            WorldRelationship r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        var manifest = manifestWith(relDto);
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = importContext(keys);

        adapter.importSection(manifest, context);

        context.register(CampaignContentType.WORLD_NPC, "npc-key", npc, npc.getId());
        context.register(CampaignContentType.FACTION, "faction-key", faction, faction.getId());
        context.runDeferred();

        assertThat(keys.bindings).containsValue("rel-key");
    }

    @Test
    void importsClockAndResolvesFactionRef() {
        var factionRef = ContentReference.packageRef(CampaignContentType.FACTION, "faction-key");
        var clockDto = new CampaignManifestV2.FactionClockDto(
                "clock-key", factionRef, "Test Clock", 6, 3, null, null, null, null, 0);

        Faction faction = new Faction();
        faction.setId(UUID.randomUUID());

        when(clockRepo.save(any())).thenAnswer(inv -> {
            FactionClock c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var manifest = manifestWith(clockDto);
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = importContext(keys);

        adapter.importSection(manifest, context);

        context.register(CampaignContentType.FACTION, "faction-key", faction, faction.getId());
        context.runDeferred();

        assertThat(keys.bindings).containsValue("clock-key");
    }

    private CampaignExportContext exportContext(CampaignSectionAdapterTest.FakeKeyService keyService) {
        return new CampaignExportContext(
                campaignId, campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
    }

    private CampaignImportContext importContext(CampaignSectionAdapterTest.FakeKeyService keys) {
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);
        return context;
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
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private CampaignManifestV2 manifestWith(Object... worldDtos) {
        List<CampaignManifestV2.WorldNpcDto> npcs = List.of();
        List<CampaignManifestV2.WorldLocationDto> locations = List.of();
        List<CampaignManifestV2.FactionDto> factions = List.of();
        List<CampaignManifestV2.WorldRelationshipDto> relationships = List.of();
        List<CampaignManifestV2.FactionClockDto> clocks = List.of();
        for (var dto : worldDtos) {
            if (dto instanceof CampaignManifestV2.WorldNpcDto n) npcs = List.of(n);
            else if (dto instanceof CampaignManifestV2.WorldLocationDto l) locations = List.of(l);
            else if (dto instanceof CampaignManifestV2.FactionDto f) factions = List.of(f);
            else if (dto instanceof CampaignManifestV2.WorldRelationshipDto r) relationships = List.of(r);
            else if (dto instanceof CampaignManifestV2.FactionClockDto c) clocks = List.of(c);
        }
        List<CampaignManifestV2.AdventureDto> noAdv = List.of();
        List<CampaignManifestV2.QuestDto> noQ = List.of();
        List<CampaignManifestV2.SourceAnnotationDto> noAnn = List.of();
        List<CampaignManifestV2.HandoutDto> noHand = List.of();
        List<CampaignManifestV2.MapDto> noMap = List.of();
        List<CampaignManifestV2.EncounterDto> noEnc = List.of();
        List<CampaignManifestV2.NoteDto> noNote = List.of();
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
        List<CampaignManifestV2.QuickNoteDto> noQn = List.of();
        List<CampaignManifestV2.AssignmentDto> noAssign = List.of();
        List<CampaignManifestV2.LedgerEntryDto> noLedger = List.of();
        List<CampaignManifestV2.TimelineEventDto> noTimeline = List.of();
        List<CampaignManifestV2.DiceRollDto> noDice = List.of();
        List<AssetDescriptor> noAssets = List.of();
        return new CampaignManifestV2(
                2, null, null, noAssets, noParty,
                noSb, noSpell, noCond, noRule, noEquip, noMagic, noClass, noSpecies, noBg, noFeat,
                noHand, noMap, noEnc, noNote, noQn, noAssign, noLedger, noTimeline,
                noAdv, null, noDice, noQ, noAnn,
                npcs, locations, factions, relationships, clocks);
    }
}
