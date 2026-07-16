package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignCompleteRoundTripTest {

    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator importer;
    @Autowired CampaignExportCoordinator exporter;
    @Autowired CampaignSemanticSnapshotService snapshotService;
    @Autowired CampaignService campaigns;
    @TempDir Path temp;

    private static final byte[] ONE_BY_ONE_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x00, 0x00, 0x00, 0x00, (byte) 0x9A, (byte) 0x7C, (byte) 0xD9,
            (byte) 0x78, 0x00, 0x00, 0x00, 0x10, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00,
            0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x0C, (byte) 0xE3,
            0x1D, (byte) 0x89, 0x1D, 0x00, 0x00, 0x00, 0x00, 0x49,
            0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private static final byte[] ONE_BY_ONE_WEBP = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x5C, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20,
            0x50, 0x00, 0x00, 0x00, (byte) 0xB0, 0x01, 0x00, (byte) 0x9D,
            (byte) 0x01, 0x2A, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00,
            0x01, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x50, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
    };

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("minimal", "/campaigns/v2/minimal.dmcampaign.json"),
                Arguments.of("feature-complete", "/campaigns/v2/feature-complete.dmcampaign/manifest.json"),
                Arguments.of("published-adventure", "/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void roundTripPreservesSemanticState(String label, String manifestPath) throws Exception {
        CampaignManifestV2 manifest;
        try (var input = getClass().getResourceAsStream(manifestPath)) {
            manifest = JsonMapper.builder().build().readValue(input, CampaignManifestV2.class);
        }

        Path staging = Files.createDirectories(temp.resolve("staging-" + label));
        Path manifestFile = Files.writeString(staging.resolve("manifest.json"),
                JsonMapper.builder().build().writeValueAsString(manifest));
        Map<String, Path> assetsByKey = new LinkedHashMap<>();
        if (manifest.assets() != null) {
            for (var asset : manifest.assets()) {
                Path assetFile = staging.resolve(asset.path());
                Files.createDirectories(assetFile.getParent());
                byte[] content = asset.mediaType().contains("png") ? ONE_BY_ONE_PNG : ONE_BY_ONE_WEBP;
                Files.write(assetFile, content);
                String sha256 = hex(MessageDigest.getInstance("SHA-256").digest(content));
                AssetDescriptor fixed = new AssetDescriptor(
                        asset.key(), asset.path(), asset.mediaType(),
                        content.length, sha256, asset.originalName());
                assetsByKey.put(asset.key(), assetFile);
                manifest = patchAsset(manifest, asset, fixed);
            }
        }
        Files.writeString(manifestFile, JsonMapper.builder().build().writeValueAsString(manifest));

        var staged = new StagedCampaignPackage(staging, manifestFile, assetsByKey,
                manifestFile.toFile().length(), manifestFile.toFile().length(),
                StagedCampaignPackage.ContainerKind.V2_JSON);
        var result = new CampaignPackageValidationResult(staged, manifest, 2, assetsByKey, List.of(), List.of());

        var preview = previews.retain(result);
        var campaign = importer.confirm(preview.previewId(), true);
        var snapshotA = snapshotService.snapshot(campaign.getId());
        assertThat(snapshotA.manifest().metadata().exclusions()).isEmpty();

        var artifact = exporter.export(campaign.getId());
        assertThat(artifact.manifest().metadata().exclusions()).isEmpty();

        var reStaged = new StagedCampaignPackage(
                Files.createDirectories(temp.resolve("reimport-" + label)),
                null, Map.of(), 0, 0, StagedCampaignPackage.ContainerKind.V2_JSON);
        var reManifest = artifact.manifest();
        Map<String, Path> reAssets = new LinkedHashMap<>();
        if (reManifest.assets() != null) {
            for (var asset : reManifest.assets()) {
                var source = artifact.assetSources().get(asset.key());
                if (source != null) {
                    Path reAssetFile = staging.resolve(asset.path());
                    Files.createDirectories(reAssetFile.getParent());
                    Files.write(reAssetFile, source.getInputStream().readAllBytes());
                    reAssets.put(asset.key(), reAssetFile);
                }
            }
        }
        var reResult = new CampaignPackageValidationResult(reStaged, reManifest, 2, reAssets, List.of(), List.of());
        var rePreview = previews.retain(reResult);
        var reCampaign = importer.confirm(rePreview.previewId(), true);
        var snapshotB = snapshotService.snapshot(reCampaign.getId());

        CampaignSemanticComparator.assertEquivalent(snapshotA, snapshotB);

        campaigns.delete(campaign.getId());
        campaigns.delete(reCampaign.getId());
    }

    private static CampaignManifestV2 patchAsset(CampaignManifestV2 m, AssetDescriptor oldDesc, AssetDescriptor newDesc) {
        if (m.assets() == null) return m;
        var assets = m.assets().stream()
                .map(a -> a.key().equals(oldDesc.key()) ? newDesc : a)
                .toList();
        return new CampaignManifestV2(m.formatVersion(), m.metadata(), m.campaign(),
                assets, m.party(), m.customStatBlocks(), m.handouts(),
                m.maps(), m.encounters(), m.notes(), m.quickNotes(),
                m.assignments(), m.ledgerEntries(), m.timelineEvents(),
                m.adventures(), m.diceRolls());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}
