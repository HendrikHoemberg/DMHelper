package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignPackageArchitectureTest {

    @TempDir
    Path tempDir;

    @Test
    void packageV2HasNoDependencyOnV2CompatibilityAdapter() throws IOException {
        Path packageV2 = Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2");
        try (Stream<Path> files = Files.walk(packageV2)) {
            files.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        assertThat(content)
                            .as("File " + p + " should not reference V2CompatibilityAdapter")
                            .doesNotContain("V2CompatibilityAdapter");
                        assertThat(content)
                            .as("File " + p + " should not reference PreparedV1Import")
                            .doesNotContain("PreparedV1Import");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    @Test
    void deletedFilesDoNotExist() {
        assertThat(Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/V2CompatibilityAdapter.java"))
            .doesNotExist();
        assertThat(Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/PreparedV1Import.java"))
            .doesNotExist();
        assertThat(Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPersistenceReceipt.java"))
            .doesNotExist();
        assertThat(Path.of("src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/HandoutImportSource.java"))
            .doesNotExist();
    }
}
