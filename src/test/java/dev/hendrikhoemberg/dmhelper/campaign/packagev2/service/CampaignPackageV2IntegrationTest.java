package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignPackageV2IntegrationTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator importer;
    @Autowired CampaignExportCoordinator exporter;
    @Autowired dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService campaigns;
    @TempDir Path temp;

    @Test
    void previewConfirmExportAndRepreviewPreservesCurrentSurfaceAndKeys() throws Exception {
        var source = new ClassPathResource("campaigns/v1/feature-complete.dmcampaign.json");
        var initialStaged = new CampaignPackageReader(temp.resolve("initial")).read(source.getInputStream(),
                "legacy.dmcampaign.json", "application/json");
        var initial = pipeline.validate(initialStaged);
        assertThat(initial.valid()).as(initial.problems().toString()).isTrue();

        var campaign = importer.confirm(previews.retain(initial).previewId(), true);
        CampaignPackageArtifact artifact = exporter.export(campaign.getId());

        assertThat(artifact.zipped()).isTrue();
        assertThat(artifact.manifest().party()).hasSize(1);
        assertThat(artifact.manifest().maps()).hasSize(1);
        assertThat(artifact.manifest().encounters()).hasSize(1);
        assertThat(artifact.manifest().adventures()).hasSize(1);
        assertThat(artifact.manifest().metadata().exclusions()).isEmpty();

        assertThat(artifact.manifest().campaign().key()).isEqualTo(initial.manifest().campaign().key());
        assertThat(artifact.manifest().party().getFirst().key()).isEqualTo(initial.manifest().party().getFirst().key());
        assertThat(artifact.manifest().party().getFirst().sheet().key())
                .isEqualTo(initial.manifest().party().getFirst().sheet().key());
        assertThat(artifact.manifest().party().getFirst().sheet().resources().getFirst().key())
                .isEqualTo(initial.manifest().party().getFirst().sheet().resources().getFirst().key());
        assertThat(artifact.manifest().maps().getFirst().tokens().getFirst().key())
                .isEqualTo(initial.manifest().maps().getFirst().tokens().getFirst().key());
        assertThat(artifact.manifest().adventures().getFirst().chapters().getFirst().scenes().getFirst().key())
                .isEqualTo(initial.manifest().adventures().getFirst().chapters().getFirst().scenes().getFirst().key());

        var bytes = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(artifact.writeRequest(), bytes);
        var reread = new CampaignPackageReader(temp.resolve("reread")).read(
                new ByteArrayInputStream(bytes.toByteArray()), artifact.filename(), artifact.mediaType().toString());
        var reparsed = pipeline.validate(reread);

        assertThat(reparsed.valid()).as(reparsed.problems().toString()).isTrue();
        assertThat(reparsed.manifest()).usingRecursiveComparison()
                .ignoringFields("metadata.createdAt")
                .isEqualTo(artifact.manifest());
        reread.close();
        campaigns.delete(campaign.getId());
    }
}
