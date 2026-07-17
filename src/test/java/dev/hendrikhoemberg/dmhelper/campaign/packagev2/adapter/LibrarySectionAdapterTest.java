package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.packagev2.LibrarySectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibrarySectionAdapterTest {

    private LibrarySectionAdapter adapter;
    private StatBlockRepository statBlockRepo;
    private SpellRepository spellRepo;
    private ConditionRepository conditionRepo;
    private RuleSectionRepository ruleSectionRepo;
    private EquipmentItemRepository equipmentItemRepo;
    private MagicItemRepository magicItemRepo;
    private CharacterClassRepository characterClassRepo;
    private SpeciesRepository speciesRepo;
    private BackgroundRepository backgroundRepo;
    private FeatRepository featRepo;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        statBlockRepo = mock(StatBlockRepository.class);
        spellRepo = mock(SpellRepository.class);
        conditionRepo = mock(ConditionRepository.class);
        ruleSectionRepo = mock(RuleSectionRepository.class);
        equipmentItemRepo = mock(EquipmentItemRepository.class);
        magicItemRepo = mock(MagicItemRepository.class);
        characterClassRepo = mock(CharacterClassRepository.class);
        speciesRepo = mock(SpeciesRepository.class);
        backgroundRepo = mock(BackgroundRepository.class);
        featRepo = mock(FeatRepository.class);
        adapter = new LibrarySectionAdapter(statBlockRepo, spellRepo, conditionRepo, ruleSectionRepo,
                equipmentItemRepo, magicItemRepo, characterClassRepo, speciesRepo, backgroundRepo, featRepo);
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder200() {
        assertThat(adapter.order()).isEqualTo(200);
    }

    @Test
    void sectionNameIsLibrary() {
        assertThat(adapter.sectionName()).isEqualTo("Library");
    }

    @Test
    void exportsCustomStatBlocks() {
        var sb1 = statBlock(UUID.randomUUID(), "Dragon Whelp", "5", "dragon", 17, "120", 1800,
                Instant.parse("2025-01-01T00:00:00Z"));
        var sb2 = statBlock(UUID.randomUUID(), "Goblin King", "3", "humanoid", 15, "65", 700,
                Instant.parse("2025-02-01T00:00:00Z"));

        when(statBlockRepo.findByCampaignIdOrderByNameAscIdAsc(campaign.getId()))
                .thenReturn(List.of(sb1, sb2));

        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                new CampaignSectionAdapterTest.FakeKeyService(), new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);

        fillRest(assembler);
        var manifest = buildManifest(assembler);
        assertThat(manifest.customStatBlocks()).hasSize(2);

        var dto1 = manifest.customStatBlocks().stream()
                .filter(d -> d.name().equals("Dragon Whelp")).findFirst().orElseThrow();
        assertThat(dto1.cr()).isEqualTo("5");
        assertThat(dto1.ac()).isEqualTo(17);
        assertThat(dto1.hp()).isEqualTo("120");
        assertThat(dto1.xp()).isEqualTo(1800);
        assertThat(dto1.createdAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));

        var dto2 = manifest.customStatBlocks().stream()
                .filter(d -> d.name().equals("Goblin King")).findFirst().orElseThrow();
        assertThat(dto2.cr()).isEqualTo("3");
        assertThat(dto2.xp()).isEqualTo(700);
    }

    @Test
    void keyStableAfterRenameAndImportPreservesValues() {
        UUID sbId = UUID.randomUUID();
        var sb = fullStatBlock(sbId);

        when(statBlockRepo.findByCampaignIdOrderByNameAscIdAsc(campaign.getId()))
                .thenReturn(List.of(sb));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);
        String exportedKey = manifest.customStatBlocks().get(0).key();

        // Rename and re-export
        sb.setName("Fire Elemental Renamed");

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
        assertThat(manifest2.customStatBlocks().get(0).key()).isEqualTo(exportedKey);

        // Import into fresh context - capture the saved entity
        var captured = new StatBlock[1];
        when(statBlockRepo.save(any())).thenAnswer(inv -> {
            var s = inv.getArgument(0, StatBlock.class);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            captured[0] = s;
            return s;
        });

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var importManifest = new CampaignManifestV2(
                2, null, null, null,
                null, manifest2.customStatBlocks(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of()
        );

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(importManifest, importContext);

        var imported = captured[0];
        assertThat(imported).isNotNull();
        assertThat(imported.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(imported.getName()).isEqualTo("Fire Elemental Renamed");
        assertThat(imported.getCr()).isEqualTo("7");
        assertThat(imported.getXp()).isEqualTo(2900);
        assertThat(imported.getStrSave()).isNull();
        assertThat(imported.getDexSave()).isEqualTo(6);
        assertThat(imported.getSkills()).isEqualTo("Perception +5");
        assertThat(imported.getTraits()).isEqualTo("[{\"name\":\"Fire Form\"}]");
        assertThat(imported.getCreatedAt()).isEqualTo(Instant.parse("2025-03-15T12:00:00Z"));
    }

    private StatBlock statBlock(UUID id, String name, String cr, String type, int ac, String hp, int xp, Instant createdAt) {
        var sb = new StatBlock();
        sb.setId(id);
        sb.setSource(ContentSource.CUSTOM);
        sb.setCampaign(campaign);
        sb.setName(name);
        sb.setCr(cr);
        sb.setType(type);
        sb.setSize("Medium");
        sb.setAlignment("neutral");
        sb.setAc(ac);
        sb.setHp(hp);
        sb.setSpeed("30 ft.");
        sb.setStrScore(10);
        sb.setDexScore(10);
        sb.setConScore(10);
        sb.setIntScore(10);
        sb.setWisScore(10);
        sb.setChaScore(10);
        sb.setXp(xp);
        sb.setCreatedAt(createdAt);
        return sb;
    }

    private StatBlock fullStatBlock(UUID id) {
        var sb = new StatBlock();
        sb.setId(id);
        sb.setSource(ContentSource.CUSTOM);
        sb.setCampaign(campaign);
        sb.setName("Fire Elemental");
        sb.setCr("7");
        sb.setType("elemental");
        sb.setSize("Large");
        sb.setAlignment("neutral");
        sb.setAc(16);
        sb.setHp("180");
        sb.setSpeed("50 ft.");
        sb.setStrScore(18);
        sb.setDexScore(14);
        sb.setConScore(16);
        sb.setIntScore(8);
        sb.setWisScore(12);
        sb.setChaScore(10);
        sb.setDexSave(6);
        sb.setConSave(7);
        sb.setWisSave(5);
        sb.setSkills("Perception +5");
        sb.setDamageVulnerabilities("cold");
        sb.setDamageResistances("bludgeoning, piercing from nonmagical");
        sb.setDamageImmunities("fire, poison");
        sb.setConditionImmunities("exhaustion, poisoned");
        sb.setSenses("darkvision 60 ft.");
        sb.setLanguages("Ignan");
        sb.setTraits("[{\"name\":\"Fire Form\"}]");
        sb.setActions("[{\"name\":\"Multiattack\"}]");
        sb.setBonusActions("[{\"name\":\"Flame Burst\"}]");
        sb.setXp(2900);
        sb.setCreatedAt(Instant.parse("2025-03-15T12:00:00Z"));
        return sb;
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
        a.party(List.of());
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
