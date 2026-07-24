package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        Files.createDirectory(stageDir);
        try {
            if (isZip(originalFilename, mediaType)) return readZip(input, stageDir);
            if (!"application/json".equals(mediaType)) {
                throw problem(ImportProblemCodes.UNSUPPORTED_MEDIA_TYPE, "Only campaign JSON or ZIP packages are supported");
            }
            return readJson(input, stageDir);
        } catch (CampaignPackageException e) {
            cleanup(stageDir);
            throw e;
        } catch (Exception e) {
            cleanup(stageDir);
            throw problem(ImportProblemCodes.PACKAGE_READ_ERROR, "The campaign package could not be read");
        }
    }

    private static boolean isZip(String filename, String mediaType) {
        return "application/vnd.dmhelper.campaign+zip".equals(mediaType)
                || filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".dmcampaign")
                && !filename.toLowerCase(Locale.ROOT).endsWith(".dmcampaign.json");
    }

    private StagedCampaignPackage readJson(InputStream input, Path stageDir) throws IOException {
        Path manifest = stageDir.resolve("manifest.json");
        long bytes = copyBounded(input, manifest, limits.maxManifestBytes(), ImportProblemCodes.MANIFEST_TOO_LARGE);
        int version;
        try (var in = Files.newInputStream(manifest)) {
            var root = JsonMapper.builder().build().readTree(in);
            version = root != null && root.has("formatVersion") && root.get("formatVersion").isIntegralNumber()
                    ? root.get("formatVersion").intValue() : -1;
        } catch (Exception e) {
            throw problem(ImportProblemCodes.INVALID_JSON, "Manifest is not valid JSON");
        }
        var kind = switch (version) {
            case 1 -> StagedCampaignPackage.ContainerKind.V1_JSON;
            case 2 -> StagedCampaignPackage.ContainerKind.V2_JSON;
            case 3 -> StagedCampaignPackage.ContainerKind.V3_JSON;
            default -> throw problem(ImportProblemCodes.UNSUPPORTED_FORMAT_VERSION, "Unsupported or missing formatVersion");
        };
        return new StagedCampaignPackage(stageDir, manifest, Map.of(), bytes, bytes, kind);
    }

    private StagedCampaignPackage readZip(InputStream input, Path stageDir) throws IOException {
        Path upload = stageDir.resolve("upload.zip");
        long uploaded = copyBounded(input, upload, limits.maxUploadBytes(), ImportProblemCodes.PACKAGE_TOO_LARGE);
        try (ZipFile zip = ZipFile.builder().setPath(upload).get()) {
            var entries = zip.getEntries();
            int count = 0;
            long expanded = 0;
            Path manifest = null;
            Map<String, Path> assets = new LinkedHashMap<>();
            Set<String> foldedPaths = new HashSet<>();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (++count > limits.maxEntries()) throw problem(ImportProblemCodes.TOO_MANY_ENTRIES, "ZIP entry limit exceeded");
                if (entry.getGeneralPurposeBit().usesEncryption()) throw problem(ImportProblemCodes.ENCRYPTED_ENTRY, "Encrypted ZIP entries are not supported");
                if (entry.isUnixSymlink()) throw problem(ImportProblemCodes.SYMLINK_ENTRY, "Symbolic-link ZIP entries are not supported");
                if (entry.isDirectory()) continue;

                String normalized = normalizePath(entry.getName());
                if (normalized == null) throw problem(ImportProblemCodes.TRAVERSAL_ASSET_PATH, "ZIP contains an invalid path");
                if (normalized.length() > limits.maxNormalizedPathLength()) throw problem(ImportProblemCodes.PATH_TOO_LONG, "ZIP path is too long");
                if (!foldedPaths.add(normalized.toLowerCase(Locale.ROOT))) {
                    throw problem(ImportProblemCodes.DUPLICATE_NORMALIZED_PATH, "ZIP contains duplicate normalized paths");
                }

                long maximum;
                String limitCode;
                if ("manifest.json".equals(normalized)) {
                    if (manifest != null) throw problem(ImportProblemCodes.DUPLICATE_MANIFEST, "ZIP contains multiple manifests");
                    maximum = limits.maxManifestBytes();
                    limitCode = ImportProblemCodes.MANIFEST_TOO_LARGE;
                } else if (isAllowedAssetPath(normalized)) {
                    maximum = limits.maxAssetBytes();
                    limitCode = ImportProblemCodes.ASSET_TOO_LARGE;
                } else {
                    throw problem(ImportProblemCodes.UNEXPECTED_ROOT_ENTRY, "ZIP contains an unsupported entry");
                }

                long declared = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (declared > maximum) throw problem(limitCode, "ZIP entry exceeds its size limit");
                if (compressed > 0 && declared >= 0 && (double) declared / compressed > limits.maxCompressionRatio()) {
                    throw problem(ImportProblemCodes.COMPRESSION_RATIO_EXCEEDED, "ZIP entry compression ratio is unsafe");
                }

                Path destination = stageDir.resolve(normalized).normalize();
                if (!destination.startsWith(stageDir)) throw problem(ImportProblemCodes.TRAVERSAL_ASSET_PATH, "ZIP path escapes staging");
                if (destination.getParent() != null) Files.createDirectories(destination.getParent());
                long actual;
                try (var entryInput = zip.getInputStream(entry)) {
                    actual = copyBounded(entryInput, destination, maximum, limitCode);
                }
                if (compressed > 0 && (double) actual / compressed > limits.maxCompressionRatio()) {
                    throw problem(ImportProblemCodes.COMPRESSION_RATIO_EXCEEDED, "ZIP entry compression ratio is unsafe");
                }
                expanded += actual;
                if (expanded > limits.maxExpandedBytes()) {
                    throw problem(ImportProblemCodes.PACKAGE_EXPANDED_TOO_LARGE, "Expanded package exceeds its size limit");
                }
                if ("manifest.json".equals(normalized)) manifest = destination;
                else assets.put(normalized, destination);
            }
            if (manifest == null) throw problem(ImportProblemCodes.MANIFEST_MISSING, "ZIP does not contain manifest.json");
            return new StagedCampaignPackage(stageDir, manifest, assets, uploaded, expanded,
                    StagedCampaignPackage.ContainerKind.V2_ZIP);
        }
    }

    private static boolean isAllowedAssetPath(String value) {
        return value.startsWith("assets/maps/")
                || value.startsWith("assets/handouts/")
                || value.startsWith("assets/portraits/");
    }

    private static long copyBounded(InputStream input, Path destination, long maximum, String code) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw problem(code, "Content exceeds its size limit");
                output.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(destination);
            throw e;
        }
        return total;
    }

    public static String normalizePath(String name) {
        if (name == null || name.indexOf('\\') >= 0) return null;
        String value = Normalizer.normalize(name, Normalizer.Form.NFC);
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) return null;
        String[] parts = value.split("/", -1);
        for (String part : parts) if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return null;
        return value;
    }

    private static CampaignPackageException problem(String code, String message) {
        return new CampaignPackageException(new CampaignImportProblem(ImportSeverity.ERROR, code, "", message, null));
    }

    private static void cleanup(Path directory) {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
