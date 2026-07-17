package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibraryContentReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.packagev2.PartySectionAdapter;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartySectionAdapterTest {

    private PartySectionAdapter adapter;
    private PartyMemberRepository partyRepo;
    private CharacterSheetRepository sheetRepo;
    private SheetResourceRepository resourceRepo;
    private SheetSpellReferenceRepository spellRefRepo;
    private SpeciesRepository speciesRepo;
    private BackgroundRepository backgroundRepo;
    private SpellRepository spellRepo;
    private CharacterClassRepository classRepo;
    private FeatRepository featRepo;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        partyRepo = mock(PartyMemberRepository.class);
        sheetRepo = mock(CharacterSheetRepository.class);
        resourceRepo = mock(SheetResourceRepository.class);
        spellRefRepo = mock(SheetSpellReferenceRepository.class);
        speciesRepo = mock(SpeciesRepository.class);
        backgroundRepo = mock(BackgroundRepository.class);
        spellRepo = mock(SpellRepository.class);
        classRepo = mock(CharacterClassRepository.class);
        featRepo = mock(FeatRepository.class);
        var libraryRefs = new LibraryContentReferenceResolver(
                spellRepo, speciesRepo, backgroundRepo, classRepo, featRepo,
                mock(MagicItemRepository.class), mock(EquipmentItemRepository.class),
                mock(StatBlockRepository.class));
        adapter = new PartySectionAdapter(partyRepo, sheetRepo, resourceRepo, spellRefRepo, libraryRefs);
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder300() {
        assertThat(adapter.order()).isEqualTo(300);
    }

    @Test
    void sectionNameIsParty() {
        assertThat(adapter.sectionName()).isEqualTo("Party");
    }

    @Test
    void keyStableAfterRenameOnPartyMember() {
        UUID pmId = UUID.randomUUID();
        var pm = partyMember(pmId, "Legolas");
        when(partyRepo.findByCampaignIdOrderByCharacterNameAscIdAsc(campaign.getId()))
                .thenReturn(List.of(pm));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);
        String key = manifest.party().get(0).key();

        pm.setCharacterName("Legolas Greenleaf");

        var keyService2 = new CampaignSectionAdapterTest.FakeKeyService();
        keyService2.bindings.putAll(keyService.bindings);
        var assembler2 = new CampaignManifestAssembler();
        assembler2.assets(List.of());
        var ctx2 = exportContext(keyService2);
        adapter.exportSection(ctx2, assembler2);
        fillRest(assembler2);
        var manifest2 = buildManifest(assembler2);

        assertThat(manifest2.party().get(0).key()).isEqualTo(key);
    }

    @Test
    void importsFullPartyWithSheetAndResourcesAndSpells() {
        var species = new Species();
        species.setSource(ContentSource.SRD);
        species.setId(UUID.randomUUID());
        species.setSourceKey("human");
        species.setName("Human");

        var background = new Background();
        background.setSource(ContentSource.SRD);
        background.setId(UUID.randomUUID());
        background.setSourceKey("soldier");
        background.setName("Soldier");

        var spell1 = new Spell();
        spell1.setSource(ContentSource.SRD);
        spell1.setId(UUID.randomUUID());
        spell1.setSourceKey("fire-bolt");
        spell1.setName("Fire Bolt");

        var spell2 = new Spell();
        spell2.setSource(ContentSource.SRD);
        spell2.setId(UUID.randomUUID());
        spell2.setSourceKey("shield");
        spell2.setName("Shield");

        when(speciesRepo.findBySourceAndSourceKey(ContentSource.SRD, "human")).thenReturn(Optional.of(species));
        when(backgroundRepo.findBySourceAndSourceKey(ContentSource.SRD, "soldier")).thenReturn(Optional.of(background));
        when(spellRepo.findBySourceAndSourceKey(ContentSource.SRD, "fire-bolt")).thenReturn(Optional.of(spell1));
        when(spellRepo.findBySourceAndSourceKey(ContentSource.SRD, "shield")).thenReturn(Optional.of(spell2));
        when(classRepo.findBySourceAndSourceKey(ContentSource.SRD, "fighter")).thenReturn(Optional.of(srdClass("fighter")));
        when(classRepo.findBySourceAndSourceKey(ContentSource.SRD, "wizard")).thenReturn(Optional.of(srdClass("wizard")));
        when(featRepo.findBySourceAndSourceKey(ContentSource.SRD, "tough")).thenReturn(Optional.of(srdFeat("tough")));
        when(featRepo.findBySourceAndSourceKey(ContentSource.SRD, "alert")).thenReturn(Optional.of(srdFeat("alert")));

        when(partyRepo.save(any())).thenAnswer(inv -> {
            var pm = inv.getArgument(0, PartyMember.class);
            if (pm.getId() == null) pm.setId(UUID.randomUUID());
            return pm;
        });
        when(sheetRepo.save(any())).thenAnswer(inv -> {
            var cs = inv.getArgument(0, CharacterSheet.class);
            if (cs.getId() == null) cs.setId(UUID.randomUUID());
            return cs;
        });
        when(resourceRepo.save(any())).thenAnswer(inv -> {
            var r = inv.getArgument(0, SheetResource.class);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(spellRefRepo.save(any())).thenAnswer(inv -> {
            var sr = inv.getArgument(0, SheetSpellReference.class);
            if (sr.getId() == null) sr.setId(UUID.randomUUID());
            return sr;
        });

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var classLevels = List.of(
                Map.of("classRef", "fighter", "level", 3),
                Map.of("classRef", "wizard", "level", 2)
        );

        var featRefKeys = List.of("tough", "alert");

        var manifest = new CampaignManifestV2(
                2, null, null, null,
                List.of(new CampaignManifestV2.PartyMemberDto(
                        "party-elara", "Elara", "Charlie", "Fighter 3 / Wizard 2",
                        16, 62, 38, 2, 30, 14, 12, 10,
                        "Has a pet owl", true,
                        new CampaignManifestV2.SheetDto(
                                "sheet-elara",
                                Map.of("str", 16, "dex", 14, "con", 14, "int", 12, "wis", 10, "cha", 8),
                                List.of(
                                        new CampaignManifestV2.ClassLevelDto(
                                                ContentReference.catalogRef(CampaignContentType.CLASS, null, "fighter"),
                                                3, List.of(10, 8)),
                                        new CampaignManifestV2.ClassLevelDto(
                                                ContentReference.catalogRef(CampaignContentType.CLASS, null, "wizard"),
                                                2, List.of(6))
                                ),
                                Map.of("skills", List.of("athletics", "arcana")),
                                ContentReference.catalogRef(CampaignContentType.SPECIES, null, "human"),
                                ContentReference.catalogRef(CampaignContentType.BACKGROUND, null, "soldier"),
                                List.of(
                                        ContentReference.catalogRef(CampaignContentType.FEAT, null, "tough"),
                                        ContentReference.catalogRef(CampaignContentType.FEAT, null, "alert")
                                ),
                                6500, Map.of("speed", 35), 2,
                                List.of(
                                        new CampaignManifestV2.ResourceDto("res-1", "Second Wind", 1, 0, "SHORT_REST"),
                                        new CampaignManifestV2.ResourceDto("res-2", "Arcane Recovery", 1, 1, "LONG_REST")
                                ),
                                List.of(
                                        new CampaignManifestV2.SpellRefDto(
                                                ContentReference.catalogRef(CampaignContentType.SPELL, null, "fire-bolt"),
                                                true,
                                                ContentReference.catalogRef(CampaignContentType.CLASS, null, "fighter")),
                                        new CampaignManifestV2.SpellRefDto(
                                                ContentReference.catalogRef(CampaignContentType.SPELL, null, "shield"),
                                                false,
                                                ContentReference.catalogRef(CampaignContentType.CLASS, null, "wizard"))
                                ),
                                Map.of("1", 2, "2", 1),
                                List.of(), List.of()
                        ),
                        null, null, null, null, null, null, null
                )),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);

        adapter.importSection(manifest, importContext);

        verify(partyRepo).save(argThat(pm ->
                pm.getCharacterName().equals("Elara") &&
                pm.getCurrentHp() == 38 &&
                pm.getMaxHp() == 62 &&
                pm.getAc() == 16
        ));

        verify(sheetRepo).save(argThat(cs ->
                cs.getAbilityScores().contains("\"str\"") &&
                cs.getSpecies() == species &&
                cs.getBackground() == background &&
                cs.getHitDiceUsed() == 2 &&
                cs.getXp() == 6500
        ));

        verify(resourceRepo, times(2)).save(any());
        verify(spellRefRepo, times(2)).save(any());
    }

    @Test
    void exportsAndImportsLiveStateFields() {
        UUID pmId = UUID.randomUUID();
        var pm = partyMember(pmId, "Elara");
        pm.setTempHp(7);
        pm.setInspiration(true);
        pm.setExhaustion(2);
        pm.setDeathSaveSuccesses(1);
        pm.setDeathSaveFailures(1);
        pm.setConcentratingOn("Bless");
        pm.setConditionsJson("[{\"sourceKey\":\"poisoned\"}]");

        when(partyRepo.findByCampaignIdOrderByCharacterNameAscIdAsc(campaign.getId()))
                .thenReturn(List.of(pm));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var dto = manifest.party().get(0);
        assertThat(dto.tempHp()).isEqualTo(7);
        assertThat(dto.inspiration()).isTrue();
        assertThat(dto.exhaustion()).isEqualTo(2);
        assertThat(dto.deathSaveSuccesses()).isEqualTo(1);
        assertThat(dto.deathSaveFailures()).isEqualTo(1);
        assertThat(dto.concentratingOn()).isEqualTo("Bless");
        assertThat(dto.conditionsJson()).isEqualTo("[{\"sourceKey\":\"poisoned\"}]");
    }

    @Test
    void importsLiveStateFieldsWithDefaultsForNull() {
        when(partyRepo.save(any())).thenAnswer(inv -> {
            var p = inv.getArgument(0, PartyMember.class);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null,
                List.of(new CampaignManifestV2.PartyMemberDto(
                        "party-test", "Test", "Dev", "Fighter 1",
                        15, 30, 30, 2, 30, 10, 10, 10,
                        null, true, null,
                        null, null, null, null, null, null, null
                )),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());
        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(partyRepo).save(argThat(pm ->
                pm.getTempHp() == 0 &&
                !pm.isInspiration() &&
                pm.getExhaustion() == 0 &&
                pm.getDeathSaveSuccesses() == 0 &&
                pm.getDeathSaveFailures() == 0 &&
                pm.getConcentratingOn() == null &&
                pm.getConditionsJson() == null
        ));
    }

    @Test
    void importsLiveStateFieldsWithValues() {
        when(partyRepo.save(any())).thenAnswer(inv -> {
            var p = inv.getArgument(0, PartyMember.class);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null,
                List.of(new CampaignManifestV2.PartyMemberDto(
                        "party-test", "Test", "Dev", "Fighter 1",
                        15, 30, 30, 2, 30, 10, 10, 10,
                        null, true, null,
                        7, true, 2, 1, 0, "Bless", "[{\"sourceKey\":\"poisoned\"}]"
                )),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());
        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(partyRepo).save(argThat(pm ->
                pm.getTempHp() == 7 &&
                pm.isInspiration() &&
                pm.getExhaustion() == 2 &&
                pm.getDeathSaveSuccesses() == 1 &&
                pm.getDeathSaveFailures() == 0 &&
                "Bless".equals(pm.getConcentratingOn()) &&
                pm.getConditionsJson() != null &&
                pm.getConditionsJson().contains("poisoned")
        ));
    }

    @Test
    void importPersistsClassSourceKeyInClob() {
        var species = new Species();
        species.setSource(ContentSource.SRD);
        species.setId(UUID.randomUUID());
        species.setSourceKey("human");
        species.setName("Human");

        var cls = srdClass("fighter");
        when(speciesRepo.findBySourceAndSourceKey(ContentSource.SRD, "human")).thenReturn(Optional.of(species));
        when(classRepo.findBySourceAndSourceKey(ContentSource.SRD, "fighter")).thenReturn(Optional.of(cls));

        when(partyRepo.save(any())).thenAnswer(inv -> {
            var pm = inv.getArgument(0, PartyMember.class);
            if (pm.getId() == null) pm.setId(UUID.randomUUID());
            return pm;
        });
        when(sheetRepo.save(any())).thenAnswer(inv -> {
            var cs = inv.getArgument(0, CharacterSheet.class);
            if (cs.getId() == null) cs.setId(UUID.randomUUID());
            return cs;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null,
                List.of(new CampaignManifestV2.PartyMemberDto(
                        "party-test", "Tester", "Dev", "Fighter 3",
                        15, 30, 30, 2, 30, 10, 10, 10,
                        null, true,
                        new CampaignManifestV2.SheetDto(
                                "sheet-test",
                                Map.of("str", 15, "dex", 14, "con", 13, "int", 10, "wis", 10, "cha", 8),
                                List.of(
                                        new CampaignManifestV2.ClassLevelDto(
                                                ContentReference.catalogRef(CampaignContentType.CLASS, null, "fighter"),
                                                3, List.of(10, 7))
                                ),
                                Map.of("skills", List.of(), "tools", List.of(), "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of()),
                                ContentReference.catalogRef(CampaignContentType.SPECIES, null, "human"),
                                null, List.of(), 0, Map.of(), 0, List.of(), List.of(), Map.of(), List.of(), List.of()
                        ),
                        null, null, null, null, null, null, null
                )),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());
        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(sheetRepo).save(argThat(cs -> {
            String cl = cs.getClassLevels();
            return cl != null
                    && cl.contains("\"classSourceKey\"")
                    && !cl.contains("\"classRef\"")
                    && cl.contains("fighter");
        }));
    }

    @Test
    void importAndExportPreserveSubclassSourceKey() {
        var fighter = srdClass("srd-2024_fighter");
        var champion = srdClass("srd-2024_fighter_champion");
        champion.setSubclassOf("srd-2024_fighter");
        when(classRepo.findBySourceAndSourceKey(ContentSource.SRD, "srd-2024_fighter"))
                .thenReturn(Optional.of(fighter));
        when(classRepo.findBySourceAndSourceKey(ContentSource.SRD, "srd-2024_fighter_champion"))
                .thenReturn(Optional.of(champion));
        when(classRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.SRD, "srd-2024_fighter"))
                .thenReturn(Optional.of(fighter));
        when(classRepo.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.SRD, "srd-2024_fighter_champion"))
                .thenReturn(Optional.of(champion));

        when(partyRepo.save(any())).thenAnswer(inv -> {
            var pm = inv.getArgument(0, PartyMember.class);
            if (pm.getId() == null) pm.setId(UUID.randomUUID());
            return pm;
        });
        when(sheetRepo.save(any())).thenAnswer(inv -> {
            var cs = inv.getArgument(0, CharacterSheet.class);
            if (cs.getId() == null) cs.setId(UUID.randomUUID());
            return cs;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null,
                List.of(new CampaignManifestV2.PartyMemberDto(
                        "party-test", "Tester", "Dev", "Fighter 3",
                        15, 30, 30, 2, 30, 10, 10, 10,
                        null, true,
                        new CampaignManifestV2.SheetDto(
                                "sheet-test",
                                Map.of("str", 15, "dex", 14, "con", 13, "int", 10, "wis", 10, "cha", 8),
                                List.of(
                                        new CampaignManifestV2.ClassLevelDto(
                                                ContentReference.catalogRef(CampaignContentType.CLASS, "SRD_5_2", "srd-2024_fighter"),
                                                3, List.of(10, 7),
                                                ContentReference.catalogRef(CampaignContentType.CLASS, "SRD_5_2", "srd-2024_fighter_champion"))
                                ),
                                Map.of(), null, null, List.of(), 0, Map.of(), 0, List.of(), List.of(), Map.of(),
                                List.of(), List.of()
                        ),
                        null, null, null, null, null, null, null
                )),
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());
        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(sheetRepo).save(argThat(cs -> {
            String cl = cs.getClassLevels();
            return cl != null
                    && cl.contains("\"classSourceKey\":\"srd-2024_fighter\"")
                    && cl.contains("\"subclassSourceKey\":\"srd-2024_fighter_champion\"");
        }));

        // Export path: sheet CLOB with subclass must emit subclassRef
        UUID pmId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        var pm = partyMember(pmId, "Champion");
        var cs = new CharacterSheet();
        cs.setId(sheetId);
        cs.setPartyMember(pm);
        cs.setClassLevels(
                "[{\"classSourceKey\":\"srd-2024_fighter\",\"subclassSourceKey\":\"srd-2024_fighter_champion\",\"level\":3,\"hitDieRolls\":[10,7]}]");
        cs.setAbilityScores("{\"str\":15,\"dex\":14,\"con\":13,\"int\":10,\"wis\":10,\"cha\":8}");
        cs.setXp(0);
        cs.setHitDiceUsed(0);
        pm.setCharacterSheet(cs);

        when(partyRepo.findByCampaignIdOrderByCharacterNameAscIdAsc(campaign.getId()))
                .thenReturn(List.of(pm));
        when(classRepo.findByCampaignIdAndSourceKey(any(), eq("srd-2024_fighter")))
                .thenReturn(Optional.empty());
        when(classRepo.findByCampaignIdAndSourceKey(any(), eq("srd-2024_fighter_champion")))
                .thenReturn(Optional.empty());
        // LibraryContentReferenceResolver.findClassForCampaign uses multiple repo methods —
        // the when() above for findBySourceAndSourceKey already covers catalog SRD lookup.

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var exported = buildManifest(assembler);

        assertThat(exported.party()).hasSize(1);
        assertThat(exported.party().get(0).sheet().classLevels()).hasSize(1);
        var level = exported.party().get(0).sheet().classLevels().get(0);
        assertThat(level.classRef().sourceKey()).isEqualTo("srd-2024_fighter");
        assertThat(level.subclassRef()).isNotNull();
        assertThat(level.subclassRef().sourceKey()).isEqualTo("srd-2024_fighter_champion");
    }

    private PartyMember partyMember(UUID id, String name) {
        var pm = new PartyMember();
        pm.setId(id);
        pm.setCampaign(campaign);
        pm.setCharacterName(name);
        pm.setPlayerName("TestPlayer");
        pm.setClassAndLevel("Fighter 1");
        pm.setAc(15);
        pm.setMaxHp(50);
        pm.setCurrentHp(50);
        pm.setInitiativeBonus(2);
        pm.setSpeed(30);
        pm.setPassivePerception(10);
        pm.setPassiveInsight(10);
        pm.setPassiveInvestigation(10);
        pm.setActive(true);
        return pm;
    }

    private static CharacterClass srdClass(String sourceKey) {
        var cls = new CharacterClass();
        cls.setId(UUID.randomUUID());
        cls.setSource(ContentSource.SRD);
        cls.setSourceKey(sourceKey);
        cls.setName(sourceKey);
        return cls;
    }

    private static Feat srdFeat(String sourceKey) {
        var feat = new Feat();
        feat.setId(UUID.randomUUID());
        feat.setSource(ContentSource.SRD);
        feat.setSourceKey(sourceKey);
        feat.setName(sourceKey);
        return feat;
    }

    private CampaignExportContext exportContext(CampaignSectionAdapterTest.FakeKeyService keyService) {
        return new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
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

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
