package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class StagedCampaignPackage implements Closeable {

    public enum ContainerKind { V1_JSON, V2_JSON, V2_ZIP }

    private final Path stagingDirectory;
    private final Path manifestPath;
    private final Map<String, Path> assetsByNormalizedPath;
    private final long uploadedBytes;
    private final long expandedBytes;
    private final ContainerKind containerKind;

    public StagedCampaignPackage(Path stagingDirectory, Path manifestPath,
                                  Map<String, Path> assetsByNormalizedPath,
                                  long uploadedBytes, long expandedBytes,
                                  ContainerKind containerKind) {
        this.stagingDirectory = stagingDirectory;
        this.manifestPath = manifestPath;
        this.assetsByNormalizedPath = Map.copyOf(assetsByNormalizedPath);
        this.uploadedBytes = uploadedBytes;
        this.expandedBytes = expandedBytes;
        this.containerKind = containerKind;
    }

    public Path stagingDirectory() { return stagingDirectory; }
    public Path manifestPath() { return manifestPath; }
    public Map<String, Path> assetsByNormalizedPath() { return assetsByNormalizedPath; }
    public long uploadedBytes() { return uploadedBytes; }
    public long expandedBytes() { return expandedBytes; }
    public ContainerKind containerKind() { return containerKind; }

    @Override
    public void close() {
        if (Files.exists(stagingDirectory)) {
            try (var files = Files.walk(stagingDirectory)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }
}
