package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportProblemCodes;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

public final class AssetSignatureValidator {

    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private AssetSignatureValidator() {}

    public static CampaignImportProblem validate(Path file, AssetDescriptor descriptor) {
        if (!SUPPORTED_MEDIA_TYPES.contains(descriptor.mediaType())) {
            return problem(ImportProblemCodes.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type: " + descriptor.mediaType());
        }
        if (!fileExtensionMatches(descriptor)) {
            return problem(ImportProblemCodes.ASSET_EXTENSION_MISMATCH, "Extension mismatch for " + descriptor.originalName());
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != descriptor.sizeBytes()) {
                return problem(ImportProblemCodes.ASSET_SIZE_MISMATCH, "Declared " + descriptor.sizeBytes() + " bytes but file is " + bytes.length);
            }
            if (!signatureMatches(bytes, descriptor.mediaType())) {
                return problem(ImportProblemCodes.ASSET_SIGNATURE_MISMATCH, "File signature does not match declared media type: " + descriptor.mediaType());
            }
            String sha256 = computeSha256(bytes);
            if (!sha256.equals(descriptor.sha256())) {
                return problem(ImportProblemCodes.ASSET_DIGEST_MISMATCH, "SHA-256 mismatch for " + descriptor.key());
            }
            return null;
        } catch (IOException e) {
            return problem(ImportProblemCodes.ASSET_READ_ERROR, "Could not read staged asset: " + e.getMessage());
        }
    }

    static boolean mediaTypeSupported(String mediaType) {
        return SUPPORTED_MEDIA_TYPES.contains(mediaType);
    }

    static String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported media type: " + mediaType);
        };
    }

    private static boolean fileExtensionMatches(AssetDescriptor d) {
        String name = d.originalName() != null ? d.originalName().toLowerCase() : d.path().toLowerCase();
        String expected = switch (d.mediaType()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
        if (name.endsWith(".jpeg")) return expected.equals(".jpg");
        return name.endsWith(expected);
    }

    private static boolean signatureMatches(byte[] bytes, String mediaType) {
        if (bytes.length < 4) return false;
        return switch (mediaType) {
            case "image/png" -> startsWith(bytes, PNG_SIGNATURE);
            case "image/jpeg" -> (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[bytes.length - 2] & 0xFF) == 0xFF && (bytes[bytes.length - 1] & 0xFF) == 0xD9;
            case "image/gif" -> (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                    && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a');
            case "image/webp" -> (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F')
                    && bytes.length > 12 && (bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static CampaignImportProblem problem(String code, String message) {
        return new CampaignImportProblem(ImportSeverity.ERROR, code, "", message, null);
    }
}
