package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class StartupBackupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupBackupRunner.class);
    private static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dataDir;
    private final Path backupDir;

    public StartupBackupRunner(@Value("${user.home}") String userHome) {
        this.dataDir = Path.of(userHome, ".dmhelper", "data");
        this.backupDir = Path.of(userHome, ".dmhelper", "backups");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Files.isDirectory(dataDir)) {
            log.info("No data directory at {} — skipping startup backup", dataDir);
            return;
        }

        try {
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FMT);
            Path snapshotDir = backupDir.resolve("dmhelper-" + timestamp);
            Files.createDirectory(snapshotDir);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
                for (Path file : stream) {
                    Files.copy(file, snapshotDir.resolve(file.getFileName()));
                }
            }

            log.info("Database backup created at {}", snapshotDir);

            rotateBackups();
        } catch (IOException e) {
            log.error("Failed to create startup backup", e);
        }
    }

    private void rotateBackups() throws IOException {
        try (Stream<Path> dirs = Files.list(backupDir)) {
            var backups = dirs
                    .filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                deleteRecursively(backups.get(i));
                log.info("Removed old backup: {}", backups.get(i));
            }
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", p, e);
                        }
                    });
        }
    }
}
