package dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LegacyV1ToV2MigrationTest {

    @Autowired LegacyV1ToV2Migration migration;
    @Autowired CampaignImportValidator validator;
    @TempDir Path temp;

    @Test
    void migrationIsDeterministicAndPreservesEverySupportedSection() throws Exception {
        var resource = new ClassPathResource("campaigns/v1/feature-complete.dmcampaign.json");
        var reader = new CampaignPackageReader(temp);
        var firstStage = reader.read(resource.getInputStream(), "legacy.dmcampaign.json", "application/json");
        var secondStage = reader.read(resource.getInputStream(), "legacy.dmcampaign.json", "application/json");

        var first = migration.migrate(firstStage, validator);
        var second = migration.migrate(secondStage, validator);

        assertThat(first.valid()).as(first.problems().toString()).isTrue();
        assertThat(first.manifest()).isEqualTo(second.manifest());
        var manifest = first.manifest();
        assertThat(manifest.maps()).isNotEmpty();
        assertThat(manifest.encounters()).isNotEmpty();
        assertThat(manifest.notes()).isNotEmpty();
        assertThat(manifest.quickNotes()).isNotEmpty();
        assertThat(manifest.assignments()).isNotEmpty();
        assertThat(manifest.ledgerEntries()).isNotEmpty();
        assertThat(manifest.timelineEvents()).isNotEmpty();
        assertThat(manifest.adventures()).isNotEmpty();
        assertThat(first.problems()).anyMatch(p -> p.code().equals("LEGACY_REFERENCE_MIGRATED"));
        assertThat(manifest.metadata().catalogSha256()).doesNotContain("placeholder");
    }
}
