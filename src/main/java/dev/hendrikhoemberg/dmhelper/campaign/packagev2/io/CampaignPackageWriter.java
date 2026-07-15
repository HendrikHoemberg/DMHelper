package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CampaignPackageWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public void write(CampaignPackageWriteRequest request, OutputStream output) throws IOException {
        var descriptors = request.manifest().assets() == null ? java.util.List.<AssetDescriptor>of()
                : request.manifest().assets();
        Set<String> descriptorKeys = new HashSet<>();
        Set<String> descriptorPaths = new HashSet<>();
        for (AssetDescriptor descriptor : descriptors) {
            if (!descriptorKeys.add(descriptor.key())) throw new IllegalArgumentException("Duplicate asset key");
            String path = CampaignPackageReader.normalizePath(descriptor.path());
            if (path == null || !(path.startsWith("assets/maps/") || path.startsWith("assets/handouts/")
                    || path.startsWith("assets/portraits/"))) {
                throw new IllegalArgumentException("Invalid asset path");
            }
            if (!descriptorPaths.add(path.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate asset path");
            }
            if (!request.assetSources().containsKey(descriptor.key())) {
                throw new IllegalArgumentException("Missing source for asset " + descriptor.key());
            }
        }
        if (!descriptorKeys.equals(request.assetSources().keySet())) {
            throw new IllegalArgumentException("Asset source keys do not match manifest descriptors");
        }

        if (descriptors.isEmpty()) {
            output.write(MAPPER.writeValueAsBytes(request.manifest()));
            return;
        }

        try (var zip = new ZipOutputStream(output)) {
            put(zip, "manifest.json", MAPPER.writeValueAsBytes(request.manifest()));
            for (AssetDescriptor descriptor : descriptors.stream()
                    .sorted(Comparator.comparing(AssetDescriptor::path)).toList()) {
                ZipEntry entry = new ZipEntry(descriptor.path());
                entry.setTime(0);
                zip.putNextEntry(entry);
                MessageDigest digest = sha256();
                long total = 0;
                try (var input = request.assetSources().get(descriptor.key()).getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        digest.update(buffer, 0, read);
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
                if (total != descriptor.sizeBytes()
                        || !HexFormat.of().formatHex(digest.digest()).equals(descriptor.sha256())) {
                    throw new IllegalArgumentException("Asset bytes do not match descriptor " + descriptor.key());
                }
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
