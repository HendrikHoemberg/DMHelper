package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.adventure.packagev2.AdventureSectionAdapter;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ChapterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdventureSectionAdapterTest {

    private AdventureRepository adventureRepo;
    private ChapterRepository chapterRepo;
    private SceneRepository sceneRepo;
    private StatBlockRepository statBlockRepository;
    private AdventureSectionAdapter adapter;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        adventureRepo = mock(AdventureRepository.class);
        chapterRepo = mock(ChapterRepository.class);
        sceneRepo = mock(SceneRepository.class);
        statBlockRepository = mock(StatBlockRepository.class);
        adapter = new AdventureSectionAdapter(
                adventureRepo, chapterRepo, sceneRepo,
                new StatBlockReferenceResolver(
                        new dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver(
                                mock(dev.hendrikhoemberg.dmhelper.library.data.SpellRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.FeatRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository.class),
                                mock(dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository.class),
                                statBlockRepository)));
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder900() {
        assertThat(adapter.order()).isEqualTo(900);
    }

    @Test
    void sectionNameIsAdventure() {
        assertThat(adapter.sectionName()).isEqualTo("Adventure");
    }

    @Test
    void exportsAdventuresWithChaptersAndScenes() {
        UUID advId = UUID.randomUUID();
        Adventure adv = adventure(advId, "Crypt Descent", "desc", "source", 0, Instant.parse("2025-06-01T12:00:00Z"));

        UUID chId = UUID.randomUUID();
        Chapter ch = chapter(chId, adv, "Chapter 1", "intro", 0);

        UUID scId = UUID.randomUUID();
        Scene sc = scene(scId, ch, "Crypt Entry", "body text", SceneStatus.UNVISITED, 0);

        when(adventureRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaign.getId()))
                .thenReturn(List.of(adv));
        when(chapterRepo.findByAdventureIdOrderBySortOrderAscIdAsc(advId))
                .thenReturn(List.of(ch));
        when(sceneRepo.findByChapterIdOrderBySortOrderAscIdAsc(chId))
                .thenReturn(List.of(sc));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.adventures()).hasSize(1);
        var advDto = manifest.adventures().get(0);
        assertThat(advDto.name()).isEqualTo("Crypt Descent");
        assertThat(advDto.description()).isEqualTo("desc");
        assertThat(advDto.sourceAttribution()).isEqualTo("source");
        assertThat(advDto.sortOrder()).isEqualTo(0);
        assertThat(advDto.createdAt()).isEqualTo(Instant.parse("2025-06-01T12:00:00Z"));

        assertThat(advDto.chapters()).hasSize(1);
        var chDto = advDto.chapters().get(0);
        assertThat(chDto.title()).isEqualTo("Chapter 1");
        assertThat(chDto.intro()).isEqualTo("intro");
        assertThat(chDto.sortOrder()).isEqualTo(0);

        assertThat(chDto.scenes()).hasSize(1);
        var scDto = chDto.scenes().get(0);
        assertThat(scDto.title()).isEqualTo("Crypt Entry");
        assertThat(scDto.body()).isEqualTo("body text");
        assertThat(scDto.status()).isEqualTo("UNVISITED");
        assertThat(scDto.sortOrder()).isEqualTo(0);
    }

    @Test
    void exportsSceneWithMapEncounterStatblocksHandoutsAndPin() {
        UUID advId = UUID.randomUUID();
        Adventure adv = adventure(advId, "Adv", null, null, 0, null);

        UUID chId = UUID.randomUUID();
        Chapter ch = chapter(chId, adv, "Ch1", null, 0);

        UUID mapId = UUID.randomUUID();
        GameMap map = mock(GameMap.class);
        when(map.getId()).thenReturn(mapId);
        when(map.getName()).thenReturn("The Crypt");

        UUID encId = UUID.randomUUID();
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn(encId);
        when(enc.getName()).thenReturn("Guardians");

        UUID sbId = UUID.randomUUID();
        StatBlock sb = mock(StatBlock.class);
        when(sb.getId()).thenReturn(sbId);
        when(sb.getName()).thenReturn("Goblin");
        when(sb.getSource()).thenReturn(ContentSource.SRD);
        when(sb.getSourceKey()).thenReturn("srd-2024_goblin");

        UUID hId = UUID.randomUUID();
        Handout h = mock(Handout.class);
        when(h.getId()).thenReturn(hId);
        when(h.getTitle()).thenReturn("Plaque");

        UUID scId = UUID.randomUUID();
        Scene sc = scene(scId, ch, "Entry", null, SceneStatus.VISITED, 0);
        sc.setMap(map);
        sc.setEncounter(enc);
        sc.setPinX(100);
        sc.setPinY(200);
        sc.getStatBlocks().add(sb);
        sc.getHandouts().add(h);

        when(adventureRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaign.getId()))
                .thenReturn(List.of(adv));
        when(chapterRepo.findByAdventureIdOrderBySortOrderAscIdAsc(advId))
                .thenReturn(List.of(ch));
        when(sceneRepo.findByChapterIdOrderBySortOrderAscIdAsc(chId))
                .thenReturn(List.of(sc));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var scDto = manifest.adventures().get(0).chapters().get(0).scenes().get(0);
        assertThat(scDto.status()).isEqualTo("VISITED");
        assertThat(scDto.mapRef()).isNotNull();
        assertThat(scDto.mapRef().type()).isEqualTo(CampaignContentType.MAP);
        assertThat(scDto.encounterRef()).isNotNull();
        assertThat(scDto.encounterRef().type()).isEqualTo(CampaignContentType.ENCOUNTER);
        assertThat(scDto.pin()).containsEntry("x", 100).containsEntry("y", 200);
        assertThat(scDto.statblockRefs()).hasSize(1);
        assertThat(scDto.statblockRefs().get(0)).isEqualTo(
                ContentReference.catalogRef(CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin"));
        assertThat(scDto.handoutRefs()).hasSize(1);
        assertThat(scDto.handoutRefs().get(0).type()).isEqualTo(CampaignContentType.HANDOUT);
    }

    @Test
    void importsAdventuresWithChaptersAndScenes() {
        var scenes = List.of(new SceneDto("sc-key", "Room 1", "body", "DONE", 3,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        var chapters = List.of(new ChapterDto("ch-key", "Chapter X", "intro text", 2, scenes));
        var adventures = List.of(new AdventureDto("adv-key", "Imported Adv", "desc", "src", 1, chapters, null));
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                adventures, null, null, List.of(), List.of()
        );

        when(adventureRepo.save(any())).thenAnswer(inv -> {
            Adventure a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        when(chapterRepo.save(any())).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(sceneRepo.save(any())).thenAnswer(inv -> {
            Scene s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var context = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        context.setCampaign(freshCampaign);
        context.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, context);

        verify(adventureRepo).save(argThat(a ->
                a.getName().equals("Imported Adv") &&
                        a.getDescription().equals("desc") &&
                        a.getSourceAttribution().equals("src") &&
                        a.getSortOrder() == 1
        ));
        verify(chapterRepo).save(argThat(c ->
                c.getTitle().equals("Chapter X") &&
                        c.getIntro().equals("intro text") &&
                        c.getSortOrder() == 2
        ));
        verify(sceneRepo).save(argThat(s ->
                s.getTitle().equals("Room 1") &&
                        s.getBody().equals("body") &&
                        s.getStatus() == SceneStatus.DONE &&
                        s.getSortOrder() == 3
        ));
    }

    @Test
    void deferredSceneRelationsResolveMapEncounterStatblocksAndHandouts() {
        var sceneStatblockRefs = List.of(ContentReference.packageRef(CampaignContentType.STATBLOCK, "sb-key"));
        var sceneHandoutRefs = List.of(ContentReference.packageRef(CampaignContentType.HANDOUT, "h-key"));
        var scenes = List.of(new SceneDto("sc-key", "Room 1", null, null, 0,
                ContentReference.packageRef(CampaignContentType.MAP, "map-key"),
                Map.of("x", 50, "y", 100),
                ContentReference.packageRef(CampaignContentType.ENCOUNTER, "enc-key"),
                sceneStatblockRefs, sceneHandoutRefs,
                null, null, null, null, null, null, null, null, null));
        var chapters = List.of(new ChapterDto("ch-key", "Ch1", null, 0, scenes));
        var adventures = List.of(new AdventureDto("adv-key", "Adv", null, null, 0, chapters, null));
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                adventures, null, null, List.of(), List.of()
        );

        when(adventureRepo.save(any())).thenAnswer(inv -> {
            Adventure a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        when(chapterRepo.save(any())).thenAnswer(inv -> {
            Chapter c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(sceneRepo.save(any())).thenAnswer(inv -> {
            Scene s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        UUID mapId = UUID.randomUUID();
        GameMap map = mock(GameMap.class);
        when(map.getId()).thenReturn(mapId);

        UUID encId = UUID.randomUUID();
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn(encId);

        UUID sbId = UUID.randomUUID();
        StatBlock sb = mock(StatBlock.class);
        when(sb.getId()).thenReturn(sbId);

        UUID hId = UUID.randomUUID();
        Handout h = mock(Handout.class);
        when(h.getId()).thenReturn(hId);

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var context = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        context.setCampaign(freshCampaign);
        context.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());
        context.register(CampaignContentType.MAP, "map-key", map, mapId);
        context.register(CampaignContentType.ENCOUNTER, "enc-key", enc, encId);
        context.register(CampaignContentType.STATBLOCK, "sb-key", sb, sbId);
        context.register(CampaignContentType.HANDOUT, "h-key", h, hId);

        adapter.importSection(manifest, context);

        context.runDeferred();

        verify(sceneRepo, times(2)).save(any());
        verify(sceneRepo, atLeastOnce()).save(argThat(s ->
                s.getMap() == map &&
                        s.getEncounter() == enc &&
                        s.getStatBlocks().contains(sb) &&
                        s.getHandouts().contains(h) &&
                        s.getPinX() == 50 &&
                        s.getPinY() == 100
        ));
    }

    @Test
    void keyStableThroughExportRoundTrip() {
        UUID advId = UUID.randomUUID();
        Adventure adv = adventure(advId, "Stable Adventure", null, null, 0, null);

        UUID chId = UUID.randomUUID();
        Chapter ch = chapter(chId, adv, "Stable Chapter", null, 0);

        UUID scId = UUID.randomUUID();
        Scene sc = scene(scId, ch, "Stable Scene", null, SceneStatus.UNVISITED, 0);

        when(adventureRepo.findByCampaignIdOrderBySortOrderAscIdAsc(campaign.getId()))
                .thenReturn(List.of(adv));
        when(chapterRepo.findByAdventureIdOrderBySortOrderAscIdAsc(advId))
                .thenReturn(List.of(ch));
        when(sceneRepo.findByChapterIdOrderBySortOrderAscIdAsc(chId))
                .thenReturn(List.of(sc));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);
        String exportedAdvKey = manifest.adventures().get(0).key();
        String exportedChKey = manifest.adventures().get(0).chapters().get(0).key();
        String exportedScKey = manifest.adventures().get(0).chapters().get(0).scenes().get(0).key();

        var keyService2 = new CampaignSectionAdapterTest.FakeKeyService();
        keyService2.bindings.putAll(keyService.bindings);
        var ctx2 = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService2, new CampaignAssetCollector());
        var assembler2 = new CampaignManifestAssembler();
        assembler2.assets(List.of());
        adapter.exportSection(ctx2, assembler2);
        fillRest(assembler2);
        var manifest2 = buildManifest(assembler2);

        assertThat(manifest2.adventures().get(0).key()).isEqualTo(exportedAdvKey);
        assertThat(manifest2.adventures().get(0).chapters().get(0).key()).isEqualTo(exportedChKey);
        assertThat(manifest2.adventures().get(0).chapters().get(0).scenes().get(0).key()).isEqualTo(exportedScKey);
    }

    private Adventure adventure(UUID id, String name, String description,
                                 String sourceAttribution, int sortOrder, Instant createdAt) {
        Adventure adv = new Adventure();
        adv.setId(id);
        adv.setCampaign(campaign);
        adv.setName(name);
        adv.setDescription(description);
        adv.setSourceAttribution(sourceAttribution);
        adv.setSortOrder(sortOrder);
        if (createdAt != null) adv.setCreatedAt(createdAt);
        return adv;
    }

    private Chapter chapter(UUID id, Adventure adventure, String title, String intro, int sortOrder) {
        Chapter ch = new Chapter();
        ch.setId(id);
        ch.setAdventure(adventure);
        ch.setTitle(title);
        ch.setIntro(intro);
        ch.setSortOrder(sortOrder);
        return ch;
    }

    private Scene scene(UUID id, Chapter chapter, String title, String body, SceneStatus status, int sortOrder) {
        Scene sc = new Scene();
        sc.setId(id);
        sc.setChapter(chapter);
        sc.setTitle(title);
        sc.setBody(body);
        sc.setStatus(status);
        sc.setSortOrder(sortOrder);
        return sc;
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
        a.session(null);
        a.diceRolls(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
