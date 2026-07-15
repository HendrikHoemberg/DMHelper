package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignPackageReaderTest {

    @TempDir Path temp;

    @Test
    void detectsJsonVersionFromContentRatherThanSharedExtension() throws Exception {
        var reader = new CampaignPackageReader(temp);
        try (var staged = reader.read(new ByteArrayInputStream("{\"formatVersion\":2}".getBytes(StandardCharsets.UTF_8)),
                "campaign.dmcampaign.json", "application/json")) {
            assertThat(staged.containerKind()).isEqualTo(StagedCampaignPackage.ContainerKind.V2_JSON);
        }
    }

    @Test
    void rejectsBackslashesInsteadOfNormalizingThem() {
        assertThat(CampaignPackageReader.normalizePath("assets\\maps\\crypt.png")).isNull();
    }

    @Test
    void rejectsCaseFoldedDuplicatePaths() throws Exception {
        byte[] zip = zip(entry("manifest.json", "{}"),
                entry("assets/maps/Crypt.png", "one"),
                entry("assets/maps/crypt.png", "two"));
        var reader = new CampaignPackageReader(temp);

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(zip), "campaign.dmcampaign",
                "application/vnd.dmhelper.campaign+zip"))
                .isInstanceOf(CampaignPackageException.class)
                .satisfies(error -> assertThat(((CampaignPackageException) error).problem().code())
                        .isEqualTo("DUPLICATE_NORMALIZED_PATH"));
    }

    @Test
    void rejectsUnixSymlinkEntries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipArchiveOutputStream(bytes)) {
            var manifest = new ZipArchiveEntry("manifest.json");
            zip.putArchiveEntry(manifest);
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
            var link = new ZipArchiveEntry("assets/maps/link.png");
            link.setUnixMode(0120777);
            zip.putArchiveEntry(link);
            zip.write("target".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
        }

        var reader = new CampaignPackageReader(temp);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes.toByteArray()), "campaign.dmcampaign",
                "application/vnd.dmhelper.campaign+zip"))
                .isInstanceOf(CampaignPackageException.class)
                .satisfies(error -> assertThat(((CampaignPackageException) error).problem().code())
                        .isEqualTo("SYMLINK_ENTRY"));
    }

    private static Entry entry(String name, String contents) {
        return new Entry(name, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(Entry... entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipArchiveOutputStream(bytes)) {
            for (Entry value : entries) {
                zip.putArchiveEntry(new ZipArchiveEntry(value.name()));
                zip.write(value.contents());
                zip.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }

    private record Entry(String name, byte[] contents) {}
}
