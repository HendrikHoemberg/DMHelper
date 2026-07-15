package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CampaignPackageReader {

    private final Path stagingRoot;
    private final CampaignPackageLimits limits;

    public CampaignPackageReader(Path stagingRoot, CampaignPackageLimits limits) {
        this.stagingRoot = stagingRoot;
        this.limits = limits;
    }

    public CampaignPackageReader(Path stagingRoot) {
        this(stagingRoot, CampaignPackageLimits.defaults());
    }

    public StagedCampaignPackage read(InputStream input, String originalFilename, String mediaType) throws IOException {
        Files.createDirectories(stagingRoot);
        Path stageDir = stagingRoot.resolve(UUID.randomUUID().toString());
        Files.createDirectories(stageDir);

        try {
            if ("application/vnd.dmhelper.campaign+zip".equals(mediaType)
                    || (originalFilename != null && originalFilename.toLowerCase().endsWith(".dmcampaign")
                    && !originalFilename.toLowerCase().endsWith(".dmcampaign.json"))) {
                return readZip(input, stageDir);
            }
            return readJson(input, stageDir, originalFilename);
        } catch (CampaignPackageException e) {
            cleanup(stageDir);
            throw e;
        } catch (Exception e) {
            cleanup(stageDir);
            throw new CampaignPackageException(
                    new CampaignImportProblem(ImportSeverity.ERROR, "PACKAGE_READ_ERROR", "",
                            "Failed to read package: " + e.getMessage(), null));
        }
    }

    private StagedCampaignPackage readJson(InputStream input, Path stageDir, String originalFilename) throws IOException {
        Path manifestPath = stageDir.resolve("manifest.json");
        long bytesCopied = Files.copy(input, manifestPath, StandardCopyOption.REPLACE_EXISTING);
        if (bytesCopied > limits.maxManifestBytes()) {
            cleanup(stageDir);
            throw new CampaignPackageException(new CampaignImportProblem(
                    ImportSeverity.ERROR, "MANIFEST_TOO_LARGE", "",
                    "Manifest exceeds " + limits.maxManifestBytes() + " bytes", null));
        }

        StagedCampaignPackage.ContainerKind kind = originalFilename != null
                && originalFilename.toLowerCase().endsWith(".dmcampaign.json")
                ? StagedCampaignPackage.ContainerKind.V1_JSON
                : StagedCampaignPackage.ContainerKind.V2_JSON;

        return new StagedCampaignPackage(stageDir, manifestPath, Map.of(), bytesCopied, bytesCopied, kind);
    }

    private StagedCampaignPackage readZip(InputStream input, Path stageDir) throws IOException {
        Path uploadPath = stageDir.resolve("upload.zip");
        byte[] buffer = new byte[8192];
        long total = 0;
        try (var out = Files.newOutputStream(uploadPath, StandardOpenOption.CREATE_NEW)) {
            int n;
            while ((n = input.read(buffer)) != -1) {
                total += n;
                if (total > limits.maxUploadBytes()) {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "PACKAGE_TOO_LARGE", "",
                            "Upload exceeds " + limits.maxUploadBytes() + " bytes", null));
                }
                out.write(buffer, 0, n);
            }
        }
        if (total > limits.maxUploadBytes()) {
            cleanup(stageDir);
            throw new CampaignPackageException(new CampaignImportProblem(
                    ImportSeverity.ERROR, "PACKAGE_TOO_LARGE", "",
                    "Upload exceeds " + limits.maxUploadBytes() + " bytes", null));
        }

        try (ZipFile zip = ZipFile.builder().setPath(uploadPath).get()) {
            var entries = zip.getEntries();
            int entryCount = 0;
            Path manifestPath = null;
            Map<String, Path> assets = new LinkedHashMap<>();
            long expandedBytes = 0;

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "TOO_MANY_ENTRIES", "",
                            "ZIP contains more than " + limits.maxEntries() + " entries", null));
                }
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "ENCRYPTED_ENTRY", "",
                            "ZIP contains encrypted entry: " + entry.getName(), null));
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String normalized = normalizePath(name);
                if (normalized == null) {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "TRAVERSAL_ASSET_PATH", "",
                            "Invalid path: " + name, null));
                }
                if (normalized.length() > limits.maxNormalizedPathLength()) {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "PATH_TOO_LONG", "",
                            "Path exceeds " + limits.maxNormalizedPathLength() + " chars: " + normalized, null));
                }

                if (normalized.equals("manifest.json")) {
                    if (manifestPath != null) {
                        cleanup(stageDir);
                        throw new CampaignPackageException(new CampaignImportProblem(
                                ImportSeverity.ERROR, "DUPLICATE_MANIFEST", "",
                                "Multiple manifest.json entries", null));
                    }
                    manifestPath = extractEntry(zip, entry, stageDir.resolve(normalized));
                } else if (normalized.startsWith("assets/")) {
                    if (assets.containsKey(normalized)) {
                        cleanup(stageDir);
                        throw new CampaignPackageException(new CampaignImportProblem(
                                ImportSeverity.ERROR, "DUPLICATE_NORMALIZED_PATH", "",
                                "Duplicate asset path: " + normalized, null));
                    }
                    long size = entry.getSize();
                    if (size > limits.maxAssetBytes()) {
                        cleanup(stageDir);
                        throw new CampaignPackageException(new CampaignImportProblem(
                                ImportSeverity.ERROR, "ASSET_TOO_LARGE", "",
                                "Asset exceeds " + limits.maxAssetBytes() + " bytes: " + normalized, null));
                    }
                    long compressedSize = entry.getCompressedSize();
                    if (compressedSize > 0 && (double) size / compressedSize > limits.maxCompressionRatio()) {
                        cleanup(stageDir);
                        throw new CampaignPackageException(new CampaignImportProblem(
                                ImportSeverity.ERROR, "COMPRESSION_RATIO_EXCEEDED", "",
                                "Unreasonable compression ratio for: " + normalized, null));
                    }
                    expandedBytes += size;
                    if (expandedBytes > limits.maxExpandedBytes()) {
                        cleanup(stageDir);
                        throw new CampaignPackageException(new CampaignImportProblem(
                                ImportSeverity.ERROR, "PACKAGE_EXPANDED_TOO_LARGE", "",
                                "Expanded content exceeds " + limits.maxExpandedBytes() + " bytes", null));
                    }
                    Path dest = stageDir.resolve(normalized);
                    Files.createDirectories(dest.getParent());
                    extractEntry(zip, entry, dest);
                    assets.put(normalized, dest);
                } else {
                    cleanup(stageDir);
                    throw new CampaignPackageException(new CampaignImportProblem(
                            ImportSeverity.ERROR, "UNEXPECTED_ROOT_ENTRY", "",
                            "Unexpected root entry: " + normalized, null));
                }
            }

            if (manifestPath == null) {
                cleanup(stageDir);
                throw new CampaignPackageException(new CampaignImportProblem(
                        ImportSeverity.ERROR, "MANIFEST_MISSING", "",
                        "No manifest.json in ZIP", null));
            }

            byte[] manifestBytes = Files.readAllBytes(manifestPath);
            if (manifestBytes.length > limits.maxManifestBytes()) {
                cleanup(stageDir);
                throw new CampaignPackageException(new CampaignImportProblem(
                        ImportSeverity.ERROR, "MANIFEST_TOO_LARGE", "",
                        "Manifest exceeds " + limits.maxManifestBytes() + " bytes", null));
            }

            return new StagedCampaignPackage(stageDir, manifestPath, assets,
                    total, expandedBytes, StagedCampaignPackage.ContainerKind.V2_ZIP);
        }
    }

    private static Path extractEntry(ZipFile zip, ZipArchiveEntry entry, Path dest) throws IOException {
        try (var in = zip.getInputStream(entry)) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return dest;
    }

    static String normalizePath(String name) {
        if (name == null) return null;
        String s = name.replace('\\', '/');
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
        if (s.startsWith("/") || s.contains("/../") || s.contains("/./") || s.startsWith("../")) return null;
        if (s.equals("..") || s.endsWith("/..")) return null;
        while (s.startsWith("./")) s = s.substring(2);
        if (s.contains("/")) {
            for (String part : s.split("/")) {
                if (part.equals(".") || part.equals("..")) return null;
                if (part.isEmpty()) return null;
            }
        }
        return s;
    }

    private static void cleanup(Path dir) {
        if (Files.exists(dir)) {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }
}
