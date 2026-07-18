package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
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
    private RollableTableEntryReferenceRepository referenceRepository;

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
        referenceRepository.deleteAll();
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
        RollableTableEntry entry = new RollableTableEntry();
        entry.setTable(srd);
        entry.setEntryKey("result");
        entry.setRangeStart(1);
        entry.setRangeEnd(6);
        entry.setResultText("Result");
        entry.setSortOrder(0);
        srd.getEntries().add(entry);
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

    private UUID createTableReferencing(UUID targetId) {
        var ref = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                targetId, null, null, "Target");
        var write = new RollableTableWrite(
                "referencer", "Referencer", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "ref", 1, 6, null, null, null, List.of(ref))));
        RollableTable table = service.create(campaignId, write, null);
        em.flush();
        return table.getId();
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
    void rejectsMissingCampaignInsteadOfCreatingGlobalTable() {
        UUID missingCampaignId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
                missingCampaignId, validRangeWrite("missing-campaign", "Missing Campaign"), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Campaign");
    }

    @Test
    void rejectsSourceKeyCollisionWithinCampaign() {
        service.create(campaignId, validRangeWrite("same-key", "First"), null);

        assertThatThrownBy(() -> service.create(
                campaignId, validRangeWrite("same-key", "Second"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceKey");
    }

    @Test
    void rejectsSourceKeyCollisionOnUpdateButAllowsUnchangedKey() {
        RollableTable first = service.create(
                campaignId, validRangeWrite("first-key", "First"), null);
        service.create(campaignId, validRangeWrite("second-key", "Second"), null);

        assertThatCode(() -> service.updateCustom(
                first.getId(), validRangeWrite("first-key", "First Updated"), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.updateCustom(
                first.getId(), validRangeWrite("second-key", "Collision"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceKey");
    }

    @Test
    void rejectsCrossCampaignEntityReference() {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        otherCampaign = campaignRepository.save(otherCampaign);

        StatBlock foreign = new StatBlock();
        foreign.setCampaign(otherCampaign);
        foreign.setSource(ContentSource.CUSTOM);
        foreign.setSourceKey("foreign-monster");
        foreign.setName("Foreign Monster");
        foreign.setCr("1");
        foreign.setType("Humanoid");
        foreign.setAc(12);
        foreign.setHp("10");
        foreign.setSpeed("30 ft.");
        foreign = statBlockRepository.save(foreign);
        em.flush();

        var ref = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.STATBLOCK,
                foreign.getId(), null, null, "Foreign Monster");
        var write = new RollableTableWrite(
                "cross-campaign", "Cross Campaign", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "foreign", 1, 6, null, null, null, List.of(ref))));

        assertThatThrownBy(() -> service.create(campaignId, write, null))
                .isInstanceOf(RollableTableValidationException.class)
                .satisfies(e -> assertThat(((RollableTableValidationException) e).problems())
                        .anyMatch(p -> p.code().equals("UNRESOLVED_REFERENCE")));
    }

    @Test
    void canonicalizesSuppliedWeightedRollExpression() {
        var write = new RollableTableWrite(
                "weighted", "Weighted", null, TableAddressMode.WEIGHTED,
                "1d999", TableCategory.GENERIC, List.of(),
                List.of(
                        new RollableTableEntryWrite("a", null, null, 3, "A", null, List.of()),
                        new RollableTableEntryWrite("b", null, null, 2, "B", null, List.of())));

        RollableTable table = service.create(campaignId, write, null);

        assertThat(table.getRollExpression()).isEqualTo("1d5");
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
    void clonesWithoutNameUsingNonCollidingCopyIdentity() {
        RollableTable cloned = service.cloneAsCustom(srdTableId, campaignId, null);

        assertThat(cloned.getName()).isEqualTo("SRD Table Copy");
        assertThat(cloned.getSourceKey()).isEqualTo("srd-table-copy");
    }

    @Test
    void rejectsCloneSourceKeyCollision() {
        service.cloneAsCustom(srdTableId, campaignId, "Named Clone");

        assertThatThrownBy(() -> service.cloneAsCustom(
                srdTableId, campaignId, "Named Clone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceKey");
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
    void promotesTableWithGlobalReferenceOfNonTableType() {
        StatBlock global = new StatBlock();
        global.setSource(ContentSource.CUSTOM);
        global.setSourceKey("global-monster");
        global.setName("Global Monster");
        global.setCr("1");
        global.setType("Humanoid");
        global.setAc(12);
        global.setHp("10");
        global.setSpeed("30 ft.");
        global = statBlockRepository.save(global);
        em.flush();

        var write = new RollableTableWrite(
                "promote-global-ref", "Promote Global Ref", null, TableAddressMode.WEIGHTED,
                null, TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "ref", null, null, 1, null, null,
                        List.of(new RollableTableReferenceWrite(
                                TableReferenceScope.ENTITY, CampaignContentType.STATBLOCK,
                                global.getId(), null, null, "Global Monster")))));
        RollableTable table = service.create(campaignId, write, null);

        assertThat(service.promoteToGlobal(table.getId()).getCampaign()).isNull();
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

    @Test
    void insertsDeletedTableMarkerOnReferencingEntry() {
        var write1 = validRangeWrite("to-delete", "To Delete");
        RollableTable toDelete = service.create(campaignId, write1, null);
        UUID idToDelete = toDelete.getId();
        em.flush();

        UUID referencerId = createTableReferencing(idToDelete);
        em.clear();

        service.deleteCustom(idToDelete, true);
        em.flush();

        RollableTable referencer = repository.findById(referencerId).orElseThrow();
        boolean hasMarker = referencer.getEntries().stream()
                .anyMatch(e -> e.getResultText() != null
                        && e.getResultText().contains("[Deleted table reference: " + toDelete.getName() + "]"));
        assertThat(hasMarker).isTrue();
    }

    @Test
    void rejectsNonExistentTableWithNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        assertThatThrownBy(() -> service.promoteToGlobal(fakeId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void detectsCycleInReferenceGraph() {
        // Create table B first (no refs)
        var writeB = validRangeWrite("table-b", "Table B");
        RollableTable tableB = service.create(campaignId, writeB, null);
        em.flush();
        em.clear();

        // Create table A -> table B
        var refToB = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                tableB.getId(), null, null, "Table B");
        var writeA = new RollableTableWrite(
                "table-a", "Table A", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "ref-b", 1, 6, null, null, null, List.of(refToB))));
        RollableTable tableA = service.create(campaignId, writeA, null);
        em.flush();

        // Now update table B to reference table A (creating a cycle)
        var refToA = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                tableA.getId(), null, null, "Table A");
        var writeBUpdate = new RollableTableWrite(
                "table-b", "Table B", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "ref-a", 1, 6, null, null, null, List.of(refToA))));
        var cycleThrowable = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.updateCustom(tableB.getId(), writeBUpdate, null),
                RollableTableValidationException.class);
        assertThat(cycleThrowable).isNotNull();
        assertThat(cycleThrowable.problems()).anyMatch(p ->
                p.code().equals("TABLE_REFERENCE_CYCLE"));
    }

    @Test
    void detectsDepthExceeded() {
        // Create a chain of 6 tables: T0 -> T1 -> ... -> T5 (depth 5, within limit)
        // The 7th table would have depth 6, exceeding max 5
        UUID prevId = null;
        for (int i = 0; i < 6; i++) {
            var ref = prevId != null
                    ? List.of(new RollableTableReferenceWrite(
                            TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                            prevId, null, null, "Prev"))
                    : List.<RollableTableReferenceWrite>of();
            var write = new RollableTableWrite(
                    "depth-" + i, "Depth " + i, null, TableAddressMode.RANGE,
                    "1d6", TableCategory.GENERIC, List.of(),
                    List.of(new RollableTableEntryWrite(
                            "entry", 1, 6, null, "Val", null, ref)));
            RollableTable t = service.create(campaignId, write, null);
            prevId = t.getId();
            em.flush();
        }

        // Now try to create a table that references T5 (depth would be 6, exceeding 5)
        var ref = List.of(new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                prevId, null, null, "Deep"));
        var write = new RollableTableWrite(
                "too-deep", "Too Deep", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "entry", 1, 6, null, "Val", null, ref)));
        var throwable = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.create(campaignId, write, null),
                RollableTableValidationException.class);
        assertThat(throwable).isNotNull();
        assertThat(throwable.problems()).anyMatch(p ->
                p.code().equals("TABLE_REFERENCE_DEPTH_EXCEEDED"));
    }

    @Test
    void clearsTagsOnEmptyList() {
        // First create with tags
        var writeWithTags = new RollableTableWrite(
                "tagged", "Tagged", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of("a", "b"),
                List.of(new RollableTableEntryWrite(
                        "only", 1, 6, null, "Result", null, List.of())));
        RollableTable table = service.create(null, writeWithTags, null);
        assertThat(table.getTags()).isEqualTo("a, b");

        // Update with empty tags
        var writeEmptyTags = new RollableTableWrite(
                "tagged", "Tagged", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "only", 1, 6, null, "Result", null, List.of())));
        RollableTable updated = service.updateCustom(table.getId(), writeEmptyTags, null);
        assertThat(updated.getTags()).isNull();
    }

    @Test
    void unresolvedReferenceOnCreate() {
        var ref = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.STATBLOCK,
                UUID.randomUUID(), null, null, "Missing");
        var write = new RollableTableWrite(
                "bad-ref", "Bad Ref", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "ref", 1, 6, null, null, null, List.of(ref))));
        var unresThrowable = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.create(null, write, null),
                RollableTableValidationException.class);
        assertThat(unresThrowable).isNotNull();
        assertThat(unresThrowable.problems()).anyMatch(p ->
                p.code().equals("UNRESOLVED_REFERENCE"));
    }

    private static RollableTableWrite validRangeWrite(String key, String name) {
        return new RollableTableWrite(
                key, name, null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(new RollableTableEntryWrite(
                        "only", 1, 6, null, "Result", null, List.of())));
    }
}
