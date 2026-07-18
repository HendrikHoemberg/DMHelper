package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({RollableTableService.class, RollableTableValidator.class, TableReferenceResolver.class,
         RollableTableDependencyService.class, CustomContentSupport.class, LibraryReferenceCleaner.class})
class RollableTableServiceTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @MockitoBean
    private CampaignCatalogService catalogService;

    @Autowired
    private RollableTableService service;

    @Autowired
    private RollableTableRepository repository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private StatBlockRepository statBlockRepository;

    @Autowired
    private AdventureRepository adventureRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private SceneLinkRepository sceneLinkRepository;

    @Autowired
    private EntityManager em;

    private UUID campaignId;
    private UUID srdTableId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        statBlockRepository.deleteAll();
        campaignRepository.deleteAll();
        sceneLinkRepository.deleteAll();
        sceneRepository.deleteAll();
        chapterRepository.deleteAll();
        adventureRepository.deleteAll();

        Campaign campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        SRDTable(campaign);
        em.flush();
    }

    private void SRDTable(Campaign campaign) {
        RollableTable srd = new RollableTable();
        srd.setSourceKey("srd-table");
        srd.setSource(ContentSource.SRD);
        srd.setName("SRD Table");
        srd.setAddressMode(TableAddressMode.RANGE);
        srd.setRollExpression("1d6");
        srd.setCategory(TableCategory.GENERIC);
        ContentProvenance prov = new ContentProvenance();
        prov.setLicenseClassification(LicenseClassification.SRD);
        srd.setProvenance(prov);
        srd = repository.save(srd);
        srdTableId = srd.getId();
    }

    private Scene createSceneWithLink(UUID tableId) {
        Adventure adventure = new Adventure();
        adventure.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        adventure.setName("Test Adventure");
        adventure = adventureRepository.save(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Test Chapter");
        chapter = chapterRepository.save(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Test Scene");
        scene.setSortOrder(0);
        scene = sceneRepository.save(scene);

        SceneLink link = new SceneLink();
        link.setScene(scene);
        link.setRole(SceneLinkRole.REFERENCE);
        link.setTargetScope(SceneLinkTargetScope.PACKAGE);
        link.setTargetType("ROLLABLE_TABLE");
        link.setTargetId(tableId);
        link.setSortOrder(0);
        scene.getLinks().add(link);
        sceneLinkRepository.save(link);

        em.flush();
        return scene;
    }

    @Test
    void createsGlobalCustomTable() {
        var write = validRangeWrite("global-table", "Global Table");
        RollableTable table = service.create(null, write, null);
        assertThat(table.getId()).isNotNull();
        assertThat(table.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(table.getCampaign()).isNull();
        assertThat(table.getName()).isEqualTo("Global Table");
    }

    @Test
    void createsCampaignCustomTable() {
        var write = validRangeWrite("camp-table", "Campaign Table");
        RollableTable table = service.create(campaignId, write, null);
        assertThat(table.getId()).isNotNull();
        assertThat(table.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(table.getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void clonesSrdAsCustom() {
        RollableTable cloned = service.cloneAsCustom(srdTableId, campaignId, "Custom SRD Clone");
        assertThat(cloned.getId()).isNotEqualTo(srdTableId);
        assertThat(cloned.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(cloned.getCampaign().getId()).isEqualTo(campaignId);
        assertThat(cloned.getName()).isEqualTo("Custom SRD Clone");
        assertThat(cloned.getAddressMode()).isEqualTo(TableAddressMode.RANGE);
    }

    @Test
    void promotesToGlobal() {
        var write = validRangeWrite("promote-me", "Promote Me");
        RollableTable table = service.create(campaignId, write, null);
        RollableTable promoted = service.promoteToGlobal(table.getId());
        assertThat(promoted.getCampaign()).isNull();
    }

    @Test
    void rejectsPromotionWithCampaignRefs() {
        StatBlock sb = new StatBlock();
        sb.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        sb.setSource(ContentSource.CUSTOM);
        sb.setName("Campaign Monster");
        sb.setCr("1");
        sb.setType("Humanoid");
        sb.setAc(12);
        sb.setHp("10");
        sb.setSpeed("30 ft.");
        sb = statBlockRepository.save(sb);
        em.flush();

        var entry = new RollableTableEntryWrite(
                "ref", null, null, 1, "A", null,
                List.of(new RollableTableReferenceWrite(
                        TableReferenceScope.ENTITY, CampaignContentType.STATBLOCK,
                        sb.getId(), null, null, "Monster")));
        var write = new RollableTableWrite(
                "promote-ref", "Promote With Refs", null, TableAddressMode.WEIGHTED,
                null, TableCategory.GENERIC, List.of(), List.of(entry));
        RollableTable table = service.create(campaignId, write, null);
        em.flush();

        UUID finalTableId = table.getId();
        assertThatThrownBy(() -> service.promoteToGlobal(finalTableId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUpdateOfSrdTable() {
        var write = validRangeWrite("new-key", "New Name");
        assertThatThrownBy(() -> service.updateCustom(srdTableId, write, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom");
    }

    @Test
    void computesDeletionImpact() {
        var write = validRangeWrite("impact", "Impact");
        RollableTable table = service.create(campaignId, write, null);
        em.flush();

        createSceneWithLink(table.getId());

        TableDeletionImpact impact = service.deletionImpact(table.getId());
        assertThat(impact.hasDependents()).isTrue();
    }

    @Test
    void rejectsDeleteWithDependentsWithoutConfirmation() {
        var write = validRangeWrite("no-del", "No Delete");
        RollableTable table = service.create(campaignId, write, null);
        createSceneWithLink(table.getId());

        UUID tableId = table.getId();
        assertThatThrownBy(() -> service.deleteCustom(tableId, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependent");
    }

    @Test
    void deletesWithConfirmationWhenDependentsExist() {
        var write = validRangeWrite("del-yes", "Delete Yes");
        RollableTable table = service.create(campaignId, write, null);
        createSceneWithLink(table.getId());
        em.flush();

        UUID tableId = table.getId();
        assertThatCode(() -> service.deleteCustom(tableId, true)).doesNotThrowAnyException();
        assertThat(repository.findById(tableId)).isEmpty();
    }

    @Test
    void deletesTableWithoutDependents() {
        var write = validRangeWrite("simple-del", "Simple Delete");
        RollableTable table = service.create(null, write, null);
        UUID tableId = table.getId();
        service.deleteCustom(tableId, false);
        assertThat(repository.findById(tableId)).isEmpty();
    }

    private static RollableTableWrite validRangeWrite(String key, String name) {
        return new RollableTableWrite(
                key, name, null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "only", 1, 6, null, "Result", null, List.of())));
    }
}
