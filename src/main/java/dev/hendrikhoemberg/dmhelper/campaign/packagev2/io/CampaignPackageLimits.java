package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

public record CampaignPackageLimits(
        long maxUploadBytes,
        int maxEntries,
        long maxManifestBytes,
        long maxAssetBytes,
        long maxExpandedBytes,
        double maxCompressionRatio,
        int maxNormalizedPathLength
) {
    public static CampaignPackageLimits defaults() {
        return new CampaignPackageLimits(
                1_073_741_824L, 2_000, 10_485_760L,
                104_857_600L, 1_073_741_824L, 100.0d, 240);
    }
}
