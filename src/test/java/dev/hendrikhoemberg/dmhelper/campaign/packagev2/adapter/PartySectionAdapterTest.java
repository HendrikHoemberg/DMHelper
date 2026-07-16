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
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
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
    private FeatRepository featRepo;
    private CharacterClassRepository classRepo;
    private SpellRepository spellRepo;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        partyRepo = mock(PartyMemberRepository.class);
        sheetRepo = mock(CharacterSheetRepository.class);
        resourceRepo = mock(SheetResourceRepository.class);
        spellRefRepo = mock(SheetSpellReferenceRepository.class);
        speciesRepo = mock(SpeciesRepository.class);
        backgroundRepo = mock(BackgroundRepository.class);
        featRepo = mock(FeatRepository.class);
        classRepo = mock(CharacterClassRepository.class);
        spellRepo = mock(SpellRepository.class);
        adapter = new PartySectionAdapter(partyRepo, sheetRepo, resourceRepo, spellRefRepo,
                speciesRepo, backgroundRepo, featRepo, classRepo, spellRepo);
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
        when(partyRepo.findByCampaignIdOrderByCharacterNameAsc(campaign.getId()))
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
        species.setId(UUID.randomUUID());
        species.setSourceKey("human");
        species.setName("Human");

        var background = new Background();
        background.setId(UUID.randomUUID());
        background.setSourceKey("soldier");
        background.setName("Soldier");

        var spell1 = new Spell();
        spell1.setId(UUID.randomUUID());
        spell1.setSourceKey("fire-bolt");
        spell1.setName("Fire Bolt");

        var spell2 = new Spell();
        spell2.setId(UUID.randomUUID());
        spell2.setSourceKey("shield");
        spell2.setName("Shield");

        when(speciesRepo.findBySourceKey("human")).thenReturn(species);
        when(backgroundRepo.findBySourceKey("soldier")).thenReturn(background);
        when(spellRepo.findBySourceKey("fire-bolt")).thenReturn(spell1);
        when(spellRepo.findBySourceKey("shield")).thenReturn(spell2);

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
                                Map.of("1", 2, "2", 1)
                        )
                )),
                null, null, null, null, null, null, null, null, null, null, null
        );

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

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

    private CampaignExportContext exportContext(CampaignSectionAdapterTest.FakeKeyService keyService) {
        return new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
        a.customStatBlocks(List.of());
        a.handouts(List.of());
        a.maps(List.of());
        a.encounters(List.of());
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.diceRolls(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
