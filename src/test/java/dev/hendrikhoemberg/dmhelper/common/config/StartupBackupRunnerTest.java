package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StartupBackupRunnerTest {

    @Test
    void backsUpBothDataAndFilesDirectories(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");
        Path dmFiles = Files.createDirectories(home.resolve(".dmhelper/files"));
        Files.writeString(dmFiles.resolve("portrait.png"), "img-bytes");

        new StartupBackupRunner(home.toString()).run(null);

        Path backups = home.resolve(".dmhelper/backups");
        try (Stream<Path> snapshots = Files.list(backups)) {
            Path snapshot = snapshots.findFirst().orElseThrow();
            assertThat(snapshot.resolve("data/dmhelper.mv.db")).exists();
            assertThat(snapshot.resolve("files/portrait.png")).exists();
        }
    }

    @Test
    void skipsMissingFilesDirectory(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");

        new StartupBackupRunner(home.toString()).run(null);

        Path backups = home.resolve(".dmhelper/backups");
        try (Stream<Path> snapshots = Files.list(backups)) {
            Path snapshot = snapshots.findFirst().orElseThrow();
            assertThat(snapshot.resolve("data/dmhelper.mv.db")).exists();
            assertThat(Files.exists(snapshot.resolve("files"))).isFalse();
        }
    }

    @Test
    void keepsAtMostTenBackups(@TempDir Path home) throws Exception {
        Path dmData = Files.createDirectories(home.resolve(".dmhelper/data"));
        Files.writeString(dmData.resolve("dmhelper.mv.db"), "db-bytes");
        Path backups = Files.createDirectories(home.resolve(".dmhelper/backups"));
        for (int i = 0; i < 12; i++) {
            Files.createDirectory(backups.resolve("dmhelper-2020-" + String.format("%02d", i)));
        }

        new StartupBackupRunner(home.toString()).run(null);

        try (Stream<Path> remaining = Files.list(backups)) {
            assertThat(remaining.filter(Files::isDirectory).count()).isEqualTo(10);
        }
    }
}
