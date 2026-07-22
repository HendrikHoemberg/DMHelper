package dev.hendrikhoemberg.dmhelper.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PackageShapeProfileTest {

    static boolean realPackageIsPresent() {
        return Files.isRegularFile(PackageShapeProfileExtractor.REAL_PACKAGE);
    }

    @Test
    void committedProfileParsesAndCarriesNoContentStrings() throws IOException {
        PackageShapeProfile profile =
                PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);

        assertThat(profile.counts()).containsEntry("scenes", 90);
        assertThat(profile.populatedFields()).containsEntry("sceneParticipant.statblockRef", 65);

        String raw = Files.readString(PackageShapeProfileExtractor.COMMITTED_PROFILE);
        assertThat(raw)
                .as("the profile must carry statistics only -- never content from the adventure")
                .doesNotContain("Phandelver", "Phandalin", "Cragmaw", "Redbrand", "Rotbrenner",
                        "Schwertk", "Wave Echo", "Wellenhall");
    }

    @Test
    @EnabledIf("realPackageIsPresent")
    void extractorReproducesTheCommittedProfile() throws IOException {
        PackageShapeProfile regenerated =
                PackageShapeProfileExtractor.extract(PackageShapeProfileExtractor.REAL_PACKAGE);
        PackageShapeProfile committed =
                PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);

        assertThat(regenerated)
                .as("""
                    The committed profile no longer describes the package. If the package changed \
                    on purpose, refresh it: ./mvnw test-compile exec:java \
                    -Dexec.mainClass=dev.hendrikhoemberg.dmhelper.support.PackageShapeProfileExtractor \
                    -Dexec.classpathScope=test""")
                .isEqualTo(committed);
    }
}
