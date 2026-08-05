package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriteRequest;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import dev.hendrikhoemberg.dmhelper.session.data.CockpitLayoutPresetRepository;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitBuiltInPresetCatalog;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutDocument;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.SavePresetRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignPackageV2IntegrationTest {

    private static final String SENTINEL = "PRIVATE_LAYOUT_SENTINEL_9D4F";

    @Autowired CampaignPackageValidationPipeline pipeline;
    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator importer;
    @Autowired CampaignExportCoordinator exporter;
    @Autowired dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService campaigns;
    @Autowired CockpitLayoutPresetService cockpitLayoutPresets;
    @Autowired CockpitLayoutPresetRepository cockpitLayoutPresetRepository;
    @Autowired CockpitBuiltInPresetCatalog builtIns;
    @TempDir Path temp;

    @Test
    void customCockpitLayoutPresetsAreExcludedFromPackageV2ExportAndSurviveImport() throws Exception {
        ensureSentinelPreset();

        var staged = stageV2FeatureComplete("sentinel-in");
        var validated = pipeline.validate(staged);
        assertThat(validated.valid()).as(validated.problems().toString()).isTrue();
        var campaign = importer.confirm(previews.retain(validated).previewId(), true);

        CampaignPackageArtifact artifact = exporter.export(campaign.getId());
        String manifestJson = JsonMapper.builder().build().writeValueAsString(artifact.manifest());
        assertThat(manifestJson).doesNotContain(SENTINEL, "cockpitLayout", "layoutPreset");

        var bytes = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(artifact.writeRequest(), bytes);
        List<String> zipEntryNames = listZipEntryNames(bytes.toByteArray());
        assertThat(zipEntryNames).isNotEmpty();
        assertThat(zipEntryNames).noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("layout"));

        var reread = new CampaignPackageReader(temp.resolve("sentinel-out")).read(
                new ByteArrayInputStream(bytes.toByteArray()), artifact.filename(), artifact.mediaType().toString());
        var reparsed = pipeline.validate(reread);
        assertThat(reparsed.valid()).as(reparsed.problems().toString()).isTrue();
        importer.confirm(previews.retain(reparsed).previewId(), true);
        reread.close();

        assertThat(cockpitLayoutPresetRepository.findAllByOrderByNormalizedNameAsc())
                .filteredOn(preset -> SENTINEL.equals(preset.getName()))
                .as("package import must neither create nor delete application-local presets")
                .hasSize(1);

        campaigns.delete(campaign.getId());
    }

    private void ensureSentinelPreset() {
        if (cockpitLayoutPresetRepository.existsByNormalizedName("private_layout_sentinel_9d4f")) {
            return;
        }
        CockpitLayoutDocument exploration = builtIns.require("builtin:exploration").layout();
        CockpitLayoutDocument layout = new CockpitLayoutDocument(
                exploration.schemaVersion(),
                SENTINEL,
                exploration.zones(),
                exploration.ratios(),
                exploration.compactModuleKeys());
        cockpitLayoutPresets.create(new SavePresetRequest(SENTINEL, layout));
    }

    private StagedCampaignPackage stageV2FeatureComplete(String stagingLabel) throws Exception {
        var mapper = JsonMapper.builder().build();
        CampaignManifestV2 manifest;
        try (var input = new ClassPathResource(
                "campaigns/v2/feature-complete.dmcampaign/manifest.json").getInputStream()) {
            manifest = mapper.readValue(input, CampaignManifestV2.class);
        }
        Map<String, InputStreamSource> assets = new LinkedHashMap<>();
        for (var descriptor : manifest.assets()) {
            byte[] bytes;
            try (var input = new ClassPathResource(
                    "campaigns/v2/feature-complete.dmcampaign/" + descriptor.path()).getInputStream()) {
                bytes = input.readAllBytes();
            }
            assets.put(descriptor.key(), new ByteArrayResource(bytes));
        }
        var output = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(new CampaignPackageWriteRequest(
                "feature-complete.dmcampaign", manifest, assets), output);
        return new CampaignPackageReader(temp.resolve(stagingLabel)).read(
                new ByteArrayInputStream(output.toByteArray()),
                "feature-complete.dmcampaign", "application/vnd.dmhelper.campaign+zip");
    }

    private static List<String> listZipEntryNames(byte[] zipBytes) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
