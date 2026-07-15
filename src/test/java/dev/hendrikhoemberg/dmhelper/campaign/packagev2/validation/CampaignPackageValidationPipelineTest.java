package dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignPackageValidationPipelineTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @TempDir Path temp;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void rejectsUnresolvedPackageAndCatalogReferencesAndOutOfBoundsTokens() throws Exception {
        var resource = new ClassPathResource("campaigns/v2/current-surface.dmcampaign/manifest.json");
        ObjectNode root = (ObjectNode) mapper.readTree(resource.getInputStream());
        ((ObjectNode) root.get("encounters").get(0).get("mapRef")).put("key", "missing-map");
        ((ObjectNode) root.get("party").get(0).get("sheet").get("speciesRef"))
                .put("sourceKey", "missing-species");
        ((ObjectNode) root.get("maps").get(0).get("tokens").get(0)).put("positionX", 100000);
        byte[] json = mapper.writeValueAsBytes(root);
        var staged = new CampaignPackageReader(temp).read(new ByteArrayInputStream(json),
                "fixture.json", "application/json");

        var result = pipeline.validate(staged);

        assertThat(result.problems()).extracting(p -> p.code()).contains(
                "UNRESOLVED_REFERENCE", "UNRESOLVED_CATALOG_REFERENCE", "TOKEN_OUT_OF_BOUNDS");
    }

    @Test
    void validatesZipAssetUsingTheSameCanonicalDescriptorPath() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        ObjectNode root;
        try (var input = new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").getInputStream()) {
            root = (ObjectNode) mapper.readTree(input);
        }
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png));
        var assets = root.putArray("assets");
        assets.addObject().put("key", "map-image").put("path", "assets/maps/map.png")
                .put("mediaType", "image/png").put("sizeBytes", png.length).put("sha256", digest)
                .put("originalName", "map.png");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
            zip.write(mapper.writeValueAsBytes(root));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/maps/map.png"));
            zip.write(png);
            zip.closeEntry();
        }
        var staged = new CampaignPackageReader(temp).read(new ByteArrayInputStream(bytes.toByteArray()),
                "fixture.dmcampaign", "application/vnd.dmhelper.campaign+zip");

        assertThat(pipeline.validate(staged).problems())
                .noneMatch(problem -> problem.severity()
                        == dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity.ERROR);
    }
}
