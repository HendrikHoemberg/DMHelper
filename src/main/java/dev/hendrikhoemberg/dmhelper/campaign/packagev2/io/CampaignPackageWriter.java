package dev.hendrikhoemberg.dmhelper.campaign.packagev2.io;

import org.springframework.core.io.InputStreamSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CampaignPackageWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public void write(CampaignPackageWriteRequest request, OutputStream output) throws IOException {
        if (request.assetSources().isEmpty()) {
            String json = MAPPER.writeValueAsString(request.manifest());
            output.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            try (var zos = new ZipOutputStream(output)) {
                ZipEntry manifestEntry = new ZipEntry("manifest.json");
                manifestEntry.setTime(0);
                zos.putNextEntry(manifestEntry);
                String json = MAPPER.writeValueAsString(request.manifest());
                zos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                var sortedKeys = new TreeMap<>(request.assetSources());
                for (var entry : sortedKeys.entrySet()) {
                    ZipEntry assetEntry = new ZipEntry("assets/" + entry.getKey());
                    assetEntry.setTime(0);
                    zos.putNextEntry(assetEntry);
                    entry.getValue().getInputStream().transferTo(zos);
                    zos.closeEntry();
                }
            }
        }
    }
}
