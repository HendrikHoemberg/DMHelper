package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignPackageWriterTest {

    @TempDir Path temp;

    @Test
    void writesAssetAtDescriptorPathAndVerifiesItsBytes() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        CampaignManifestV2 manifest = withAsset(png, "assets/maps/crypt.png");
        var request = new CampaignPackageWriteRequest("crypt.dmcampaign", manifest,
                Map.of("crypt-image", new ByteArrayResource(png)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new CampaignPackageWriter().write(request, output);

        Path zipPath = temp.resolve("package.zip");
        Files.write(zipPath, output.toByteArray());
        try (ZipFile zip = ZipFile.builder().setPath(zipPath).get()) {
            assertThat(zip.getEntry("assets/maps/crypt.png")).isNotNull();
            assertThat(zip.getEntry("assets/crypt-image")).isNull();
        }
    }

    @Test
    void rejectsMissingOrDigestMismatchedSources() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        CampaignManifestV2 manifest = withAsset(png, "assets/maps/crypt.png");
        var missing = new CampaignPackageWriteRequest("crypt.dmcampaign", manifest, Map.of());
        var mismatch = new CampaignPackageWriteRequest("crypt.dmcampaign", manifest,
                Map.of("crypt-image", new ByteArrayResource(new byte[]{1, 2, 3})));

        assertThatThrownBy(() -> new CampaignPackageWriter().write(missing, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignPackageWriter().write(mismatch, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CampaignManifestV2 withAsset(byte[] bytes, String path) throws Exception {
        var mapper = JsonMapper.builder().build();
        try (var in = CampaignPackageWriterTest.class.getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            CampaignManifestV2 base = mapper.readValue(in, CampaignManifestV2.class);
            AssetDescriptor asset = new AssetDescriptor("crypt-image", path, "image/png", bytes.length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "crypt.png");
            return new CampaignManifestV2(base.formatVersion(), base.metadata(), base.campaign(), List.of(asset),
                    base.party(), base.customStatBlocks(),
                    base.customSpells(), base.customConditions(), base.customRules(),
                    base.customEquipment(), base.customMagicItems(), base.customClasses(), base.customSpecies(),
                    base.customBackgrounds(), base.customFeats(),
                    base.handouts(), base.maps(), base.encounters(),
                    base.notes(), base.quickNotes(), base.assignments(), base.ledgerEntries(), base.timelineEvents(),
                    base.adventures(), base.session(), base.diceRolls(), base.quests(), base.annotations());
        }
    }
}
