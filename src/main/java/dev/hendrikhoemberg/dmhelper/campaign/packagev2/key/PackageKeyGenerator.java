package dev.hendrikhoemberg.dmhelper.campaign.packagev2.key;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

public final class PackageKeyGenerator {

    private PackageKeyGenerator() {}

    public static String generate(CampaignContentType type, String displayName, String stableIdentity) {
        String slug = slugify(displayName);
        if (slug.isEmpty()) {
            slug = type.name().toLowerCase(Locale.ROOT);
        }
        String suffix = computeSuffix(type, stableIdentity);
        int maxSlugLen = 100 - 1 - suffix.length();
        if (slug.length() > maxSlugLen) {
            slug = slug.substring(0, maxSlugLen);
        }
        slug = stripEndPunctuation(slug);
        return slug + '-' + suffix;
    }

    private static String slugify(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        String noCombining = normalized.replaceAll("[\\p{M}]", "");
        String lower = noCombining.toLowerCase(Locale.ROOT);
        String replaced = lower.replaceAll("[^a-z0-9._-]+", "-");
        return stripEndPunctuation(replaced);
    }

    private static String stripEndPunctuation(String s) {
        return s.replaceAll("^[._-]+|[._-]+$", "");
    }

    private static String computeSuffix(CampaignContentType type, String stableIdentity) {
        String input = type.name() + ':' + stableIdentity;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
