package dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignCatalogServiceTest {

    @Autowired
    private CampaignCatalogService catalogService;

    @Test
    void snapshotContainsEntriesForAllTypes() {
        CatalogSnapshot snapshot = catalogService.snapshot();
        assertThat(snapshot.version()).isEqualTo("srd-5.2-dmhelper-1");
        assertThat(snapshot.entries()).isNotEmpty();
        assertThat(snapshot.entries()).extracting(CatalogSnapshot.Entry::type)
                .contains(CampaignContentType.STATBLOCK, CampaignContentType.SPELL,
                        CampaignContentType.CONDITION, CampaignContentType.RULE,
                        CampaignContentType.EQUIPMENT_ITEM, CampaignContentType.MAGIC_ITEM,
                        CampaignContentType.CLASS, CampaignContentType.SPECIES,
                        CampaignContentType.BACKGROUND, CampaignContentType.FEAT);
    }

    @Test
    void everyEntryTupleIsUnique() {
        CatalogSnapshot snapshot = catalogService.snapshot();
        var seen = new HashSet<String>();
        for (var entry : snapshot.entries()) {
            String tuple = entry.type() + ":" + entry.ruleset() + ":" + entry.sourceKey();
            assertThat(seen.add(tuple)).as("duplicate tuple: " + tuple).isTrue();
        }
    }

    @Test
    void entriesAreSortedByTypeThenSourceKey() {
        CatalogSnapshot snapshot = catalogService.snapshot();
        for (int i = 1; i < snapshot.entries().size(); i++) {
            var prev = snapshot.entries().get(i - 1);
            var curr = snapshot.entries().get(i);
            int typeCmp = prev.type().compareTo(curr.type());
            if (typeCmp == 0) {
                assertThat(curr.sourceKey()).isGreaterThanOrEqualTo(prev.sourceKey());
            } else {
                assertThat(typeCmp).isLessThan(0);
            }
        }
    }

    @Test
    void rulesetAndSourceAreConstant() {
        CatalogSnapshot snapshot = catalogService.snapshot();
        assertThat(snapshot.entries()).allMatch(e ->
                "SRD_5_2".equals(e.ruleset()) && "SRD".equals(e.source()));
    }

    @Test
    void aliasesAreEmptyInThisMilestone() {
        CatalogSnapshot snapshot = catalogService.snapshot();
        assertThat(snapshot.entries()).allMatch(e -> e.aliases().isEmpty());
    }

    @Test
    void sha256IsReproducible() {
        String hash1 = catalogService.snapshot().sha256();
        String hash2 = catalogService.snapshot().sha256();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void resolveFindsExistingEntry() {
        var resolved = catalogService.resolve(
                CampaignContentType.CONDITION, "SRD_5_2", "blinded");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().name()).isNotEmpty();
        assertThat(resolved.get().type()).isEqualTo(CampaignContentType.CONDITION);
    }

    @Test
    void resolveReturnsEmptyForUnknownEntry() {
        assertThat(catalogService.resolve(
                CampaignContentType.SPELL, "SRD_5_2", "nonexistent")).isEmpty();
    }

    @Test
    void resolveReturnsEmptyForWrongRuleset() {
        assertThat(catalogService.resolve(
                CampaignContentType.SPELL, "WRONG", "fireball")).isEmpty();
    }

    @Test
    void writeSnapshotWhenRequested() throws Exception {
        if (!"true".equals(System.getProperty("dmhelper.writeCatalogSnapshot"))) {
            return;
        }
        String json = new tools.jackson.databind.json.JsonMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(catalogService.snapshot());
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("src/main/resources/catalog/srd-5.2-catalog.json"),
                json);
        System.out.println("Wrote catalog snapshot to catalog/srd-5.2-catalog.json");
    }
}
