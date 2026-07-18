package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RollableTablePersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private RollableTableRepository repository;

    private Campaign campaign;
    private RollableTable table;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Rollable Table Test");
        em.persist(campaign);

        ContentProvenance provenance = new ContentProvenance();
        provenance.setLicenseClassification(LicenseClassification.ORIGINAL);
        provenance.setSourceTitle("Homebrew");

        table = new RollableTable();
        table.setSourceKey("test-encounter-table");
        table.setSource(ContentSource.CUSTOM);
        table.setCampaign(campaign);
        table.setProvenance(provenance);
        table.setName("Wilderness Encounters");
        table.setDescription("Random encounters in the forest");
        table.setAddressMode(TableAddressMode.RANGE);
        table.setRollExpression("1d20");
        table.setCategory(TableCategory.ENCOUNTER);
        table.setTags("forest, random");
        em.persist(table);

        RollableTableEntry entry1 = new RollableTableEntry();
        entry1.setTable(table);
        entry1.setEntryKey("goblins");
        entry1.setRangeStart(1);
        entry1.setRangeEnd(10);
        entry1.setResultText("3 goblins appear");
        entry1.setSortOrder(0);
        em.persist(entry1);

        RollableTableEntry entry2 = new RollableTableEntry();
        entry2.setTable(table);
        entry2.setEntryKey("wolves");
        entry2.setRangeStart(11);
        entry2.setRangeEnd(20);
        entry2.setResultText("A pack of wolves");
        entry2.setSortOrder(1);
        em.persist(entry2);

        RollableTableEntryReference ref = new RollableTableEntryReference();
        ref.setEntry(entry2);
        ref.setTargetScope(TableReferenceScope.CATALOG);
        ref.setTargetType("STATBLOCK");
        ref.setCatalogRuleset("dnd5e");
        ref.setCatalogSourceKey("srd-2024_wolf");
        ref.setDisplayText("Wolf");
        ref.setSortOrder(0);
        em.persist(ref);

        em.flush();
        em.clear();
    }

    @Test
    void persistsAndRehydratesTableWithEntriesAndReferences() {
        List<RollableTable> tables = repository.findAll();
        assertThat(tables).hasSize(1);

        RollableTable loaded = tables.get(0);
        assertThat(loaded.getSourceKey()).isEqualTo("test-encounter-table");
        assertThat(loaded.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getProvenance().getLicenseClassification()).isEqualTo(LicenseClassification.ORIGINAL);
        assertThat(loaded.getProvenance().getSourceTitle()).isEqualTo("Homebrew");
        assertThat(loaded.getName()).isEqualTo("Wilderness Encounters");
        assertThat(loaded.getDescription()).isEqualTo("Random encounters in the forest");
        assertThat(loaded.getAddressMode()).isEqualTo(TableAddressMode.RANGE);
        assertThat(loaded.getRollExpression()).isEqualTo("1d20");
        assertThat(loaded.getCategory()).isEqualTo(TableCategory.ENCOUNTER);
        assertThat(loaded.getTags()).isEqualTo("forest, random");
        assertThat(loaded.getCreatedAt()).isNotNull();

        assertThat(loaded.getEntries()).hasSize(2);

        RollableTableEntry first = loaded.getEntries().get(0);
        assertThat(first.getEntryKey()).isEqualTo("goblins");
        assertThat(first.getRangeStart()).isEqualTo(1);
        assertThat(first.getRangeEnd()).isEqualTo(10);
        assertThat(first.getResultText()).isEqualTo("3 goblins appear");
        assertThat(first.getSortOrder()).isEqualTo(0);
        assertThat(first.getTable().getId()).isEqualTo(loaded.getId());

        RollableTableEntry second = loaded.getEntries().get(1);
        assertThat(second.getEntryKey()).isEqualTo("wolves");
        assertThat(second.getRangeStart()).isEqualTo(11);
        assertThat(second.getRangeEnd()).isEqualTo(20);
        assertThat(second.getResultText()).isEqualTo("A pack of wolves");
        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(second.getTable().getId()).isEqualTo(loaded.getId());

        assertThat(second.getReferences()).hasSize(1);

        RollableTableEntryReference ref = second.getReferences().get(0);
        assertThat(ref.getTargetScope()).isEqualTo(TableReferenceScope.CATALOG);
        assertThat(ref.getTargetType()).isEqualTo("STATBLOCK");
        assertThat(ref.getCatalogRuleset()).isEqualTo("dnd5e");
        assertThat(ref.getCatalogSourceKey()).isEqualTo("srd-2024_wolf");
        assertThat(ref.getDisplayText()).isEqualTo("Wolf");
        assertThat(ref.getSortOrder()).isEqualTo(0);
        assertThat(ref.getEntry().getId()).isEqualTo(second.getId());
    }

    @Test
    void visibleQueryIncludesSameCampaignGlobalAndSrdButExcludesOtherCampaign() {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        em.persist(otherCampaign);

        persistTable("global-table", "Global Table", ContentSource.CUSTOM, null);
        persistTable("srd-table", "SRD Table", ContentSource.SRD, null);
        persistTable("foreign-table", "Foreign Table", ContentSource.CUSTOM, otherCampaign);
        em.flush();
        em.clear();

        assertThat(repository.findVisibleByCampaignId(campaign.getId()))
                .extracting(RollableTable::getName)
                .containsExactly("Global Table", "SRD Table", "Wilderness Encounters")
                .doesNotContain("Foreign Table");
        assertThat(repository.findVisibleByCampaignId(null))
                .extracting(RollableTable::getName)
                .containsExactly("Global Table", "SRD Table")
                .doesNotContain("Foreign Table", "Wilderness Encounters");
    }

    private void persistTable(String sourceKey, String name, ContentSource source, Campaign owner) {
        RollableTable candidate = new RollableTable();
        candidate.setSourceKey(sourceKey);
        candidate.setSource(source);
        candidate.setCampaign(owner);
        candidate.setName(name);
        candidate.setAddressMode(TableAddressMode.WEIGHTED);
        candidate.setRollExpression("1d1");
        candidate.setCategory(TableCategory.GENERIC);
        em.persist(candidate);
    }
}
